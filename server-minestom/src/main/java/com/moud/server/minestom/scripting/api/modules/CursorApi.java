package com.moud.server.minestom.scripting.api.modules;


import com.moud.core.scene.Node;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.scripting.player.PlayerStateManager;
import org.graalvm.polyglot.HostAccess;

public final class CursorApi {
    private final ServerScene scene;
    private final PlayerStateManager playerState;
    private final long selfId;

    public CursorApi(ServerScene scene, PlayerStateManager playerState, long selfId) {
        this.scene = scene;
        this.playerState = playerState;
        this.selfId = selfId;
    }

    @HostAccess.Export
    public void enable() {
        setEnabled(true);
    }

    @HostAccess.Export
    public void disable() {
        setEnabled(false);
    }

    @HostAccess.Export
    public void setVisible(boolean visible) {
        Node self = scene.engine().sceneTree().getNode(selfId);
        String uuid = playerState.resolveOwnerUuidOrSinglePlayer(self);
        if (uuid != null) {
            playerState.setCursorState(selfId, uuid, playerState.isCursorModeEnabled(uuid), visible);
        }
    }

    @HostAccess.Export
    public boolean enabled() {
        Node self = scene.engine().sceneTree().getNode(selfId);
        String uuid = playerState.resolveOwnerUuidOrSinglePlayer(self);
        return uuid != null && playerState.isCursorModeEnabled(uuid);
    }

    @HostAccess.Export
    public boolean visible() {
        Node self = scene.engine().sceneTree().getNode(selfId);
        String uuid = playerState.resolveOwnerUuidOrSinglePlayer(self);
        return uuid == null || playerState.isOsCursorVisible(uuid);
    }

    @HostAccess.Export
    public double[] position() {
        Node self = scene.engine().sceneTree().getNode(selfId);
        String uuid = playerState.resolveOwnerUuidOrSinglePlayer(self);
        return playerState.getCursorPosition(uuid);
    }

    private void setEnabled(boolean enabled) {
        Node self = scene.engine().sceneTree().getNode(selfId);
        String uuid = playerState.resolveOwnerUuidOrSinglePlayer(self);
        if (uuid != null) {
            playerState.setCursorState(selfId, uuid, enabled, playerState.isOsCursorVisible(uuid));
        }
    }
}
