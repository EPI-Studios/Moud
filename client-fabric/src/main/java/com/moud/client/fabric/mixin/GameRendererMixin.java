package com.moud.client.fabric.mixin;

import com.moud.client.fabric.runtime.ClientCameraState;
import com.moud.client.fabric.runtime.ClientCameraStateBus;
import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import com.moud.net.protocol.RuntimeState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public final class GameRendererMixin {
    @Inject(method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"))
    private void moud$travelFrame(RenderTickCounter counter, CallbackInfo ci) {
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime != null) {
            runtime.travelFrame(MinecraftClient.getInstance());
        }
    }

    @ModifyArgs(
            method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/Camera;update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V"
            )
    )
    private void moud$forceNoInverseCameraWhenOverriding(Args args) {
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime == null || !runtime.isActive()) {
            return;
        }
        ClientCameraState camState = ClientCameraStateBus.get();
        if (camState != null && camState.hasOverride) {
            args.set(3, false);
            return;
        }
        RuntimeState st = runtime.lastServerState();
        if (st != null && (st.useFollowCamera() || st.useSceneCamera() || st.useScriptCamera())) {
            args.set(3, false);
        }
    }
    @Inject(
            method = "getFov(Lnet/minecraft/client/render/Camera;FZ)D",
            at = @At("RETURN"),
            cancellable = true
    )
    private void moud$getFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        ClientCameraState camState = ClientCameraStateBus.get();
        if (camState != null && camState.fov > 0.0) {
            cir.setReturnValue((double) camState.fov);
        }
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void moud$bobView(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime != null && runtime.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void moud$tiltViewWhenHurt(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime != null && runtime.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void moud$renderHand(Camera camera, float tickDelta, Matrix4f matrix, CallbackInfo ci) {
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime != null && runtime.shouldHideVanillaHand()) {
            ci.cancel();
        }
    }
}
