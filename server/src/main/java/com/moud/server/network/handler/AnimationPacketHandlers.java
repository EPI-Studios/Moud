package com.moud.server.network.handler;

import com.moud.network.MoudPackets.*;
import com.moud.server.editor.AnimationManager;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.permissions.PermissionManager;
import com.moud.server.permissions.ServerPermission;
import net.minestom.server.entity.Player;

import java.util.List;

public final class AnimationPacketHandlers implements PacketHandlerGroup {
    private final ServerNetworkManager networkManager;

    public AnimationPacketHandlers(ServerNetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    @Override
    public void register(PacketRegistry registry) {
        registry.register(AnimationSavePacket.class, this::handleSave);
        registry.register(AnimationLoadPacket.class, this::handleLoad);
        registry.register(AnimationListPacket.class, this::handleList);
        registry.register(AnimationPlayPacket.class, this::handlePlay);
        registry.register(AnimationStopPacket.class, this::handleStop);
        registry.register(AnimationSeekPacket.class, this::handleSeek);
    }

    private boolean hasEditorPermission(Player player) {
        return PermissionManager.getInstance().has(player, ServerPermission.EDITOR);
    }

    private void handleSave(Player player, AnimationSavePacket packet) {
        if (!networkManager.isMoudClient(player) || !hasEditorPermission(player)) {
            return;
        }
        AnimationManager.getInstance().handleSave(packet);
    }

    private void handleLoad(Player player, AnimationLoadPacket packet) {
        if (!networkManager.isMoudClient(player) || !hasEditorPermission(player)) {
            networkManager.send(player, new AnimationLoadResponsePacket(
                    packet.projectPath(),
                    null,
                    false,
                    "Permission denied"
            ));
            return;
        }
        AnimationManager.getInstance().handleLoad(packet, networkManager, player);
    }

    private void handleList(Player player, AnimationListPacket packet) {
        if (!networkManager.isMoudClient(player) || !hasEditorPermission(player)) {
            networkManager.send(player, new AnimationListResponsePacket(List.of()));
            return;
        }
        AnimationManager.getInstance().handleList(networkManager, player);
    }

    private void handlePlay(Player player, AnimationPlayPacket packet) {
        if (!networkManager.isMoudClient(player) || !hasEditorPermission(player)) {
            return;
        }
        AnimationManager.getInstance().handlePlay(packet);
    }

    private void handleStop(Player player, AnimationStopPacket packet) {
        if (!networkManager.isMoudClient(player) || !hasEditorPermission(player)) {
            return;
        }
        AnimationManager.getInstance().handleStop(packet);
    }

    private void handleSeek(Player player, AnimationSeekPacket packet) {
        if (!networkManager.isMoudClient(player) || !hasEditorPermission(player)) {
            return;
        }
        AnimationManager.getInstance().handleSeek(packet);
    }
}
