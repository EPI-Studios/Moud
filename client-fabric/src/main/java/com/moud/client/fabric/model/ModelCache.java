package com.moud.client.fabric.model;

import com.moud.client.fabric.assets.AssetsClient;
import com.moud.client.fabric.model.loader.BbmodelLoader;
import com.moud.client.fabric.net.ClientSessionBus;
import com.moud.core.assets.AssetHash;
import com.moud.core.assets.AssetMeta;
import com.moud.core.assets.ResPath;
import com.moud.net.protocol.AssetManifestResponse;
import com.moud.net.protocol.AssetTransferStatus;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;

import java.util.HashMap;
import java.util.Map;

public final class ModelCache implements AssetsClient.Listener {
    private static final Object LOCK = new Object();
    private static ModelCache instance;
    private static AssetsClient assets;

    private static Map<ResPath, AssetMeta> manifest = Map.of();
    private static final Map<AssetHash, ModelEntry> entriesByHash = new HashMap<>();

    private ModelCache() {}

    public static void init(AssetsClient assetsClient) {
        if (assetsClient == null) return;
        synchronized (LOCK) {
            assets = assetsClient;
            if (instance == null) {
                instance = new ModelCache();
                assetsClient.addListener(instance);
            }
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            manifest = Map.of();
            entriesByHash.clear();
        }
    }

    public static ModelAsset get(String resPathStr) {
        if (resPathStr == null || resPathStr.isBlank()) return null;
        ResPath resPath;
        try { resPath = new ResPath(resPathStr); } catch (Exception e) { return null; }

        AssetMeta meta;
        synchronized (LOCK) { meta = manifest.get(resPath); }
        if (meta == null) return null;

        AssetHash hash = meta.hash();
        if (hash == null) return null;

        ModelEntry entry;
        synchronized (LOCK) {
            entry = entriesByHash.computeIfAbsent(hash, h -> new ModelEntry(h, resPathStr));
            if (entry.state == ModelState.READY)      return entry.asset;
            if (entry.state != ModelState.NEW)        return null;
            entry.state = ModelState.REQUESTED;
        }

        requestDownload(hash);
        return null;
    }

    private static void requestDownload(AssetHash hash) {
        AssetsClient a;
        synchronized (LOCK) { a = assets; }
        if (a == null) return;
        Session s = ClientSessionBus.get();
        if (s == null || s.state() != SessionState.CONNECTED) return;
        a.download(s, hash);
    }

    @Override
    public void onManifest(AssetManifestResponse response) {
        if (response == null || response.entries() == null) return;
        HashMap<ResPath, AssetMeta> next = new HashMap<>();
        for (AssetManifestResponse.Entry entry : response.entries()) {
            if (entry == null || entry.path() == null || entry.meta() == null) continue;
            String p = entry.path().value();
            if (p != null && p.endsWith(".bbmodel")) next.put(entry.path(), entry.meta());
        }
        synchronized (LOCK) { manifest = Map.copyOf(next); }
    }

    @Override
    public void onDownloadComplete(AssetHash hash, AssetTransferStatus status, byte[] bytes, String message) {
        if (hash == null) return;
        ModelEntry entry;
        synchronized (LOCK) { entry = entriesByHash.get(hash); }
        if (entry == null) return;

        if (status != AssetTransferStatus.OK || bytes == null || bytes.length == 0) {
            synchronized (LOCK) { entry.state = ModelState.FAILED; }
            return;
        }

        String path = entry.path;
        String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        if (name.endsWith(".bbmodel")) name = name.substring(0, name.length() - 8);

        byte[] data = bytes;
        String finalName = name;
        Thread.ofVirtual().name("moud-model-load").start(() -> {
            try {
                ModelAsset asset = BbmodelLoader.load(data, finalName);
                synchronized (LOCK) {
                    entry.asset = asset;
                    entry.state = ModelState.READY;
                }
            } catch (Exception e) {
                synchronized (LOCK) { entry.state = ModelState.FAILED; }
            }
        });
    }

    private enum ModelState { NEW, REQUESTED, READY, FAILED }

    private static final class ModelEntry {
        final AssetHash hash;
        final String path;
        ModelAsset asset;
        ModelState state = ModelState.NEW;

        ModelEntry(AssetHash hash, String path) {
            this.hash = hash;
            this.path = path;
        }
    }
}
