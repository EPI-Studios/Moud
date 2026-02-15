package com.moud.client.fabric.runtime;

import com.moud.client.fabric.mixin.accessor.CameraAccessor;
import com.moud.core.physics.CharacterPhysics;
import com.moud.net.protocol.PlayerInput;
import com.moud.net.protocol.RuntimeState;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;
import com.moud.net.transport.Lane;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

public final class PlayRuntimeClient {
    private static final float LOOK_SENS_DEG_PER_PIXEL = 0.15f;
    private static final float MIN_PITCH = -89.0f;
    private static final float MAX_PITCH = 89.0f;
    private static final float EYE_HEIGHT = 1.6f;
    private static final float CORRECTION_DECAY = 15.0f;
    private static final float HARD_SNAP_DIST = 2.0f;
    private static final float DEFAULT_SPEED = 6.0f;

    private boolean active;
    private boolean forward;
    private boolean back;
    private boolean left;
    private boolean right;
    private boolean jump;
    private boolean sprint;

    private boolean mouseInit;
    private double lastMouseX;
    private double lastMouseY;
    private float yawDeg;
    private float pitchDeg;
    private long clientTick;
    private long lastSendNs;

    private float predX;
    private float predY;
    private float predZ;
    private float predVelX;
    private float predVelY;
    private float predVelZ;
    private boolean predOnFloor;
    private long lastFrameNs;

    private float corrX;
    private float corrY;
    private float corrZ;

    private volatile RuntimeState lastServerState;

    public RuntimeState lastServerState() {
        return lastServerState;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        if (this.active == active) {
            return;
        }
        this.active = active;
        clearInput();
        mouseInit = false;
        if (active) {
            RuntimeState state = lastServerState;
            if (state != null) {
                yawDeg = normalizeYaw(state.camYawDeg());
                pitchDeg = clampPitch(state.camPitchDeg());
                predX = state.charX();
                predY = state.charY();
                predZ = state.charZ();
                predVelX = state.velX();
                predVelY = state.velY();
                predVelZ = state.velZ();
                predOnFloor = state.onFloor();
            }
            corrX = corrY = corrZ = 0.0f;
            lastFrameNs = 0L;
        }
    }

    public void onDisconnect() {
        active = false;
        clearInput();
        mouseInit = false;
        lastServerState = null;
        predX = predY = predZ = 0.0f;
        predVelX = predVelY = predVelZ = 0.0f;
        predOnFloor = true;
        corrX = corrY = corrZ = 0.0f;
        lastFrameNs = 0L;
    }

    public void onRuntimeState(RuntimeState state) {
        lastServerState = state;
        if (!active) {
            return;
        }

        float errX = state.charX() - predX;
        float errY = state.charY() - predY;
        float errZ = state.charZ() - predZ;

        float dist = (float) Math.sqrt(errX * errX + errY * errY + errZ * errZ);
        if (dist > HARD_SNAP_DIST) {
            predX = state.charX();
            predY = state.charY();
            predZ = state.charZ();
            predVelX = state.velX();
            predVelY = state.velY();
            predVelZ = state.velZ();
            predOnFloor = state.onFloor();
            corrX = corrY = corrZ = 0.0f;
        } else {
            predX = state.charX();
            predY = state.charY();
            predZ = state.charZ();
            predVelX = state.velX();
            predVelY = state.velY();
            predVelZ = state.velZ();
            predOnFloor = state.onFloor();
            corrX -= errX;
            corrY -= errY;
            corrZ -= errZ;
        }
    }

    public void onKeyEvent(int key, int action) {
        if (!active) {
            return;
        }
        boolean down = action != GLFW.GLFW_RELEASE;
        switch (key) {
            case GLFW.GLFW_KEY_W -> forward = down;
            case GLFW.GLFW_KEY_S -> back = down;
            case GLFW.GLFW_KEY_A -> left = down;
            case GLFW.GLFW_KEY_D -> right = down;
            case GLFW.GLFW_KEY_SPACE -> jump = down;
            case GLFW.GLFW_KEY_LEFT_SHIFT -> sprint = down;
            default -> {
            }
        }
    }

