package com.moud.core.mesh.source;

import java.util.Objects;

public record AssetRefMesh(String path) implements MeshSource {
    public AssetRefMesh {
        Objects.requireNonNull(path, "path");
        if (path.isBlank()) {
            throw new IllegalArgumentException("asset path must not be blank");
        }
    }
}
