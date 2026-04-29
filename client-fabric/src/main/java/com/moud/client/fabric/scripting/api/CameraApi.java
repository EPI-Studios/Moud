package com.moud.client.fabric.scripting.api;

import com.moud.client.fabric.runtime.CameraLookTarget;
import com.moud.client.fabric.runtime.ClientCameraState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public final class CameraApi {

    private final ClientCameraState state;

    public CameraApi(ClientCameraState state) {
        this.state = state;
    }

    public void setPos(double x, double y, double z) {
        state.posX = (float) x;
        state.posY = (float) y;
        state.posZ = (float) z;
        state.hasOverride = true;
    }

    public void setYaw(double deg) {
        state.yaw = (float) deg;
        state.hasOverride = true;
    }

    public void setPitch(double deg) {
        float p = (float) deg;
        if (p > 89f)  p = 89f;
        if (p < -89f) p = -89f;
        state.pitch = p;
        state.hasOverride = true;
    }

    public void setRoll(double deg) {
        state.roll = (float) deg;
    }

    public void setFov(double deg) {
        state.fov = (float) deg;
    }

    public float getYaw() {
        return state.getYawOrPrev();
    }

    public float getPitch() {
        return state.getPitchOrPrev();
    }

    public float getPlayerYaw() {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        return p == null ? getYaw() : p.getYaw();
    }

    public float getPlayerPitch() {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        return p == null ? getPitch() : p.getPitch();
    }

    public float getRoll() {
        return state.getRollOrPrev();
    }

    public float getFov() {
        return state.getFovOrPrev();
    }
    public void captureMouse(boolean enabled) {
        state.captureMouseEnabled = enabled;
    }

    public float[] getMouseDelta() {
        if (!state.captureMouseEnabled) {
            return new float[]{0f, 0f};
        }
        return new float[]{state.mouseDx, state.mouseDy};
    }

    public void setPlayerYaw(double deg) {
        state.playerYaw = (float) deg;
    }

    public void setLookTarget(double x, double y, double z, double strength, double maxAngleDeg) {
        CameraLookTarget.setSoft(x, y, z, (float) strength, (float) maxAngleDeg);
    }

    public void setLookTargetHard(double x, double y, double z) {
        CameraLookTarget.setHard(x, y, z);
    }

    public void clearLookTarget() {
        CameraLookTarget.clear();
    }
}
