package com.moud.net.protocol;

import com.moud.core.assets.AssetMeta;
import com.moud.core.assets.ResPath;

import java.util.List;

public record AssetManifestResponse(long requestId, List<Entry> entries) implements Message {
    @Override
    public MessageType type() {
        return MessageType.ASSET_MANIFEST_RESPONSE;
    }

    public record Entry(ResPath path, AssetMeta meta) {
    }
}

