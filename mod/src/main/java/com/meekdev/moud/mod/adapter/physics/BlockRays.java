package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.script.api.BlockRef;
import java.util.function.Supplier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

// the blocks of whichever level this side is in: rays against them, and reading and writing them
public final class BlockRays implements BlockRef {

    private final Supplier<@Nullable Level> level;
    private final boolean writable;

    public BlockRays(Supplier<@Nullable Level> level, boolean writable) {
        this.level = level;
        this.writable = writable;
    }

    @Override
    public @Nullable Hit raycast(Vec3 from, Vec3 direction, double range) {
        Level in = level.get();
        if (in == null || range <= 0 || direction.lengthSq() < 1e-24) return null;
        Vec3 way = direction.normalize();
        Vec3 to = from.add(way.mul(range));
        BlockHitResult hit = in.clip(new ClipContext(vec(from), vec(to), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS || hit.isInside()) return null;
        net.minecraft.world.phys.Vec3 at = hit.getLocation();
        Direction face = hit.getDirection();
        BlockPos pos = hit.getBlockPos();
        String block = BuiltInRegistries.BLOCK.getKey(in.getBlockState(pos).getBlock()).toString();
        Vec3 point = new Vec3(at.x, at.y, at.z);
        return new Hit(point, new Vec3(face.getStepX(), face.getStepY(), face.getStepZ()), point.distance(from), block);
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

    private static BlockState parse(String block) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, block, false).blockState();
        } catch (CommandSyntaxException wrong) {
            throw new IllegalArgumentException("'" + block + "' is not a block: " + wrong.getMessage());
        }
    }

    private static net.minecraft.world.phys.Vec3 vec(Vec3 v) {
        return new net.minecraft.world.phys.Vec3(v.x(), v.y(), v.z());
    }
}
