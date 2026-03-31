package com.moud.client.fabric.assets;

import com.moud.client.fabric.net.ClientSessionBus;
import com.moud.client.fabric.util.ClientDebugLog;
import com.moud.core.assets.AssetHash;
import com.moud.core.assets.AssetMeta;
import com.moud.core.assets.AssetType;
import com.moud.core.assets.ResPath;
import com.moud.net.protocol.AssetManifestResponse;
import com.moud.net.protocol.AssetTransferStatus;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MoudAudioAssets implements AssetsClient.Listener {
    private static final Object LOCK = new Object();
    private static MoudAudioAssets instance;

    private static AssetsClient assets;
    private static long lastManifestRequestMs;

    private static Map<ResPath, AssetMeta> metaByPath = Map.of();
    private static List<String> audioPaths = List.of();
    private static final Map<AssetHash, BlobEntry> blobsByHash = new HashMap<>();
    private static final Map<ResPath, Long> versionByPath = new HashMap<>();

    private MoudAudioAssets() {
    }

    public static void init(AssetsClient assetsClient) {
        if (assetsClient == null) {
            return;
        }
        synchronized (LOCK) {
            assets = assetsClient;
            if (instance == null) {
                instance = new MoudAudioAssets();
                assetsClient.addListener(instance);
            }
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            metaByPath = Map.of();
            audioPaths = List.of();
            blobsByHash.clear();
            versionByPath.clear();
            lastManifestRequestMs = 0L;
        }
    }

    public static List<String> audioAssetPaths() {
        synchronized (LOCK) {
            return audioPaths;
        }
    }

    public static long versionOf(String resPathRaw) {
        ResPath path = parse(resPathRaw);
        if (path == null) {
            return 0L;
        }
        synchronized (LOCK) {
            return versionByPath.getOrDefault(path, 0L);
        }
    }

    public static boolean exists(String resPathRaw) {
        ResPath path = parse(resPathRaw);
        if (path == null) {
            return false;
        }
        synchronized (LOCK) {
            return metaByPath.containsKey(path);
        }
    }

    public static byte[] readBytes(String resPathRaw) {
        ResPath path = parse(resPathRaw);
        if (path == null) {
            return null;
        }
        AssetMeta meta;
        synchronized (LOCK) {
            meta = metaByPath.get(path);
        }
        if (meta == null) {
            maybeRequestManifest();
            return null;
        }
        if (meta.hash() == null || meta.type() != AssetType.AUDIO) {
            return null;
        }

        BlobEntry entry;
        synchronized (LOCK) {
            entry = blobsByHash.get(meta.hash());
            if (entry == null) {
                entry = new BlobEntry(meta.hash());
                blobsByHash.put(meta.hash(), entry);
            }
            if (entry.state == BlobState.READY) {
                return entry.bytes;
            }
            if (entry.state == BlobState.REQUESTED || entry.state == BlobState.FAILED) {
                return null;
            }
            entry.state = BlobState.REQUESTED;
        }

        requestDownload(meta.hash());
        return null;
    }

    private static ResPath parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new ResPath(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void maybeRequestManifest() {
        AssetsClient a;
        synchronized (LOCK) {
            a = assets;
        }
        if (a == null) {
            return;
        }
        Session s = ClientSessionBus.get();
        if (s == null || s.state() != SessionState.CONNECTED) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastManifestRequestMs < 2_000L) {
            return;
        }
        lastManifestRequestMs = now;
        a.requestManifest(s);
    }

    private static void requestDownload(AssetHash hash) {
        AssetsClient a;
        synchronized (LOCK) {
            a = assets;
        }
        if (a == null || hash == null) {
            return;
        }
        Session s = ClientSessionBus.get();
        if (s == null || s.state() != SessionState.CONNECTED) {
            return;
        }
        a.download(s, hash);
    }

    @Override
    public void onManifest(AssetManifestResponse response) {
        if (response == null || response.entries() == null) {
            return;
        }
        HashMap<ResPath, AssetMeta> nextMeta = new HashMap<>();
        ArrayList<String> audios = new ArrayList<>();
        for (AssetManifestResponse.Entry entry : response.entries()) {
            if (entry == null || entry.path() == null || entry.meta() == null) {
                continue;
            }
            if (entry.meta().type() != AssetType.AUDIO) {
                continue;
            }
            nextMeta.put(entry.path(), entry.meta());
            audios.add(entry.path().value());
        }
        audios.sort(String::compareTo);
        synchronized (LOCK) {
            Map<ResPath, AssetMeta> oldMeta = metaByPath;
            for (Map.Entry<ResPath, AssetMeta> e : nextMeta.entrySet()) {
                AssetMeta prev = oldMeta.get(e.getKey());
                if (prev != null && !Objects.equals(prev.hash(), e.getValue().hash())) {
                    if (prev.hash() != null) {
                        blobsByHash.remove(prev.hash());
                    }
                    versionByPath.merge(e.getKey(), 1L, Long::sum);
                }
            }
            metaByPath = Map.copyOf(nextMeta);
            audioPaths = List.copyOf(audios);
        }
    }

    @Override
    public void onDownloadComplete(AssetHash hash, AssetTransferStatus status, byte[] bytes, String message) {
        if (hash == null) {
            return;
        }
        BlobEntry entry;
        synchronized (LOCK) {
            entry = blobsByHash.get(hash);
            if (entry == null) {
                entry = new BlobEntry(hash);
                blobsByHash.put(hash, entry);
            }
        }
        if (status != AssetTransferStatus.OK || bytes == null) {
            synchronized (LOCK) {
                entry.state = BlobState.FAILED;
            }
            return;
        }
        synchronized (LOCK) {
            entry.bytes = bytes;
            entry.state = BlobState.READY;
            for (Map.Entry<ResPath, AssetMeta> e : metaByPath.entrySet()) {
                if (e != null && e.getValue() != null && hash.equals(e.getValue().hash())) {
                    versionByPath.merge(e.getKey(), 1L, Long::sum);
                }
            }
        }
        ClientDebugLog.debug("AudioAssets blob ready hash=" + hash.hex());
    }

    private enum BlobState {
        NEW,
        REQUESTED,
        READY,
        FAILED
    }

    private static final class BlobEntry {
        private final AssetHash hash;
        private BlobState state = BlobState.NEW;
        private byte[] bytes;

        private BlobEntry(AssetHash hash) {
            this.hash = Objects.requireNonNull(hash, "hash");
        }
    }
}
