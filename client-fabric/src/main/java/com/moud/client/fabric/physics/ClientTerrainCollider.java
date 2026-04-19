package com.moud.client.fabric.physics;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.*;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ClientTerrainCollider {
    private static final int RADIUS_SECTIONS = 1;

    private final PhysicsSystem system;
    private final BodyInterface bodies;
    private final int staticLayer;

    private final Long2IntMap activeSections = new Long2IntOpenHashMap();
    private final ConcurrentLinkedQueue<Long> pendingInvalidations = new ConcurrentLinkedQueue<>();

    private int lastPlayerSx = Integer.MIN_VALUE, lastPlayerSy = Integer.MIN_VALUE, lastPlayerSz = Integer.MIN_VALUE;
    private ClientWorld lastWorld;

    private record BoxData(float cx, float cy, float cz, float hx, float hy, float hz) {}

    public ClientTerrainCollider(PhysicsSystem system, int staticLayer) {
        this.system = system;
        this.bodies = system.getBodyInterface();
        this.staticLayer = staticLayer;
        this.activeSections.defaultReturnValue(Jolt.cInvalidBodyId);
    }

    public void update(ClientWorld world, double px, double py, double pz) {
        if (world == null) return;

        drainPendingInvalidations();

        int sx = ChunkSectionPos.getSectionCoord(px);
        int sy = ChunkSectionPos.getSectionCoord(py);
        int sz = ChunkSectionPos.getSectionCoord(pz);

        if (world == lastWorld && sx == lastPlayerSx && sy == lastPlayerSy && sz == lastPlayerSz) return;

        if (world != lastWorld) clear();

        lastWorld = world;
        lastPlayerSx = sx; lastPlayerSy = sy; lastPlayerSz = sz;

        var desired = new LongOpenHashSet(27);
        for (int dx = -RADIUS_SECTIONS; dx <= RADIUS_SECTIONS; dx++) {
            for (int dy = -RADIUS_SECTIONS; dy <= RADIUS_SECTIONS; dy++) {
                for (int dz = -RADIUS_SECTIONS; dz <= RADIUS_SECTIONS; dz++) {
                    desired.add(ChunkSectionPos.asLong(sx + dx, sy + dy, sz + dz));
                }
            }
        }

        var it = activeSections.long2IntEntrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (desired.contains(e.getLongKey())) continue;

            int bodyId = e.getIntValue();
            if (bodyId != Jolt.cInvalidBodyId) {
                JoltSafety.breadcrumb("destroyTerrainBody", "id=" + bodyId);
                destroyBodySafe(bodyId);
            }
            it.remove();
        }

        var desiredIt = desired.longIterator();
        while (desiredIt.hasNext()) {
            long section = desiredIt.nextLong();
            if (!activeSections.containsKey(section)) {
                activeSections.put(section, bakeSection(world, section));
            }
        }
    }

    public void invalidateBlock(int worldX, int worldY, int worldZ) {
        pendingInvalidations.add(ChunkSectionPos.asLong(worldX >> 4, worldY >> 4, worldZ >> 4));
    }

    private void drainPendingInvalidations() {
        Long key;
        boolean changed = false;

        while ((key = pendingInvalidations.poll()) != null) {
            long k = key;
            if (!activeSections.containsKey(k)) continue;

            int bodyId = activeSections.remove(k);
            if (bodyId != Jolt.cInvalidBodyId) destroyBodySafe(bodyId);
            changed = true;
        }

        if (changed) lastPlayerSx = Integer.MIN_VALUE; // force rebuild next tick
    }

    public void clear() {
        for (var e : activeSections.long2IntEntrySet()) {
            if (e.getIntValue() != Jolt.cInvalidBodyId) destroyBodySafe(e.getIntValue());
        }
        activeSections.clear();
        lastPlayerSx = lastPlayerSy = lastPlayerSz = Integer.MIN_VALUE;
    }

    private void destroyBodySafe(int id) {
        try {
            bodies.removeBody(id);
            bodies.destroyBody(id);
        } catch (Throwable ignored) {}
    }

    private int bakeSection(ClientWorld world, long section) {
        int sx = ChunkSectionPos.unpackX(section), sy = ChunkSectionPos.unpackY(section), sz = ChunkSectionPos.unpackZ(section);
        int ox = sx * 16, oy = sy * 16, oz = sz * 16;

        List<BoxData> boxes = new ArrayList<>();
        var pos = new BlockPos.Mutable();

        for (int ix = 0; ix < 16; ix++) {
            for (int iy = 0; iy < 16; iy++) {
                for (int iz = 0; iz < 16; iz++) {
                    pos.set(ox + ix, oy + iy, oz + iz);
                    try {
                        BlockState state = world.getBlockState(pos);
                        if (state.isAir()) continue;

                        VoxelShape shape = state.getCollisionShape(world, pos);
                        if (shape.isEmpty()) continue;

                        for (Box b : shape.getBoundingBoxes()) {
                            float hx = (float) ((b.maxX - b.minX) * 0.5);
                            float hy = (float) ((b.maxY - b.minY) * 0.5);
                            float hz = (float) ((b.maxZ - b.minZ) * 0.5);

                            if (hx <= 1e-4f || hy <= 1e-4f || hz <= 1e-4f) continue;

                            float cx = (float) (pos.getX() + b.minX + hx - ox);
                            float cy = (float) (pos.getY() + b.minY + hy - oy);
                            float cz = (float) (pos.getZ() + b.minZ + hz - oz);
                            boxes.add(new BoxData(cx, cy, cz, hx, hy, hz));
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }

        if (boxes.isEmpty()) return Jolt.cInvalidBodyId;

        var origin = new RVec3((double) ox, (double) oy, (double) oz);

        if (boxes.size() == 1) {
            var b = boxes.get(0);
            if (!JoltSafety.checkExtents("bakeSection.singleBox", b.hx, b.hy, b.hz)) return Jolt.cInvalidBodyId;
            JoltSafety.breadcrumb("bakeSection.singleBox", "section=" + sx + "," + sy + "," + sz, "extents=" + b.hx + "," + b.hy + "," + b.hz);

            try (var shape = new BoxShapeSettings(b.hx, b.hy, b.hz);
                 var bcs = new BodyCreationSettings(shape, new RVec3(ox + b.cx, oy + b.cy, oz + b.cz), Quat.sIdentity(), EMotionType.Static, staticLayer)) {
                return bodies.createAndAddBody(bcs, EActivation.DontActivate);
            } catch (Throwable t) {
                return Jolt.cInvalidBodyId;
            }
        }

        if (!JoltSafety.checkCompoundChildCount("bakeSection.compound", boxes.size())) return Jolt.cInvalidBodyId;
        JoltSafety.breadcrumb("bakeSection.compound", "section=" + sx + "," + sy + "," + sz, "children=" + boxes.size());

        try (var compound = new StaticCompoundShapeSettings()) {
            for (var b : boxes) {
                if (!JoltSafety.checkExtents("bakeSection.child", b.hx, b.hy, b.hz)) continue;
                compound.addShape(new Vec3(b.cx, b.cy, b.cz), Quat.sIdentity(), new BoxShapeSettings(b.hx, b.hy, b.hz));
            }

            try (var bcs = new BodyCreationSettings(compound, origin, Quat.sIdentity(), EMotionType.Static, staticLayer)) {
                return bodies.createAndAddBody(bcs, EActivation.DontActivate);
            }
        } catch (Throwable t) {
            return Jolt.cInvalidBodyId;
        }
    }
}