package com.moud.client.fabric.runtime;

import com.moud.core.util.MathUtils;
import com.moud.client.fabric.mixin.accessor.CameraAccessor;
import com.moud.client.fabric.render.VeilSceneNodeRenderer;
import com.moud.client.fabric.scripting.ClientScriptRuntime;
import com.moud.client.fabric.scripting.api.InputApi;
import com.moud.net.protocol.PlayerInput;
import com.moud.net.protocol.RuntimeState;
import com.moud.net.protocol.CursorState;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;
import com.moud.net.transport.Lane;
import net.minecraft.client.input.Input;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class PlayRuntimeClient {
    private static final Pattern PLAYER_STATE_KEY_PATTERN = Pattern.compile("[a-z0-9._-]{1,32}");
    private boolean active;
    private volatile RuntimeState lastServerState;
    private final ClientCameraState cameraState = new ClientCameraState();
    private boolean serverCursorModeEnabled;
    private boolean serverOsCursorVisible = true;
    private Boolean localCursorModeEnabled;
    private Boolean localOsCursorVisible;
    private boolean cursorResetPending;
    private float cursorX;
    private float cursorY;
    private final LinkedHashMap<String, String> playerState = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> pendingPlayerState = new LinkedHashMap<>();
    private long clientTick;
    private long lastFrameNanoTime;
    private final PlayRuntimeInputState inputState = new PlayRuntimeInputState();
    private final CharacterBody3D characterBody = new CharacterBody3D();
    private final ClientScriptRuntime clientScriptRuntime = new ClientScriptRuntime();

    private float prevX, prevY, prevZ, prevYaw, prevPitch, prevRoll;
    private float currX, currY, currZ, currYaw, currPitch, currRoll;
    private boolean hasPrev;

    public RuntimeState lastServerState() {
        return lastServerState;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isCharacterBodyDriving() {
        return active && characterBody.isActive();
    }

    public double characterBodyVelX() {
        return characterBody.velocity().x;
    }

    public double characterBodyVelY() {
        return characterBody.velocity().y;
    }

    public double characterBodyVelZ() {
        return characterBody.velocity().z;
    }

    public void setActive(boolean active) {
        if (this.active && !active) {
            inputState.clear();
            characterBody.reset();
            lastFrameNanoTime = 0L;
            clearLocalCursorOverrides();
            VeilSceneNodeRenderer.clearRuntimeBodyOverride();
            clientScriptRuntime.unloadAll();
        }
        this.active = active;
        ClientCameraStateBus.set(active ? cameraState : null);
    }

    public void onDisconnect() {
        active = false;
        lastServerState = null;
        hasPrev = false;
        prevX = 0f; prevY = 0f; prevZ = 0f;
        prevYaw = 0f; prevPitch = 0f; prevRoll = 0f;
        currX = 0f; currY = 0f; currZ = 0f;
        currYaw = 0f; currPitch = 0f; currRoll = 0f;
        serverCursorModeEnabled = false;
        serverOsCursorVisible = true;
        localCursorModeEnabled = null;
        localOsCursorVisible = null;
        cursorResetPending = false;
        cursorX = 0.0f;
        cursorY = 0.0f;
        playerState.clear();
        pendingPlayerState.clear();
        clientTick = 0L;
        lastFrameNanoTime = 0L;
        inputState.clear();
        characterBody.reset();
        VeilSceneNodeRenderer.clearRuntimeBodyOverride();
        clientScriptRuntime.unloadAll();
    }

    public void onCursorState(CursorState state) {
        if (state == null) {
            return;
        }
        cursorResetPending = false;
        serverCursorModeEnabled = state.cursorModeEnabled();
        serverOsCursorVisible = state.osCursorVisible();
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
        characterBody.tick(client.player, inputState);
        syncCharacterBodyRenderOverride(client.player);
        clientTick++;
        updateCursorPosition(client);
        float yaw = client.player.getYaw();
        float pitch = client.player.getPitch();
        PlayRuntimeInputState.Movement movement = inputState.movement();
        String stateKey = "";
        String stateValue = "";
        Iterator<Map.Entry<String, String>> it = pendingPlayerState.entrySet().iterator();
        if (it.hasNext()) {
            Map.Entry<String, String> next = it.next();
            stateKey = next.getKey();
            stateValue = next.getValue();
            it.remove();
        }
        session.send(Lane.INPUT,
                new PlayerInput(clientTick, movement.moveX(), movement.moveZ(), yaw, pitch, cursorX, cursorY, stateKey, stateValue, inputState.jump(), inputState.sprint(), inputState.sneak()));
    }

    public void captureInput() {
        inputState.captureKeys();
    }

    public void onKeyEvent(int key, int scancode, int action) {
        if (!active) {
            return;
        }
        inputState.onKeyEvent(key, scancode, action);
    }

    public boolean applyCharacterInputTo(Input input) {
        return active && characterBody.applyInputToVanilla(input, inputState);
    }

    public boolean handleCharacterBodyTravel(PlayerEntity player, Vec3d movementInput) {
        return active && characterBody.isActive();
    }

    public void travelFrame(MinecraftClient client, float tickDelta) {
        if (!active || client == null || client.player == null || client.currentScreen != null) {
            lastFrameNanoTime = 0L;
            return;
        }
        if (!characterBody.isActive()) {
            lastFrameNanoTime = 0L;
            return;
        }
        long now = System.nanoTime();
        double dt;
        if (lastFrameNanoTime == 0L) {
            dt = 1.0 / 60.0;
        } else {
            dt = (now - lastFrameNanoTime) / 1_000_000_000.0;
            if (dt > 0.1) dt = 0.1;
            if (dt <= 0.0) dt = 1.0 / 120.0;
        }
        lastFrameNanoTime = now;

        inputState.captureKeys();

        cameraState.resetForFrame();

        PlayRuntimeInputState.Movement movement = inputState.movement();
        InputApi.InputStateSnapshot inputSnapshot = new InputApi.InputStateSnapshot(
                inputState.jump(),
                inputState.sprint(),
                inputState.sneak(),
                movement.moveX(),
                movement.moveZ(),
                cursorX,
                cursorY
        );

        clientScriptRuntime.syncAllNodes(characterBody, inputSnapshot, cameraState);
        clientScriptRuntime.frame(dt);
        if (!Float.isNaN(cameraState.playerYaw) && client.player != null) {
            // Only set the physics/look yaw (used by CharacterBody3D for movement direction).
            // bodyYaw is managed by the script via body:writeFloat("rotation_y", ...).
            client.player.setYaw(cameraState.playerYaw);
        }

        double preTravelX = client.player.getX();
        double preTravelY = client.player.getY();
        double preTravelZ = client.player.getZ();

        characterBody.applyRenderPose(client.player, tickDelta);
        syncCharacterBodyVisualState(client.player, preTravelX, preTravelY, preTravelZ);

        if (cameraState.hasOverride && !Float.isNaN(cameraState.posX)) {
            cameraState.posX += (float)(client.player.getX() - preTravelX);
            cameraState.posY += (float)(client.player.getY() - preTravelY);
            cameraState.posZ += (float)(client.player.getZ() - preTravelZ);
        }
    }

    public boolean applyCameraOverride(Camera camera, float partialTick) {
        if (!active) {
            return false;
        }

        if (!(camera instanceof CameraAccessor accessor)) {
            return false;
        }

        if (cameraState.hasOverride) {
            return applyScriptCamera(accessor);
        }

        RuntimeState st = lastServerState;
        if (st == null) {
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

    private boolean applyScriptCamera(CameraAccessor accessor) {
        float x = cameraState.posX;
        float y = cameraState.posY;
        float z = cameraState.posZ;
        if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z)) {
            return false;
        }
        float yaw   = Float.isNaN(cameraState.yaw)   ? 0f : cameraState.yaw;
        float pitch = Float.isNaN(cameraState.pitch)  ? 0f : cameraState.pitch;
        float roll  = Float.isNaN(cameraState.roll)   ? 0f : cameraState.roll;
        accessor.moud$setThirdPerson(true);
        accessor.moud$setCameraPosition(x, y, z);
        accessor.moud$setRotation(yaw, pitch);
        applyRoll(accessor, roll);
        return true;
    }

    public static volatile boolean scriptForceHideHand;

    public boolean shouldHideVanillaHand() {
        if (scriptForceHideHand) return true;
        if (!active) return false;
        if (cameraState.hasOverride) return true;
        RuntimeState st = lastServerState;
        return st != null && (st.useFollowCamera() || st.useSceneCamera() || st.useScriptCamera());
    }

    private void syncCharacterBodyRenderOverride(PlayerEntity player) {
        long nodeId = characterBody.nodeId();
        if (!active || player == null || nodeId <= 0L) {
            VeilSceneNodeRenderer.clearRuntimeBodyOverride();
            return;
        }
        VeilSceneNodeRenderer.setRuntimeBodyOverride(
                nodeId,
                (float) player.getX(),
                (float) player.getY(),
                (float) player.getZ(),
                player.getYaw()
        );
    }

    private void syncCharacterBodyVisualState(PlayerEntity player, double prevX, double prevY, double prevZ) {
        if (player == null || !characterBody.isActive()) {
            return;
        }
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        // CharacterBody3D.travel already set x/y/z to the interpolated position for this frame.
        // We set prevX/Y/Z to the same value to disable vanilla interpolation, otherwise
        // it would try to lerp between render frames using tickDelta (20 Hz), causing jitter.
        player.prevX = x;
        player.prevY = y;
        player.prevZ = z;
        player.lastRenderX = x;
        player.lastRenderY = y;
        player.lastRenderZ = z;
        syncCharacterBodyRenderOverride(player);
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
        return MathUtils.normalizeYaw(yawDeg);
    }

    private static float clampPitchDeg(float pitchDeg, float min, float max) {
        return MathUtils.clampPitch(pitchDeg, min, max);
    }

    public ClientCameraState cameraState() {
        return cameraState;
    }

    public float playerRoll() {
        return active ? characterBody.rotationZ() : 0f;
    }

    public boolean isCharacterBodyDrivingMovement() {
        return active && characterBody.isActive();
    }

    public boolean shouldBlockVanillaInput(MinecraftClient client) {
        return isCursorModeEnabled() || (active && characterBody.isActive());
    }

    public boolean isCursorModeEnabled() {
        if (cursorResetPending) {
            return false;
        }
        return localCursorModeEnabled != null ? localCursorModeEnabled : serverCursorModeEnabled;
    }

    public void setCursorModeEnabled(boolean enabled) {
        cursorResetPending = false;
        this.localCursorModeEnabled = enabled;
        if (!enabled && Boolean.FALSE.equals(localOsCursorVisible)) {
            this.localOsCursorVisible = Boolean.TRUE;
        }
    }

    public boolean isOsCursorVisible() {
        return localOsCursorVisible != null ? localOsCursorVisible : serverOsCursorVisible;
    }

    public void setOsCursorVisible(boolean visible) {
        cursorResetPending = false;
        this.localOsCursorVisible = visible;
        if (visible) {
            this.localCursorModeEnabled = true;
        }
    }

    public void clearLocalCursorOverrides() {
        localCursorModeEnabled = null;
        localOsCursorVisible = null;
    }

    public void onEditorClosed() {
        clearLocalCursorOverrides();
        cursorResetPending = true;
    }

    public float cursorX() {
        return cursorX;
    }

    public float cursorY() {
        return cursorY;
    }

    public String getPlayerState(String key) {
        String safeKey = sanitizePlayerStateKey(key);
        if (safeKey == null) {
            return "";
        }
        return playerState.getOrDefault(safeKey, "");
    }

    public void setPlayerState(String key, String value) {
        String safeKey = sanitizePlayerStateKey(key);
        if (safeKey == null) {
            return;
        }
        String safeValue = sanitizePlayerStateValue(value);
        String prev = playerState.put(safeKey, safeValue);
        if (safeValue.equals(prev)) {
            return;
        }
        pendingPlayerState.put(safeKey, safeValue);
    }

    public void applyCursorMode(MinecraftClient client) {
        if (client == null || client.mouse == null || client.currentScreen != null) {
            return;
        }
        long windowHandle = client.getWindow().getHandle();
        if (isCursorModeEnabled()) {
            if (client.mouse.isCursorLocked()) {
                client.mouse.unlockCursor();
            }
            GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR,
                    isOsCursorVisible() ? GLFW.GLFW_CURSOR_NORMAL : GLFW.GLFW_CURSOR_HIDDEN);
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
        if (!client.isWindowFocused()) {
            return;
        }
        long handle = client.getWindow().getHandle();
        double[] mx = new double[1];
        double[] my = new double[1];
        GLFW.glfwGetCursorPos(handle, mx, my);
        int[] ww = new int[1];
        int[] wh = new int[1];
        GLFW.glfwGetWindowSize(handle, ww, wh);
        double cx = mx[0];
        double cy = my[0];
        if (ww[0] > 0 && wh[0] > 0 && (cx < 0 || cx > ww[0] || cy < 0 || cy > wh[0])) {
            return;
        }
        cursorX = (float) cx;
        cursorY = (float) cy;
    }

    private static String sanitizePlayerStateKey(String key) {
        if (key == null) {
            return null;
        }
        String trimmed = key.trim().toLowerCase();
        if (!PLAYER_STATE_KEY_PATTERN.matcher(trimmed).matches()) {
            return null;
        }
        return trimmed;
    }

    private static String sanitizePlayerStateValue(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > 128) {
            return trimmed.substring(0, 128);
        }
        return trimmed;
    }
}
