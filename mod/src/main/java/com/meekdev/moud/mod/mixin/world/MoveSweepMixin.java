package com.meekdev.moud.mod.mixin.world;

import com.meekdev.moud.mod.MoudMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class MoveSweepMixin {

    private static final double LIMIT = 512.0;

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void moud$refuseUnsweepable(MoverType type, Vec3 movement, CallbackInfo ci) {
        if (Double.isFinite(movement.x) && Double.isFinite(movement.y) && Double.isFinite(movement.z)
                && movement.lengthSqr() <= LIMIT * LIMIT) {
            return;
        }
        Entity self = (Entity) (Object) this;
        MoudMod.LOG.warn("refused a {} move of {},{},{} for {} standing at {},{},{}",
                type, movement.x, movement.y, movement.z, self.getType().toShortString(),
                self.getX(), self.getY(), self.getZ());
        ci.cancel();
    }
}
