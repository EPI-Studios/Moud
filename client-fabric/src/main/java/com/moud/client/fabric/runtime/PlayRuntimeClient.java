package com.moud.client.fabric.runtime;

import com.miry.ui.util.MathUtils;
import com.moud.client.fabric.mixin.accessor.CameraAccessor;
import com.moud.net.protocol.PlayerInput;
import com.moud.net.protocol.RuntimeState;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;
import com.moud.net.transport.Lane;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PlayRuntimeClient {
    private boolean active;
    private volatile RuntimeState lastServerState;

    // previous and current camera poses for per-frame interpolation
    private float prevX, prevY, prevZ, prevYaw, prevPitch, prevRoll;
    private float currX, currY, currZ, currYaw, currPitch, currRoll;
    private boolean hasPrev;

    public RuntimeState lastServerState() {
        return lastServerState;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void onDisconnect() {
        active = false;
        lastServerState = null;
        hasPrev = false;
    }

    public void onRuntimeState(RuntimeState state) {
        if (state != null && state.useSceneCamera()) {
            prevX = currX; prevY = currY; prevZ = currZ;
            prevYaw = currYaw; prevPitch = currPitch; prevRoll = currRoll;
            currX = state.sceneCamX(); currY = state.sceneCamY(); currZ = state.sceneCamZ();
            currYaw = normalizeYawDeg(state.sceneCamYawDeg());
            currPitch = clampPitchDeg(state.sceneCamPitchDeg(), -89.0f, 89.0f);
            currRoll = Float.isFinite(state.sceneCamRollDeg()) ? state.sceneCamRollDeg() : 0.0f;
            if (!hasPrev) {
                prevX = currX; prevY = currY; prevZ = currZ;
                prevYaw = currYaw; prevPitch = currPitch; prevRoll = currRoll;
                hasPrev = true;
            }
        } else {
            hasPrev = false;
        }
        lastServerState = state;
    }

    public void onPauseMenuOpened() {
    }

    public void tick(Session session) {
        if (!active || session == null || session.state() != SessionState.CONNECTED) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.currentScreen != null) {
            return;
        }
        float yaw = client.player.getYaw();
        float pitch = client.player.getPitch();
        session.send(Lane.INPUT,
                new PlayerInput(0L, 0.0f, 0.0f, yaw, pitch, false, false));
    }

    public boolean applyCameraOverride(Camera camera, float partialTick) {
        if (!active) {
            return false;
        }
        RuntimeState st = lastServerState;
        if (st == null) {
            return false;
        }

        if (!(camera instanceof CameraAccessor accessor)) {
            return false;
        }

        if (st.useFollowCamera()) {
            return applyFollowCamera(accessor, st, partialTick);
        }
        if (st.useSceneCamera()) {
            return applySceneCamera(accessor, partialTick);
        }
        return false;
    }

    private boolean applyFollowCamera(CameraAccessor accessor, RuntimeState st, float partialTick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return false;

        float t = MathUtils.clamp(partialTick, 0.0f, 1.0f);
        // interpolated player position using client-side prediction — zero lag
        double px = lerp(mc.player.prevX, mc.player.getX(), t);
        double py = lerp(mc.player.prevY, mc.player.getY(), t);
        double pz = lerp(mc.player.prevZ, mc.player.getZ(), t);
        float yawRad = (float) Math.toRadians(mc.player.getYaw(t));

        // forward = (sin(yaw), 0, cos(yaw)), right = (cos(yaw), 0, -sin(yaw))
        float fwdX = (float) Math.sin(yawRad);
        float fwdZ = (float) Math.cos(yawRad);
        float rightX = (float) Math.cos(yawRad);
        float rightZ = -(float) Math.sin(yawRad);

        float lx = st.followCamLocalX();
        float ly = st.followCamLocalY();
        float lz = st.followCamLocalZ();
        double camX = px + fwdX * lz + rightX * lx;
        double camY = py + ly;
        double camZ = pz + fwdZ * lz + rightZ * lx;

        float yawDeg = normalizeYawDeg(mc.player.getYaw(t));
        float pitch = clampPitchDeg(st.followCamPitchDeg(), -89.0f, 89.0f);
        float roll = st.followCamRollDeg();

        accessor.moud$setThirdPerson(true);
        accessor.moud$setCameraPosition(camX, camY, camZ);
        accessor.moud$setRotation(yawDeg, pitch);
        applyRoll(accessor, roll);
        return true;
    }

    private boolean applySceneCamera(CameraAccessor accessor, float partialTick) {
        float t = MathUtils.clamp(partialTick, 0.0f, 1.0f);
        float x = hasPrev ? lerp(prevX, currX, t) : currX;
        float y = hasPrev ? lerp(prevY, currY, t) : currY;
        float z = hasPrev ? lerp(prevZ, currZ, t) : currZ;
        float yaw = hasPrev ? lerpYaw(prevYaw, currYaw, t) : currYaw;
        float pitch = hasPrev ? lerp(prevPitch, currPitch, t) : currPitch;
        float roll = hasPrev ? lerp(prevRoll, currRoll, t) : currRoll;

        accessor.moud$setThirdPerson(true);
        accessor.moud$setCameraPosition(x, y, z);
        accessor.moud$setRotation(yaw, pitch);
        applyRoll(accessor, roll);
        return true;
    }

    private static void applyRoll(CameraAccessor accessor, float roll) {
        if (!Float.isFinite(roll) || Math.abs(roll) <= 1e-4f) return;
        Quaternionf base = accessor.moud$getRotation();
        if (base == null) return;
        Quaternionf original = new Quaternionf(base);
        Vector3f forward = new Vector3f(0.0f, 0.0f, 1.0f).rotate(original);
        Quaternionf qRoll = new Quaternionf().fromAxisAngleRad(forward, (float) Math.toRadians(roll));
        base.set(qRoll).mul(original);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }

    private static float lerpYaw(float from, float to, float t) {
        float diff = to - from;
        while (diff > 180f) diff -= 360f;
        while (diff < -180f) diff += 360f;
        return from + diff * t;
    }

    private static float normalizeYawDeg(float yawDeg) {
        if (!Float.isFinite(yawDeg)) {
            return 0.0f;
        }
        float y = yawDeg % 360.0f;
        if (y < -180.0f) y += 360.0f;
        else if (y > 180.0f) y -= 360.0f;
        return y;
    }

    private static float clampPitchDeg(float pitchDeg, float min, float max) {
        if (!Float.isFinite(pitchDeg)) {
            return 0.0f;
        }
        return MathUtils.clamp(pitchDeg, min, max);
    }

    public boolean shouldBlockVanillaInput(MinecraftClient client) {
        return false;
    }
}
