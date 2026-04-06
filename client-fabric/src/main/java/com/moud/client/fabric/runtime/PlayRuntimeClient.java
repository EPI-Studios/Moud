package com.moud.client.fabric.runtime;

import com.miry.ui.util.MathUtils;
import com.moud.client.fabric.mixin.accessor.CameraAccessor;
import com.moud.net.protocol.PlayerInput;
import com.moud.net.protocol.RuntimeState;
import com.moud.net.protocol.CursorState;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;
import com.moud.net.transport.Lane;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PlayRuntimeClient {
    private boolean active;
    private volatile RuntimeState lastServerState;
    private boolean cursorModeEnabled;
    private boolean osCursorVisible = true;
    private float cursorX;
    private float cursorY;

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
        cursorModeEnabled = false;
        osCursorVisible = true;
        cursorX = 0.0f;
        cursorY = 0.0f;
    }

    public void onCursorState(CursorState state) {
        if (state == null) {
            return;
        }
        cursorModeEnabled = state.cursorModeEnabled();
        osCursorVisible = state.osCursorVisible();
    }

    public void onRuntimeState(RuntimeState state) {
        boolean useExternal = state != null && (state.useSceneCamera() || state.useScriptCamera());
        if (useExternal) {
            float nextX, nextY, nextZ, nextYaw, nextPitch, nextRoll;
            if (state.useScriptCamera()) {
                nextX = state.scriptCamX();
                nextY = state.scriptCamY();
                nextZ = state.scriptCamZ();
                nextYaw = state.scriptCamYawDeg();
                nextPitch = state.scriptCamPitchDeg();
                nextRoll = state.scriptCamRollDeg();
            } else {
                nextX = state.sceneCamX();
                nextY = state.sceneCamY();
                nextZ = state.sceneCamZ();
                nextYaw = state.sceneCamYawDeg();
                nextPitch = state.sceneCamPitchDeg();
                nextRoll = state.sceneCamRollDeg();
            }

            prevX = currX; prevY = currY; prevZ = currZ;
            prevYaw = currYaw; prevPitch = currPitch; prevRoll = currRoll;
            currX = nextX; currY = nextY; currZ = nextZ;
            currYaw = normalizeYawDeg(-nextYaw);
            currPitch = clampPitchDeg(nextPitch, -89.0f, 89.0f);
            currRoll = Float.isFinite(nextRoll) ? nextRoll : 0.0f;
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
        updateCursorPosition(client);
        float yaw = client.player.getYaw();
        float pitch = client.player.getPitch();
        session.send(Lane.INPUT,
                new PlayerInput(0L, 0.0f, 0.0f, yaw, pitch, cursorX, cursorY, false, false));
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
        if (st.useScriptCamera() || st.useSceneCamera()) {
            return applySceneCamera(accessor, partialTick);
        }
        return false;
    }

    public boolean shouldHideVanillaHand() {
        if (!active) return false;
        RuntimeState st = lastServerState;
        return st != null && (st.useFollowCamera() || st.useSceneCamera() || st.useScriptCamera());
    }

    private boolean applyFollowCamera(CameraAccessor accessor, RuntimeState st, float partialTick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return false;

        float t = MathUtils.clamp(partialTick, 0.0f, 1.0f);
        double px = lerp(mc.player.prevX, mc.player.getX(), t);
        double py = lerp(mc.player.prevY, mc.player.getY(), t);
        double pz = lerp(mc.player.prevZ, mc.player.getZ(), t);
        Vec3d fwd = mc.player.getRotationVec(t);
        double fwdX = fwd.x;
        double fwdZ = fwd.z;
        double lenSq = fwdX * fwdX + fwdZ * fwdZ;
        if (lenSq < 1e-8) {
            fwdX = 0.0;
            fwdZ = 1.0;
            lenSq = 1.0;
        }
        double invLen = 1.0 / Math.sqrt(lenSq);
        fwdX *= invLen;
        fwdZ *= invLen;
        double rightX = -fwdZ;
        double rightZ = fwdX;

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
        return cursorModeEnabled;
    }

    public boolean isCursorModeEnabled() {
        return cursorModeEnabled;
    }

    public boolean isOsCursorVisible() {
        return osCursorVisible;
    }

    public void applyCursorMode(MinecraftClient client) {
        if (client == null || client.mouse == null || client.currentScreen != null) {
            return;
        }
        long windowHandle = client.getWindow().getHandle();
        if (cursorModeEnabled) {
            if (client.mouse.isCursorLocked()) {
                client.mouse.unlockCursor();
            }
            GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR,
                    osCursorVisible ? GLFW.GLFW_CURSOR_NORMAL : GLFW.GLFW_CURSOR_HIDDEN);
        } else if (!client.mouse.isCursorLocked()) {
            client.mouse.lockCursor();
        }
    }

    private void updateCursorPosition(MinecraftClient client) {
        if (client == null || client.getWindow() == null) {
            cursorX = 0.0f;
            cursorY = 0.0f;
            return;
        }
        long handle = client.getWindow().getHandle();
        double[] mx = new double[1];
        double[] my = new double[1];
        GLFW.glfwGetCursorPos(handle, mx, my);
        cursorX = (float) mx[0];
        cursorY = (float) my[0];
    }
}
