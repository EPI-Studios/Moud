package com.moud.client.fabric.runtime;

import com.moud.client.fabric.physics.rapier.ClientRapierPhysics;
import com.moud.client.fabric.scene.SceneCameraResolver;
import com.moud.client.fabric.scene.tween.ClientTweenPlayer;
import com.moud.client.fabric.render.MoudLocalPlayerRenderer;
import com.moud.client.fabric.render.VeilSceneRenderer;
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
import net.minecraft.client.option.Perspective;
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
    private final MoudPlayer player = new MoudPlayer();

    public MoudPlayer player() {
        return player;
    }

    private Perspective savedPerspective;
    private volatile boolean usingSceneCamera;

    public boolean isUsingSceneCamera() {
        return usingSceneCamera;
    }

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
            player.reset();
            MoudLocalPlayerRenderer.get().reset();
            lastFrameNanoTime = 0L;
            clearLocalCursorOverrides();
            VeilSceneNodeRenderer.clearRuntimeBodyOverride();
            clientScriptRuntime.unloadAll();
            ClientRapierPhysics.visuals().clear();
            VeilSceneRenderer.resetPoseStates();
            restoreSavedPerspective();
        }
        this.active = active;
        ClientCameraStateBus.set(active ? cameraState : null);
    }

    public void onDisconnect() {
        active = false;
        lastServerState = null;
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
        player.reset();
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
        // Camera resolution moved to client-side scene-graph composition (SceneCameraResolver).
        // The useSceneCamera/useFollowCamera/useScriptCamera/sceneCam*/followCam*/scriptCam* wire
        // fields are still in the protocol for back-compat but no longer drive the camera.
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
        // Body sim moved to travelFrame (render rate). This tick path stays only for the 20Hz
        // PlayerInput packet to the server below — vanilla MC's PlayerEntity.travel is fully
        // cancelled by PlayerEntityCharacterBodyMixin, so the 20Hz sim was never the authority.
        syncCharacterBodyRenderOverride(client.player);
        clientTick++;
        updateCursorPosition(client);
        float yaw;
        float pitch;
        if (player.isActive()) {
            yaw = player.yaw();
            pitch = player.pitch();
        } else {
            yaw = client.player.getYaw();
            pitch = client.player.getPitch();
        }
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

        ClientRapierPhysics.get().tickRenderFrame();
        ClientTweenPlayer.get().tick(System.nanoTime());

        clientScriptRuntime.syncAllNodes(characterBody, inputSnapshot, cameraState);
        clientScriptRuntime.frame(dt);

        if (!characterBody.isActive()) {
            return;
        }
        if (!Float.isNaN(cameraState.playerYaw) && client.player != null) {
            // only the look yaw, body yaw is owned by scripts via rotation_y
            client.player.setYaw(cameraState.playerYaw);
        }

        double preTravelX = client.player.getX();
        double preTravelY = client.player.getY();
        double preTravelZ = client.player.getZ();

        // Render-rate body sim. Fixed-substep accumulator paces variable-dt render frames into
        // SUB_DT (1/60s) physics steps. Writes mc.player position + scene-graph overrides every
        // frame so the camera composes against the live pose with zero snapshot lag.
        characterBody.tickRenderFrame(client.player, inputState, dt);
        characterBody.publishTo(player, client.player.getYaw(), client.player.getPitch(), client.player.bodyYaw, client.player.headYaw);
        syncSoundListenerFromMoudPlayer(client.player);

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

        // Layer 2: scene-graph camera resolution. Read the active Camera3D's world transform
        // directly from the client scene tree every frame. No wire snapshot, no lerp — the camera
        // composes against whatever the body's position is THIS frame, the same way Godot/Unity do.
        SceneCameraResolver.ResolvedCamera resolved = SceneCameraResolver.resolve();
        if (resolved != null) {
            cameraState.orthographic = resolved.orthographic();
            cameraState.orthoSize = resolved.orthoSize();
            if (resolved.fov() > 0f && cameraState.fov <= 0f) {
                cameraState.fov = resolved.fov();
            }
            usingSceneCamera = true;
            forceThirdPersonPerspective();
            accessor.moud$setThirdPerson(true);
            accessor.moud$setCameraPosition(resolved.worldX(), resolved.worldY(), resolved.worldZ());
            accessor.moud$setRotation(resolved.yawDeg(), resolved.pitchDeg());
            applyRoll(accessor, resolved.rollDeg());
            return true;
        }

        // No Camera3D in the scene → vanilla first-person.
        usingSceneCamera = false;
        accessor.moud$setThirdPerson(false);
        restoreSavedPerspective();
        return false;
    }

    private void forceThirdPersonPerspective() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        if (savedPerspective == null) {
            savedPerspective = mc.options.getPerspective();
        }
        if (mc.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }
    }

    private void restoreSavedPerspective() {
        if (savedPerspective == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.options != null) {
            mc.options.setPerspective(savedPerspective);
        }
        savedPerspective = null;
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
        return usingSceneCamera;
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

    private void syncSoundListenerFromMoudPlayer(PlayerEntity mcPlayer) {
        if (mcPlayer == null || !player.isActive()) {
            return;
        }
        // mc.player is no longer authoritative for body pose — it's a vehicle for vanilla's sound
        // listener (and hand/HUD positioning). Drive its position from MoudPlayer every render frame
        // so the listener tracks the live sim. Pin prev/lastRender to current so vanilla doesn't
        // re-lerp at 20Hz on top of our per-frame pose.
        double x = player.x();
        double y = player.y();
        double z = player.z();
        mcPlayer.setPosition(x, y, z);
        mcPlayer.prevX = x;
        mcPlayer.prevY = y;
        mcPlayer.prevZ = z;
        mcPlayer.lastRenderX = x;
        mcPlayer.lastRenderY = y;
        mcPlayer.lastRenderZ = z;
        syncCharacterBodyRenderOverride(mcPlayer);
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
