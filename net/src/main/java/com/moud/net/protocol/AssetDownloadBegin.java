package com.moud.net.protocol;

import com.moud.core.assets.AssetHash;
import com.moud.core.assets.AssetType;

public record AssetDownloadBegin(
        AssetHash hash,
        long sizeBytes,
        AssetType assetType,
        AssetTransferStatus status,
        String message
) implements Message {
    @Override
    public MessageType type() {
        return MessageType.ASSET_DOWNLOAD_BEGIN;
    }
}

