package com.moud.server.network.handler;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets.*;
import com.moud.server.entity.DisplayManager;
import com.moud.server.entity.ModelManager;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.permissions.PermissionManager;
import com.moud.server.permissions.ServerPermission;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;

public final class RuntimeUpdatePacketHandlers implements PacketHandlerGroup {
    private final ServerNetworkManager networkManager;

    public RuntimeUpdatePacketHandlers(ServerNetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    @Override
    public void register(PacketRegistry registry) {
        registry.register(UpdateRuntimeModelPacket.class, this::handleRuntimeModelUpdate);
        registry.register(UpdateRuntimeDisplayPacket.class, this::handleRuntimeDisplayUpdate);
        registry.register(UpdatePlayerTransformPacket.class, this::handlePlayerTransformUpdate);
    }

    private boolean hasEditorPermission(Player player) {
        return PermissionManager.getInstance().has(player, ServerPermission.EDITOR);
    }

    private void handleRuntimeModelUpdate(Player player, UpdateRuntimeModelPacket packet) {
        if (!networkManager.isMoudClient(player) || !hasEditorPermission(player)) {
            return;
        }
        var proxy = ModelManager.getInstance().getById(packet.modelId());
        if (proxy == null) {
            return;
        }
        Vector3 position = packet.position() != null ? packet.position() : proxy.getPosition();
        Quaternion rotation = packet.rotation() != null ? packet.rotation() : proxy.getRotation();
        Vector3 scale = packet.scale() != null ? packet.scale() : proxy.getScale();
        proxy.setPosition(position);
        proxy.setRotation(rotation);
        proxy.setScale(scale);
    }

    private void handleRuntimeDisplayUpdate(Player player, UpdateRuntimeDisplayPacket packet) {
        if (!networkManager.isMoudClient(player) || !hasEditorPermission(player)) {
            return;
        }
        var proxy = DisplayManager.getInstance().getById(packet.displayId());
        if (proxy == null) {
            return;
        }
        if (packet.position() != null) {
            proxy.setPosition(packet.position());
        }
        if (packet.rotation() != null) {
            proxy.setRotation(packet.rotation());
        }
        if (packet.scale() != null) {
            proxy.setScale(packet.scale());
        }
    }

    private void handlePlayerTransformUpdate(Player player, UpdatePlayerTransformPacket packet) {
        if (!networkManager.isMoudClient(player) || !hasEditorPermission(player)) {
            return;
        }
        if (packet.playerId() == null || packet.position() == null) {
            return;
        }
        Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(packet.playerId());
        if (target == null) {
            return;
        }
        Vector3 position = packet.position();
        Pos current = target.getPosition();
        Pos destination = new Pos(position.x, position.y, position.z, current.yaw(), current.pitch());
        target.teleport(destination);
    }
}
