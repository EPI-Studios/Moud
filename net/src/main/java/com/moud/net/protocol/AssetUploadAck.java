package com.moud.net.protocol;

import com.moud.core.assets.AssetHash;
import com.moud.core.assets.ResPath;

public record AssetUploadAck(ResPath path, AssetHash hash, AssetTransferStatus status, String message) implements Message {
    @Override
    public MessageType type() {
        return MessageType.ASSET_UPLOAD_ACK;
    }
}

