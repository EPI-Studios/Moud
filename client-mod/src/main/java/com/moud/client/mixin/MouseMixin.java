package com.moud.client.mixin;

import com.moud.client.api.service.ClientAPIService;
import com.moud.client.network.MoudPackets;
import com.moud.client.player.ClientPlayerModelManager;
import com.moud.client.ui.UIOverlayManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Optional;

@Mixin(Mouse.class)
public abstract class MouseMixin {

    @Shadow @Final private MinecraftClient client;
    @Shadow private double x;
    @Shadow private double y;

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void moud_onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (client.currentScreen != null || action != 1) {
            return;
        }


        ClientPlayerModelManager modelManager = ClientPlayerModelManager.getInstance();
        if (!modelManager.getAllModels().isEmpty() && client.player != null) {
            Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
            Vec3d cameraDir = Vec3d.fromPolar(client.player.getPitch(), client.player.getYaw());

            for (ClientPlayerModelManager.ManagedPlayerModel model : modelManager.getAllModels().values()) {
                Vec3d modelVecPos = new Vec3d(model.getPosition().x, model.getPosition().y, model.getPosition().z);
                Box modelBox = new Box(modelVecPos.x - 0.5, modelVecPos.y, modelVecPos.z - 0.5,
                        modelVecPos.x + 0.5, modelVecPos.y + 2.0, modelVecPos.z + 0.5);

                Optional<Vec3d> intersection = modelBox.raycast(cameraPos, cameraPos.add(cameraDir.multiply(100.0)));

                if (intersection.isPresent()) {
                    ClientPlayNetworking.send(new MoudPackets.PlayerModelClickPacket(model.getModelId(), this.x, this.y, button));
                    ci.cancel();
                    return;
                }
            }
        }


        if (ClientAPIService.INSTANCE != null && ClientAPIService.INSTANCE.cursor.isVisible()) {
            if (UIOverlayManager.getInstance().handleOverlayClick(this.x, this.y, button)) {
                ci.cancel();
                return;
            }
        }

        if(ClientPlayNetworking.canSend(MoudPackets.C2S_PlayerClickPacket.ID)) {
            ClientPlayNetworking.send(new MoudPackets.C2S_PlayerClickPacket(button));
        }
    }

    @Inject(
            method = "onCursorPos(JDD)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void moud_onMouseMove(long window, double x, double y, CallbackInfo ci) {
        if (ClientPlayNetworking.canSend(MoudPackets.C2S_MouseMovementPacket.ID)) {
            double dx = x - this.x;
            double dy = y - this.y;
            ClientPlayNetworking.send(new MoudPackets.C2S_MouseMovementPacket((float) dx, (float) dy));
        }

        if (ClientAPIService.INSTANCE != null && ClientAPIService.INSTANCE.camera.isCustomCameraActive()) {
            ci.cancel();
        }
    }
}