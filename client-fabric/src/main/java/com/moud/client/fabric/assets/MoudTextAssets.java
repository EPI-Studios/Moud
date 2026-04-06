package com.moud.client.fabric.assets;


import com.moud.client.fabric.render.VeilSceneNodeRenderer;
import com.moud.client.fabric.util.ClientDebugLog;
import com.moud.core.assets.AssetHash;
import com.moud.core.assets.AssetMeta;
import com.moud.core.assets.AssetType;
import com.moud.core.assets.ResPath;
import com.moud.net.protocol.AssetManifestResponse;
import com.moud.net.protocol.AssetTransferStatus;
import com.moud.client.fabric.net.ClientSessionBus;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class MoudTextAssets implements AssetsClient.Listener {
    private static final Object LOCK = new Object();
    private static MoudTextAssets instance;

    private static AssetsClient assets;
    private static long lastManifestRequestMs;

    private static Map<ResPath, AssetMeta> metaByPath = Map.of();
    private static List<String> textPaths = List.of();
    private static final Map<AssetHash, BlobEntry> blobsByHash = new HashMap<>();
    private static final Map<ResPath, String> overrideTextByPath = new HashMap<>();
    private static final Map<ResPath, Long> versionByPath = new HashMap<>();

    private MoudTextAssets() {
    }

    public static void init(AssetsClient assetsClient) {
        if (assetsClient == null) {
            return;
        }
        synchronized (LOCK) {
            assets = assetsClient;
            if (instance == null) {
                instance = new MoudTextAssets();
                assetsClient.addListener(instance);
            }
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            metaByPath = Map.of();
            textPaths = List.of();
            blobsByHash.clear();
            overrideTextByPath.clear();
            versionByPath.clear();
            lastManifestRequestMs = 0L;
        }
    }

    public static List<String> textAssetPaths() {
        synchronized (LOCK) {
            return textPaths;
        }
    }

    public static long versionOf(String resPathRaw) {
        if (resPathRaw == null || resPathRaw.isBlank()) {
            return 0L;
        }
        ResPath resPath;
        try {
            resPath = new ResPath(resPathRaw.trim());
        } catch (Exception ignored) {
            return 0L;
        }
        synchronized (LOCK) {
            return versionByPath.getOrDefault(resPath, 0L);
        }
    }

    public static boolean exists(String resPathRaw) {
        if (resPathRaw == null || resPathRaw.isBlank()) {
            return false;
        }
        ResPath resPath;
        try {
            resPath = new ResPath(resPathRaw.trim());
        } catch (Exception ignored) {
            return false;
        }
        synchronized (LOCK) {
            return overrideTextByPath.containsKey(resPath) || metaByPath.containsKey(resPath);
        }
    }

    public static String readText(String resPathRaw) {
        if (resPathRaw == null || resPathRaw.isBlank()) {
            return null;
        }
        ResPath resPath;
        try {
            resPath = new ResPath(resPathRaw.trim());
        } catch (Exception ignored) {
            return null;
        }

        AssetMeta meta;
        synchronized (LOCK) {
            String override = overrideTextByPath.get(resPath);
            if (override != null) {
                return override;
            }
            meta = metaByPath.get(resPath);
        }
        if (meta == null) {
            maybeRequestManifest();
            return null;
        }
        if (meta.hash() == null) {
            return null;
        }
        String path = resPath.value().toLowerCase();
        boolean isTextType = meta.type() == AssetType.TEXT
                || path.endsWith(".moudmat")
                || path.endsWith(".moudshader")
                || path.endsWith(".json")
                || path.endsWith(".tres")
                || path.endsWith(".glsl")
                || path.contains("/materials/")
                || path.contains("/shaders/");

        if (!isTextType) {
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
                return entry.text;
            }
            if (entry.state == BlobState.REQUESTED) {
                return null;
            }
            if (entry.state == BlobState.FAILED) {
                return null;
            }
            entry.state = BlobState.REQUESTED;
        }

        requestDownload(meta.hash());
        return null;
    }

    public static void overrideText(String resPathRaw, String text) {
        if (resPathRaw == null || resPathRaw.isBlank()) {
            return;
        }
        ResPath path;
        try {
            path = new ResPath(resPathRaw.trim());
        } catch (Exception ignored) {
            return;
        }
        String next = text == null ? "" : text;
        synchronized (LOCK) {
            overrideTextByPath.put(path, next);
            versionByPath.merge(path, 1L, Long::sum);
        }
    }

    private static void maybeRequestManifest() {
        AssetsClient a;
        Session s;
        synchronized (LOCK) {
            a = assets;
        }
        if (a == null) {
            return;
        }
        s = ClientSessionBus.get();
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
        ArrayList<String> texts = new ArrayList<>();
        for (AssetManifestResponse.Entry entry : response.entries()) {
            if (entry == null || entry.path() == null || entry.meta() == null) {
                continue;
            }
            nextMeta.put(entry.path(), entry.meta());
            if (entry.meta().type() == AssetType.TEXT) {
                texts.add(entry.path().value());
            }
        }
        texts.sort(String::compareTo);
        synchronized (LOCK) {
            Map<ResPath, AssetMeta> oldMeta = metaByPath;
            for (Map.Entry<ResPath, AssetMeta> e : nextMeta.entrySet()) {
                AssetMeta prev = oldMeta.get(e.getKey());
                if (prev != null && !prev.hash().equals(e.getValue().hash())) {
                    blobsByHash.remove(prev.hash());
                    versionByPath.merge(e.getKey(), 1L, Long::sum);
                }
            }
            metaByPath = Map.copyOf(nextMeta);
            textPaths = List.copyOf(texts);
        }
        ClientDebugLog.debug("TextAssets manifest applied entries=" + nextMeta.size());
        VeilSceneNodeRenderer.clearMaterialTextureCache();
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
                entry.error = message == null ? "" : message;
            }
            return;
        }

        String text;
        try {
            text = new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            synchronized (LOCK) {
                entry.state = BlobState.FAILED;
                entry.error = e.getMessage() == null ? "decode failed" : e.getMessage();
            }
            return;
        }

        synchronized (LOCK) {
            entry.text = text;
            entry.error = "";
            entry.state = BlobState.READY;
            for (Map.Entry<ResPath, AssetMeta> e : metaByPath.entrySet()) {
                if (e == null || e.getKey() == null || e.getValue() == null || e.getValue().hash() == null) {
                    continue;
                }
                if (hash.equals(e.getValue().hash())) {
                    versionByPath.merge(e.getKey(), 1L, Long::sum);
                }
            }
        }
        ClientDebugLog.debug("TextAssets blob ready hash=" + hash.hex());
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
        private String text;
        private String error = "";

        private BlobEntry(AssetHash hash) {
            this.hash = Objects.requireNonNull(hash, "hash");
        }

        @Override
        public String toString() {
            return "BlobEntry{hash=" + hash.hex().substring(0, 8) + ", state=" + state.name().toLowerCase(Locale.ROOT) + "}";
        }
    }
}
