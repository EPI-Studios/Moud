package com.moud.client.editor.assets;

import com.moud.client.network.ClientPacketWrapper;
import com.moud.network.MoudPackets;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class EditorAssetCatalog {
    private static final EditorAssetCatalog INSTANCE = new EditorAssetCatalog();

    private final CopyOnWriteArrayList<MoudPackets.EditorAssetDefinition> assets = new CopyOnWriteArrayList<>();
    private volatile boolean requested = false;

    private EditorAssetCatalog() {}

    public static EditorAssetCatalog getInstance() {
        return INSTANCE;
    }

    public void requestAssetsIfNeeded() {
        if (requested) {
            return;
        }
        requested = true;
        ClientPacketWrapper.sendToServer(new MoudPackets.RequestEditorAssetsPacket());
    }

    public void forceRefresh() {
        requested = false;
        requestAssetsIfNeeded();
    }

    public void handleAssetList(MoudPackets.EditorAssetListPacket packet) {
        assets.clear();
        assets.addAll(packet.assets());
    }

    public List<MoudPackets.EditorAssetDefinition> getAssets() {
        return Collections.unmodifiableList(assets);
    }
}
