package com.moud.core.mesh.source;

import java.util.Objects;

public record HashRefMesh(String hash) implements MeshSource {
    public HashRefMesh {
        Objects.requireNonNull(hash, "hash");
        if (hash.isBlank()) {
            throw new IllegalArgumentException("hash must not be blank");
        }
    }
}
