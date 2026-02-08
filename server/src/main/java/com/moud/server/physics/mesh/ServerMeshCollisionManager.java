package com.moud.server.physics.mesh;

import com.moud.api.collision.AABB;
import com.moud.api.collision.CollisionMesh;
import com.moud.api.collision.MeshCollider;
import com.moud.api.collision.MeshCollisionResult;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.entity.ModelManager;
import com.moud.server.proxy.ModelProxy;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.instance.Instance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public final class ServerMeshCollisionManager {
    private static final double MOVEMENT_EPS = 1e-6;
    private static final ServerMeshCollisionManager INSTANCE = new ServerMeshCollisionManager();

    public static ServerMeshCollisionManager getInstance() {
        return INSTANCE;
    }

    private final Map<Long, CollisionEntry> entries = new ConcurrentHashMap<>();

    private ServerMeshCollisionManager() {
    }

    public List<CollisionMesh> getMeshesNear(Instance instance, AABB queryBox) {
        List<CollisionMesh> result = new ArrayList<>();
        if (instance == null || queryBox == null) {
            return result;
        }

        List<ModelProxy> models = new ArrayList<>(ModelManager.getInstance().getAllModels());
        models.sort(Comparator.comparingLong(ModelProxy::getId));
        for (ModelProxy model : models) {
            if (model == null || !isMeshCollisionEnabled(model)) {
                continue;
            }

            Entity entity = model.getEntity();
            if (entity == null || entity.getInstance() != instance) {
                continue;
            }

            CollisionMesh mesh = getOrUpdateMesh(model, entity);
            if (mesh == null) {
                continue;
            }

            AABB bounds = mesh.getBounds();
            if (bounds != null && bounds.intersects(queryBox)) {
                result.add(mesh);
            }
        }

        return result;
    }

    public MeshCollisionResult collidePlayer(Instance instance, AABB playerBox, Vector3 movement, float stepHeight) {
        if (instance == null || playerBox == null || movement == null) {
            return MeshCollisionResult.none(movement);
        }

        AABB queryBox = playerBox.union(playerBox.moved(movement.x, movement.y, movement.z)).expanded(0.5, 0.5, 0.5);
        List<CollisionMesh> meshes = getMeshesNear(instance, queryBox);
        if (meshes.isEmpty()) {
            return MeshCollisionResult.none(movement);
        }

        Vector3 allowedMovement = movement;
        boolean horizontalCollision = false;
        boolean verticalCollision = false;

        for (CollisionMesh mesh : meshes) {
            Vector3 before = allowedMovement;
            MeshCollisionResult result = MeshCollider.collideWithStepUp(playerBox, allowedMovement, mesh, stepHeight);
            allowedMovement = result.allowedMovement();

            if (Math.abs(allowedMovement.x - before.x) > MOVEMENT_EPS
                    || Math.abs(allowedMovement.z - before.z) > MOVEMENT_EPS) {
                horizontalCollision = true;
            }
            if (Math.abs(allowedMovement.y - before.y) > MOVEMENT_EPS) {
                verticalCollision = true;
            }
        }

        return new MeshCollisionResult(allowedMovement, horizontalCollision, verticalCollision, Vector3.zero(), 0);
    }

    public void clearCache() {
        entries.clear();
    }

    private static boolean isMeshCollisionEnabled(ModelProxy model) {
        if (model == null) {
            return false;
        }
        MoudPackets.CollisionMode mode = model.getWireCollisionMode();
        if (mode == null || mode == MoudPackets.CollisionMode.BOX) {
            return false;
        }
        if (model.getCompressedVertices() == null || model.getCompressedIndices() == null) {
            model.ensureCollisionPayload();
        }
        return model.getCompressedVertices() != null && model.getCompressedIndices() != null;
    }

    private CollisionMesh getOrUpdateMesh(ModelProxy model, Entity entity) {
        long modelId = model.getId();
        String modelPath = model.getModelPath();
        if (modelPath == null || modelPath.isBlank()) {
            return null;
        }

        ModelCollisionLibrary.MeshData meshData = ModelCollisionLibrary.getMesh(modelPath);
        if (meshData == null || meshData.vertices() == null || meshData.indices() == null) {
            return null;
        }
        if (meshData.vertices().length < 9 || meshData.indices().length < 3) {
            return null;
        }

        Vector3 position = model.getPosition();
        if (position == null && entity != null) {
            Pos pos = entity.getPosition();
            position = new Vector3(pos.x(), pos.y(), pos.z());
        }

        Quaternion rotation = model.getRotation();
        Vector3 scale = model.getScale();

        CollisionEntry entry = entries.get(modelId);
        if (entry == null || !entry.modelPath.equals(modelPath)) {
            entry = new CollisionEntry(modelPath, meshData.vertices(), meshData.indices(), position, rotation, scale);
            entries.put(modelId, entry);
            return entry.worldMesh;
        }

        entry.update(position, rotation, scale);
        return entry.worldMesh;
    }

    private static final class CollisionEntry {
        private final String modelPath;
        private final float[] localVertices;
        private final int[] indices;

        private CollisionMesh worldMesh;
        private Vector3 basePosition = Vector3.zero();
        private Quaternion baseRotation = Quaternion.identity();
        private Vector3 baseScale = Vector3.one();

        private CollisionEntry(
                String modelPath,
                float[] localVertices,
                int[] indices,
                Vector3 pos,
                Quaternion rot,
                Vector3 scale
        ) {
            this.modelPath = modelPath;
            this.localVertices = localVertices;
            this.indices = indices;
            rebuild(pos, rot, scale);
        }

        private void update(Vector3 pos, Quaternion rot, Vector3 scale) {
            Quaternion rotation = rot != null ? rot : Quaternion.identity();
            Vector3 scaleVec = scale != null ? scale : Vector3.one();
            if (!sameRotation(rotation, baseRotation) || !sameScale(scaleVec, baseScale)) {
                rebuild(pos, rotation, scaleVec);
                return;
            }
            updatePosition(pos);
        }

        private void updatePosition(Vector3 pos) {
            if (worldMesh == null) {
                return;
            }
            Vector3 p = pos != null ? pos : Vector3.zero();
            Vector3 delta = p.subtract(basePosition);
            worldMesh.setOffset(delta.x, delta.y, delta.z);
        }

        private void rebuild(Vector3 pos, Quaternion rot, Vector3 scale) {
            Vector3 position = pos != null ? pos : Vector3.zero();
            Quaternion rotation = rot != null ? rot : Quaternion.identity();
            Vector3 scaleVec = scale != null ? scale : Vector3.one();

            float[] transformed = applyTransform(localVertices, position, rotation, scaleVec);
            this.worldMesh = new CollisionMesh(transformed, indices);
            this.basePosition = new Vector3(position);
            this.baseRotation = new Quaternion(rotation);
            this.baseScale = new Vector3(scaleVec);
        }

        private static boolean sameRotation(Quaternion a, Quaternion b) {
            if (a == b) {
                return true;
            }
            if (a == null || b == null) {
                return false;
            }
            return Math.abs(a.x - b.x) <= 1e-6f
                    && Math.abs(a.y - b.y) <= 1e-6f
                    && Math.abs(a.z - b.z) <= 1e-6f
                    && Math.abs(a.w - b.w) <= 1e-6f;
        }

        private static boolean sameScale(Vector3 a, Vector3 b) {
            if (a == b) {
                return true;
            }
            if (a == null || b == null) {
                return false;
            }
            return Math.abs(a.x - b.x) <= 1e-6f
                    && Math.abs(a.y - b.y) <= 1e-6f
                    && Math.abs(a.z - b.z) <= 1e-6f;
        }

        private static float[] applyTransform(float[] verts, Vector3 pos, Quaternion rot, Vector3 scale) {
            float[] out = new float[verts.length];
            Quaternion q = rot != null ? rot : Quaternion.identity();
            Vector3 s = scale != null ? scale : Vector3.one();
            Vector3 p = pos != null ? pos : Vector3.zero();
            for (int i = 0; i < verts.length; i += 3) {
                Vector3 v = new Vector3(verts[i], verts[i + 1], verts[i + 2]);
                v = new Vector3(v.x * s.x, v.y * s.y, v.z * s.z);
                v = rotate(v, q);
                v = new Vector3(v.x + p.x, v.y + p.y, v.z + p.z);
                out[i] = v.x;
                out[i + 1] = v.y;
                out[i + 2] = v.z;
            }
            return out;
        }

        private static Vector3 rotate(Vector3 v, Quaternion q) {
            double qx = q.x;
            double qy = q.y;
            double qz = q.z;
            double qw = q.w;
            double ix = qw * v.x + qy * v.z - qz * v.y;
            double iy = qw * v.y + qz * v.x - qx * v.z;
            double iz = qw * v.z + qx * v.y - qy * v.x;
            double iw = -qx * v.x - qy * v.y - qz * v.z;
            double rx = ix * qw + iw * -qx + iy * -qz - iz * -qy;
            double ry = iy * qw + iw * -qy + iz * -qx - ix * -qz;
            double rz = iz * qw + iw * -qz + ix * -qy - iy * -qx;
            return new Vector3(rx, ry, rz);
        }
    }
}
