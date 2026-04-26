package com.moud.server.minestom.scripting.api.modules;


import com.moud.core.scene.Node;
import com.moud.core.scripts.luau.LuauExport;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.scripting.player.PlayerStateManager;
import org.graalvm.polyglot.HostAccess;

@LuauExport(name = "CursorApi", doc = "Cursor mode and OS-cursor visibility toggles plus position queries.")
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
    @LuauExport
    public void enable() {
        setEnabled(true);
    }

    @HostAccess.Export
    @LuauExport
    public void disable() {
        setEnabled(false);
    }

    @HostAccess.Export
    @LuauExport
    public void setVisible(boolean visible) {
        Node self = scene.engine().sceneTree().getNode(selfId);
        String uuid = playerState.resolveOwnerUuidOrSinglePlayer(self);
        if (uuid != null) {
            playerState.setCursorState(selfId, uuid, playerState.isCursorModeEnabled(uuid), visible);
        }
    }

    @HostAccess.Export
    @LuauExport
    public boolean enabled() {
        Node self = scene.engine().sceneTree().getNode(selfId);
        String uuid = playerState.resolveOwnerUuidOrSinglePlayer(self);
        return uuid != null && playerState.isCursorModeEnabled(uuid);
    }

    @HostAccess.Export
    @LuauExport
    public boolean visible() {
        Node self = scene.engine().sceneTree().getNode(selfId);
        String uuid = playerState.resolveOwnerUuidOrSinglePlayer(self);
        return uuid == null || playerState.isOsCursorVisible(uuid);
    }

    @HostAccess.Export
    @LuauExport
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
