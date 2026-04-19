package com.moud.client.fabric.mixin;

import com.moud.client.fabric.physics.ClientPhysicsWorld;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z", at = @At("RETURN"))
    private void moud$onBlockChanged(BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            ClientPhysicsWorld.get().onBlockChanged(pos.getX(), pos.getY(), pos.getZ());
        }
    }
}