    public void onMouseMove(double x, double y) {
        if (!active) {
            return;
        }
        if (!mouseInit) {
            mouseInit = true;
            lastMouseX = x;
            lastMouseY = y;
            return;
        }
        double dx = x - lastMouseX;
        double dy = y - lastMouseY;
        lastMouseX = x;
        lastMouseY = y;

        yawDeg = normalizeYaw(yawDeg + (float) (dx * LOOK_SENS_DEG_PER_PIXEL));
        pitchDeg = clampPitch(pitchDeg + (float) (dy * LOOK_SENS_DEG_PER_PIXEL));
    }

    public void tick(Session session) {
        sendInput(session, 60);
    }

    public void sendInput(Session session, int maxHz) {
        if (!active || session == null || session.state() != SessionState.CONNECTED) {
            return;
        }
        long now = System.nanoTime();
        int hz = Math.max(1, Math.min(240, maxHz));
        long interval = 1_000_000_000L / hz;
        if (lastSendNs != 0L && now - lastSendNs < interval) {
            return;
        }
        lastSendNs = now;

        float moveX = (right ? 1.0f : 0.0f) + (left ? -1.0f : 0.0f);
        float moveZ = (forward ? 1.0f : 0.0f) + (back ? -1.0f : 0.0f);
        float len = (float) Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (len > 1e-6f && len > 1.0f) {
            moveX /= len;
            moveZ /= len;
        }

        session.send(Lane.INPUT, new PlayerInput(++clientTick, moveX, moveZ, yawDeg, pitchDeg, jump, sprint));
    }

    public void updatePrediction() {
        if (!active) {
            return;
        }
        long now = System.nanoTime();
        if (lastFrameNs == 0L) {
            lastFrameNs = now;
            return;
        }
        float dt = (float) ((now - lastFrameNs) / 1_000_000_000.0);
        lastFrameNs = now;
        dt = Math.min(dt, 0.1f);

        float moveX = (right ? 1.0f : 0.0f) + (left ? -1.0f : 0.0f);
        float moveZ = (forward ? 1.0f : 0.0f) + (back ? -1.0f : 0.0f);
        float len = (float) Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (len > 1e-6f && len > 1.0f) {
            moveX /= len;
            moveZ /= len;
        }

        CharacterPhysics.State current = new CharacterPhysics.State(
                predX, predY, predZ, predVelX, predVelY, predVelZ, predOnFloor);
        CharacterPhysics.State next = CharacterPhysics.simulate(
                current, moveX, moveZ, yawDeg, DEFAULT_SPEED, jump, sprint, dt);

        predX = next.x();
        predY = next.y();
        predZ = next.z();
        predVelX = next.velX();
        predVelY = next.velY();
        predVelZ = next.velZ();
        predOnFloor = next.onFloor();

        float decay = (float) Math.exp(-CORRECTION_DECAY * dt);
        corrX *= decay;
        corrY *= decay;
        corrZ *= decay;
    }

    public boolean applyCameraOverride(Camera camera) {
        Objects.requireNonNull(camera, "camera");
        if (!active) {
            return false;
        }

        updatePrediction();

        float camX = predX + corrX;
        float camY = predY + corrY + EYE_HEIGHT;
        float camZ = predZ + corrZ;

        if (!(camera instanceof CameraAccessor accessor)) {
            return false;
        }
        accessor.moud$setThirdPerson(false);
        accessor.moud$setCameraPosition(camX, camY, camZ);
        accessor.moud$setRotation(yawDeg, pitchDeg);
        return true;
    }

    public boolean shouldBlockVanillaInput(MinecraftClient client) {
        return active && client != null && client.currentScreen == null;
    }

    private void clearInput() {
        forward = back = left = right = jump = sprint = false;
    }

    private static float clampPitch(float pitchDeg) {
        if (!Float.isFinite(pitchDeg)) {
            return 0.0f;
        }
        return Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitchDeg));
    }

    private static float normalizeYaw(float yawDeg) {
        if (!Float.isFinite(yawDeg)) {
            return 0.0f;
        }
        float wrapped = (float) (yawDeg % 360.0);
        if (wrapped > 180.0f) {
            wrapped -= 360.0f;
        } else if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }
}
