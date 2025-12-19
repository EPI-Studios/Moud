package com.moud.client.model;

import com.moud.api.collision.OBB;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.client.util.ClientEntityResolver;
import com.moud.client.rendering.MeshBuffer;
import com.moud.client.util.OBJLoader;
import com.moud.network.MoudPackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class RenderableModel {
    public static final int FLOATS_PER_VERTEX = 8;

    private final long id;
    private final String modelPath;
    private float[] vertices;
    private int[] indices;
    private MeshBuffer meshBuffer;
    private Vector3 position = Vector3.zero();
    private Quaternion rotation = Quaternion.identity();
    private Vector3 scale = Vector3.one();

    private Vector3 renderPosition = Vector3.zero();
    private Vector3 previousRenderPosition = Vector3.zero();
    private Quaternion renderRotation = Quaternion.identity();
    private Quaternion previousRenderRotation = Quaternion.identity();
    private Vector3 renderScale = Vector3.one();
    private Vector3 previousRenderScale = Vector3.one();

    private Vector3 smoothingStartPosition = Vector3.zero();
    private Quaternion smoothingStartRotation = Quaternion.identity();
    private Vector3 smoothingStartScale = Vector3.one();
    private static final float DEFAULT_SMOOTHING_TICKS = 5.0f;
    private static final float SNAP_DISTANCE_SQ = 64.0f;
    private float smoothingDurationTicks = DEFAULT_SMOOTHING_TICKS;
    private float smoothingProgress = 1.0f;
    private boolean smoothingEnabled = true;
    private boolean firstUpdate = true;
    private Identifier texture = Identifier.of("minecraft", "textures/block/white_concrete.png");
    private double collisionWidth;
    private double collisionHeight;
    private double collisionDepth;
    private List<OBB> collisionBoxes = new ArrayList<>();
    private Vector3 meshMin;
    private Vector3 meshMax;

    private MoudPackets.DisplayAnchorType anchorType = MoudPackets.DisplayAnchorType.FREE;
    private UUID anchorEntityUuid;
    private Long anchorModelId;
    private BlockPos anchorBlockPos;
    private boolean anchorLocalSpace = true;
    private boolean anchorInheritRotation = true;
    private boolean anchorInheritScale = true;
    private boolean anchorIncludePitch = false;

    public RenderableModel(long id, String modelPath) {
        this.id = id;
        this.modelPath = modelPath;
    }

    public void uploadMesh(OBJLoader.OBJMesh meshData) {
        this.vertices = Arrays.copyOf(meshData.vertices(), meshData.vertices().length);
        this.indices = Arrays.copyOf(meshData.indices(), meshData.indices().length);
        computeMeshBounds();

        if (this.meshBuffer != null) {
            this.meshBuffer.close();
        }
        this.meshBuffer = new MeshBuffer(this.vertices, this.indices);
    }

    public void updateTransform(Vector3 pos, Quaternion rot, Vector3 scale) {
        if (pos == null || rot == null || scale == null) {
            return;
        }

        if (anchorType != null && anchorType != MoudPackets.DisplayAnchorType.FREE) {
            this.position = new Vector3(pos);
            this.rotation = new Quaternion(rot);
            this.scale = new Vector3(scale);
            this.firstUpdate = false;
            return;
        }

        this.position = new Vector3(pos);
        this.rotation = new Quaternion(rot);
        this.scale = new Vector3(scale);

        if (firstUpdate || !smoothingEnabled) {
            this.renderPosition = new Vector3(pos);
            this.previousRenderPosition = new Vector3(pos);
            this.smoothingStartPosition = new Vector3(pos);

            this.renderRotation = new Quaternion(rot);
            this.previousRenderRotation = new Quaternion(rot);
            this.smoothingStartRotation = new Quaternion(rot);

            this.renderScale = new Vector3(scale);
            this.previousRenderScale = new Vector3(scale);
            this.smoothingStartScale = new Vector3(scale);

            this.smoothingProgress = 1.0f;
            firstUpdate = false;
            return;
        }

        this.smoothingStartPosition = new Vector3(renderPosition);
        this.smoothingStartRotation = new Quaternion(renderRotation);
        this.smoothingStartScale = new Vector3(renderScale);

        if (smoothingEnabled) {
            float distanceSq = distanceSquared(renderPosition, position);
            if (distanceSq > SNAP_DISTANCE_SQ) {
                snapToTarget();
                return;
            }
            this.smoothingProgress = 0.0f;
        } else {
            snapToTarget();
        }
    }

    public void updateAnchor(MoudPackets.DisplayAnchorType type,
                             UUID entityUuid,
                             Long modelId,
                             Vector3 blockPosition,
                             Vector3 localPosition,
                             Quaternion localRotation,
                             Vector3 localScale,
                             boolean localSpace,
                             boolean inheritRotation,
                             boolean inheritScale,
                             boolean includePitch) {
        MoudPackets.DisplayAnchorType newType = type != null ? type : MoudPackets.DisplayAnchorType.FREE;

        if (anchorType != null && anchorType != MoudPackets.DisplayAnchorType.FREE
                && newType == MoudPackets.DisplayAnchorType.FREE) {
            WorldTransform world = computeWorldTransform(getCurrentTickDelta(), 0);
            anchorType = newType;
            anchorEntityUuid = null;
            anchorModelId = null;
            anchorBlockPos = null;
            anchorLocalSpace = true;
            anchorInheritRotation = true;
            anchorInheritScale = true;
            anchorIncludePitch = false;

            position = world.position();
            rotation = world.rotation();
            scale = world.scale();
            snapToTarget();
            return;
        }

        anchorType = newType;
        anchorEntityUuid = newType == MoudPackets.DisplayAnchorType.ENTITY ? entityUuid : null;
        anchorModelId = newType == MoudPackets.DisplayAnchorType.MODEL ? modelId : null;
        anchorBlockPos = newType == MoudPackets.DisplayAnchorType.BLOCK && blockPosition != null
                ? BlockPos.ofFloored(blockPosition.x, blockPosition.y, blockPosition.z)
                : null;
        anchorLocalSpace = localSpace;
        anchorInheritRotation = inheritRotation;
        anchorInheritScale = inheritScale;
        anchorIncludePitch = includePitch;

        if (newType != MoudPackets.DisplayAnchorType.FREE) {
            position = localPosition != null ? new Vector3(localPosition) : Vector3.zero();
            rotation = localRotation != null ? new Quaternion(localRotation) : Quaternion.identity();
            scale = localScale != null ? new Vector3(localScale) : Vector3.one();
            firstUpdate = false;
        }
    }

    public long getId() { return id; }
    public String getModelPath() { return modelPath; }
    public Vector3 getInterpolatedPosition(float tickDelta) {
        if (anchorType != null && anchorType != MoudPackets.DisplayAnchorType.FREE) {
            return computeWorldTransform(tickDelta, 0).position();
        }
        float t = Math.max(0.0f, Math.min(1.0f, tickDelta));
        return new Vector3(
                previousRenderPosition.x + (renderPosition.x - previousRenderPosition.x) * t,
                previousRenderPosition.y + (renderPosition.y - previousRenderPosition.y) * t,
                previousRenderPosition.z + (renderPosition.z - previousRenderPosition.z) * t
        );
    }

    public Quaternion getInterpolatedRotation(float tickDelta) {
        if (anchorType != null && anchorType != MoudPackets.DisplayAnchorType.FREE) {
            return computeWorldTransform(tickDelta, 0).rotation();
        }
        float t = Math.max(0.0f, Math.min(1.0f, tickDelta));
        return previousRenderRotation.slerp(renderRotation, t);
    }
    public Vector3 getInterpolatedScale(float tickDelta) {
        if (anchorType != null && anchorType != MoudPackets.DisplayAnchorType.FREE) {
            return computeWorldTransform(tickDelta, 0).scale();
        }
        float t = Math.max(0.0f, Math.min(1.0f, tickDelta));
        return new Vector3(
                previousRenderScale.x + (renderScale.x - previousRenderScale.x) * t,
                previousRenderScale.y + (renderScale.y - previousRenderScale.y) * t,
                previousRenderScale.z + (renderScale.z - previousRenderScale.z) * t
        );
    }

    public void tickSmoothing(float deltaTicks) {
        if (anchorType != null && anchorType != MoudPackets.DisplayAnchorType.FREE) {
            return;
        }
        if (firstUpdate || !smoothingEnabled) {
            return;
        }

        if (deltaTicks <= 0.0f) {
            deltaTicks = 1.0f;
        }

        this.previousRenderPosition = new Vector3(renderPosition);
        this.previousRenderRotation = new Quaternion(renderRotation);
        this.previousRenderScale = new Vector3(renderScale);

        if (smoothingProgress >= 1.0f) {
            snapToTarget();
            return;
        }

        float duration = Math.max(1.0f, smoothingDurationTicks);
        smoothingProgress = Math.min(1.0f, smoothingProgress + (deltaTicks / duration));
        float t = Math.max(0.0f, Math.min(1.0f, smoothingProgress));

        this.renderPosition = Vector3.lerp(smoothingStartPosition, position, t);
        this.renderRotation = smoothingStartRotation.slerp(rotation, t);
        this.renderScale = Vector3.lerp(smoothingStartScale, scale, t);
    }

    private void snapToTarget() {
        this.renderPosition = new Vector3(position);
        this.previousRenderPosition = new Vector3(position);
        this.smoothingStartPosition = new Vector3(position);

        this.renderRotation = new Quaternion(rotation);
        this.previousRenderRotation = new Quaternion(rotation);
        this.smoothingStartRotation = new Quaternion(rotation);

        this.renderScale = new Vector3(scale);
        this.previousRenderScale = new Vector3(scale);
        this.smoothingStartScale = new Vector3(scale);

        this.smoothingProgress = 1.0f;
    }

    public Quaternion getRotation() {
        if (anchorType != null && anchorType != MoudPackets.DisplayAnchorType.FREE) {
            return getInterpolatedRotation(getCurrentTickDelta());
        }
        return rotation;
    }

    public Vector3 getScale() {
        if (anchorType != null && anchorType != MoudPackets.DisplayAnchorType.FREE) {
            return getInterpolatedScale(getCurrentTickDelta());
        }
        return scale;
    }

    public Vector3 getPosition() {
        if (anchorType != null && anchorType != MoudPackets.DisplayAnchorType.FREE) {
            return getInterpolatedPosition(getCurrentTickDelta());
        }
        return position;
    }

    public boolean isAnchored() {
        return anchorType != null && anchorType != MoudPackets.DisplayAnchorType.FREE;
    }
    public void setSmoothingDurationTicks(float ticks) {
        this.smoothingDurationTicks = Math.max(1.0f, ticks);
    }
    public float getSmoothingDurationTicks() {
        return smoothingDurationTicks;
    }
    public void setSmoothingEnabled(boolean enabled) {
        this.smoothingEnabled = enabled;
        if (!enabled) {
            snapToTarget();
        }
    }
    public boolean isSmoothingEnabled() {
        return smoothingEnabled;
    }
    public boolean hasMeshData() { return vertices != null && indices != null; }
    public float[] getVertices() { return vertices; }
    public int[] getIndices() { return indices; }
    public MeshBuffer getMeshBuffer() { return meshBuffer; }
    public boolean hasMeshBuffer() { return meshBuffer != null && meshBuffer.isUploaded(); }
    public Identifier getTexture() { return texture; }
    public void setTexture(Identifier texture) {
        Identifier normalized = normalizeTexture(texture);
        if (normalized == null) {
            return;
        }
        if (!normalized.equals(this.texture)) {
            this.texture = normalized;
            org.slf4j.LoggerFactory.getLogger(RenderableModel.class)
                    .info("RenderableModel {} texture set to {}", id, normalized);
        }
    }
    public boolean hasCollisionBox() {
        return collisionWidth > 0 && collisionHeight > 0 && collisionDepth > 0;
    }
    public void updateCollisionBox(double width, double height, double depth) {
        this.collisionWidth = width;
        this.collisionHeight = height;
        this.collisionDepth = depth;
    }
    public double getCollisionWidth() { return collisionWidth; }
    public double getCollisionHeight() { return collisionHeight; }
    public double getCollisionDepth() { return collisionDepth; }
    public void setCollisionBoxes(List<OBB> boxes) {
        this.collisionBoxes = new ArrayList<>(boxes);
    }
    public List<OBB> getCollisionBoxes() {
        return new ArrayList<>(collisionBoxes);
    }
    public boolean hasCollisionBoxes() {
        return !collisionBoxes.isEmpty();
    }

    @Override
    public String toString() {
        return "RenderableModel{id=" + id + ", modelPath='" + modelPath + "', texture=" + texture + "}";
    }
    public boolean hasMeshBounds() {
        return meshMin != null && meshMax != null;
    }
    public Vector3 getMeshMin() {
        return meshMin;
    }
    public Vector3 getMeshMax() {
        return meshMax;
    }
    public Vector3 getMeshCenter() {
        if (!hasMeshBounds()) {
            return Vector3.zero();
        }
        return new Vector3(
                (meshMin.x + meshMax.x) * 0.5f,
                (meshMin.y + meshMax.y) * 0.5f,
                (meshMin.z + meshMax.z) * 0.5f
        );
    }
    public Vector3 getMeshHalfExtents() {
        if (!hasMeshBounds()) {
            return Vector3.zero();
        }
        return new Vector3(
                (meshMax.x - meshMin.x) * 0.5f,
                (meshMax.y - meshMin.y) * 0.5f,
                (meshMax.z - meshMin.z) * 0.5f
        );
    }

    public BlockPos getBlockPos() {
        Vector3 current = getPosition();
        return BlockPos.ofFloored(current.x, current.y, current.z);
    }

    private float getCurrentTickDelta() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return 1.0f;
        }
        return client.getRenderTickCounter().getTickDelta(true);
    }

    private WorldTransform computeWorldTransform(float tickDelta, int depth) {
        if (depth > 8) {
            return fallbackWorldTransform();
        }

        if (anchorType == null || anchorType == MoudPackets.DisplayAnchorType.FREE) {
            return new WorldTransform(
                    unanchoredInterpolatedPosition(tickDelta),
                    unanchoredInterpolatedRotation(tickDelta),
                    unanchoredInterpolatedScale(tickDelta)
            );
        }

        WorldTransform parent = resolveParentTransform(tickDelta, depth);
        if (parent == null) {
            return fallbackWorldTransform();
        }

        Vector3 localPos = position != null ? new Vector3(position) : Vector3.zero();
        Quaternion localRot = rotation != null ? new Quaternion(rotation) : Quaternion.identity();
        Vector3 localScale = scale != null ? new Vector3(scale) : Vector3.one();

        Vector3 translated = localPos;
        if (anchorLocalSpace) {
            translated = localPos.multiply(parent.scale());
            translated = parent.rotation().rotate(translated);
        }

        Vector3 worldPos = parent.position().add(translated);
        Quaternion worldRot = anchorInheritRotation
                ? parent.rotation().multiply(localRot).normalize()
                : localRot;
        Vector3 worldScale = anchorInheritScale
                ? parent.scale().multiply(localScale)
                : localScale;

        return new WorldTransform(worldPos, worldRot, worldScale);
    }

    private WorldTransform resolveParentTransform(float tickDelta, int depth) {
        return switch (anchorType) {
            case BLOCK -> {
                if (anchorBlockPos == null) {
                    yield null;
                }
                Vector3 pos = new Vector3(
                        anchorBlockPos.getX() + 0.5f,
                        anchorBlockPos.getY() + 0.5f,
                        anchorBlockPos.getZ() + 0.5f
                );
                yield new WorldTransform(pos, Quaternion.identity(), Vector3.one());
            }
            case ENTITY -> {
                Entity entity = ClientEntityResolver.resolve(anchorEntityUuid);
                if (entity == null) {
                    yield null;
                }
                Vec3d pos = entity.getLerpedPos(tickDelta);
                float yaw = entity.getYaw(tickDelta);
                float pitch = anchorIncludePitch ? entity.getPitch(tickDelta) : 0.0f;
                yield new WorldTransform(new Vector3(pos.x, pos.y, pos.z), Quaternion.fromEuler(pitch, yaw, 0.0f), Vector3.one());
            }
            case MODEL -> {
                if (anchorModelId == null || anchorModelId == id) {
                    yield null;
                }
                RenderableModel parent = ClientModelManager.getInstance().getModel(anchorModelId);
                if (parent == null) {
                    yield null;
                }
                yield parent.computeWorldTransform(tickDelta, depth + 1);
            }
            case FREE -> null;
        };
    }

    private WorldTransform fallbackWorldTransform() {
        return new WorldTransform(
                renderPosition != null ? new Vector3(renderPosition) : Vector3.zero(),
                renderRotation != null ? new Quaternion(renderRotation) : Quaternion.identity(),
                renderScale != null ? new Vector3(renderScale) : Vector3.one()
        );
    }

    private Vector3 unanchoredInterpolatedPosition(float tickDelta) {
        float t = Math.max(0.0f, Math.min(1.0f, tickDelta));
        return new Vector3(
                previousRenderPosition.x + (renderPosition.x - previousRenderPosition.x) * t,
                previousRenderPosition.y + (renderPosition.y - previousRenderPosition.y) * t,
                previousRenderPosition.z + (renderPosition.z - previousRenderPosition.z) * t
        );
    }

    private Quaternion unanchoredInterpolatedRotation(float tickDelta) {
        float t = Math.max(0.0f, Math.min(1.0f, tickDelta));
        return previousRenderRotation.slerp(renderRotation, t);
    }

    private Vector3 unanchoredInterpolatedScale(float tickDelta) {
        float t = Math.max(0.0f, Math.min(1.0f, tickDelta));
        return new Vector3(
                previousRenderScale.x + (renderScale.x - previousRenderScale.x) * t,
                previousRenderScale.y + (renderScale.y - previousRenderScale.y) * t,
                previousRenderScale.z + (renderScale.z - previousRenderScale.z) * t
        );
    }

    private record WorldTransform(Vector3 position, Quaternion rotation, Vector3 scale) {
    }

    public void destroy() {
        if (this.meshBuffer != null) {
            this.meshBuffer.close();
            this.meshBuffer = null;
        }
        this.vertices = null;
        this.indices = null;
        this.meshMin = null;
        this.meshMax = null;
    }

    private static float distanceSquared(Vector3 a, Vector3 b) {
        if (a == null || b == null) {
            return Float.POSITIVE_INFINITY;
        }
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        float dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private void computeMeshBounds() {
        if (vertices == null || vertices.length < 8) {
            meshMin = null;
            meshMax = null;
            return;
        }
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (int i = 0; i < vertices.length; i += 8) {
            float x = vertices[i];
            float y = vertices[i + 1];
            float z = vertices[i + 2];
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }

        if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(minZ)) {
            meshMin = null;
            meshMax = null;
            return;
        }
        meshMin = new Vector3(minX, minY, minZ);
        meshMax = new Vector3(maxX, maxY, maxZ);
    }

    private Identifier normalizeTexture(Identifier original) {
        if (original == null) {
            return null;
        }
        if ("moud".equals(original.getNamespace())) {
            String path = original.getPath();
            if (path.startsWith("moud/") && path.length() > 5) {
                return Identifier.of("moud", path.substring(5));
            }
        }
        return original;
    }
}
