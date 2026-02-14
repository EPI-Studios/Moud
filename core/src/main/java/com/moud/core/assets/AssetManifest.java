package com.moud.core.assets;

import java.util.Map;
import java.util.Objects;

public record AssetManifest(Map<ResPath, AssetMeta> entries) {
    public AssetManifest {
        if (entries == null) {
            entries = Map.of();
        } else {
            entries = Map.copyOf(entries);
        }
    }

    public AssetMeta get(ResPath path) {
        Objects.requireNonNull(path, "path");
        return entries.get(path);
    }
}

