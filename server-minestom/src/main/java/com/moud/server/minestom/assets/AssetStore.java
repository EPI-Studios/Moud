package com.moud.server.minestom.assets;

import com.moud.core.assets.AssetHash;
import com.moud.core.assets.AssetManifest;
import com.moud.core.assets.AssetMeta;
import com.moud.core.assets.ResPath;

import java.io.IOException;

public interface AssetStore {
    AssetManifest manifest();

    AssetMeta meta(ResPath path);

    AssetMeta metaByHash(AssetHash hash);

    boolean hasBlob(AssetHash hash);

    byte[] readBlob(AssetHash hash) throws IOException;

    void put(ResPath path, AssetMeta meta, byte[] bytes) throws IOException;

    void putMapping(ResPath path, AssetMeta meta) throws IOException;
}

