package com.moud.client.collision;

import com.moud.client.model.RenderableModel;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ModelCollisionManager {
    private static final ModelCollisionManager INSTANCE = new ModelCollisionManager();

    private final Map<Long, ModelCollisionVolume> volumes = new ConcurrentHashMap<>();

    private ModelCollisionManager() {
    }

    public static ModelCollisionManager getInstance() {
        return INSTANCE;
    }

    public void sync(RenderableModel model) {
        if (model == null) {
            return;
        }
        boolean hasMesh = model.hasMeshBounds();
        boolean hasFallback = model.getCollisionWidth() > 0 && model.getCollisionHeight() > 0 && model.getCollisionDepth() > 0;
        if (!hasMesh && !hasFallback) {
            removeModel(model.getId());
            return;
        }
        ModelCollisionVolume volume = volumes.computeIfAbsent(model.getId(), ModelCollisionVolume::new);
        volume.update(model);
    }

    public void updateTransform(RenderableModel model) {
        if (model == null) {
            return;
        }
        ModelCollisionVolume volume = volumes.get(model.getId());
        if (volume == null) {
            return;
        }
        volume.updateTransform(model);
    }

    public void removeModel(long modelId) {
        volumes.remove(modelId);
    }

    public void clear() {
        volumes.clear();
    }

    public List<Box> getDebugBoxes() {
        if (volumes.isEmpty()) {
            return Collections.emptyList();
        }
        List<Box> boxes = new ArrayList<>();
        for (ModelCollisionVolume volume : volumes.values()) {
            if (!volume.isActive()) {
                continue;
            }
            if (ClientCollisionManager.hasDebugData(volume.getModelId())) {
                continue;
            }
            List<Box> cached = volume.getBoxes();
            if (cached != null && !cached.isEmpty()) {
                boxes.addAll(cached);
                continue;
            }
            VoxelShape shape = volume.getVoxelShape();
            if (shape != null) {
                shape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) ->
                        boxes.add(new Box(minX, minY, minZ, maxX, maxY, maxZ)));
                continue;
            }
            Box bounds = volume.getBounds();
            if (bounds != null) {
                boxes.add(bounds);
            }
        }
        return boxes;
    }

    public List<VoxelShape> collectShapes(Box query) {
        if (query == null || volumes.isEmpty()) {
            return Collections.emptyList();
        }

        List<VoxelShape> shapes = null;
        for (ModelCollisionVolume volume : volumes.values()) {
            if (!volume.isActive() || !volume.intersects(query)) {
                continue;
            }
            if (ClientCollisionManager.hasDebugData(volume.getModelId())) {
                continue;
            }
            if (shapes == null) {
                shapes = new ArrayList<>();
            }
            List<VoxelShape> boxShapes = volume.getBoxShapes();
            if (!boxShapes.isEmpty()) {
                shapes.addAll(boxShapes);
            } else {
                VoxelShape shape = volume.getVoxelShape();
                if (shape != null) {
                    shapes.add(shape);
                }
            }
        }

        return shapes != null ? shapes : Collections.emptyList();
    }

    public List<Box> collectBounds(Box query) {
        if (query == null || volumes.isEmpty()) {
            return Collections.emptyList();
        }

        List<Box> bounds = null;
        for (ModelCollisionVolume volume : volumes.values()) {
            if (!volume.isActive() || !volume.intersects(query)) {
                continue;
            }
            if (ClientCollisionManager.hasDebugData(volume.getModelId())) {
                continue;
            }
            Box modelBounds = volume.getBounds();
            if (modelBounds == null) {
                continue;
            }
            if (bounds == null) {
                bounds = new ArrayList<>();
            }
            bounds.add(modelBounds);
        }
        return bounds != null ? bounds : Collections.emptyList();
    }

    public long pick(Vec3d origin, Vec3d direction, double maxDistance) {
        if (origin == null || direction == null || volumes.isEmpty()) {
            return -1L;
        }
        ClientCollisionManager.RaycastHit meshHit = ClientCollisionManager.raycastAny(origin, direction, maxDistance);
        if (meshHit != null) {
            return meshHit.modelId();
        }
        double bestDistance = maxDistance;
        long bestId = -1L;
        for (ModelCollisionVolume volume : volumes.values()) {
            if (!volume.isActive()) {
                continue;
            }
            Box bounds = volume.getBounds();
            if (bounds == null) {
                continue;
            }
            double distance = rayIntersectAabb(origin, direction, bounds);
            if (distance >= 0 && distance < bestDistance) {
                bestDistance = distance;
                bestId = volume.getModelId();
            }
        }
        return bestId;
    }

    private double rayIntersectAabb(Vec3d origin, Vec3d direction, Box box) {
        double invX = 1.0 / (direction.x == 0 ? 1e-9 : direction.x);
        double invY = 1.0 / (direction.y == 0 ? 1e-9 : direction.y);
        double invZ = 1.0 / (direction.z == 0 ? 1e-9 : direction.z);

        double t1 = (box.minX - origin.x) * invX;
        double t2 = (box.maxX - origin.x) * invX;
        double tmin = Math.min(t1, t2);
        double tmax = Math.max(t1, t2);

        double ty1 = (box.minY - origin.y) * invY;
        double ty2 = (box.maxY - origin.y) * invY;
        tmin = Math.max(tmin, Math.min(ty1, ty2));
        tmax = Math.min(tmax, Math.max(ty1, ty2));

        double tz1 = (box.minZ - origin.z) * invZ;
        double tz2 = (box.maxZ - origin.z) * invZ;
        tmin = Math.max(tmin, Math.min(tz1, tz2));
        tmax = Math.min(tmax, Math.max(tz1, tz2));

        if (tmax < 0 || tmin > tmax) {
            return -1;
        }
        return tmin >= 0 ? tmin : tmax;
    }
}
