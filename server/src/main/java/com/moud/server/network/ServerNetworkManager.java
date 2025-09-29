package com.moud.server.network;

import com.moud.network.MoudPackets.*;
import com.moud.server.client.ClientScriptManager;
import com.moud.server.cursor.CursorService;
import com.moud.server.events.EventDispatcher;
import com.moud.server.movement.ServerMovementHandler;
import com.moud.server.player.PlayerCameraManager;
import com.moud.server.proxy.PlayerModelProxy;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import com.moud.server.player.PlayerCursorDirectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ServerNetworkManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerNetworkManager.class);
    private static final int SUPPORTED_PROTOCOL_VERSION = 1;

    private final EventDispatcher eventDispatcher;
    private final ClientScriptManager clientScriptManager;
    private final ConcurrentMap<UUID, ClientSession> moudClients = new ConcurrentHashMap<>();
    private static ServerNetworkManager instance;

    public ServerNetworkManager(EventDispatcher eventDispatcher, ClientScriptManager clientScriptManager) {
        this.eventDispatcher = eventDispatcher;
        this.clientScriptManager = clientScriptManager;
        instance = this;
    }

    public static ServerNetworkManager getInstance() {
        return instance;
    }

    public void initialize() {
        registerMinestomListeners();
        registerPacketHandlers();
        LOGGER.info("Server network manager initialized");
    }

    private void registerMinestomListeners() {
        MinecraftServer.getGlobalEventHandler()
                .addListener(PlayerPluginMessageEvent.class, this::onPluginMessage)
                .addListener(PlayerDisconnectEvent.class, this::onPlayerDisconnect)
                .addListener(PlayerSpawnEvent.class, this::onPlayerJoin);
    }

    private void registerPacketHandlers() {
        ServerPacketWrapper.registerHandler(HelloPacket.class, this::handleHelloPacket);
        ServerPacketWrapper.registerHandler(ServerboundScriptEventPacket.class, this::handleScriptEvent);
        ServerPacketWrapper.registerHandler(ClientUpdateCameraPacket.class, this::handleCameraUpdate);
        ServerPacketWrapper.registerHandler(MouseMovementPacket.class, this::handleMouseMovement);
        ServerPacketWrapper.registerHandler(PlayerClickPacket.class, this::handlePlayerClick);
        ServerPacketWrapper.registerHandler(PlayerModelClickPacket.class, this::handlePlayerModelClick);
        ServerPacketWrapper.registerHandler(ClientUpdateValuePacket.class, this::handleSharedValueUpdate);
        ServerPacketWrapper.registerHandler(MovementStatePacket.class, this::handleMovementState);
    }

    private void handleMovementState(Object player, MovementStatePacket packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;

        ServerMovementHandler.getInstance().handleMovementState(minestomPlayer, packet);
        eventDispatcher.dispatchMovementEvent(minestomPlayer, packet);
    }
    private void onPluginMessage(PlayerPluginMessageEvent event) {
        String outerChannel = event.getIdentifier();
        Player player = event.getPlayer();

        if ("moud:wrapper".equals(outerChannel)) {
            try {
                MinestomByteBuffer buffer = new MinestomByteBuffer(event.getMessage());
                String innerChannel = buffer.readString();
                byte[] innerData = buffer.readByteArray();
                ServerPacketWrapper.handleIncoming(innerChannel, innerData, player);
            } catch (Exception e) {
                LOGGER.error("Failed to unwrap Moud payload from client {}", player.getUsername(), e);
            }
        }
    }

    public <T> boolean send(Player player, T packet) {
        if (!isMoudClient(player)) {
            LOGGER.trace("Skipping packet send to non-Moud client {}", player.getUsername());
            return false;
        }

        try {
            player.sendPacket(ServerPacketWrapper.createPacket(packet));
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to send packet {} to {}", packet.getClass().getSimpleName(), player.getUsername(), e);
            return false;
        }
    }

    public <T> int broadcast(T packet) {
        int sentCount = 0;
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (send(player, packet)) {
                sentCount++;
            }
        }

        if (sentCount == 0) {
            LOGGER.trace("Broadcast {} skipped - no active Moud clients", packet.getClass().getSimpleName());
        }

        return sentCount;
    }

    private void handleHelloPacket(Object player, HelloPacket packet) {
        Player minestomPlayer = (Player) player;
        int clientVersion = packet.protocolVersion();
        if (clientVersion != SUPPORTED_PROTOCOL_VERSION) {
            LOGGER.warn("Player {} has unsupported Moud protocol version: {} (expected: {})",
                    minestomPlayer.getUsername(), clientVersion, SUPPORTED_PROTOCOL_VERSION);
            minestomPlayer.kick("Unsupported Moud client version");
            return;
        }
        ClientSession previousSession = moudClients.put(minestomPlayer.getUuid(), new ClientSession(Instant.now()));

        if (previousSession != null) {
            LOGGER.debug("Player {} re-registered Moud session (previous handshake: {})",
                    minestomPlayer.getUsername(), previousSession.handshakeTime());
        } else {
            LOGGER.info("Player {} connected with Moud client (protocol: {})", minestomPlayer.getUsername(), clientVersion);
        }
        sendClientScripts(minestomPlayer);
    }

    private void handleScriptEvent(Object player, ServerboundScriptEventPacket packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;
        eventDispatcher.dispatchScriptEvent(packet.eventName(), packet.eventData(), minestomPlayer);
    }

    private void handleCameraUpdate(Object player, ClientUpdateCameraPacket packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;
        PlayerCameraManager.getInstance().updateCameraDirection(minestomPlayer, packet.direction());
    }

    private void handlePlayerModelClick(Object player, PlayerModelClickPacket packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;
        PlayerModelProxy model = PlayerModelProxy.getById(packet.modelId());
        if (model != null) {
            model.triggerClick(minestomPlayer, packet.mouseX(), packet.mouseY(), packet.button());
        }
    }

    private void handleMouseMovement(Object player, MouseMovementPacket packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;

        PlayerCursorDirectionManager.getInstance().updateFromMouseDelta(minestomPlayer, packet.deltaX(), packet.deltaY());

        eventDispatcher.dispatchMouseMoveEvent(minestomPlayer, packet.deltaX(), packet.deltaY());
    }

    private void handlePlayerClick(Object player, PlayerClickPacket packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;
        eventDispatcher.dispatchPlayerClickEvent(minestomPlayer, packet.button());
    }

    private void handleSharedValueUpdate(Object player, ClientUpdateValuePacket packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;
        LOGGER.debug("Received shared value update from {}: {}.{} = {}",
                minestomPlayer.getUsername(), packet.storeName(), packet.key(), packet.value());
    }

    private void onPlayerJoin(PlayerSpawnEvent event) {
        if (event.isFirstSpawn()) {
            Player player = event.getPlayer();
            PlayerCursorDirectionManager.getInstance().onPlayerJoin(player);
            CursorService.getInstance().onPlayerJoin(player);
        }
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        Player player = event.getPlayer();
        moudClients.remove(player.getUuid());
        PlayerCameraManager.getInstance().onPlayerDisconnect(player);
        PlayerCursorDirectionManager.getInstance().onPlayerDisconnect(player);
        CursorService.getInstance().onPlayerQuit(player);
        LOGGER.debug("Player {} disconnected, cleaned up client state", player.getUsername());
    }

    private void sendClientScripts(Player player) {
        if (!clientScriptManager.hasClientScripts()) {
            LOGGER.info("No client scripts available to send to {}", player.getUsername());
            return;
        }
        try {
            byte[] scriptData = clientScriptManager.getCompiledScripts();
            String hash = clientScriptManager.getScriptsHash();
            LOGGER.info("Sending client scripts to {}: hash={}, size={} bytes", player.getUsername(), hash, scriptData.length);
            if (!send(player, new SyncClientScriptsPacket(hash, scriptData))) {
                LOGGER.error("Failed to send client scripts payload to {}", player.getUsername());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to send client scripts to {}", player.getUsername(), e);
        }
    }

    public void sendScriptEvent(Player player, String eventName, String eventData) {
        if (!isMoudClient(player)) {
            LOGGER.warn("Attempted to send script event to non-Moud client: {}", player.getUsername());
            return;
        }
        if (!send(player, new ClientboundScriptEventPacket(eventName, eventData))) {
            LOGGER.warn("Script event {} dropped for {}", eventName, player.getUsername());
        }
    }

    public boolean isMoudClient(Player player) {
        return moudClients.containsKey(player.getUuid());
    }

    private record ClientSession(Instant handshakeTime) {
    }
}
