package com.moud.net.protocol;

import com.moud.core.assets.AssetHash;
import com.moud.core.assets.ResPath;

public record AssetUploadComplete(ResPath path, AssetHash hash) implements Message {
    @Override
    public MessageType type() {
        return MessageType.ASSET_UPLOAD_COMPLETE;
    }
}

