package com.moud.client.fabric.mixin;

import com.moud.client.fabric.MoudServerDetector;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerInfo.class)
public abstract class ServerInfoMixin {
    @Inject(method = "setStatus", at = @At("TAIL"))
    private void moud$detectMoudFromStatus(ServerInfo.Status status, CallbackInfo ci) {
        MoudServerDetector.scanAndMark((ServerInfo) (Object) this);
    }

    @Inject(method = "copyFrom", at = @At("TAIL"))
    private void moud$detectMoudFromCopy(ServerInfo source, CallbackInfo ci) {
        MoudServerDetector.scanAndMark((ServerInfo) (Object) this);
    }
}
