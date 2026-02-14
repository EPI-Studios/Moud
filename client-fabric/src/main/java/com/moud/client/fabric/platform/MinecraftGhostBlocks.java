package com.moud.client.fabric.platform;

import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneOpResult;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import com.moud.core.csg.CsgVoxelizer;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.Objects;

public final class MinecraftGhostBlocks {
    private static final MinecraftGhostBlocks INSTANCE = new MinecraftGhostBlocks();

    public static MinecraftGhostBlocks get() {
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

    private Vector3d startBase = new Vector3d();
    private Vector3d startSize = new Vector3d(1.0, 1.0, 1.0);
    private Vector3d startRotDeg = new Vector3d();
    private Vector3d startCenter = new Vector3d();

    private Vector3d renderBase = new Vector3d();
    private Vector3d renderSize = new Vector3d(1.0, 1.0, 1.0);
    private Vector3d renderRotDeg = new Vector3d();
    private long keepPreviewUntilNs;
    private long awaitingAckDeadlineNs;
    private int worldUpdatedStreak;

    private MinecraftGhostBlocks() {
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

    public Vector3d startBase() {
        return startBase;
    }

    public Vector3d startSize() {
        return startSize;
    }

    public Vector3d startRotDeg() {
        return startRotDeg;
    }

    public Vector3d startCenter() {
        return startCenter;
    }

    public Vector3d renderBase() {
        return renderBase;
    }

    public Vector3d renderSize() {
        return renderSize;
    }

    public Vector3d renderRotDeg() {
        return renderRotDeg;
    }

    public void startCsgBlock(long nodeId,
                             int baseX,
                             int baseY,
                             int baseZ,
                             int sx,
                             int sy,
                             int sz,
                             Vector3d rotDeg,
                             String blockId) {
        cancel();

        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client == null ? null : client.world;
        if (world == null) {
            return;
        }

        int xs = Math.max(1, sx);
        int ys = Math.max(1, sy);
        int zs = Math.max(1, sz);
        Vector3d r = rotDeg == null ? new Vector3d() : new Vector3d(rotDeg);

        BlockState defaultState = resolveBlockState(blockId, Blocks.STONE.getDefaultState());
        if (defaultState.isAir()) {
            defaultState = Blocks.STONE.getDefaultState();
        }

        int bx = baseX;
        int by = baseY;
        int bz = baseZ;

        LongOpenHashSet selectionPositions = new LongOpenHashSet();
        collectCsgPositions(bx, by, bz, xs, ys, zs, (float) r.x, (float) r.y, (float) r.z, selectionPositions);

        Vector3d startBase = new Vector3d(bx, by, bz);
        Vector3d startSize = new Vector3d(xs, ys, zs);
        Vector3d startCenter = new Vector3d(startBase).add(new Vector3d(startSize).mul(0.5));

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

    public void setRenderTransform(Vector3d base, Vector3d size, Vector3d rotDeg) {
        if (phase != Phase.DRAGGING) {
            return;
        }
        if (base != null) {
            renderBase = new Vector3d(base);
        }
        if (size != null) {
            renderSize = new Vector3d(size);
        }
        if (rotDeg != null) {
            renderRotDeg = new Vector3d(rotDeg);
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

    public void commitAndPredict(Vector3d finalBase, Vector3d finalSize, Vector3d finalRotDeg) {
        if (phase != Phase.DRAGGING) {
            return;
        }
        Vector3d base = finalBase == null ? renderBase : finalBase;
        Vector3d size = finalSize == null ? renderSize : finalSize;
        Vector3d rot = finalRotDeg == null ? renderRotDeg : finalRotDeg;
        renderBase = new Vector3d(base);
        renderSize = new Vector3d(size);
        renderRotDeg = new Vector3d(rot);
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

    private static BlockState resolveBlockState(String blockId, BlockState fallback) {
        if (blockId == null || blockId.isBlank()) {
            return fallback;
        }
        Identifier id = Identifier.tryParse(blockId.trim());
        if (id == null) {
            return fallback;
        }
        Block block = Registries.BLOCK.get(id);
        if (block == null || block == Blocks.AIR) {
            return fallback;
        }
        return block.getDefaultState();
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
        startBase = new Vector3d();
        startSize = new Vector3d(1.0, 1.0, 1.0);
        startRotDeg = new Vector3d();
        startCenter = new Vector3d();
        renderBase = new Vector3d();
        renderSize = new Vector3d(1.0, 1.0, 1.0);
        renderRotDeg = new Vector3d();
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
        CsgVoxelizer.forEachVoxel(
                new CsgVoxelizer.VoxelDefinition(x, y, z, sx, sy, sz, rxDeg, ryDeg, rzDeg),
                (xx, yy, zz) -> out.add(BlockPos.asLong(xx, yy, zz))
        );
    }
}
