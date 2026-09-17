package com.meekdev.moud.mod.mixin.player;

import com.meekdev.moud.mod.server.tool.ServerTools;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
abstract class DropMixin {

    @Inject(method = "drop(Z)V", at = @At("HEAD"), cancellable = true)
    private void moud$dropTool(boolean all, CallbackInfo ci) {
        if (ServerTools.drop((ServerPlayer) (Object) this)) ci.cancel();
    }
}
