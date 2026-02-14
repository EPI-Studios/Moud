package com.moud.net.protocol;

import com.moud.core.assets.AssetHash;
import com.moud.core.assets.AssetType;
import com.moud.core.assets.ResPath;

public record AssetUploadBegin(ResPath path, AssetHash hash, long sizeBytes, AssetType assetType) implements Message {
    @Override
    public MessageType type() {
        return MessageType.ASSET_UPLOAD_BEGIN;
    }
}

