package com.moud.server.minestom.scripting.api;


import com.moud.core.scene.Node;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.physics.CollisionEvent;
import com.moud.server.minestom.script.ScriptMessageRouter;
import com.moud.server.minestom.scripting.api.modules.*;
import com.moud.server.minestom.scripting.input.ScriptInputApi;
import com.moud.server.minestom.scripting.player.InputEvent;
import com.moud.server.minestom.scripting.player.PlayerInfo;
import com.moud.server.minestom.scripting.runtime.RuntimeFacade;
import com.moud.server.minestom.util.DebugLog;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CoreScriptApi {
    private static final String LOG_TAG = "script-runtime";

    private final ServerScene scene;
    private final RuntimeFacade runtime;
    private final long selfId;
    private final NodeApi nodeApi;
    private final SceneApi sceneApi;
    private final PhysicsApi physicsApi;
    private final PlayerApi playerApi;
    private final CameraApi cameraApi;
    private final CursorApi cursorApi;
    private final MessagingApi messagingApi;
    private final ParticlesApi particlesApi;
    private final PersistApi persistApi;
    private final HttpApi httpApi;
    private final MeshApi meshApi;
    private final ServerApi serverApi;
    private final TweenApi tweenApi;

    public CoreScriptApi(ServerScene scene, RuntimeFacade runtime, long selfId) {
        this.scene = scene;
        this.runtime = runtime;
        this.selfId = selfId;
        this.nodeApi = new NodeApi(scene, runtime, selfId);
        this.sceneApi = new SceneApi(scene, runtime);
        this.physicsApi = new PhysicsApi(scene, runtime, nodeApi);
        this.playerApi = new PlayerApi(scene, runtime.playerState());
        this.cameraApi = new CameraApi(scene, runtime.playerState(), selfId);
        this.cursorApi = new CursorApi(scene, runtime.playerState(), selfId);
        ScriptMessageRouter router = runtime.scriptMessageRouter();
        this.messagingApi = router == null
                ? null
                : new MessagingApi(router, selfId, runtime.connectedPlayerUuids());
        this.particlesApi = router == null
                ? null
                : new ParticlesApi(router, runtime.connectedPlayerUuids());
        this.persistApi = new PersistApi(runtime.persistence());
        this.httpApi = new HttpApi();
        this.meshApi = new MeshApi(scene, runtime.meshPublishService(), runtime);
        this.serverApi = new ServerApi(scene, runtime);
        this.tweenApi = new TweenApi(scene, runtime.playerNetworkSink());
    }

    @HostAccess.Export
    public TweenApi tween() {
        return tweenApi;
    }

    @HostAccess.Export
    public ServerApi server() {
        return serverApi;
    }

    @HostAccess.Export
    public ParticlesApi particles() {
        return particlesApi;
    }

    @HostAccess.Export
    public MessagingApi msg() {
        return messagingApi;
    }

    @HostAccess.Export
    public PersistApi persist() {
        return persistApi;
    }

    @HostAccess.Export
    public HttpApi http() {
        return httpApi;
    }

    @HostAccess.Export
    public MeshApi mesh() {
        return meshApi;
    }

    @HostAccess.Export
    public void log(String message) {
        DebugLog.info(LOG_TAG, "scene=" + scene.sceneId() + " nodeId=" + selfId + " " + (message == null ? "" : message));
    }

    @HostAccess.Export
    public long id() {
        return nodeApi.id();
    }

    @HostAccess.Export
    public long rootId() {
        return scene.engine().sceneTree().root().nodeId();
    }

    @HostAccess.Export
    public String name() {
        return nodeApi.name();
    }

    @HostAccess.Export
    public String type() {
        return nodeApi.type();
    }

    @HostAccess.Export
    public String typeOf(long nodeId) {
        return nodeApi.typeOf(nodeId);
    }

    @HostAccess.Export
    public String get(String key) {
        return nodeApi.get(key);
    }

    @HostAccess.Export
    public String get(long nodeId, String key) {
        return nodeApi.get(nodeId, key);
    }

    @HostAccess.Export
    public void set(String key, String value) {
        nodeApi.set(key, value);
    }

    @HostAccess.Export
    public void set(long nodeId, String key, String value) {
        nodeApi.set(nodeId, key, value);
    }

    public void set(long nodeId, String key, double value) {
        nodeApi.setNumber(nodeId, key, value);
    }

    public void set(long nodeId, String key, float value) {
        nodeApi.setNumber(nodeId, key, value);
    }

    public void set(long nodeId, String key, int value) {
        nodeApi.setNumber(nodeId, key, value);
    }

    public void set(long nodeId, String key, long value) {
        nodeApi.setNumber(nodeId, key, (double) value);
    }

    public void set(long nodeId, String key, boolean value) {
        nodeApi.set(nodeId, key, value ? "true" : "false");
    }

    public void set(String key, double value) {
        nodeApi.setNumber(key, value);
    }

    public void set(String key, float value) {
        nodeApi.setNumber(key, value);
    }

    public void set(String key, int value) {
        nodeApi.setNumber(key, value);
    }

    public void set(String key, long value) {
        nodeApi.setNumber(key, (double) value);
    }

    public void set(String key, boolean value) {
        nodeApi.set(key, value ? "true" : "false");
    }

    @HostAccess.Export
    public void setNumber(String key, double value) {
        nodeApi.setNumber(key, value);
    }

    @HostAccess.Export
    public void setNumber(long nodeId, String key, double value) {
        nodeApi.setNumber(nodeId, key, value);
    }

    @HostAccess.Export
    public double getNumber(String key, double fallback) {
        return nodeApi.getNumber(key, fallback);
    }

    @HostAccess.Export
    public double getNumber(long nodeId, String key, double fallback) {
        return nodeApi.getNumber(nodeId, key, fallback);
    }

    @HostAccess.Export
    public void remove(String key) {
        nodeApi.remove(key);
    }

    @HostAccess.Export
    public void remove(long nodeId, String key) {
        nodeApi.remove(nodeId, key);
    }

    @HostAccess.Export
    public void rename(String name) {
        nodeApi.rename(name);
    }

    @HostAccess.Export
    public void rename(long nodeId, String name) {
        nodeApi.rename(nodeId, name);
    }

    @HostAccess.Export
    public void reparent(long nodeId, long newParentId) {
        nodeApi.reparent(nodeId, newParentId);
    }

    @HostAccess.Export
    public void free(long nodeId) {
        nodeApi.free(nodeId);
    }

    @HostAccess.Export
    public long createRuntime(long parentId, String name, String typeId) {
        return nodeApi.createRuntime(parentId, name, typeId);
    }

    @HostAccess.Export
    public void flush() {
        nodeApi.flush();
    }

    @HostAccess.Export
    public String getString(String key, String fallback) {
        return nodeApi.getString(key, fallback);
    }

    @HostAccess.Export
    public String getString(long nodeId, String key, String fallback) {
        return nodeApi.getString(nodeId, key, fallback);
    }

    @HostAccess.Export
    public void setUniform(long nodeId, String name, double... values) {
        if (nodeId <= 0L || name == null || name.isBlank() || values == null) {
            return;
        }
        List<Float> floats = new ArrayList<>(values.length);
        for (double value : values) {
            floats.add((float) value);
        }
        scene.engine().setUniform(nodeId, name, List.copyOf(floats));
    }

    @HostAccess.Export
    public void setInstances(long nodeId, Object data) {
        if (nodeId <= 0L || data == null) {
            return;
        }
        float[] arr = toFloatArray(data);
        if (arr != null && arr.length > 0) {
            runtime.multiMeshManager().setInstances(nodeId, arr);
        }
    }

    private static float[] toFloatArray(Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof float[] floats) {
            return Arrays.copyOf(floats, floats.length);
        }
        if (data instanceof double[] doubles) {
            float[] out = new float[doubles.length];
            for (int i = 0; i < doubles.length; i++) {
                out[i] = (float) doubles[i];
            }
            return out;
        }
        if (data instanceof int[] ints) {
            float[] out = new float[ints.length];
            for (int i = 0; i < ints.length; i++) {
                out[i] = ints[i];
            }
            return out;
        }
        if (data instanceof long[] longs) {
            float[] out = new float[longs.length];
            for (int i = 0; i < longs.length; i++) {
                out[i] = longs[i];
            }
            return out;
        }
        if (data instanceof Object[] objects) {
            float[] out = new float[objects.length];
            for (int i = 0; i < objects.length; i++) {
                if (!(objects[i] instanceof Number number)) {
                    return null;
                }
                out[i] = number.floatValue();
            }
            return out;
        }
        if (data instanceof List<?> list) {
            float[] out = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (!(item instanceof Number number)) {
                    return null;
                }
                out[i] = number.floatValue();
            }
            return out;
        }
        if (data instanceof Value value) {
            if (!value.hasArrayElements()) {
                return null;
            }
            int len = (int) value.getArraySize();
            float[] out = new float[len];
            for (int i = 0; i < len; i++) {
                out[i] = (float) value.getArrayElement(i).asDouble();
            }
            return out;
        }
        return null;
    }

    @HostAccess.Export
    public InputEvent input() {
        Node self = scene.engine().sceneTree().getNode(selfId);
        return runtime.playerState().inputEventForNode(self);
    }

    @HostAccess.Export
    public long find(String path) {
        return nodeApi.find(path);
    }

    @HostAccess.Export
    public void after(double seconds, Object callback) {
        runtime.scheduler().scheduleTimer(seconds, runtime.toScriptCallable(callback));
    }

    @HostAccess.Export
    public void emit_signal(String signal) {
        runtime.signalBus().emit(selfId, signal, runtime.instanceValueMap());
    }

    @HostAccess.Export
    public void emit_signal(String signal, Object arg1) {
        runtime.signalBus().emit(selfId, signal, runtime.instanceValueMap(), arg1);
    }

    @HostAccess.Export
    public void emit_signal(String signal, Object arg1, Object arg2) {
        runtime.signalBus().emit(selfId, signal, runtime.instanceValueMap(), arg1, arg2);
    }

    @HostAccess.Export
    public void emit_signal(String signal, Object arg1, Object arg2, Object arg3) {
        runtime.signalBus().emit(selfId, signal, runtime.instanceValueMap(), arg1, arg2, arg3);
    }

    @HostAccess.Export
    public void connect(long sourceId, String signal, long targetId, String method) {
        runtime.signalBus().connect(sourceId, signal, targetId, method);
    }

    @HostAccess.Export
    public void disconnect(long sourceId, String signal, long targetId, String method) {
        runtime.signalBus().disconnect(sourceId, signal, targetId, method);
    }

    @HostAccess.Export
    public ScriptInputApi getInput() {
        return runtime.inputApiForNode(selfId);
    }

    @HostAccess.Export
    public double playerX() {
        return playerApi.playerX(scene.engine().sceneTree().getNode(selfId));
    }

    @HostAccess.Export
    public double playerY() {
        return playerApi.playerY(scene.engine().sceneTree().getNode(selfId));
    }

    @HostAccess.Export
    public double playerZ() {
        return playerApi.playerZ(scene.engine().sceneTree().getNode(selfId));
    }

    @HostAccess.Export
    public double playerYaw() {
        return playerApi.playerYaw(scene.engine().sceneTree().getNode(selfId));
    }

    @HostAccess.Export
    public boolean teleportPlayer(String playerUuid, double x, double y, double z) {
        return playerApi.teleportPlayer(playerUuid, x, y, z);
    }

    @HostAccess.Export
    public boolean teleportPlayer(String playerUuid, double x, double y, double z, double yawDeg, double pitchDeg) {
        return playerApi.teleportPlayer(playerUuid, x, y, z, yawDeg, pitchDeg);
    }

    @HostAccess.Export
    public void playerSetVelocity(String playerUuid, double vx, double vy, double vz) {
        runtime.playerNetworkSink().sendPlayerVelocity(playerUuid, (float) vx, (float) vy, (float) vz);
    }

    @HostAccess.Export
    public void playerAddVelocity(String playerUuid, double vx, double vy, double vz) {
        runtime.playerNetworkSink().sendPlayerVelocity(playerUuid, (float) vx, (float) vy, (float) vz);
    }

    @HostAccess.Export
    public double[] playerGetVelocity(String playerUuid) {
        return playerApi.playerGetVelocity(playerUuid);
    }

    @HostAccess.Export
    public void setActiveCamera(long nodeId) {
        cameraApi.scene(nodeId);
    }

    @HostAccess.Export
    public void setFollowCamera(double localX, double localY, double localZ, double pitchDeg, double rollDeg) {
        cameraApi.follow(localX, localY, localZ, pitchDeg, rollDeg);
    }

    @HostAccess.Export
    public void setScriptCamera(double x, double y, double z, double yawDeg, double pitchDeg, double rollDeg) {
        cameraApi.scriptable(x, y, z, yawDeg, pitchDeg, rollDeg);
    }

    @HostAccess.Export
    public void resetCamera() {
        cameraApi.reset();
    }

    @HostAccess.Export
    public void setCursorMode(boolean enabled) {
        if (enabled) {
            cursorApi.enable();
        } else {
            cursorApi.disable();
        }
    }

    @HostAccess.Export
    public boolean isCursorModeEnabled() {
        return cursorApi.enabled();
    }

    @HostAccess.Export
    public void setOsCursorVisible(boolean visible) {
        cursorApi.setVisible(visible);
    }

    @HostAccess.Export
    public boolean isOsCursorVisible() {
        return cursorApi.visible();
    }

    @HostAccess.Export
    public double[] getCursorPosition() {
        return cursorApi.position();
    }

    @HostAccess.Export
    public CameraApi camera() {
        return cameraApi;
    }

    @HostAccess.Export
    public CursorApi cursor() {
        return cursorApi;
    }

    @HostAccess.Export
    public void setSceneCurrentCamera(long cameraNodeId) {
        sceneApi.setSceneCurrentCamera(cameraNodeId);
    }

    @HostAccess.Export
    public void clearAllSceneCurrentCameras() {
        sceneApi.clearAllSceneCurrentCameras();
    }

    @HostAccess.Export
    public CollisionEvent[] getCollisionEvents() {
        return physicsApi.getCollisionEvents();
    }

    @HostAccess.Export
    public double[] getBodyVelocity(long nodeId) {
        return physicsApi.getBodyVelocity(nodeId);
    }

    @HostAccess.Export
    public double[] getCharacterVelocity(long nodeId) {
        return physicsApi.getCharacterVelocity(nodeId);
    }

    @HostAccess.Export
    public void setCharacterVelocity(long nodeId, double vx, double vy, double vz) {
        physicsApi.setCharacterVelocity(nodeId, vx, vy, vz);
    }

    @HostAccess.Export
    public void setCharacterScriptControlled(long nodeId, boolean controlled) {
        physicsApi.setCharacterScriptControlled(nodeId, controlled);
    }

    @HostAccess.Export
    public double[] moveAndSlide(long nodeId, double deltaSeconds) {
        return physicsApi.moveAndSlide(nodeId, deltaSeconds);
    }

    @HostAccess.Export
    public boolean isOnFloor(long nodeId) {
        return physicsApi.isOnFloor(nodeId);
    }

    @HostAccess.Export
    public boolean isOnWall(long nodeId) {
        return physicsApi.isOnWall(nodeId);
    }

    @HostAccess.Export
    public boolean isOnCeiling(long nodeId) {
        return physicsApi.isOnCeiling(nodeId);
    }

    @HostAccess.Export
    public double[] getWallNormal(long nodeId) {
        return physicsApi.getWallNormal(nodeId);
    }

    @HostAccess.Export
    public double[] getInputDirection(long nodeId) {
        return physicsApi.getInputDirection(nodeId);
    }

    @HostAccess.Export
    public void applyForce(long nodeId, double fx, double fy, double fz) {
        physicsApi.applyForce(nodeId, fx, fy, fz);
    }

    @HostAccess.Export
    public void applyImpulse(long nodeId, double fx, double fy, double fz) {
        physicsApi.applyImpulse(nodeId, fx, fy, fz);
    }

    @HostAccess.Export
    public void setLinearVelocity(long nodeId, double vx, double vy, double vz) {
        physicsApi.setLinearVelocity(nodeId, vx, vy, vz);
    }

    @HostAccess.Export
    public long[] getChildren(long nodeId) {
        return nodeApi.getChildren(nodeId);
    }

    @HostAccess.Export
    public boolean exists(long nodeId) {
        return nodeApi.exists(nodeId);
    }

    @HostAccess.Export
    public PlayerInfo[] getPlayers() {
        return playerApi.getPlayers();
    }

    @HostAccess.Export
    public void tween(long nodeId, String prop, double targetValue, double duration) {
        double fromValue = nodeApi.getNumber(nodeId, prop, targetValue);
        runtime.scheduler().tween(runtime.mutator(), nodeId, prop, fromValue, targetValue, duration);
    }

    @HostAccess.Export
    public long instantiate(String scenePath, long parentId) {
        return sceneApi.instantiate(scenePath, parentId);
    }

    @HostAccess.Export
    public NodeApi node() {
        return nodeApi;
    }

    @HostAccess.Export
    public SceneApi scene() {
        return sceneApi;
    }

    @HostAccess.Export
    public PhysicsApi physics() {
        return physicsApi;
    }

    @HostAccess.Export
    public PlayerApi player() {
        return playerApi;
    }
}
