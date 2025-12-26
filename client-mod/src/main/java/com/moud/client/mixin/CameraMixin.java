package com.moud.client.mixin;

import com.moud.client.MoudClientMod;
import com.moud.client.api.service.ClientAPIService;
import com.moud.client.api.service.CameraService;
import com.moud.client.camera.VanillaTeleportSmoothing;
import com.moud.client.editor.camera.EditorCameraController;
import com.moud.client.mixin.accessor.CameraAccessor;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPos(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Shadow private boolean ready;
    @Shadow private Entity focusedEntity;

    @Unique
    private Vec3d moud$vanillaPreUpdatePos;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void moud_applyCameraOverrides(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        moud$vanillaPreUpdatePos = null;
        if (this.ready) {
            this.focusedEntity = focusedEntity;
        }

        EditorCameraController controller = EditorCameraController.getInstance();
        controller.updateRenderState();
        if (controller.isActive() && controller.applyToCamera((Camera) (Object) this)) {
            ci.cancel();
            return;
        }

        if (!MoudClientMod.isCustomCameraActive()) {
            moud$vanillaPreUpdatePos = ((Camera) (Object) this).getPos();
            return;
        }

        if (ClientAPIService.INSTANCE != null && ClientAPIService.INSTANCE.camera != null) {
            CameraService cameraService = ClientAPIService.INSTANCE.camera;

            cameraService.updateCamera(tickDelta);

            Vector3d pos = cameraService.getPosition();
            if (pos != null) {
                //MoudClientMod.getLogger().info("Custom camera applying pos={}, yaw={}, pitch={}", pos, cameraService.getYaw(), cameraService.getPitch());
                this.setPos(pos.x, pos.y, pos.z);
            }

            Float yaw = cameraService.getYaw();
            Float pitch = cameraService.getPitch();
            if (yaw != null && pitch != null) {
                this.setRotation(yaw, pitch);
            }

            ((CameraAccessor) (Object) this).moud$setThirdPerson(true);
        }

        ci.cancel();
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void moud$applyVanillaTeleportSmoothing(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        Vec3d preUpdatePos = moud$vanillaPreUpdatePos;
        moud$vanillaPreUpdatePos = null;

        if (ClientAPIService.INSTANCE != null && ClientAPIService.INSTANCE.camera != null) {
            var cameraService = ClientAPIService.INSTANCE.camera;
            if (cameraService.isScriptableCameraActive()) {
                cameraService.updateScriptableCamera(tickDelta);

                Camera cam = (Camera) (Object) this;
                Vec3d currentPos = cam.getPos();

                Vector3d posOffset = cameraService.getScriptablePositionOffset();
                double rollOffset = cameraService.getScriptableRollOffset();

                if (posOffset != null && (Math.abs(posOffset.x) > 0.01 || Math.abs(posOffset.y) > 0.01 || Math.abs(posOffset.z) > 0.01)) {
                }

                if (posOffset != null && currentPos != null) {
                    double yawRad = Math.toRadians(cam.getYaw());
                    double pitchRad = Math.toRadians(cam.getPitch());

                    double cosYaw = Math.cos(yawRad);
                    double sinYaw = Math.sin(yawRad);
                    double cosPitch = Math.cos(pitchRad);
                    double sinPitch = Math.sin(pitchRad);

                    double fwdX = -sinYaw * cosPitch;
                    double fwdY = -sinPitch;
                    double fwdZ = cosYaw * cosPitch;

                    double rightX = cosYaw;
                    double rightY = 0;
                    double rightZ = sinYaw;

                    double upX = rightY * fwdZ - rightZ * fwdY;
                    double upY = rightZ * fwdX - rightX * fwdZ;
                    double upZ = rightX * fwdY - rightY * fwdX;

                    double worldOffsetX = posOffset.x * rightX + posOffset.y * upX + posOffset.z * fwdX;
                    double worldOffsetY = posOffset.x * rightY + posOffset.y * upY + posOffset.z * fwdY;
                    double worldOffsetZ = posOffset.x * rightZ + posOffset.y * upZ + posOffset.z * fwdZ;

                    this.setPos(
                        currentPos.x + worldOffsetX,
                        currentPos.y + worldOffsetY,
                        currentPos.z + worldOffsetZ
                    );
                }

                float baseYaw = cam.getYaw();
                float basePitch = cam.getPitch();

                if (cameraService.isLookAtEnabled()) {
                    double[] lookAtRot = cameraService.computeLookAtRotation();
                    if (lookAtRot != null) {
                        double strength = cameraService.getLookAtStrength();
                        double smoothTime = cameraService.getLookAtSmoothTime();

                        float targetYaw = (float) lookAtRot[0];
                        float targetPitch = (float) lookAtRot[1];

                        double t = 1.0 - Math.pow(0.01, tickDelta * 0.05 / smoothTime);
                        baseYaw = (float) net.minecraft.util.math.MathHelper.lerpAngleDegrees(
                            (float) (t * strength), baseYaw, targetYaw);
                        basePitch = (float) net.minecraft.util.math.MathHelper.lerp(
                            t * strength, basePitch, targetPitch);
                    }
                }

                if (cameraService.isYawLocked()) {
                    baseYaw = (float) cameraService.getLockedYaw();
                }
                if (cameraService.isPitchLocked()) {
                    basePitch = (float) cameraService.getLockedPitch();
                }

                if (cameraService.isYawLimitsEnabled()) {
                    double[] yawLimits = cameraService.getYawLimits();
                    double center = yawLimits[2];
                    double minYaw = center + yawLimits[0];
                    double maxYaw = center + yawLimits[1];

                    double relativeYaw = baseYaw - center;
                    while (relativeYaw > 180) relativeYaw -= 360;
                    while (relativeYaw < -180) relativeYaw += 360;

                    relativeYaw = Math.max(yawLimits[0], Math.min(yawLimits[1], relativeYaw));
                    baseYaw = (float) (center + relativeYaw);
                }

                if (cameraService.isPitchLimitsEnabled()) {
                    double[] pitchLimits = cameraService.getPitchLimits();
                    basePitch = (float) Math.max(pitchLimits[0], Math.min(pitchLimits[1], basePitch));
                }

                double yawOffset = cameraService.getScriptableYawOffset();
                double pitchOffset = cameraService.getScriptablePitchOffset();

                float newYaw = baseYaw + (float) yawOffset;
                float newPitch = (float) Math.max(-90, Math.min(90, basePitch + pitchOffset));
                this.setRotation(newYaw, newPitch);
            }
        }

        if (!VanillaTeleportSmoothing.isEnabled()) {
            return;
        }

        Vec3d targetPos = ((Camera) (Object) this).getPos();
        if (targetPos == null) {
            return;
        }

        if (preUpdatePos != null && !VanillaTeleportSmoothing.isInProgress()) {
            VanillaTeleportSmoothing.onCameraDelta(preUpdatePos.subtract(targetPos));
        }

        Vec3d offset = VanillaTeleportSmoothing.currentOffset();
        if (offset == null || offset.lengthSquared() < 1.0e-10) {
            return;
        }

        this.setPos(targetPos.x + offset.x, targetPos.y + offset.y, targetPos.z + offset.z);
    }

    @Shadow public abstract float getYaw();
    @Shadow public abstract float getPitch();
}
