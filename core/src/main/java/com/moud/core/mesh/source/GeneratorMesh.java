package com.moud.core.mesh.source;

import com.google.gson.JsonObject;

import java.util.Objects;

public record GeneratorMesh(
        String scriptPath,
        JsonObject params,
        long seed,
        MeshAuthority authority
) implements MeshSource {
    public GeneratorMesh {
        Objects.requireNonNull(scriptPath, "scriptPath");
        Objects.requireNonNull(authority, "authority");
        if (scriptPath.isBlank()) {
            throw new IllegalArgumentException("scriptPath must not be blank");
        }
        if (params == null) {
            params = new JsonObject();
        }
    }
}
