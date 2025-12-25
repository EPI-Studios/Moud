package com.moud.server.primitives;

import com.moud.api.math.MathUtils;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.plugin.api.services.primitives.PrimitiveHandle;
import com.moud.plugin.api.services.primitives.PrimitiveMaterial;
import com.moud.plugin.api.services.primitives.PrimitiveType;

import java.util.ArrayList;
import java.util.List;

public class PrimitiveInstance implements PrimitiveHandle {
    private final long id;
    private final PrimitiveType type;
    private final String groupId;
    private final PrimitiveServiceImpl service;
    private final PrimitiveMaterial material;
    private Vector3 position;
    private Quaternion rotation;
    private Vector3 scale;
    private List<Vector3> vertices;
    private List<Integer> indices;
    private boolean physicsDynamic = false;
    private float physicsMass = 1.0f;
    private boolean removed = false;
    private boolean dirty = false;
    private boolean materialDirty = false;
    private boolean verticesDirty = false;

    public PrimitiveInstance(long id, PrimitiveType type, Vector3 position, Quaternion rotation,
                             Vector3 scale, PrimitiveMaterial material, String groupId,
                             PrimitiveServiceImpl service) {
        this(id, type, position, rotation, scale, material, groupId, service, null, null);
    }

    public PrimitiveInstance(long id, PrimitiveType type, Vector3 position, Quaternion rotation,
                             Vector3 scale, PrimitiveMaterial material, String groupId,
                             PrimitiveServiceImpl service, List<Vector3> vertices, List<Integer> indices) {
        this.id = id;
        this.type = type;
        this.position = position != null ? new Vector3(position) : Vector3.zero();
        this.rotation = rotation != null ? new Quaternion(rotation.x, rotation.y, rotation.z, rotation.w) : Quaternion.identity();
        this.scale = scale != null ? new Vector3(scale) : Vector3.one();
        this.material = material != null ? material.copy() : PrimitiveMaterial.white();
        this.groupId = groupId;
        this.service = service;
        this.vertices = copyVertices(vertices);
        this.indices = copyIndices(indices);
    }

    public boolean isPhysicsDynamic() {
        return physicsDynamic;
    }

    public float getPhysicsMass() {
        return physicsMass;
    }

    void configurePhysics(boolean dynamic, float mass) {
        this.physicsDynamic = dynamic;
        this.physicsMass = Float.isFinite(mass) && mass > 0.0f ? mass : 1.0f;
    }

