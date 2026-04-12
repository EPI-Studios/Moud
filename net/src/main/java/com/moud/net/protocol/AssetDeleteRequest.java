package com.moud.net.protocol;

import com.moud.core.assets.ResPath;

public record AssetDeleteRequest(long requestId, ResPath path) implements Message {
    @Override
    public MessageType type() {
        return MessageType.ASSET_DELETE_REQUEST;
    }
}
