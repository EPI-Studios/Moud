package com.moud.net.protocol;

public record AssetManifestRequest(long requestId) implements Message {
    @Override
    public MessageType type() {
        return MessageType.ASSET_MANIFEST_REQUEST;
    }
}

