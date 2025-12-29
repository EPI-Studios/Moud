package com.moud.client.primitives;

import com.moud.network.MoudPackets;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

public final class PrimitiveCollisionManager {
    private static final PrimitiveCollisionManager INSTANCE = new PrimitiveCollisionManager();

    private final Map<Long, PrimitiveCollision> collisions = new ConcurrentHashMap<>();
    private final Set<Long> pendingUpdates = ConcurrentHashMap.newKeySet();

    public static PrimitiveCollisionManager getInstance() { return INSTANCE; }
    private PrimitiveCollisionManager() {}

    public void registerPrimitive(ClientPrimitive primitive) {
        updatePrimitive(primitive);
    }

    public void updatePrimitive(ClientPrimitive primitive) {
        if (primitive == null || !primitive.hasCollision()) return;

        // prevent queuing multiple updates for the same primitive
        if (!pendingUpdates.add(primitive.getId())) return;

        // capture state on the main thread
        CollisionTask task = new CollisionTask(primitive);

        CompletableFuture.supplyAsync(task::calculate, ForkJoinPool.commonPool())
                .thenAccept(result -> collisions.put(task.id, result))
                .whenComplete((res, ex) -> {
                    pendingUpdates.remove(task.id);
                    if (ex != null) ex.printStackTrace();
                });
    }

    public void unregisterPrimitive(long id) {
        collisions.remove(id);
        pendingUpdates.remove(id); // cancels interest in the result
    }

    public void clear() {
        collisions.clear();
        pendingUpdates.clear();
    }

    public List<VoxelShape> getCollisionShapes(Box queryBox) {
        List<VoxelShape> shapes = new ArrayList<>();
        for (PrimitiveCollision col : collisions.values()) {
            if (PrimitiveMeshCollisionManager.getInstance().hasMesh(col.id())) {
                continue;
            }
            if (col.bounds().intersects(queryBox)) {
                if (!col.boxShapes().isEmpty()) {
                    shapes.addAll(col.boxShapes());
                } else if (col.shape() != null) {
                    shapes.add(col.shape());
                }
            }
        }
        return shapes;
    }

    public record PrimitiveCollision(long id, Box bounds, VoxelShape shape, List<VoxelShape> boxShapes) {}
}
