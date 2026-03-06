package com.moud.client.fabric.runtime;

import com.moud.client.fabric.mixin.accessor.CameraAccessor;
import com.moud.client.fabric.render.VeilSceneNodeRenderer;
import com.moud.client.fabric.scene.ClientSceneBus;
import com.moud.net.protocol.PlayerInput;
import com.moud.net.protocol.RuntimeState;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;
import com.moud.net.transport.Lane;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

public final class PlayRuntimeClient {
    private static final float EYE_HEIGHT = 1.6f;
    private static final float DEFAULT_SPEED = 6.0f;

    private boolean active;

    private final PlayRuntimeInputState input = new PlayRuntimeInputState();
    private final PlayRuntimeLookState look = new PlayRuntimeLookState();
    private final PlayRuntimePredictionState prediction = new PlayRuntimePredictionState();
    private final PlayRuntimeSceneCameraAttachment sceneCamera = new PlayRuntimeSceneCameraAttachment();

    private long clientTick;
    private long lastSendNs;

    private volatile RuntimeState lastServerState;
    private volatile long bodyNodeId;

    public RuntimeState lastServerState() {
        return lastServerState;
    }

    public boolean isActive() {
        return active;
    }

    public boolean hasCamera() {
        RuntimeState state = lastServerState;
        return state == null || state.hasCamera();
    }

    public void setActive(boolean active) {
        if (this.active == active) {
            return;
        }
        this.active = active;
        input.clear();
        look.resetMouse();
        if (active) {
            RuntimeState state = lastServerState;
            if (state != null) {
                look.setAngles(state.camYawDeg(), state.camPitchDeg());
                prediction.resetFromServer(state);
                sceneCamera.updateFromServerState(state);
            }
        } else {
            bodyNodeId = 0L;
            VeilSceneNodeRenderer.clearRuntimeBodyOverride();
        }
    }

    public void onDisconnect() {
        active = false;
        input.clear();
        look.resetMouse();
        lastServerState = null;
        bodyNodeId = 0L;
        VeilSceneNodeRenderer.clearRuntimeBodyOverride();
        prediction.reset();
        sceneCamera.clear();
    }

    public void onRuntimeState(RuntimeState state) {
        lastServerState = state;
        bodyNodeId = state == null ? 0L : state.bodyNodeId();
        sceneCamera.updateFromServerState(state);
        if (!active || state == null) {
            return;
        }
        prediction.applyServerState(state);
    }

    public void onKeyEvent(int key, int action) {
        if (!active) {
            return;
        }
        input.onKeyEvent(key, action);
    }

    public void onPauseMenuOpened() {
        input.clear();
        look.resetMouse();
    }

    public void onMouseMove(double x, double y) {
        if (!active) {
            return;
        }
        look.onMouseMove(x, y);
    }

    public void tick(Session session) {
        sendInput(session, 60);
    }

    public void sendInput(Session session, int maxHz) {
        if (!active || session == null || session.state() != SessionState.CONNECTED) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen != null) {
            return;
        }
        look.syncFromVanillaPlayer(client);
        long now = System.nanoTime();
        int hz = Math.max(1, Math.min(240, maxHz));
        long interval = 1_000_000_000L / hz;
        if (lastSendNs != 0L && now - lastSendNs < interval) {
            return;
        }
        lastSendNs = now;

