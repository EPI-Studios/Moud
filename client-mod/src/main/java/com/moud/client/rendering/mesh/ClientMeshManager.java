package com.moud.client.rendering.mesh;

import com.moud.api.math.Vector3;
import com.moud.api.rendering.mesh.Mesh;
import com.moud.api.rendering.mesh.MeshData;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientMeshManager {
    private static final ClientMeshManager INSTANCE = new ClientMeshManager();

    private final Map<Long, ClientMeshInstance> meshes = new ConcurrentHashMap<>();

    private ClientMeshManager() {
    }

    public static ClientMeshManager getInstance() {
        return INSTANCE;
    }

    public ClientMeshInstance upsert(long meshId, MeshData data, Vector3 position, Vector3 rotation, Vector3 scale) {
        Mesh mesh = data.toMesh();
        return meshes.compute(meshId, (id, existing) -> {
            if (existing == null) {
                return new ClientMeshInstance(id, mesh, position, rotation, scale);
            }
            existing.updateMesh(mesh);
            existing.updateTransform(position, rotation, scale);
            return existing;
        });
    }

    public void updateTransform(long meshId, Vector3 position, Vector3 rotation, Vector3 scale) {
        ClientMeshInstance instance = meshes.get(meshId);
        if (instance != null) {
            instance.updateTransform(position, rotation, scale);
        }
    }

    public void remove(long meshId) {
        meshes.remove(meshId);
    }

    public Collection<ClientMeshInstance> getMeshes() {
        return meshes.values();
    }

    public boolean isEmpty() {
        return meshes.isEmpty();
    }

    public void clear() {
        meshes.clear();
    }
}
