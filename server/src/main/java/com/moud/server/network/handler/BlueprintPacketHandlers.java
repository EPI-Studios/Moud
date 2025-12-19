package com.moud.server.network.handler;

import com.moud.network.MoudPackets.*;
import com.moud.server.editor.BlueprintStorage;
import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.permissions.PermissionManager;
import com.moud.server.permissions.ServerPermission;
import net.minestom.server.entity.Player;

import java.io.IOException;

public final class BlueprintPacketHandlers implements PacketHandlerGroup {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(BlueprintPacketHandlers.class);

    private final ServerNetworkManager networkManager;
    private final BlueprintStorage blueprintStorage;

    public BlueprintPacketHandlers(ServerNetworkManager networkManager, BlueprintStorage blueprintStorage) {
        this.networkManager = networkManager;
        this.blueprintStorage = blueprintStorage;
    }

    @Override
    public void register(PacketRegistry registry) {
        registry.register(SaveBlueprintPacket.class, this::handleSave);
        registry.register(RequestBlueprintPacket.class, this::handleRequest);
    }

    private boolean hasEditorPermission(Player player) {
        return PermissionManager.getInstance().has(player, ServerPermission.EDITOR);
    }

    private void handleSave(Player player, SaveBlueprintPacket packet) {
        String name = packet.name() == null ? "" : packet.name().trim();
        if (!networkManager.isMoudClient(player) || !hasEditorPermission(player)) {
            networkManager.send(player, new BlueprintSaveAckPacket(name, false, "Permission denied"));
            return;
        }
        boolean success = false;
        String message;

        if (name.isEmpty() || packet.data() == null || packet.data().length == 0) {
            message = "Invalid blueprint payload";
        } else {
            try {
                blueprintStorage.save(name, packet.data());
                success = true;
                message = "saved";
            } catch (IOException e) {
                message = e.getMessage();
                LOGGER.error(LogContext.builder()
                        .put("player", player.getUsername())
                        .put("blueprint", name)
                        .build(), "Failed to save blueprint", e);
            }
        }
        networkManager.send(player, new BlueprintSaveAckPacket(name, success, message == null ? "" : message));
    }

    private void handleRequest(Player player, RequestBlueprintPacket packet) {
        String name = packet.name() == null ? "" : packet.name().trim();
        if (!networkManager.isMoudClient(player) || !hasEditorPermission(player)) {
            networkManager.send(player, new BlueprintDataPacket(name, null, false, "Permission denied"));
            return;
        }
        byte[] data = null;
        boolean success = false;
        String message;

        if (name.isEmpty()) {
            message = "Invalid name";
        } else {
            try {
                if (!blueprintStorage.exists(name)) {
                    message = "Not found";
                } else {
                    data = blueprintStorage.load(name);
                    success = true;
                    message = "";
                }
            } catch (IOException e) {
                message = e.getMessage();
                LOGGER.error(LogContext.builder()
                        .put("player", player.getUsername())
                        .put("blueprint", name)
                        .build(), "Failed to load blueprint", e);
            }
        }
        networkManager.send(player, new BlueprintDataPacket(name, data, success, message == null ? "" : message));
    }
}
