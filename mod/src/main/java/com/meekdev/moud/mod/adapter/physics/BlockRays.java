package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.script.api.BlockRef;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

public final class BlockRays implements BlockRef {

    private final Supplier<@Nullable Level> level;
    private final boolean writable;

    public BlockRays(Supplier<@Nullable Level> level, boolean writable) {
        this.level = level;
        this.writable = writable;
    }

    @Override
    public @Nullable Hit raycast(Vector3 from, Vector3 direction, double range) {
        return raycast(from, direction, range, false);
    }

    @Override
    public @Nullable Hit raycast(Vector3 from, Vector3 direction, double range, boolean fluids) {
        Level in = level.get();
        if (in == null || range <= 0 || direction.lengthSq() < 1e-24) return null;
        Vector3 way = direction.normalize();
        Vector3 to = from.add(way.mul(range));
        BlockHitResult hit = in.clip(new ClipContext(vec(from), vec(to), ClipContext.Block.COLLIDER,
                fluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS || hit.isInside()) return null;
        Vec3 at = hit.getLocation();
        Direction face = hit.getDirection();
        BlockPos pos = hit.getBlockPos();
        String block = BuiltInRegistries.BLOCK.getKey(in.getBlockState(pos).getBlock()).toString();
        Vector3 point = new Vector3(at.x, at.y, at.z);
        return new Hit(point, new Vector3(face.getStepX(), face.getStepY(), face.getStepZ()), point.distance(from), block);
    }

    @Override
    public boolean writable() {
        return writable;
    }

    @Override
    public String get(int x, int y, int z) {
        Level in = level.get();
        return in == null ? "minecraft:air" : BlockStateParser.serialize(in.getBlockState(new BlockPos(x, y, z)));
    }

    @Override
    public void set(int x, int y, int z, String block) {
        Level in = level.get();
        if (in != null) in.setBlock(new BlockPos(x, y, z), parse(block), Block.UPDATE_ALL);
    }

    @Override
    public long fill(int x0, int y0, int z0, int x1, int y1, int z1, String block) {
        Level in = level.get();
        if (in == null) return 0;
        BlockState state = parse(block);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        long count = 0;
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    in.setBlock(at.set(x, y, z), state, Block.UPDATE_CLIENTS);
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public String id(int x, int y, int z) {
        Level in = level.get();
        return in == null ? "minecraft:air" : BuiltInRegistries.BLOCK.getKey(in.getBlockState(new BlockPos(x, y, z)).getBlock()).toString();
    }

    @Override
    public boolean solid(int x, int y, int z) {
        Level in = level.get();
        return in != null && in.getBlockState(new BlockPos(x, y, z)).blocksMotion();
    }

    @Override
    public boolean air(int x, int y, int z) {
        Level in = level.get();
        return in == null || in.getBlockState(new BlockPos(x, y, z)).isAir();
    }

    @Override
    public boolean fluid(int x, int y, int z) {
        Level in = level.get();
        return in != null && !in.getFluidState(new BlockPos(x, y, z)).isEmpty();
    }

    @Override
    public int light(int x, int y, int z) {
        Level in = level.get();
        return in == null ? 15 : in.getMaxLocalRawBrightness(new BlockPos(x, y, z));
    }

    @Override
    public int top(int x, int z) {
        Level in = level.get();
        return in == null ? -64 : in.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
    }

    @Override
    public String rotate(String block, int quarterTurns) {
        Rotation turn = switch (Math.floorMod(quarterTurns, 4)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        return BlockStateParser.serialize(parse(block).rotate(turn));
    }

    public static final Queue<Change> SERVER_CHANGES = new ConcurrentLinkedQueue<>();
    public static final Queue<Change> CLIENT_CHANGES = new ConcurrentLinkedQueue<>();

    @Override
    public void drainChanges(Consumer<Change> out) {
        Queue<Change> queue = writable ? SERVER_CHANGES : CLIENT_CHANGES;
        for (Change change; (change = queue.poll()) != null; ) out.accept(change);
    }

    public static void onBlockChanged(Level in, BlockPos pos, BlockState state) {
        Queue<Change> queue = in.isClientSide() ? CLIENT_CHANGES : SERVER_CHANGES;
        if (queue.size() > 100_000) queue.poll();
        queue.add(new Change(pos.getX(), pos.getY(), pos.getZ(), BlockStateParser.serialize(state)));
    }

    private static BlockState parse(String block) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, block, false).blockState();
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException("'" + block + "' is not a block: " + e.getMessage());
        }
    }

    private static Vec3 vec(Vector3 v) {
        return new Vec3(v.x(), v.y(), v.z());
    }
}
