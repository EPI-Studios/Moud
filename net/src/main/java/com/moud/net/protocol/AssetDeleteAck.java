package com.moud.net.protocol;

import com.moud.core.assets.ResPath;

public record AssetDeleteAck(long requestId, ResPath path, AssetTransferStatus status, String message) implements Message {
    @Override
    public MessageType type() {
        return MessageType.ASSET_DELETE_ACK;
    }
}
