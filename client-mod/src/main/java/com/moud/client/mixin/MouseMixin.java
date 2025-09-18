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

import java.util.Optional;

@Mixin(Mouse.class)
public abstract class MouseMixin {

    @Shadow @Final private MinecraftClient client;
    @Shadow private double x;
    @Shadow private double y;

    private boolean firstMouseMove = true;
    private double lastX = 0.0;
    private double lastY = 0.0;

    @Inject(method = "lockCursor", at = @At("HEAD"))
    private void onCursorLock(CallbackInfo ci) {
        this.firstMouseMove = true;
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void moud_onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (client.currentScreen != null || action != 1) {
            return;
        }

        ClientPlayerModelManager modelManager = ClientPlayerModelManager.getInstance();
//        if (!modelManager.getAllModels().isEmpty() && client.player != null) {
//            Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
//            Vec3d cameraDir = Vec3d.fromPolar(client.player.getPitch(), client.player.getYaw());
//
//            for (ClientPlayerModelManager.ManagedPlayerModel model : modelManager.getAllModels().values()) {
//                Vec3d modelVecPos = new Vec3d(model.getPosition().x, model.getPosition().y, model.getPosition().z);
//                Box modelBox = new Box(modelVecPos.x - 0.5, modelVecPos.y, modelVecPos.z - 0.5,
//                        modelVecPos.x + 0.5, modelVecPos.y + 2.0, modelVecPos.z + 0.5);
//
//                Optional<Vec3d> intersection = modelBox.raycast(cameraPos, cameraPos.add(cameraDir.multiply(100.0)));
//
//                if (intersection.isPresent()) {
//                    ClientPlayNetworking.send(new MoudPackets.PlayerModelClickPacket(model.getModelId(), this.x, this.y, button));
//                    ci.cancel();
//                    return;
//                }
//            }
//        }

        if (ClientAPIService.INSTANCE != null && ClientAPIService.INSTANCE.cursor.isVisible()) {
            if (UIOverlayManager.getInstance().handleOverlayClick(this.x, this.y, button)) {
                ci.cancel();
                return;
            }
        }

        ClientPlayNetworking.send(new MoudPackets.C2S_PlayerClickPacket(button));
    }

    @Inject(method = "updateMouse", at = @At("HEAD"))
    private void moud_updateMouse(CallbackInfo ci) {
        double currentX = client.mouse.getX();
        double currentY = client.mouse.getY();

        if (firstMouseMove) {
            lastX = currentX;
            lastY = currentY;
            firstMouseMove = false;
            return;
        }

        double dx = currentX - lastX;
        double dy = currentY - lastY;

        if (Math.abs(dx) > 0.01 || Math.abs(dy) > 0.01) {
            ClientPlayNetworking.send(new MoudPackets.C2S_MouseMovementPacket((float) dx, (float) dy));

            if (ClientAPIService.INSTANCE != null && ClientAPIService.INSTANCE.input != null) {
                ClientAPIService.INSTANCE.input.triggerMouseMoveEvent(dx, dy);
            }
        }

        lastX = currentX;
        lastY = currentY;
    }
}