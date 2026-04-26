package com.moud.core.mesh.source;

import com.moud.core.mesh.ArrayMesh;

import java.util.Optional;

public interface ArrayMeshResolver {
    Optional<ArrayMesh> resolve(MeshSource source);
}
