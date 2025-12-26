package com.moud.server.network.handler;

import com.moud.network.MoudPackets.*;
import com.moud.server.events.EventDispatcher;
import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.movement.ServerMovementHandler;
import com.moud.server.movement.PlayerMovementSimService;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.player.PlayerCameraManager;
import com.moud.server.player.PlayerCursorDirectionManager;
import com.moud.server.proxy.PlayerModelProxy;
import com.moud.server.ui.UIOverlayService;
import net.minestom.server.entity.Player;


public final class CorePacketHandlers implements PacketHandlerGroup {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(CorePacketHandlers.class);

    private final ServerNetworkManager networkManager;
    private final EventDispatcher eventDispatcher;

    public CorePacketHandlers(ServerNetworkManager networkManager, EventDispatcher eventDispatcher) {
        this.networkManager = networkManager;
        this.eventDispatcher = eventDispatcher;
    }

    @Override
    public void register(PacketRegistry registry) {
        registry.register(MovementStatePacket.class, this::handleMovementState);
        registry.register(PlayerInputPacket.class, this::handlePlayerInput);
        registry.register(ClientUpdateCameraPacket.class, this::handleCameraUpdate);
        registry.register(MouseMovementPacket.class, this::handleMouseMovement);
        registry.register(PlayerClickPacket.class, this::handlePlayerClick);
        registry.register(PlayerModelClickPacket.class, this::handlePlayerModelClick);
        registry.register(UIInteractionPacket.class, this::handleUiInteraction);
        registry.register(ClientUpdateValuePacket.class, this::handleSharedValueUpdate);
    }

    private void handleMovementState(Player player, MovementStatePacket packet) {
        if (!networkManager.isMoudClient(player)) {
            return;
        }
        ServerMovementHandler.getInstance().handleMovementState(player, packet);
        eventDispatcher.dispatchMovementEvent(player, packet);
    }

    private void handlePlayerInput(Player player, PlayerInputPacket packet) {
        if (!networkManager.isMoudClient(player)) {
            return;
        }
        PlayerMovementSimService.getInstance().handleInput(player, packet);
    }

    private void handleCameraUpdate(Player player, ClientUpdateCameraPacket packet) {
        if (!networkManager.isMoudClient(player)) {
            return;
        }
        PlayerCameraManager.getInstance().updateCameraDirection(player, packet.direction());
    }

    private void handleMouseMovement(Player player, MouseMovementPacket packet) {
        if (!networkManager.isMoudClient(player)) {
            return;
        }
        PlayerCursorDirectionManager.getInstance().updateFromMouseDelta(player, packet.deltaX(), packet.deltaY());
        eventDispatcher.dispatchMouseMoveEvent(player, packet.deltaX(), packet.deltaY());
    }

    private void handlePlayerClick(Player player, PlayerClickPacket packet) {
        if (!networkManager.isMoudClient(player)) {
            return;
        }
        eventDispatcher.dispatchPlayerClickEvent(player, packet.button());
    }

    private void handlePlayerModelClick(Player player, PlayerModelClickPacket packet) {
        if (!networkManager.isMoudClient(player)) {
            return;
        }
        PlayerModelProxy model = PlayerModelProxy.getById(packet.modelId());
        if (model != null) {
            model.triggerClick(player, packet.mouseX(), packet.mouseY(), packet.button());
        }
    }

    private void handleUiInteraction(Player player, UIInteractionPacket packet) {
        if (!networkManager.isMoudClient(player)) {
            return;
        }
        UIOverlayService.getInstance().handleInteraction(player, packet);
    }

    private void handleSharedValueUpdate(Player player, ClientUpdateValuePacket packet) {
        if (!networkManager.isMoudClient(player)) {
            return;
        }
        LogContext context = LogContext.builder()
                .put("player", player.getUsername())
                .put("store", packet.storeName())
                .put("key", packet.key())
                .build();
        LOGGER.debug(context, "Received shared value update: {}.{} = {}",
                packet.storeName(), packet.key(), packet.value());
    }
}
