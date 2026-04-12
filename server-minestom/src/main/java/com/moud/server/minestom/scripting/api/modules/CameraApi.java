package com.moud.server.minestom.scripting.api.modules;



import com.moud.core.scene.Node;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.scripting.player.PlayerStateManager;
import org.graalvm.polyglot.HostAccess;

public final class CameraApi {
    private final ServerScene scene;
    private final PlayerStateManager playerState;
    private final long selfId;

    public CameraApi(ServerScene scene, PlayerStateManager playerState, long selfId) {
        this.scene = scene;
        this.playerState = playerState;
        this.selfId = selfId;
    }

    @HostAccess.Export
    public void follow(double localX, double localY, double localZ, double pitchDeg, double rollDeg) {
        Node self = scene.engine().sceneTree().getNode(selfId);
        String uuid = playerState.resolveOwnerUuidOrSinglePlayer(self);
        if (uuid != null) {
            playerState.setFollowCamera(selfId, uuid, new float[]{
                    (float) localX, (float) localY, (float) localZ,
                    (float) pitchDeg, (float) rollDeg
            });
        }
    }

    @HostAccess.Export
    public void scene(long cameraNodeId) {
        Node self = scene.engine().sceneTree().getNode(selfId);
        String uuid = playerState.resolveOwnerUuidOrSinglePlayer(self);
        if (uuid != null) {
            playerState.setActiveCamera(selfId, uuid, cameraNodeId);
        }
    }

    @HostAccess.Export
    public void scriptable(double x, double y, double z, double yawDeg, double pitchDeg, double rollDeg) {
        Node self = scene.engine().sceneTree().getNode(selfId);
        String uuid = playerState.resolveOwnerUuidOrSinglePlayer(self);
        if (uuid != null) {
            playerState.setScriptCamera(selfId, uuid, new float[]{
                    (float) x, (float) y, (float) z,
                    (float) yawDeg, (float) pitchDeg, (float) rollDeg
            });
        }
    }

    @HostAccess.Export
    public void reset() {
        Node self = scene.engine().sceneTree().getNode(selfId);
        String uuid = playerState.resolveOwnerUuidOrSinglePlayer(self);
        if (uuid != null) {
            playerState.resetCamera(uuid);
        }
    }
}
