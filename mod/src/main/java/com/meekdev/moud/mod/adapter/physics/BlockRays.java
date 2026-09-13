package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.script.api.BlockRef;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

// a ray against the solid blocks of whichever level this side is in
public final class BlockRays implements BlockRef {

    private final Supplier<@Nullable Level> level;

    public BlockRays(Supplier<@Nullable Level> level) {
        this.level = level;
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

    private static net.minecraft.world.phys.Vec3 vec(Vec3 v) {
        return new net.minecraft.world.phys.Vec3(v.x(), v.y(), v.z());
    }
}
