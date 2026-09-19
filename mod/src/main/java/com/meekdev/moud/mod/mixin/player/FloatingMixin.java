package com.meekdev.moud.mod.mixin.player;

import com.meekdev.moud.mod.server.MoudServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class FloatingMixin {

    @Inject(method = "getMaximumFlyingTicks", at = @At("HEAD"), cancellable = true)
    private void moud$standOnParts(Entity entity, CallbackInfoReturnable<Integer> cir) {
        if (MoudServer.place() != null) cir.setReturnValue(Integer.MAX_VALUE);
    }
}
