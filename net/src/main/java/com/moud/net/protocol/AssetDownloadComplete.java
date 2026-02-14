package com.moud.net.protocol;

import com.moud.core.assets.AssetHash;

public record AssetDownloadComplete(AssetHash hash, AssetTransferStatus status, String message) implements Message {
    @Override
    public MessageType type() {
        return MessageType.ASSET_DOWNLOAD_COMPLETE;
    }
}

