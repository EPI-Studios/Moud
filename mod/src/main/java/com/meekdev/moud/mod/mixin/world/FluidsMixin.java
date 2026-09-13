package com.meekdev.moud.mod.mixin.world;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FlowingFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlowingFluid.class)
abstract class FluidsMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(ServerLevel level, BlockPos pos, BlockState state, FluidState fluid, CallbackInfo ci) {
        if (!MoudMod.features().isOn(Feature.FLUIDS)) ci.cancel();
    }
}
