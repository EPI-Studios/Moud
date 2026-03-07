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
    }

    public void onRuntimeState(RuntimeState state) {
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

    public boolean applyCameraOverride(Camera camera) {
        if (!active) {
            return false;
        }
        RuntimeState st = lastServerState;
        if (st == null || !st.useSceneCamera()) {
            return false;
        }

        if (!(camera instanceof CameraAccessor accessor)) {
            return false;
        }

        float yaw = normalizeYawDeg(st.sceneCamYawDeg());
        float pitch = clampPitchDeg(st.sceneCamPitchDeg(), -89.0f, 89.0f);

        accessor.moud$setThirdPerson(false);
        accessor.moud$setCameraPosition(st.sceneCamX(), st.sceneCamY(), st.sceneCamZ());
        accessor.moud$setRotation(yaw, pitch);

        float rollDeg = st.sceneCamRollDeg();
        if (Float.isFinite(rollDeg) && Math.abs(rollDeg) > 1e-4f) {
            Quaternionf base = accessor.moud$getRotation();
            if (base != null) {
                Quaternionf original = new Quaternionf(base);
                Vector3f forward = new Vector3f(0.0f, 0.0f, 1.0f).rotate(original);
                Quaternionf qRoll = new Quaternionf().fromAxisAngleRad(forward, (float) Math.toRadians(rollDeg));
                base.set(qRoll).mul(original);
            }
        }
        return true;
    }

    private static float normalizeYawDeg(float yawDeg) {
        if (!Float.isFinite(yawDeg)) {
            return 0.0f;
        }
        float y = yawDeg % 360.0f;
        if (y < -180.0f) {
            y += 360.0f;
        } else if (y > 180.0f) {
            y -= 360.0f;
        }
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
