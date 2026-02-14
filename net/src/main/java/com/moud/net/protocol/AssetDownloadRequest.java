package com.moud.net.protocol;

import com.moud.core.assets.AssetHash;

public record AssetDownloadRequest(AssetHash hash) implements Message {
    @Override
    public MessageType type() {
        return MessageType.ASSET_DOWNLOAD_REQUEST;
    }
}

