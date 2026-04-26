package com.moud.server.minestom.collision.source;

import com.moud.core.mesh.ArrayMesh;
import com.moud.core.mesh.Surface;
import com.moud.core.mesh.source.ArrayMeshResolver;
import com.moud.core.mesh.source.MeshSource;
import com.moud.core.mesh.source.MeshSourceCodec;
import com.moud.core.physics.CollisionGeometry;
import com.moud.core.scene.Node;

import java.util.Objects;

public final class ProceduralMeshGeometrySource implements CollisionGeometrySource {
    private final ArrayMeshResolver resolver;

    public ProceduralMeshGeometrySource(ArrayMeshResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @Override
    public boolean supports(String typeId) {
        return "MeshInstance3D".equals(typeId);
    }

    @Override
    public boolean canHandle(Node node, String typeId) {
        if (!supports(typeId) || node == null) {
            return false;
        }
        var raw = node.getProperty("mesh_source");
        return raw != null && !raw.isBlank();
    }

    @Override
    public CollisionGeometry extract(Node node, String typeId) {
        var raw = node.getProperty("mesh_source");
        if (raw == null || raw.isBlank()) {
            return CollisionGeometry.EMPTY;
        }
        MeshSource source;
        try {
            source = MeshSourceCodec.decode(raw);
        } catch (RuntimeException e) {
            return CollisionGeometry.EMPTY;
        }
        if (source == null) {
            return CollisionGeometry.EMPTY;
        }
        var resolved = resolver.resolve(source);
        if (resolved.isEmpty()) {
            return CollisionGeometry.EMPTY;
        }
        return flatten(resolved.get());
    }

    private static CollisionGeometry flatten(ArrayMesh mesh) {
        int totalVertexFloats = 0;
        int totalIndices = 0;
        for (Surface surface : mesh.surfaces()) {
            totalVertexFloats += surface.positions().length;
            totalIndices += surface.indices().length;
        }
        float[] vertices = new float[totalVertexFloats];
        int[] indices = new int[totalIndices];
        int vertexCursor = 0;
        int indexCursor = 0;
        int vertexBase = 0;
        for (Surface surface : mesh.surfaces()) {
            System.arraycopy(surface.positions(), 0, vertices, vertexCursor, surface.positions().length);
            vertexCursor += surface.positions().length;
            for (int idx : surface.indices()) {
                indices[indexCursor++] = idx + vertexBase;
            }
            vertexBase += surface.vertexCount();
        }
        return new CollisionGeometry(vertices, indices);
    }
}