    void applyPhysicsTransform(Vector3 position, Quaternion rotation) {
        if (position != null) {
            this.position = new Vector3(position);
        }
        if (rotation != null) {
            this.rotation = new Quaternion(rotation.x, rotation.y, rotation.z, rotation.w);
        }
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public PrimitiveType getType() {
        return type;
    }

    @Override
    public String getGroupId() {
        return groupId;
    }

    @Override
    public Vector3 getPosition() {
        return position;
    }

    @Override
    public void setPosition(Vector3 position) {
        this.position = new Vector3(position);
        this.dirty = true;
        broadcastIfNotBatching();
    }

    @Override
    public Quaternion getRotation() {
        return rotation;
    }

    @Override
    public void setRotation(Quaternion rotation) {
        this.rotation = new Quaternion(rotation.x, rotation.y, rotation.z, rotation.w);
        this.dirty = true;
        broadcastIfNotBatching();
    }

    @Override
    public Vector3 getScale() {
        return scale;
    }

    @Override
    public void setScale(Vector3 scale) {
        this.scale = new Vector3(scale);
        this.dirty = true;
        broadcastIfNotBatching();
    }

    @Override
    public void setTransform(Vector3 position, Quaternion rotation, Vector3 scale) {
        if (position != null) this.position = new Vector3(position);
        if (rotation != null) this.rotation = new Quaternion(rotation.x, rotation.y, rotation.z, rotation.w);
        if (scale != null) this.scale = new Vector3(scale);
        this.dirty = true;
        broadcastIfNotBatching();
    }

    @Override
    public void setFromTo(Vector3 from, Vector3 to, float thickness) {
        Vector3 direction = to.subtract(from);
        float length = direction.length();
        if (length < MathUtils.EPSILON) {
            this.position = new Vector3(from);
            this.scale = new Vector3(thickness, thickness, thickness);
            this.rotation = Quaternion.identity();
        } else {
            this.position = from.add(to).multiply(0.5f);
            Vector3 normalizedDir = direction.normalize();
            this.rotation = Quaternion.fromToRotation(Vector3.up(), normalizedDir);
            this.scale = new Vector3(thickness, length, thickness);
        }
        this.dirty = true;
        broadcastIfNotBatching();
    }

    @Override
    public void setColor(float r, float g, float b) {
        this.material.r = r;
        this.material.g = g;
        this.material.b = b;
        this.materialDirty = true;
        broadcastIfNotBatching();
    }

    @Override
    public void setColor(float r, float g, float b, float a) {
        this.material.r = r;
        this.material.g = g;
        this.material.b = b;
        this.material.a = a;
        this.materialDirty = true;
        broadcastIfNotBatching();
    }

    @Override
    public void setUnlit(boolean unlit) {
        this.material.unlit = unlit;
        this.materialDirty = true;
        broadcastIfNotBatching();
    }

    @Override
    public void setRenderThroughBlocks(boolean enabled) {
        this.material.renderThroughBlocks = enabled;
        this.materialDirty = true;
        broadcastIfNotBatching();
    }

    @Override
    public void setDoubleSided(boolean doubleSided) {
        this.material.doubleSided = doubleSided;
        this.materialDirty = true;
        broadcastIfNotBatching();
    }

    @Override
    public void setTexture(String texturePath) {
        this.material.texture = texturePath;
        this.materialDirty = true;
        broadcastIfNotBatching();
    }

    @Override
    public void remove() {
        if (!removed) {
            removed = true;
            service.removePrimitiveInternal(this);
        }
    }

    @Override
    public boolean isRemoved() {
        return removed;
    }

    public PrimitiveMaterial getMaterial() {
        return material;
    }

    public List<Vector3> getVertices() {
        return vertices;
    }

    public List<Integer> getIndices() {
        return indices;
    }

    @Override
    public void setVertices(List<Vector3> vertices) {
        this.vertices = copyVertices(vertices);
        this.verticesDirty = true;
        broadcastIfNotBatching();
    }

    @Override
    public void setMesh(List<Vector3> vertices, List<Integer> indices) {
        this.vertices = copyVertices(vertices);
        this.indices = copyIndices(indices);
        this.verticesDirty = true;
        broadcastIfNotBatching();
    }

    public boolean isDirty() {
        return dirty;
    }

    public boolean isMaterialDirty() {
        return materialDirty;
    }

    public boolean isVerticesDirty() {
        return verticesDirty;
    }

    public void clearDirty() {
        dirty = false;
        materialDirty = false;
        verticesDirty = false;
    }

    private void broadcastIfNotBatching() {
        if (!service.isBatching() && !removed) {
            if (dirty) {
                service.broadcastTransform(this);
            }
            if (materialDirty) {
                service.broadcastMaterial(this);
            }
            if (verticesDirty) {
                service.broadcastVertices(this);
            }
            clearDirty();
        }
    }

    private List<Vector3> copyVertices(List<Vector3> verts) {
        List<Vector3> copy = new ArrayList<>();
        if (verts != null) {
            for (Vector3 v : verts) {
                if (v != null) {
                    copy.add(new Vector3(v));
                }
            }
        }
        return copy;
    }

    private List<Integer> copyIndices(List<Integer> inds) {
        List<Integer> copy = new ArrayList<>();
        if (inds != null) {
            for (Integer idx : inds) {
                if (idx != null) {
                    copy.add(idx);
                }
            }
        }
        return copy;
    }
}
