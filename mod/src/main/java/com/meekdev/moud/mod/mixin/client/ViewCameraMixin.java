package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.adapter.render.FirstPersonView;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Camera.class, priority = 1100)
abstract class ViewCameraMixin {

    @Shadow private Vec3 position;
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;
    @Shadow private int matrixPropertiesDirty;
    @Shadow private boolean detached;
    @Shadow @Final private Matrix4f cachedViewRotMatrix;

    @Shadow protected abstract void setPosition(Vec3 position);

    @Shadow private void prepareCullFrustum(Matrix4fc viewRotation, Matrix4f projection, Vec3 at) { throw new AssertionError(); }

    @Shadow private Matrix4f createProjectionMatrixForCulling() { throw new AssertionError(); }

    @Shadow public abstract Matrix4f getViewRotationMatrix(Matrix4f dest);

    @Inject(method = "update", at = @At("TAIL"))
    private void moud$viewModelCamera(DeltaTracker tracker, CallbackInfo ci) {
        if (detached || (Object) this != Minecraft.getInstance().gameRenderer.getMainCamera()) return;
        CFrame offset = FirstPersonView.cameraOffset();
        if (offset.equals(CFrame.IDENTITY)) return;
        Vector3 at = offset.position();
        Quat turn = offset.rotation();
        Vector3f moved = rotation.transform(new Vector3f((float) at.x(), (float) at.y(), (float) at.z()));
        setPosition(position.add(moved.x, moved.y, moved.z));
        rotation.mul(new Quaternionf((float) turn.x(), (float) turn.y(), (float) turn.z(), (float) turn.w())).normalize();
        forwards.set(0, 0, -1).rotate(rotation);
        up.set(0, 1, 0).rotate(rotation);
        left.set(-1, 0, 0).rotate(rotation);
        matrixPropertiesDirty = -1;
        prepareCullFrustum(getViewRotationMatrix(cachedViewRotMatrix), createProjectionMatrixForCulling(), position);
    }
}
