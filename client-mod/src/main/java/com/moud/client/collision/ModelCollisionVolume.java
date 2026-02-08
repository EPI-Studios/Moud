package com.moud.client.collision;

import com.moud.api.collision.OBB;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.client.model.RenderableModel;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class ModelCollisionVolume {
    private static final float ROTATION_EPSILON = 1.0e-3f;
    private static final double MAX_DIMENSION = 256.0;
    private static final Logger LOGGER = LoggerFactory.getLogger(ModelCollisionVolume.class);
    private static final float TARGET_CELL_SIZE = 0.5f;
    private static final float TRI_PADDING_FACTOR = 0.1f;
    private static final int MAX_DIVISIONS_PER_AXIS = 128;
    private static final int MAX_BOXES = 8000;
    private static final int MAX_OCCUPIED_CELLS = 5000;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));

    private final long modelId;
    private final AtomicReference<BuildRequest> pending = new AtomicReference<>();
    private final AtomicBoolean building = new AtomicBoolean(false);
    private volatile Vector3 position = Vector3.zero();
    private volatile Quaternion rotation = Quaternion.identity();
    private volatile Vector3 scale = Vector3.one();
    private volatile double fallbackWidth;
    private volatile double fallbackHeight;
    private volatile double fallbackDepth;
    private volatile Box bounds;
    private volatile VoxelShape voxelShape;
    private volatile List<Box> boxCache = List.of();
    private volatile Vector3 shapePosition = Vector3.zero();
    private volatile Quaternion shapeRotation = Quaternion.identity();
    private volatile Vector3 shapeScale = Vector3.one();
    private volatile int collisionBoxesHash;
    private volatile long lastCollisionInstallNanos;
    private volatile int lastCollisionInstallCount;
    private volatile long lastReuseLogNanos;
    private volatile int lastReuseCount;
    private boolean loggedCollisionBoxes;
    private boolean loggedGridFallback;
    private boolean loggedAabbFallback;

    public ModelCollisionVolume(long modelId) {
        this.modelId = modelId;
    }

    public long getModelId() {
        return modelId;
    }

    public void update(RenderableModel model) {
        Objects.requireNonNull(model, "model");
        Vector3 modelPos = model.getPosition();
        this.position = modelPos != null ? new Vector3(modelPos) : Vector3.zero();
        Quaternion modelRot = model.getRotation();
        this.rotation = modelRot != null ? new Quaternion(modelRot) : Quaternion.identity();
        this.scale = model.getScale() != null ? new Vector3(model.getScale()) : Vector3.one();
        this.fallbackWidth = model.getCollisionWidth();
        this.fallbackHeight = model.getCollisionHeight();
        this.fallbackDepth = model.getCollisionDepth();

        float[] verticesCopy = model.getVertices() != null ? model.getVertices().clone() : null;
        int[] indicesCopy = model.getIndices() != null ? model.getIndices().clone() : null;
        List<OBB> collisionCopy = model.getCollisionBoxes() != null ? new ArrayList<>(model.getCollisionBoxes()) : List.of();
        Vector3 posSnapshot = this.position;
        Quaternion rotSnapshot = this.rotation;
        Vector3 scaleSnapshot = this.scale;
        double fw = this.fallbackWidth;
        double fh = this.fallbackHeight;
        double fd = this.fallbackDepth;
        int collisionHash = hashCollisionBoxes(collisionCopy);

        Box provisional = verticesCopy != null ? computeFromMeshVertices(verticesCopy, posSnapshot, rotSnapshot, scaleSnapshot) : null;
        if (provisional == null) {
            provisional = computeFromFallback(fw, fh, fd, posSnapshot, rotSnapshot);
        }
        if (provisional != null && !isTooLarge(provisional)) {
            this.bounds = provisional;
            this.voxelShape = VoxelShapes.cuboid(provisional.minX, provisional.minY, provisional.minZ, provisional.maxX, provisional.maxY, provisional.maxZ);
        }

        if (collisionCopy.size() > 1) {

            if (canReuseCollisionBoxes(collisionHash, rotSnapshot, scaleSnapshot)) {
                logCollisionReuse(collisionCopy.size());
                Vector3 delta = posSnapshot.subtract(shapePosition);
                if (Math.abs(delta.x) > 1.0e-6 || Math.abs(delta.y) > 1.0e-6 || Math.abs(delta.z) > 1.0e-6) {
                    if (bounds != null) {
                        bounds = bounds.offset(delta.x, delta.y, delta.z);
                    }
                    if (voxelShape != null) {
                        voxelShape = voxelShape.offset(delta.x, delta.y, delta.z);
                    }
                    if (boxCache != null && !boxCache.isEmpty()) {
                        List<Box> shifted = new ArrayList<>(boxCache.size());
                        for (Box box : boxCache) {
                            shifted.add(box.offset(delta.x, delta.y, delta.z));
                        }
                        boxCache = shifted;
                    }
                    shapePosition = posSnapshot;
                }
                return;
            }

            List<Box> boxes = computeFromCollisionBoxes(collisionCopy, posSnapshot, rotSnapshot, scaleSnapshot);
            Box boundsBox = enclosingBox(boxes);
            if (boundsBox != null && !isTooLarge(boundsBox)) {
                logCollisionInstall(boxes.size());
                this.bounds = boundsBox;
                this.boxCache = boxes;
                this.voxelShape = null;
                this.shapePosition = posSnapshot;
                this.shapeRotation = rotSnapshot;
                this.shapeScale = scaleSnapshot;
                this.collisionBoxesHash = collisionHash;
                return;
            }

            this.collisionBoxesHash = 0;
        } else {
            LOGGER.info("Model {} has no collision boxes; using voxel fallback", modelId);
        }

        pending.set(new BuildRequest(verticesCopy, indicesCopy, collisionCopy, posSnapshot, rotSnapshot, scaleSnapshot, fw, fh, fd, collisionHash));
        if (building.compareAndSet(false, true)) {
            EXECUTOR.submit(this::processQueue);
        }
    }

    public void updateTransform(RenderableModel model) {
        Objects.requireNonNull(model, "model");

        Vector3 modelPos = model.getPosition();
        Quaternion modelRot = model.getRotation();
        Vector3 modelScale = model.getScale();

        Vector3 posSnapshot = modelPos != null ? new Vector3(modelPos) : Vector3.zero();
        Quaternion rotSnapshot = modelRot != null ? new Quaternion(modelRot) : Quaternion.identity();
        Vector3 scaleSnapshot = modelScale != null ? new Vector3(modelScale) : Vector3.one();

        this.position = posSnapshot;
        this.rotation = rotSnapshot;
        this.scale = scaleSnapshot;

        List<OBB> collisionBoxesSnapshot = model.getCollisionBoxes();
        int collisionHash = hashCollisionBoxes(collisionBoxesSnapshot);

        if (collisionBoxesSnapshot != null && collisionBoxesSnapshot.size() > 1) {
            if (canReuseCollisionBoxes(collisionHash, rotSnapshot, scaleSnapshot)) {
                shiftByDelta(posSnapshot);
                return;
            }

            List<Box> boxes = computeFromCollisionBoxes(collisionBoxesSnapshot, posSnapshot, rotSnapshot, scaleSnapshot);
            Box boundsBox = enclosingBox(boxes);
            if (boundsBox != null && !isTooLarge(boundsBox)) {
                this.bounds = boundsBox;
                this.boxCache = boxes;
                this.voxelShape = null;
                this.shapePosition = posSnapshot;
                this.shapeRotation = rotSnapshot;
                this.shapeScale = scaleSnapshot;
                this.collisionBoxesHash = collisionHash;
                return;
            }
        }

        Box fallback = computeFromFallback(model.getCollisionWidth(), model.getCollisionHeight(), model.getCollisionDepth(), posSnapshot, rotSnapshot);
        if (fallback == null && model.hasMeshBounds()) {
            fallback = computeFromMeshBounds(model.getMeshMin(), model.getMeshMax(), posSnapshot, rotSnapshot, scaleSnapshot);
        }

        if (fallback != null && !isTooLarge(fallback)) {
            this.bounds = fallback;
            this.voxelShape = VoxelShapes.cuboid(fallback.minX, fallback.minY, fallback.minZ, fallback.maxX, fallback.maxY, fallback.maxZ);
            this.boxCache = List.of();
            this.shapePosition = posSnapshot;
            this.shapeRotation = rotSnapshot;
            this.shapeScale = scaleSnapshot;
            this.collisionBoxesHash = collisionHash;
            return;
        }

        shiftByDelta(posSnapshot);
    }

    public boolean isActive() {
        return bounds != null && hasShapes();
    }

    public boolean intersects(Box other) {
        return bounds != null && bounds.intersects(other);
    }

    public Box getBounds() {
        return bounds;
    }

    public VoxelShape getVoxelShape() {
        return voxelShape;
    }

    public List<Box> getBoxes() {
        return boxCache != null ? boxCache : List.of();
    }

    public List<VoxelShape> getBoxShapes() {
        List<Box> boxes = getBoxes();
        if (boxes.isEmpty()) {
            return List.of();
        }
        List<VoxelShape> shapes = new ArrayList<>(boxes.size());
        for (Box box : boxes) {
            shapes.add(VoxelShapes.cuboid(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
        }
        return shapes;
    }

    private BuildResult rebuildVolume(float[] vertices, int[] indices, List<OBB> collisionBoxesSnapshot,
                                      Vector3 pos, Quaternion rot, Vector3 scl,
                                      double fw, double fh, double fd) {

        if (collisionBoxesSnapshot != null && collisionBoxesSnapshot.size() > 1) {
            BuildResult collisionResult = rebuildFromCollisionBoxes(collisionBoxesSnapshot, pos, rot, scl);
            if (collisionResult != null) {
                logCollisionInstall(collisionBoxesSnapshot.size());
                return collisionResult;
            }
            if (!loggedCollisionBoxes) {
                LOGGER.debug("Model {} expected collisionBoxes but none present; falling back to voxel.", modelId);
                loggedCollisionBoxes = true;
            }
        }

        List<Box> meshBoxes = computeFromMeshGrid(vertices, indices, pos, rot, scl);
        if (!meshBoxes.isEmpty()) {
            return new BuildResult(buildShapeFromBoxes(meshBoxes), enclosingBox(meshBoxes), meshBoxes);
        } else if (!loggedGridFallback) {
            LOGGER.debug("Model {} voxel fallback produced no boxes; using AABB fallback.", modelId);
            loggedGridFallback = true;
        }

        Box computed = computeFromMeshVertices(vertices, pos, rot, scl);
        if (computed == null) {
            computed = computeFromFallback(fw, fh, fd, pos, rot);
        }
        if (computed == null || isTooLarge(computed)) {
            if (!loggedAabbFallback) {
                LOGGER.debug("Model {} AABB fallback too large or null; no collision volume.", modelId);
                loggedAabbFallback = true;
            }
            return null;
        }
        List<Box> single = computed == null ? List.of() : List.of(computed);
        return new BuildResult(computed == null ? null : VoxelShapes.cuboid(
                computed.minX, computed.minY, computed.minZ, computed.maxX, computed.maxY, computed.maxZ
        ), computed, single);
    }

    private BuildResult rebuildFromCollisionBoxes(List<OBB> collisionBoxesSnapshot, Vector3 pos, Quaternion rot, Vector3 scl) {
        List<Box> boxes = computeFromCollisionBoxes(collisionBoxesSnapshot, pos, rot, scl);
        if (boxes.isEmpty()) {
            return null;
        }
        Box boundsBox = enclosingBox(boxes);
        if (boundsBox == null || isTooLarge(boundsBox)) {
            return null;
        }
        return new BuildResult(buildShapeFromBoxes(boxes), boundsBox, boxes);
    }

    private void processQueue() {
        try {
            while (true) {
                BuildRequest request = pending.getAndSet(null);
                if (request == null) {
                    return;
                }
                BuildResult result = rebuildVolume(
                        request.vertices(),
                        request.indices(),
                        request.collisionBoxes(),
                        request.position(),
                        request.rotation(),
                        request.scale(),
                        request.fw(),
                        request.fh(),
                        request.fd()
                );
                if (result != null && result.bounds() != null && !isTooLarge(result.bounds())) {
                    this.bounds = result.bounds();
                    this.voxelShape = result.shape();
                    this.boxCache = result.boxes();
                    this.shapePosition = request.position();
                    this.shapeRotation = request.rotation();
                    this.shapeScale = request.scale();
                    this.collisionBoxesHash = request.collisionHash();
                } else if (result == null) {
                    this.bounds = null;
                    this.voxelShape = null;
                    this.boxCache = List.of();
                    this.collisionBoxesHash = 0;
                }
            }
        } finally {
            building.set(false);

            if (pending.get() != null && building.compareAndSet(false, true)) {
                EXECUTOR.submit(this::processQueue);
            }
        }
    }

    private List<Box> computeFromCollisionBoxes(List<OBB> collisionBoxes, Vector3 modelPos, Quaternion modelRot, Vector3 modelScale) {
        if (collisionBoxes == null || collisionBoxes.isEmpty()) {
            return new ArrayList<>();
        }

        List<Box> boxes = new ArrayList<>();
        Vector3 modelPosLocal = modelPos != null ? modelPos : Vector3.zero();
        Quaternion modelRotLocal = modelRot != null ? modelRot : Quaternion.identity();
        Vector3 modelScaleLocal = modelScale != null ? modelScale : Vector3.one();

        for (OBB obb : collisionBoxes) {

            Vector3 scaledCenter = new Vector3(
                    obb.center.x * modelScaleLocal.x,
                    obb.center.y * modelScaleLocal.y,
                    obb.center.z * modelScaleLocal.z
            );

            Vector3 rotatedCenter = modelRotLocal.rotate(scaledCenter);

            Vector3 worldCenter = modelPosLocal.add(rotatedCenter);

            Vector3 scaledExtents = new Vector3(
                    Math.abs(obb.halfExtents.x * modelScaleLocal.x),
                    Math.abs(obb.halfExtents.y * modelScaleLocal.y),
                    Math.abs(obb.halfExtents.z * modelScaleLocal.z)
            );

            Quaternion worldRotation = modelRotLocal.multiply(obb.rotation);

            OBB worldOBB = new OBB(worldCenter, scaledExtents, worldRotation);

            Vector3 min = worldOBB.getMin();
            Vector3 max = worldOBB.getMax();

            boxes.add(new Box(min.x, min.y, min.z, max.x, max.y, max.z));
        }

        return boxes;
    }

    private Box computeFromMeshBounds(Vector3 localMin, Vector3 localMax, Vector3 pos, Quaternion rot, Vector3 scale) {
        if (localMin == null || localMax == null) {
            return null;
        }

        Vector3 scaleVec = scale != null ? scale : Vector3.one();
        Quaternion rotationLocal = rot != null ? rot : Quaternion.identity();
        Vector3 positionLocal = pos != null ? pos : Vector3.zero();

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        float[] xs = new float[]{localMin.x, localMax.x};
        float[] ys = new float[]{localMin.y, localMax.y};
        float[] zs = new float[]{localMin.z, localMax.z};

        for (float x : xs) {
            for (float y : ys) {
                for (float z : zs) {
                    Vector3 corner = new Vector3(
                            x * scaleVec.x,
                            y * scaleVec.y,
                            z * scaleVec.z
                    );
                    Vector3 world = rotationLocal.rotate(corner).add(positionLocal);
                    minX = Math.min(minX, world.x);
                    minY = Math.min(minY, world.y);
                    minZ = Math.min(minZ, world.z);
                    maxX = Math.max(maxX, world.x);
                    maxY = Math.max(maxY, world.y);
                    maxZ = Math.max(maxZ, world.z);
                }
            }
        }

        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)) {
            return null;
        }

        Box box = new Box(minX, minY, minZ, maxX, maxY, maxZ);
        return isTooLarge(box) ? null : box;
    }

    private void shiftByDelta(Vector3 posSnapshot) {
        if (posSnapshot == null) {
            return;
        }
        Vector3 delta = posSnapshot.subtract(shapePosition);
        if (Math.abs(delta.x) <= 1.0e-6 && Math.abs(delta.y) <= 1.0e-6 && Math.abs(delta.z) <= 1.0e-6) {
            return;
        }
        if (bounds != null) {
            bounds = bounds.offset(delta.x, delta.y, delta.z);
        }
        if (voxelShape != null) {
            voxelShape = voxelShape.offset(delta.x, delta.y, delta.z);
        }
        if (boxCache != null && !boxCache.isEmpty()) {
            List<Box> shifted = new ArrayList<>(boxCache.size());
            for (Box box : boxCache) {
                shifted.add(box.offset(delta.x, delta.y, delta.z));
            }
            boxCache = shifted;
        }
        shapePosition = posSnapshot;
    }

    private void buildFromMultipleBoxes(List<Box> boxes) {
        if (boxes.isEmpty()) {
            bounds = null;
            voxelShape = null;
            return;
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (Box box : boxes) {
            minX = Math.min(minX, box.minX);
            minY = Math.min(minY, box.minY);
            minZ = Math.min(minZ, box.minZ);
            maxX = Math.max(maxX, box.maxX);
            maxY = Math.max(maxY, box.maxY);
            maxZ = Math.max(maxZ, box.maxZ);
        }

        bounds = new Box(minX, minY, minZ, maxX, maxY, maxZ);

        VoxelShape shape = VoxelShapes.empty();
        for (Box box : boxes) {
            shape = VoxelShapes.union(shape, VoxelShapes.cuboid(
                    box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ
            ));
        }
        if (isTooLarge(bounds)) {
            bounds = null;
            voxelShape = null;
        } else {
            voxelShape = shape;
        }
    }

    private Box computeFromMeshVertices(float[] vertices, Vector3 pos, Quaternion rot, Vector3 scale) {
        if (vertices == null) {
            return null;
        }
        if (vertices.length < 3) {
            return null;
        }

        Vector3 scaleVec = scale != null ? scale : Vector3.one();
        Quaternion rotationLocal = rot != null ? rot : Quaternion.identity();
        Quaternionf rotationQuat = new Quaternionf(rotationLocal.x, rotationLocal.y, rotationLocal.z, rotationLocal.w);
        if (rotationQuat.lengthSquared() > 0) {
            rotationQuat.normalize();
        } else {
            rotationQuat.identity();
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        Vector3f scratch = new Vector3f();

        for (int i = 0; i < vertices.length; i += RenderableModel.FLOATS_PER_VERTEX) {
            float x = vertices[i];
            float y = vertices[i + 1];
            float z = vertices[i + 2];

            scratch.set(
                    x * scaleVec.x,
                    y * scaleVec.y,
                    z * scaleVec.z
            );

            rotationQuat.transform(scratch);
            scratch.add(pos.x, pos.y, pos.z);

            double cx = scratch.x();
            double cy = scratch.y();
            double cz = scratch.z();

            minX = Math.min(minX, cx);
            minY = Math.min(minY, cy);
            minZ = Math.min(minZ, cz);
            maxX = Math.max(maxX, cx);
            maxY = Math.max(maxY, cy);
            maxZ = Math.max(maxZ, cz);
        }

        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)) {
            return null;
        }

        Box box = new Box(minX, minY, minZ, maxX, maxY, maxZ);
        if (isTooLarge(box)) {
            return null;
        }
        return box;
    }

    private Box computeFromFallback(double fw, double fh, double fd, Vector3 pos, Quaternion rot) {
        if (fw <= 0 || fh <= 0 || fd <= 0) {
            return null;
        }

        Quaternion rotationLocal = rot != null ? rot : Quaternion.identity();
        float lenSq = rotationLocal.x * rotationLocal.x + rotationLocal.y * rotationLocal.y + rotationLocal.z * rotationLocal.z;
        if (lenSq <= ROTATION_EPSILON) {
            double halfX = fw * 0.5d;
            double halfZ = fd * 0.5d;
            double minX = pos.x - halfX;
            double minY = pos.y;
            double minZ = pos.z - halfZ;
            return new Box(minX, minY, minZ, minX + fw, minY + fh, minZ + fd);
        }

        Quaternionf rotationQuat = new Quaternionf(rotationLocal.x, rotationLocal.y, rotationLocal.z, rotationLocal.w);
        if (rotationQuat.lengthSquared() > 0) {
            rotationQuat.normalize();
        } else {
            rotationQuat.identity();
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        Vector3f center = new Vector3f(pos.x, (float) (pos.y + fh * 0.5d), pos.z);
        Vector3f halfExtents = new Vector3f(
                (float) (fw * 0.5d),
                (float) (fh * 0.5d),
                (float) (fd * 0.5d)
        );

        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dy = -1; dy <= 1; dy += 2) {
                for (int dz = -1; dz <= 1; dz += 2) {
                    Vector3f corner = new Vector3f(
                            halfExtents.x * dx,
                            halfExtents.y * dy,
                            halfExtents.z * dz
                    );
                    rotationQuat.transform(corner);
                    corner.add(center);

                    double cx = corner.x();
                    double cy = corner.y();
                    double cz = corner.z();
                    minX = Math.min(minX, cx);
                    minY = Math.min(minY, cy);
                    minZ = Math.min(minZ, cz);
                    maxX = Math.max(maxX, cx);
                    maxY = Math.max(maxY, cy);
                    maxZ = Math.max(maxZ, cz);
                }
            }
        }

        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)) {
            return null;
        }

        Box box = new Box(minX, minY, minZ, maxX, maxY, maxZ);
        if (isTooLarge(box)) {
            return null;
        }
        return box;
    }

    private List<Box> computeFromMeshGrid(float[] vertices, int[] indices, Vector3 modelPos, Quaternion modelRot, Vector3 modelScale) {
        List<Box> boxes = new ArrayList<>();
        if (vertices == null || indices == null || vertices.length < 3 || indices.length < 3) {
            return boxes;
        }

        Vector3 scaleVec = modelScale != null ? modelScale : Vector3.one();
        Quaternion modelRotLocal = modelRot != null ? modelRot : Quaternion.identity();
        Vector3 modelPosLocal = modelPos != null ? modelPos : Vector3.zero();

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < vertices.length; i += RenderableModel.FLOATS_PER_VERTEX) {
            float x = vertices[i] * scaleVec.x;
            float y = vertices[i + 1] * scaleVec.y;
            float z = vertices[i + 2] * scaleVec.z;
            Vector3 rotated = modelRot.rotate(new Vector3(x, y, z)).add(modelPos);
            minX = Math.min(minX, rotated.x);
            minY = Math.min(minY, rotated.y);
            minZ = Math.min(minZ, rotated.z);
            maxX = Math.max(maxX, rotated.x);
            maxY = Math.max(maxY, rotated.y);
            maxZ = Math.max(maxZ, rotated.z);
        }

        if (!Double.isFinite(minX)) {
            return boxes;
        }

        int divX = Math.max(1, Math.min(MAX_DIVISIONS_PER_AXIS, (int) Math.ceil((maxX - minX) / TARGET_CELL_SIZE)));
        int divY = Math.max(1, Math.min(MAX_DIVISIONS_PER_AXIS, (int) Math.ceil((maxY - minY) / TARGET_CELL_SIZE)));
        int divZ = Math.max(1, Math.min(MAX_DIVISIONS_PER_AXIS, (int) Math.ceil((maxZ - minZ) / TARGET_CELL_SIZE)));
        double stepX = (maxX - minX) / divX;
        double stepY = (maxY - minY) / divY;
        double stepZ = (maxZ - minZ) / divZ;

        Set<Integer> occupied = new HashSet<>();
        double padding = TARGET_CELL_SIZE * TRI_PADDING_FACTOR;

        for (int tri = 0; tri < indices.length && occupied.size() < MAX_OCCUPIED_CELLS; tri += 3) {
            int i0 = indices[tri] * RenderableModel.FLOATS_PER_VERTEX;
            int i1 = indices[tri + 1] * RenderableModel.FLOATS_PER_VERTEX;
            int i2 = indices[tri + 2] * RenderableModel.FLOATS_PER_VERTEX;
            Vector3 p0 = toWorld(vertices, i0, scaleVec, modelRotLocal, modelPosLocal);
            Vector3 p1 = toWorld(vertices, i1, scaleVec, modelRotLocal, modelPosLocal);
            Vector3 p2 = toWorld(vertices, i2, scaleVec, modelRotLocal, modelPosLocal);
            double triMinX = Math.min(p0.x, Math.min(p1.x, p2.x)) - padding;
            double triMaxX = Math.max(p0.x, Math.max(p1.x, p2.x)) + padding;
            double triMinY = Math.min(p0.y, Math.min(p1.y, p2.y)) - padding;
            double triMaxY = Math.max(p0.y, Math.max(p1.y, p2.y)) + padding;
            double triMinZ = Math.min(p0.z, Math.min(p1.z, p2.z)) - padding;
            double triMaxZ = Math.max(p0.z, Math.max(p1.z, p2.z)) + padding;

            int startX = clamp((int) Math.floor((triMinX - minX) / stepX), 0, divX - 1);
            int endX = clamp((int) Math.floor((triMaxX - minX) / stepX), 0, divX - 1);
            int startY = clamp((int) Math.floor((triMinY - minY) / stepY), 0, divY - 1);
            int endY = clamp((int) Math.floor((triMaxY - minY) / stepY), 0, divY - 1);
            int startZ = clamp((int) Math.floor((triMinZ - minZ) / stepZ), 0, divZ - 1);
            int endZ = clamp((int) Math.floor((triMaxZ - minZ) / stepZ), 0, divZ - 1);

            for (int ix = startX; ix <= endX && occupied.size() < MAX_OCCUPIED_CELLS; ix++) {
                for (int iy = startY; iy <= endY && occupied.size() < MAX_OCCUPIED_CELLS; iy++) {
                    for (int iz = startZ; iz <= endZ && occupied.size() < MAX_OCCUPIED_CELLS; iz++) {
                        double cellMinX = minX + ix * stepX;
                        double cellMinY = minY + iy * stepY;
                        double cellMinZ = minZ + iz * stepZ;
                        double cellMaxX = cellMinX + stepX;
                        double cellMaxY = cellMinY + stepY;
                        double cellMaxZ = cellMinZ + stepZ;
                        if (!triangleIntersectsAabb(p0, p1, p2, cellMinX, cellMinY, cellMinZ, cellMaxX, cellMaxY, cellMaxZ)) {
                            continue;
                        }
                        int key = pack(ix, iy, iz);
                        if (occupied.add(key) && occupied.size() == MAX_OCCUPIED_CELLS && !loggedGridFallback) {
                            LOGGER.debug("Model {} occupied cell cap hit ({} cells), stopping voxel fill early.", modelId, MAX_OCCUPIED_CELLS);
                            loggedGridFallback = true;
                        }
                    }
                }
            }
        }
        if (occupied.isEmpty()) {
            return boxes;
        }

        int total = divX * divY * divZ;
        boolean[] grid = new boolean[total];
        for (int key : occupied) {
            int x = (key >> 14) & 0x7F;
            int y = (key >> 7) & 0x7F;
            int z = key & 0x7F;
            int idx = (y * divZ + z) * divX + x;
            grid[idx] = true;
        }

        List<RunZ> yzRuns = new ArrayList<>();
        for (int y = 0; y < divY; y++) {
            HashMap<String, TempRun> active = new HashMap<>();
            for (int z = 0; z < divZ; z++) {
                Set<String> seenThisZ = new HashSet<>();
                int x = 0;
                while (x < divX) {
                    int idx = (y * divZ + z) * divX + x;
                    if (grid[idx]) {
                        int x0 = x;
                        while (x + 1 < divX && grid[(y * divZ + z) * divX + (x + 1)]) {
                            x++;
                        }
                        int x1 = x;
                        String key = x0 + "|" + x1;
                        TempRun run = active.get(key);
                        if (run != null && run.lastZ == z - 1) {
                            run.lastZ = z;
                        } else {
                            if (run != null) {
                                yzRuns.add(new RunZ(x0, x1, run.startZ, run.lastZ, y));
                            }
                            active.put(key, new TempRun(x0, x1, z, z, y));
                        }
                        seenThisZ.add(key);
                    }
                    x++;
                }

                List<String> toRemove = new ArrayList<>();
                for (var entry : active.entrySet()) {
                    if (!seenThisZ.contains(entry.getKey()) && entry.getValue().lastZ < z) {
                        TempRun run = entry.getValue();
                        yzRuns.add(new RunZ(run.x0, run.x1, run.startZ, run.lastZ, y));
                        toRemove.add(entry.getKey());
                    }
                }
                for (String k : toRemove) {
                    active.remove(k);
                }
            }

            for (TempRun run : active.values()) {
                yzRuns.add(new RunZ(run.x0, run.x1, run.startZ, run.lastZ, y));
            }
        }

        if (yzRuns.isEmpty()) {
            return boxes;
        }

        yzRuns.sort(Comparator.comparingInt((RunZ r) -> r.y)
                .thenComparingInt(r -> r.z0)
                .thenComparingInt(r -> r.x0));

        List<RunY> finalRuns = new ArrayList<>();
        HashMap<String, RunY> activeY = new HashMap<>();
        int currentY = yzRuns.get(0).y;
        for (RunZ run : yzRuns) {
            if (run.y != currentY) {
                activeY.values().forEach(finalRuns::add);
                activeY.clear();
                currentY = run.y;
            }
            String key = run.x0 + "|" + run.x1 + "|" + run.z0 + "|" + run.z1;
            RunY existing = activeY.get(key);
            if (existing != null && existing.endY == run.y - 1) {
                existing.endY = run.y;
            } else {
                if (existing != null) {
                    finalRuns.add(existing);
                }
                activeY.put(key, new RunY(run.x0, run.x1, run.z0, run.z1, run.y, run.y));
            }
        }
        activeY.values().forEach(finalRuns::add);

        boxes.clear();
        for (RunY r : finalRuns) {
            double cellMinX = minX + r.x0 * stepX;
            double cellMaxX = minX + (r.x1 + 1) * stepX;
            double cellMinZ = minZ + r.z0 * stepZ;
            double cellMaxZ = minZ + (r.z1 + 1) * stepZ;
            double cellMinY = minY + r.startY * stepY;
            double cellMaxY = minY + (r.endY + 1) * stepY;
            boxes.add(new Box(cellMinX, cellMinY, cellMinZ, cellMaxX, cellMaxY, cellMaxZ));
        }
        return boxes;
    }

    private Vector3 toWorld(float[] vertices, int idx, Vector3 scale, Quaternion rot, Vector3 pos) {
        float x = vertices[idx] * scale.x;
        float y = vertices[idx + 1] * scale.y;
        float z = vertices[idx + 2] * scale.z;
        return rot.rotate(new Vector3(x, y, z)).add(pos);
    }

    private int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private int pack(int x, int y, int z) {

        return (x << 14) | (y << 7) | z;
    }

    private VoxelShape buildShapeFromBoxes(List<Box> boxes) {
        if (boxes == null || boxes.isEmpty()) {
            return null;
        }
        VoxelShape shape = VoxelShapes.empty();
        int used = 0;
        for (Box box : boxes) {
            shape = VoxelShapes.union(shape, VoxelShapes.cuboid(
                    box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ
            ));
            used++;
            if (used >= MAX_BOXES) {
                break;
            }
        }
        return shape;
    }

    private Box enclosingBox(List<Box> boxes) {
        if (boxes == null || boxes.isEmpty()) {
            return null;
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Box box : boxes) {
            minX = Math.min(minX, box.minX);
            minY = Math.min(minY, box.minY);
            minZ = Math.min(minZ, box.minZ);
            maxX = Math.max(maxX, box.maxX);
            maxY = Math.max(maxY, box.maxY);
            maxZ = Math.max(maxZ, box.maxZ);
        }
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private boolean canReuseCollisionBoxes(int newHash, Quaternion rot, Vector3 scl) {
        return bounds != null
                && newHash != 0
                && newHash == collisionBoxesHash
                && rotationEquals(shapeRotation, rot)
                && scaleEquals(shapeScale, scl)
                && hasShapes();
    }

    private boolean rotationEquals(Quaternion a, Quaternion b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b, ROTATION_EPSILON);
    }

    private boolean scaleEquals(Vector3 a, Vector3 b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return Math.abs(a.x - b.x) <= 1.0e-5 &&
                Math.abs(a.y - b.y) <= 1.0e-5 &&
                Math.abs(a.z - b.z) <= 1.0e-5;
    }

    private int hashCollisionBoxes(List<OBB> boxes) {
        if (boxes == null || boxes.isEmpty()) {
            return 0;
        }
        int hash = 1;
        for (OBB obb : boxes) {
            if (obb == null) {
                continue;
            }
            hash = 31 * hash + hashVector(obb.center);
            hash = 31 * hash + hashVector(obb.halfExtents);
            hash = 31 * hash + (obb.rotation != null ? obb.rotation.hashCode() : 0);
        }
        return hash;
    }

    private int hashVector(Vector3 v) {
        if (v == null) {
            return 0;
        }
        int result = Float.floatToIntBits(v.x);
        result = 31 * result + Float.floatToIntBits(v.y);
        result = 31 * result + Float.floatToIntBits(v.z);
        return result;
    }

    private boolean triangleIntersectsAabb(Vector3 v0, Vector3 v1, Vector3 v2,
                                           double minX, double minY, double minZ,
                                           double maxX, double maxY, double maxZ) {

        double[] boxCenter = {(minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5};
        double[] boxHalf = {(maxX - minX) * 0.5, (maxY - minY) * 0.5, (maxZ - minZ) * 0.5};
        double[] tv0 = {v0.x - boxCenter[0], v0.y - boxCenter[1], v0.z - boxCenter[2]};
        double[] tv1 = {v1.x - boxCenter[0], v1.y - boxCenter[1], v1.z - boxCenter[2]};
        double[] tv2 = {v2.x - boxCenter[0], v2.y - boxCenter[1], v2.z - boxCenter[2]};

        double[] e0 = {tv1[0] - tv0[0], tv1[1] - tv0[1], tv1[2] - tv0[2]};
        double[] e1 = {tv2[0] - tv1[0], tv2[1] - tv1[1], tv2[2] - tv1[2]};
        double[] e2 = {tv0[0] - tv2[0], tv0[1] - tv2[1], tv0[2] - tv2[2]};

        if (!axisTest(e0, tv0, tv1, tv2, boxHalf)) return false;
        if (!axisTest(e1, tv0, tv1, tv2, boxHalf)) return false;
        if (!axisTest(e2, tv0, tv1, tv2, boxHalf)) return false;

        if (!overlapOnAxis(tv0[0], tv1[0], tv2[0], boxHalf[0])) return false;
        if (!overlapOnAxis(tv0[1], tv1[1], tv2[1], boxHalf[1])) return false;
        if (!overlapOnAxis(tv0[2], tv1[2], tv2[2], boxHalf[2])) return false;

        double[] normal = cross(e0, e1);
        return planeBoxOverlap(normal, tv0, boxHalf);
    }

    private boolean overlapOnAxis(double a, double b, double c, double half) {
        double min = Math.min(a, Math.min(b, c));
        double max = Math.max(a, Math.max(b, c));
        return !(min > half || max < -half);
    }

    private boolean axisTest(double[] edge, double[] v0, double[] v1, double[] v2, double[] half) {

        double fex = Math.abs(edge[0]);
        double fey = Math.abs(edge[1]);
        double fez = Math.abs(edge[2]);

        double p0 = edge[2] * v0[1] - edge[1] * v0[2];
        double p1 = edge[2] * v1[1] - edge[1] * v1[2];
        double p2 = edge[2] * v2[1] - edge[1] * v2[2];
        double min = Math.min(p0, Math.min(p1, p2));
        double max = Math.max(p0, Math.max(p1, p2));
        double rad = fez * half[1] + fey * half[2];
        if (min > rad || max < -rad) return false;

        p0 = -edge[2] * v0[0] + edge[0] * v0[2];
        p1 = -edge[2] * v1[0] + edge[0] * v1[2];
        p2 = -edge[2] * v2[0] + edge[0] * v2[2];
        min = Math.min(p0, Math.min(p1, p2));
        max = Math.max(p0, Math.max(p1, p2));
        rad = fez * half[0] + fex * half[2];
        if (min > rad || max < -rad) return false;

        p0 = edge[1] * v0[0] - edge[0] * v0[1];
        p1 = edge[1] * v1[0] - edge[0] * v1[1];
        p2 = edge[1] * v2[0] - edge[0] * v2[1];
        min = Math.min(p0, Math.min(p1, p2));
        max = Math.max(p0, Math.max(p1, p2));
        rad = fey * half[0] + fex * half[1];
        return !(min > rad || max < -rad);
    }

    private double[] cross(double[] a, double[] b) {
        return new double[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    private boolean planeBoxOverlap(double[] normal, double[] vert, double[] maxbox) {
        double[] vmin = new double[3];
        double[] vmax = new double[3];
        for (int q = 0; q < 3; q++) {
            double v = vert[q];
            if (normal[q] > 0.0f) {
                vmin[q] = -maxbox[q] - v;
                vmax[q] = maxbox[q] - v;
            } else {
                vmin[q] = maxbox[q] - v;
                vmax[q] = -maxbox[q] - v;
            }
        }
        if (dot(normal, vmin) > 0.0f) return false;
        return dot(normal, vmax) >= 0.0f;
    }

    private double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private boolean isTooLarge(Box box) {
        if (box == null) return true;
        double dx = box.maxX - box.minX;
        double dy = box.maxY - box.minY;
        double dz = box.maxZ - box.minZ;
        return dx > MAX_DIMENSION || dy > MAX_DIMENSION || dz > MAX_DIMENSION;
    }

    private boolean hasShapes() {
        return voxelShape != null || (boxCache != null && !boxCache.isEmpty());
    }

    private void logCollisionInstall(int count) {
        long now = System.nanoTime();
        if (count != lastCollisionInstallCount || now - lastCollisionInstallNanos >= 1_000_000_000L) {
            LOGGER.info("Model {} installed {} collision boxes from server (no union)", modelId, count);
            lastCollisionInstallCount = count;
            lastCollisionInstallNanos = now;
        }
    }

    private void logCollisionReuse(int count) {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }
        long now = System.nanoTime();
        if (count != lastReuseCount || now - lastReuseLogNanos >= 5_000_000_000L) {
            LOGGER.debug("Model {} reusing {} collision boxes (translate-only)", modelId, count);
            lastReuseCount = count;
            lastReuseLogNanos = now;
        }
    }

    private record BuildResult(VoxelShape shape, Box bounds, List<Box> boxes) {
    }

    private record BuildRequest(float[] vertices, int[] indices, List<OBB> collisionBoxes,
                                Vector3 position, Quaternion rotation, Vector3 scale,
                                double fw, double fh, double fd, int collisionHash) {
    }

    private static final class TempRun {
        final int x0;
        final int x1;
        final int startZ;
        final int y;
        int lastZ;

        TempRun(int x0, int x1, int startZ, int lastZ, int y) {
            this.x0 = x0;
            this.x1 = x1;
            this.startZ = startZ;
            this.lastZ = lastZ;
            this.y = y;
        }
    }

    private record RunZ(int x0, int x1, int z0, int z1, int y) {
    }

    private static final class RunY {
        final int x0;
        final int x1;
        final int z0;
        final int z1;
        int startY;
        int endY;

        RunY(int x0, int x1, int z0, int z1, int startY, int endY) {
            this.x0 = x0;
            this.x1 = x1;
            this.z0 = z0;
            this.z1 = z1;
            this.startY = startY;
            this.endY = endY;
        }
    }
}
