package com.moud.core.mesh.source;

import java.util.Objects;

public record InlineMesh(String hash, byte[] meshBinary) implements MeshSource {
    public InlineMesh {
        Objects.requireNonNull(hash, "hash");
        Objects.requireNonNull(meshBinary, "meshBinary");
    }
}
