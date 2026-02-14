package com.moud.client.fabric.editor.ghost;

import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneOpResult;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import com.moud.core.csg.CsgVoxelizer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.Objects;

public final class EditorGhostBlocks {
    private static final EditorGhostBlocks INSTANCE = new EditorGhostBlocks();

    public static EditorGhostBlocks get() {
        return INSTANCE;
    }

    public enum Phase {
        IDLE,
        DRAGGING,
        AWAITING_ACK
    }

    private Phase phase = Phase.IDLE;
    private long nodeId;
    private LongOpenHashSet previewPositions = new LongOpenHashSet();
    private LongOpenHashSet maskPositions = new LongOpenHashSet();
    private int maskMinX = Integer.MAX_VALUE;
    private int maskMinY = Integer.MAX_VALUE;
    private int maskMinZ = Integer.MAX_VALUE;
    private int maskMaxX = Integer.MIN_VALUE;
    private int maskMaxY = Integer.MIN_VALUE;
    private int maskMaxZ = Integer.MIN_VALUE;

    private BlockState csgDefaultState = Blocks.STONE.getDefaultState();

    private Vec3d startBase = Vec3d.ZERO;
    private Vec3d startSize = new Vec3d(1.0, 1.0, 1.0);
    private Vec3d startRotDeg = Vec3d.ZERO;
    private Vec3d startCenter = Vec3d.ZERO;

    private Vec3d renderBase = Vec3d.ZERO;
    private Vec3d renderSize = new Vec3d(1.0, 1.0, 1.0);
    private Vec3d renderRotDeg = Vec3d.ZERO;
    private long keepPreviewUntilNs;
    private long awaitingAckDeadlineNs;
    private int worldUpdatedStreak;

    private EditorGhostBlocks() {
    }

    public boolean isActive() {
        return phase != Phase.IDLE;
    }

    public boolean isDragging() {
        return phase == Phase.DRAGGING;
    }

    public Phase phase() {
        return phase;
    }

    public long nodeId() {
        return nodeId;
    }

    public LongOpenHashSet previewPositions() {
        return previewPositions;
    }

