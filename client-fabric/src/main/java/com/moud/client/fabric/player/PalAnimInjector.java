package com.moud.client.fabric.player;

import com.moud.client.fabric.assets.AssetsClient;
import com.moud.client.fabric.net.ClientSessionBus;
import com.moud.client.fabric.util.ClientDebugLog;
import com.moud.core.assets.AssetHash;
import com.moud.net.protocol.AssetManifestResponse;
import com.moud.net.protocol.AssetTransferStatus;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.loading.UniversalAnimLoader;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class PalAnimInjector implements AssetsClient.Listener {

    private static final String TAG = "PalAnimInjector";

    private final AssetsClient assets;
    private final Object lock = new Object();
    private Map<AssetHash, String> animHashToPath = new HashMap<>();

    public PalAnimInjector(AssetsClient assets) {
        this.assets = assets;
    }

    @Override
    public void onManifest(AssetManifestResponse response) {
        if (response == null || response.entries() == null) return;
        Map<AssetHash, String> next = new HashMap<>();
        for (AssetManifestResponse.Entry entry : response.entries()) {
            if (entry == null || entry.path() == null || entry.meta() == null) continue;
            String path = entry.path().value();
            if (isAnimationPath(path) && entry.meta().hash() != null) {
                next.put(entry.meta().hash(), path);
            }
        }

        Set<AssetHash> toDownload;
        synchronized (lock) {
            animHashToPath = next;
            toDownload = Set.copyOf(next.keySet());
        }

        Session s = ClientSessionBus.get();
        if (s != null && s.state() == SessionState.CONNECTED) {
            for (AssetHash hash : toDownload) {
                assets.download(s, hash);
            }
        }
    }

    @Override
    public void onDownloadComplete(AssetHash hash, AssetTransferStatus status, byte[] bytes, String message) {
        if (status != AssetTransferStatus.OK || bytes == null || hash == null) return;
        boolean isAnim;
        synchronized (lock) {
            isAnim = animHashToPath.containsKey(hash);
        }
        if (!isAnim) return;

        try {
            Map<String, Animation> loaded = UniversalAnimLoader.loadAnimations(new ByteArrayInputStream(bytes));
            if (loaded == null || loaded.isEmpty()) return;

            Field f = PlayerAnimResources.class.getDeclaredField("ANIMATIONS");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Identifier, Animation> animations = (Map<Identifier, Animation>) f.get(null);

            for (Map.Entry<String, Animation> e : loaded.entrySet()) {
                Identifier id = Identifier.of("moud", e.getKey());
                animations.put(id, e.getValue());
                ClientDebugLog.debug(TAG + " registered animation: moud:" + e.getKey());
            }
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Failed to inject animation blob hash=" + hash.hex() + ": " + e.getMessage());
        }
    }

    private static boolean isAnimationPath(String path) {
        if (path == null) return false;
        if (path.startsWith("res://animations/") && path.endsWith(".json")) return true;
        return path.endsWith(".animation.json");
    }
}
