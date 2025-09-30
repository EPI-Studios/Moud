package com.moud.client.rendering.mesh;

import com.moud.api.math.Vector3;
import com.moud.api.rendering.mesh.Mesh;
import net.minecraft.util.math.BlockPos;

public final class ClientMeshInstance {
    private final long id;
    private Mesh mesh;
    private Vector3 position;
    private Vector3 rotation;
    private Vector3 scale;

    public ClientMeshInstance(long id, Mesh mesh, Vector3 position, Vector3 rotation, Vector3 scale) {
        this.id = id;
        this.mesh = mesh;
        this.position = new Vector3(position != null ? position : Vector3.zero());
        this.rotation = new Vector3(rotation != null ? rotation : Vector3.zero());
        this.scale = new Vector3(scale != null ? scale : Vector3.one());
    }

    public long getId() {
        return id;
    }

    public Mesh getMesh() {
        return mesh;
    }

    public void updateMesh(Mesh mesh) {
        this.mesh = mesh;
    }

    public Vector3 getPosition() {
        return new Vector3(position);
    }

    public Vector3 getRotation() {
        return new Vector3(rotation);
    }

    public Vector3 getScale() {
        return new Vector3(scale);
    }

    public void updateTransform(Vector3 position, Vector3 rotation, Vector3 scale) {
        if (position != null) {
            this.position = new Vector3(position);
        }
        if (rotation != null) {
            this.rotation = new Vector3(rotation);
        }
        if (scale != null) {
            this.scale = new Vector3(scale);
        }
    }

    public BlockPos getBlockPos() {
        Vector3 pos = position;
        return BlockPos.ofFloored(pos.x, pos.y, pos.z);
    }
}