        PlayRuntimeInputState.Movement movement = input.movement();
        session.send(Lane.INPUT, new PlayerInput(++clientTick, movement.moveX(), movement.moveZ(), look.yawDeg(), look.pitchDeg(), input.jump(), input.sprint()));
    }

    public void updatePrediction() {
        if (!active) {
            return;
        }
        PlayRuntimeInputState.Movement movement = input.movement();
        float speed = DEFAULT_SPEED;
        long bodyId = bodyNodeId;
        if (bodyId > 0L) {
            SceneSnapshot.NodeSnapshot node = ClientSceneBus.getNode(bodyId);
            float sceneSpeed = parseFloat(node, "speed", Float.NaN);
            if (Float.isFinite(sceneSpeed) && sceneSpeed >= 0.0f) {
                speed = sceneSpeed;
            }
        }
        prediction.updatePrediction(look.yawDeg(), movement.moveX(), movement.moveZ(), input.jump(), input.sprint(), speed);
    }

    public boolean applyCameraOverride(Camera camera) {
        Objects.requireNonNull(camera, "camera");
        if (!active) {
            return false;
        }
        RuntimeState st = lastServerState;
        look.syncFromVanillaPlayer(MinecraftClient.getInstance());
        updatePrediction();

        long bodyId = bodyNodeId;
        if (bodyId > 0L) {
            VeilSceneNodeRenderer.setRuntimeBodyOverride(bodyId, prediction.baseX(), prediction.baseY(), prediction.baseZ(), look.yawDeg());
        } else {
            VeilSceneNodeRenderer.clearRuntimeBodyOverride();
        }

        if (st != null && !st.hasCamera()) {
            return false;
        }

        boolean useSceneCamera = st != null && st.useSceneCamera();
        boolean attachedSceneCamera = useSceneCamera && sceneCamera.isAttached();

        float camX;
        float camY;
        float camZ;
        float yaw;
        float pitch;
        float rollDeg = 0.0f;
        if (useSceneCamera && !attachedSceneCamera) {
            camX = st.sceneCamX();
            camY = st.sceneCamY();
            camZ = st.sceneCamZ();
            yaw = PlayRuntimeAngles.normalizeYaw(st.sceneCamYawDeg());
            pitch = PlayRuntimeAngles.clampPitch(st.sceneCamPitchDeg());
            rollDeg = st.sceneCamRollDeg();
        } else {
            if (useSceneCamera) {
                yaw = PlayRuntimeAngles.normalizeYaw(look.yawDeg() + sceneCamera.localYawOffDeg());
                pitch = PlayRuntimeAngles.clampPitch(look.pitchDeg() + sceneCamera.localPitchOffDeg());
                rollDeg = sceneCamera.localRollOffDeg();
            } else {
                yaw = look.yawDeg();
                pitch = look.pitchDeg();
                rollDeg = 0.0f;
            }

            float baseX = prediction.baseX();
            float baseY = prediction.baseY();
            float baseZ = prediction.baseZ();

            if (attachedSceneCamera && sceneCamera.isLocalOffsetValid()) {
                float yawRad = (float) Math.toRadians(PlayRuntimeAngles.normalizeYaw(look.yawDeg()));
                float cos = (float) Math.cos(yawRad);
                float sin = (float) Math.sin(yawRad);
                float wx = sceneCamera.localOffX() * cos + sceneCamera.localOffZ() * sin;
                float wz = -sceneCamera.localOffX() * sin + sceneCamera.localOffZ() * cos;
                camX = baseX + wx;
                camY = baseY + sceneCamera.localOffY();
                camZ = baseZ + wz;
            } else {
                camX = baseX;
                camY = baseY + EYE_HEIGHT;
                camZ = baseZ;
            }
        }

        if (!(camera instanceof CameraAccessor accessor)) {
            return false;
        }
        accessor.moud$setThirdPerson(false);
        accessor.moud$setCameraPosition(camX, camY, camZ);
        accessor.moud$setRotation(yaw, pitch);

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

    private static float parseFloat(SceneSnapshot.NodeSnapshot node, String key, float fallback) {
        if (node == null || key == null) {
            return fallback;
        }
        var props = node.properties();
        if (props == null || props.isEmpty()) {
            return fallback;
        }
        for (SceneSnapshot.Property prop : props) {
            if (prop != null && key.equals(prop.key())) {
                try {
                    float v = Float.parseFloat(prop.value());
                    return Float.isFinite(v) ? v : fallback;
                } catch (Exception ignored) {
                    return fallback;
                }
            }
        }
        return fallback;
    }

    public boolean shouldBlockVanillaInput(MinecraftClient client) {
        return active && client != null && client.currentScreen == null;
    }
}
