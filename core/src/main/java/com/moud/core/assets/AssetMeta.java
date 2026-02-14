package com.moud.core.assets;

import java.util.Objects;

public record AssetMeta(AssetHash hash, long sizeBytes, AssetType type) {
    public AssetMeta {
        Objects.requireNonNull(hash, "hash");
        Objects.requireNonNull(type, "type");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must be >= 0");
        }
    }
}