    public boolean shouldHideInChunks(BlockPos pos, BlockState state) {
        if (pos == null || state == null || !isActive() || maskPositions == null || maskPositions.isEmpty()) {
            return false;
        }
        if (state.isAir()) {
            return false;
        }
        if (state.getBlock() != csgDefaultState.getBlock()) {
            return false;
        }
        return maskPositions.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()));
    }

    public BlockState csgDefaultState() {
        return csgDefaultState;
    }

    public Vec3d startBase() {
        return startBase;
    }

    public Vec3d startSize() {
        return startSize;
    }

    public Vec3d startRotDeg() {
        return startRotDeg;
    }

    public Vec3d startCenter() {
        return startCenter;
    }

    public Vec3d renderBase() {
        return renderBase;
    }

    public Vec3d renderSize() {
        return renderSize;
    }

    public Vec3d renderRotDeg() {
        return renderRotDeg;
    }

    public void startCsgBlock(long nodeId,
                             BlockPos base,
                             int sx,
                             int sy,
                             int sz,
                             Vec3d rotDeg,
                             BlockState csgDefaultState) {
        Objects.requireNonNull(base, "base");
        cancel();

        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client == null ? null : client.world;
        if (world == null) {
            return;
        }

        int xs = Math.max(1, sx);
        int ys = Math.max(1, sy);
        int zs = Math.max(1, sz);
        Vec3d r = rotDeg == null ? Vec3d.ZERO : rotDeg;

        BlockState defaultState = csgDefaultState == null ? Blocks.STONE.getDefaultState() : csgDefaultState;
        if (defaultState.isAir()) {
            defaultState = Blocks.STONE.getDefaultState();
        }

        int bx = base.getX();
        int by = base.getY();
        int bz = base.getZ();

        LongOpenHashSet selectionPositions = new LongOpenHashSet();
        collectCsgPositions(bx, by, bz, xs, ys, zs, (float) r.x, (float) r.y, (float) r.z, selectionPositions);

        Vec3d startBase = new Vec3d(bx, by, bz);
        Vec3d startSize = new Vec3d(xs, ys, zs);
        Vec3d startCenter = startBase.add(startSize.multiply(0.5));

        LongOpenHashSet nextMask = new LongOpenHashSet(selectionPositions.size());
        BlockPos.Mutable pos = new BlockPos.Mutable();
        Block expectedBlock = defaultState.getBlock();
        for (long packed : selectionPositions) {
            pos.set(BlockPos.unpackLongX(packed), BlockPos.unpackLongY(packed), BlockPos.unpackLongZ(packed));
            BlockState state = world.getBlockState(pos);
            if (state == null || state.isAir()) {
                continue;
            }
            if (expectedBlock != Blocks.AIR && state.getBlock() != expectedBlock) {
                continue;
            }
            nextMask.add(packed);
            maskMinX = Math.min(maskMinX, pos.getX());
            maskMinY = Math.min(maskMinY, pos.getY());
            maskMinZ = Math.min(maskMinZ, pos.getZ());
            maskMaxX = Math.max(maskMaxX, pos.getX());
            maskMaxY = Math.max(maskMaxY, pos.getY());
            maskMaxZ = Math.max(maskMaxZ, pos.getZ());
        }

        this.nodeId = nodeId;
        this.csgDefaultState = defaultState;
        this.startBase = startBase;
        this.startSize = startSize;
        this.startRotDeg = r;
        this.startCenter = startCenter;

        this.renderBase = startBase;
        this.renderSize = startSize;
        this.renderRotDeg = r;
        this.previewPositions = selectionPositions;
        this.maskPositions = nextMask;

        if (this.previewPositions.isEmpty()) {
            clear();
            return;
        }

        phase = Phase.DRAGGING;
        scheduleMaskRerender();
    }

    public void setRenderTransform(Vec3d base, Vec3d size, Vec3d rotDeg) {
        if (phase != Phase.DRAGGING) {
            return;
        }
        if (base != null) {
            renderBase = base;
        }
        if (size != null) {
            renderSize = size;
        }
        if (rotDeg != null) {
            renderRotDeg = rotDeg;
        }
        rebuildPreviewPositions();
    }

    public void clientTick() {
        if (phase != Phase.AWAITING_ACK) {
            return;
        }
        long now = System.nanoTime();
        if (keepPreviewUntilNs > 0L) {
            if (worldLooksUpdated()) {
                clear();
                return;
            }
            if (now >= keepPreviewUntilNs) {
                clear();
                return;
            }
        }
        if (awaitingAckDeadlineNs > 0L && now >= awaitingAckDeadlineNs) {
            clear();
        }
    }

    public void commitAndPredict(Vec3d finalBase, Vec3d finalSize, Vec3d finalRotDeg) {
        if (phase != Phase.DRAGGING) {
            return;
        }
        Vec3d base = finalBase == null ? renderBase : finalBase;
        Vec3d size = finalSize == null ? renderSize : finalSize;
        Vec3d rot = finalRotDeg == null ? renderRotDeg : finalRotDeg;
        renderBase = base;
        renderSize = size;
        renderRotDeg = rot;
        rebuildPreviewPositions();
        phase = Phase.AWAITING_ACK;
        keepPreviewUntilNs = 0L;
        awaitingAckDeadlineNs = System.nanoTime() + 2_000_000_000L;
        worldUpdatedStreak = 0;
    }

    public void cancel() {
        clear();
    }

    public void onAck(SceneOpAck ack) {
        if (ack == null || phase != Phase.AWAITING_ACK) {
            return;
        }
        boolean relevant = false;
        boolean allOk = true;
        if (ack.results() != null) {
            for (SceneOpResult r : ack.results()) {
                if (r == null) {
                    continue;
                }
                if (r.targetId() != nodeId) {
                    continue;
                }
                relevant = true;
                if (!r.ok()) {
                    allOk = false;
                }
            }
        }
        if (!relevant) {
            return;
        }

        keepPreviewUntilNs = allOk ? (System.nanoTime() + 2_500_000_000L) : 0L;
        awaitingAckDeadlineNs = 0L;
        if (!allOk) {
            clear();
        }
    }

    private boolean worldLooksUpdated() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client == null ? null : client.world;
        if (world == null || previewPositions == null || previewPositions.isEmpty()) {
            return true;
        }
        Block expected = csgDefaultState.getBlock();
        if (expected == Blocks.AIR) {
            return true;
        }
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int checked = 0;
        int maxCheck = previewPositions.size() <= 4096 ? Integer.MAX_VALUE : 1024;
        for (long packed : previewPositions) {
            pos.set(BlockPos.unpackLongX(packed), BlockPos.unpackLongY(packed), BlockPos.unpackLongZ(packed));
            BlockState state = world.getBlockState(pos);
            if (state == null || state.getBlock() != expected) {
                worldUpdatedStreak = 0;
                return false;
            }
            checked++;
            if (checked >= maxCheck) {
                break;
            }
        }
        worldUpdatedStreak++;
        return worldUpdatedStreak >= 2;
    }

    private void rebuildPreviewPositions() {
        int bx = (int) Math.round(renderBase.x);
        int by = (int) Math.round(renderBase.y);
        int bz = (int) Math.round(renderBase.z);
        int sx = Math.max(1, (int) Math.round(renderSize.x));
        int sy = Math.max(1, (int) Math.round(renderSize.y));
        int sz = Math.max(1, (int) Math.round(renderSize.z));

        LongOpenHashSet next = new LongOpenHashSet();
        collectCsgPositions(bx, by, bz, sx, sy, sz, (float) renderRotDeg.x, (float) renderRotDeg.y, (float) renderRotDeg.z, next);
        previewPositions = next;
    }

    private void scheduleMaskRerender() {
        MinecraftClient client = MinecraftClient.getInstance();
        WorldRenderer wr = client == null ? null : client.worldRenderer;
        if (wr == null) {
            return;
        }
        if (maskMinX == Integer.MAX_VALUE) {
            return;
        }
        wr.scheduleBlockRenders(maskMinX, maskMinY, maskMinZ, maskMaxX, maskMaxY, maskMaxZ);
    }

    private void clear() {
        boolean hadMask = maskPositions != null && !maskPositions.isEmpty();
        if (hadMask) {
            scheduleMaskRerender();
        }
        phase = Phase.IDLE;
        nodeId = 0L;
        previewPositions = new LongOpenHashSet();
        maskPositions = new LongOpenHashSet();
        maskMinX = maskMinY = maskMinZ = Integer.MAX_VALUE;
        maskMaxX = maskMaxY = maskMaxZ = Integer.MIN_VALUE;
        csgDefaultState = Blocks.STONE.getDefaultState();
        startBase = Vec3d.ZERO;
        startSize = new Vec3d(1.0, 1.0, 1.0);
        startRotDeg = Vec3d.ZERO;
        startCenter = Vec3d.ZERO;
        renderBase = Vec3d.ZERO;
        renderSize = new Vec3d(1.0, 1.0, 1.0);
        renderRotDeg = Vec3d.ZERO;
        keepPreviewUntilNs = 0L;
        awaitingAckDeadlineNs = 0L;
        worldUpdatedStreak = 0;
    }

    private static void collectCsgPositions(int x,
                                            int y,
                                            int z,
                                            int sx,
                                            int sy,
                                            int sz,
                                            float rxDeg,
                                            float ryDeg,
                                            float rzDeg,
                                            LongOpenHashSet out) {
        if (out == null) {
            return;
        }
        CsgVoxelizer.forEachVoxel(x, y, z, sx, sy, sz, rxDeg, ryDeg, rzDeg, (xx, yy, zz) -> out.add(BlockPos.asLong(xx, yy, zz)));
    }
}
