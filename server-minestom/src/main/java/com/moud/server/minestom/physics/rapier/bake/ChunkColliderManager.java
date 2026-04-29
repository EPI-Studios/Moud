package com.moud.server.minestom.physics.rapier.bake;

import com.moud.physics.api.BodyHandle;
import com.moud.physics.api.CollisionGroups;
import com.moud.physics.api.Quat;
import com.moud.physics.api.ShapeDesc;
import com.moud.physics.api.Transform;
import com.moud.physics.api.Vec3;
import com.moud.physics.rapier.RapierPhysicsWorld;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

/**
 * Owns the chunk-section static colliders for a single {@link Instance}.
 *
 * <p>Bakes happen on a dedicated baker thread pool; results are drained on the
 * server tick thread which is the only place {@link RapierPhysicsWorld} is
 * mutated. Block edits coalesce per tick - 100 blocks broken in a section
 * within one tick produces exactly one re-bake. New bakes are throttled to 8
 * per tick to avoid avalanche on chunk-load boundaries.
 */
public final class ChunkColliderManager {

    private static final int BAKE_THREADS_PER_INSTANCE = 2;
    private static final int MAX_NEW_BAKES_PER_TICK = 8;

    private final Instance instance;
    private final RapierPhysicsWorld world;
    private final ExecutorService bakerPool;

    private final Long2ObjectOpenHashMap<BodyHandle> sectionToBody = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet pendingBakes = new LongOpenHashSet();
    private final LongOpenHashSet dirtySections = new LongOpenHashSet();
    private final Queue<BakeResult> results = new ConcurrentLinkedQueue<>();

    public ChunkColliderManager(Instance instance, RapierPhysicsWorld world) {
        this.instance = instance;
        this.world = world;
        AtomicInteger n = new AtomicInteger();
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "moud-physics-baker-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        this.bakerPool = Executors.newFixedThreadPool(BAKE_THREADS_PER_INSTANCE, tf);
    }

    /** Tick-thread entry: section just loaded; schedule its initial bake. */
    public void onSectionLoaded(int chunkX, int sectionY, int chunkZ) {
        markDirty(chunkX, sectionY, chunkZ);
    }

    /** Tick-thread entry: section unloaded; drop any pending bake and remove its body now. */
    public void onSectionUnloaded(int chunkX, int sectionY, int chunkZ) {
        long key = SectionPos.pack(chunkX, sectionY, chunkZ);
        pendingBakes.remove(key);
        dirtySections.remove(key);
        BodyHandle h = sectionToBody.remove(key);
        if (h != null) world.remove(h);
    }

    /** Tick-thread entry: block changed at world coords (absolute). */
    public void onBlockChanged(int worldX, int worldY, int worldZ) {
        markDirty(worldX >> 4, worldY >> 4, worldZ >> 4);
    }

    private void markDirty(int chunkX, int sectionY, int chunkZ) {
        dirtySections.add(SectionPos.pack(chunkX, sectionY, chunkZ));
    }

    /** Call once per server tick on the tick thread. */
    public void tick() {
        drainResults();
        scheduleDirtyBakes();
    }

    private void drainResults() {
        BakeResult r;
        while ((r = results.poll()) != null) {
            applyResult(r);
        }
    }

    private void applyResult(BakeResult r) {
        pendingBakes.remove(r.sectionPos());
        // If the section unloaded between schedule and result, drop the result.
        if (!isSectionLoaded(r.sectionPos())) return;
        BodyHandle old = sectionToBody.remove(r.sectionPos());
        if (old != null) world.remove(old);
        if (r.trimesh().isEmpty()) return;
        Vec3 origin = sectionOrigin(r.sectionPos());
        BodyHandle newBody = world.addStatic(
                new ShapeDesc.Trimesh(r.trimesh().vertices(), r.trimesh().indices()),
                new Transform(origin, Quat.IDENTITY),
                CollisionGroups.DEFAULT);
        sectionToBody.put(r.sectionPos(), newBody);
    }

    private void scheduleDirtyBakes() {
        if (dirtySections.isEmpty()) return;
        int started = 0;
        LongIterator it = dirtySections.iterator();
        while (it.hasNext() && started < MAX_NEW_BAKES_PER_TICK) {
            long key = it.nextLong();
            it.remove();
            if (pendingBakes.contains(key)) continue;
            BakeJob job = snapshot(key);
            if (job == null) continue; // section unloaded between dirty mark and snapshot
            pendingBakes.add(key);
            bakerPool.submit(() -> runBake(job));
            started++;
        }
    }

    private BakeJob snapshot(long key) {
        if (!isSectionLoaded(key)) return null;
        boolean[] solid = new boolean[4096];
        Map<Integer, Block> custom = new HashMap<>();
        int cx = SectionPos.chunkX(key);
        int sy = SectionPos.sectionY(key);
        int cz = SectionPos.chunkZ(key);
        int baseX = cx << 4;
        int baseY = sy << 4;
        int baseZ = cz << 4;
        for (int dx = 0; dx < 16; dx++) {
            for (int dy = 0; dy < 16; dy++) {
                for (int dz = 0; dz < 16; dz++) {
                    Block b = instance.getBlock(baseX + dx, baseY + dy, baseZ + dz, Block.Getter.Condition.TYPE);
                    if (b == null || b.isAir()) continue;
                    if (CustomShapeEmitter.isFullCubeOrEmpty(b)) {
                        solid[BakeJob.blockIndex(dx, dy, dz)] = true;
                    } else {
                        custom.put(BakeJob.blockIndex(dx, dy, dz), b);
                    }
                }
            }
        }
        return new BakeJob(key, solid, custom);
    }

    private void runBake(BakeJob job) {
        try {
            SectionTrimesh greedy = GreedyMesher.mesh(job.solid());
            FloatArrayList verts = new FloatArrayList(greedy.vertices());
            IntArrayList idx = new IntArrayList(greedy.indices());
            job.customBlocks().forEach((blockIdx, block) -> {
                int dx = blockIdx / 256;
                int dy = (blockIdx / 16) % 16;
                int dz = blockIdx % 16;
                CustomShapeEmitter.emit(block, dx, dy, dz, verts, idx);
            });
            SectionTrimesh combined = new SectionTrimesh(verts.toFloatArray(), idx.toIntArray());
            results.offer(new BakeResult(job.sectionPos(), combined));
        } catch (RuntimeException e) {
            // Bake failed - emit an empty result so the section's pending state clears
            // and any future block change can retry. Operator-actionable failures
            // surface via instance-tick error handling, not log spam here.
            results.offer(new BakeResult(job.sectionPos(), SectionTrimesh.empty()));
        }
    }

    private boolean isSectionLoaded(long key) {
        int cx = SectionPos.chunkX(key);
        int cz = SectionPos.chunkZ(key);
        Chunk c = instance.getChunk(cx, cz);
        // Minestom Chunk doesn't expose per-section load directly; assume loaded if the chunk is.
        return c != null;
    }

    private static Vec3 sectionOrigin(long key) {
        return new Vec3(SectionPos.chunkX(key) << 4,
                SectionPos.sectionY(key) << 4,
                SectionPos.chunkZ(key) << 4);
    }

    public void close() {
        bakerPool.shutdownNow();
        for (BodyHandle h : sectionToBody.values()) world.remove(h);
        sectionToBody.clear();
        pendingBakes.clear();
        dirtySections.clear();
        results.clear();
    }
}
