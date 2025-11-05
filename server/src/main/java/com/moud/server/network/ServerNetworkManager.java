package com.moud.server.network;

import com.moud.network.MoudPackets;
import com.moud.network.MoudPackets.*;
import com.moud.server.client.ClientScriptManager;
import com.moud.server.cursor.CursorService;
import com.moud.server.entity.DisplayManager;
import com.moud.server.entity.ModelManager;
import com.moud.server.events.EventDispatcher;
import com.moud.server.lighting.ServerLightingManager;
import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.movement.ServerMovementHandler;
import com.moud.server.network.diagnostics.NetworkProbe;
import com.moud.server.player.PlayerCameraManager;
import com.moud.server.proxy.MediaDisplayProxy;
import com.moud.server.proxy.PlayerModelProxy;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import com.moud.server.player.PlayerCursorDirectionManager;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ServerNetworkManager {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(
            ServerNetworkManager.class,
            LogContext.builder().put("subsystem", "network").build()
    );
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
        ServerPacketWrapper.registerHandler(ClientReadyPacket.class, this::handleClientReady);

    }

    private void handleMovementState(Object player, MovementStatePacket packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;

        ServerMovementHandler.getInstance().handleMovementState(minestomPlayer, packet);
        eventDispatcher.dispatchMovementEvent(minestomPlayer, packet);
    }

    private void handleClientReady(Object player, ClientReadyPacket packet) {
        Player minestomPlayer = (Player) player;
        LogContext context = playerContext(minestomPlayer);
        LOGGER.info(context, "Client {} is ready, syncing lights", minestomPlayer.getUsername());
        ServerLightingManager.getInstance().syncLightsToPlayer(minestomPlayer);
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
                LOGGER.error(playerContext(player), "Failed to unwrap Moud payload from client {}", e, player.getUsername());
            }
        }
    }

    public <T> boolean send(Player player, T packet) {
        if (!isMoudClient(player)) {
            LOGGER.trace(playerContext(player), "Skipping packet send to non-Moud client {}", player.getUsername());
            return false;
        }

        long start = System.nanoTime();
        ServerPacketWrapper.PacketEnvelope envelope;
        try {
            envelope = ServerPacketWrapper.wrapPacket(packet);
        } catch (Exception e) {
            LogContext failureContext = playerContext(player).merge(LogContext.builder()
                    .put("packet", packet.getClass().getSimpleName())
                    .put("phase", "encode")
                    .build());
            LOGGER.error(failureContext, "Failed to encode packet {}", e, packet.getClass().getSimpleName());
            NetworkProbe.getInstance().recordOutbound(player, packet.getClass().getSimpleName(), "encode-error", 0, 0, System.nanoTime() - start, false);
            return false;
        }

        boolean success = false;
        try {
            player.sendPacket(envelope.packet());
            success = true;
            return true;
        } catch (Exception e) {
            LogContext failureContext = playerContext(player).merge(LogContext.builder()
                    .put("packet", packet.getClass().getSimpleName())
                    .build());
            LOGGER.error(failureContext, "Failed to send packet {} to {}", e, packet.getClass().getSimpleName(), player.getUsername());
            return false;
        } finally {
            NetworkProbe.getInstance().recordOutbound(
                    player,
                    envelope.packetType(),
                    envelope.innerChannel(),
                    envelope.payloadBytes(),
                    envelope.totalBytes(),
                    System.nanoTime() - start,
                    success
            );
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

    public <T> int broadcastExcept(T packet, Player exceptPlayer) {
        int sentCount = 0;
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (player.getUuid().equals(exceptPlayer.getUuid())) {
                continue;
            }
            if (send(player, packet)) {
                sentCount++;
            }
        }
        return sentCount;
    }

    public <T> int sendToPlayers(T packet, Collection<Player> players) {
        int sentCount = 0;
        for (Player player : players) {
            if (send(player, packet)) {
                sentCount++;
            }
        }
        return sentCount;
    }

    public <T> int broadcastInRange(T packet, net.minestom.server.coordinate.Pos position, double range) {
        int sentCount = 0;
        double rangeSq = range * range;
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (player.getPosition().distanceSquared(position) <= rangeSq) {
                if (send(player, packet)) {
                    sentCount++;
                }
            }
        }
        return sentCount;
    }

    private void handleHelloPacket(Object player, HelloPacket packet) {
        Player minestomPlayer = (Player) player;
        int clientVersion = packet.protocolVersion();
        LogContext baseContext = playerContext(minestomPlayer).merge(LogContext.builder()
                .put("client_protocol", clientVersion)
                .build());
        if (clientVersion != SUPPORTED_PROTOCOL_VERSION) {
            LogContext mismatchContext = baseContext.merge(LogContext.builder()
                    .put("expected_protocol", SUPPORTED_PROTOCOL_VERSION)
                    .build());
            LOGGER.warn(mismatchContext, "Player {} has unsupported Moud protocol version: {} (expected: {})",
                    minestomPlayer.getUsername(), clientVersion, SUPPORTED_PROTOCOL_VERSION);
            minestomPlayer.kick("Unsupported Moud client version");
            return;
        }
        ClientSession previousSession = moudClients.put(minestomPlayer.getUuid(), new ClientSession(Instant.now()));

        if (previousSession != null) {
            LogContext resumedContext = baseContext.merge(LogContext.builder()
                    .put("previous_handshake", previousSession.handshakeTime().toString())
                    .build());
            LOGGER.debug(resumedContext, "Player {} re-registered Moud session (previous handshake: {})",
                    minestomPlayer.getUsername(), previousSession.handshakeTime());
        } else {
            LOGGER.info(baseContext, "Player {} connected with Moud client (protocol: {})", minestomPlayer.getUsername(), clientVersion);
        }

        eventDispatcher.dispatchMoudReady(minestomPlayer);

        sendClientScripts(minestomPlayer);

        Collection<PlayerModelProxy> playerModels = PlayerModelProxy.getAllModels();
        if (!playerModels.isEmpty()) {
            playerModels.forEach(model -> {
                send(minestomPlayer, new PlayerModelCreatePacket(model.getModelId(), model.getPosition(), model.getSkinUrl()));
                send(minestomPlayer, new PlayerModelUpdatePacket(model.getModelId(), model.getPosition(), model.getYaw(), model.getPitch()));

                String skinUrl = model.getSkinUrl();
                if (skinUrl != null && !skinUrl.isEmpty()) {
                    send(minestomPlayer, new PlayerModelSkinPacket(model.getModelId(), skinUrl));
                }

                String currentAnimation = model.getCurrentAnimation();
                if (currentAnimation != null && !currentAnimation.isEmpty()) {
                    send(minestomPlayer, new S2C_PlayModelAnimationPacket(model.getModelId(), currentAnimation));
                }
            });

            LogContext playerModelContext = baseContext.merge(LogContext.builder()
                    .put("synced_player_models", playerModels.size())
                    .build());
            LOGGER.info(playerModelContext, "Synced {} existing player models to {}", playerModels.size(), minestomPlayer.getUsername());
        }

        ModelManager.getInstance().getAllModels().forEach(model -> {
            MoudPackets.S2C_CreateModelPacket createPacket = new MoudPackets.S2C_CreateModelPacket(
                    model.getId(),
                    model.getModelPath(),
                    model.getPosition(),
                    model.getRotation(),
                    model.getScale(),
                    model.getCollisionWidth(),
                    model.getCollisionHeight(),
                    model.getCollisionDepth(),
                    model.getTexture()
            );
            send(minestomPlayer, createPacket);
        });
        int modelCount = ModelManager.getInstance().getAllModels().size();
        if (modelCount > 0) {
            LogContext modelSyncContext = baseContext.merge(LogContext.builder()
                    .put("synced_models", modelCount)
                    .build());
            LOGGER.info(modelSyncContext, "Synced {} existing models to {}", modelCount, minestomPlayer.getUsername());
        }

        Collection<MediaDisplayProxy> displays = DisplayManager.getInstance().getAllDisplays();
        if (!displays.isEmpty()) {
            for (MediaDisplayProxy display : displays) {
                send(minestomPlayer, display.snapshot());
            }
            LogContext displaySyncContext = baseContext.merge(LogContext.builder()
                    .put("synced_displays", displays.size())
                    .build());
            LOGGER.info(displaySyncContext, "Synced {} existing displays to {}", displays.size(), minestomPlayer.getUsername());
        }


        MinecraftServer.getSchedulerManager().buildTask(() -> {
            LogContext lightingContext = baseContext.merge(LogContext.builder()
                    .put("phase", "lighting-sync")
                    .put("delay_ms", java.time.Duration.ofSeconds(1).toMillis())
                    .build());
            LOGGER.info(lightingContext, "Syncing lights to player {} after initialization delay", minestomPlayer.getUsername());
            ServerLightingManager.getInstance().syncLightsToPlayer(minestomPlayer);
        }).delay(java.time.Duration.ofSeconds(1)).schedule();
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
        LogContext context = playerContext(minestomPlayer).merge(LogContext.builder()
                .put("store", packet.storeName())
                .put("key", packet.key())
                .build());
        LOGGER.debug(context, "Received shared value update from {}: {}.{} = {}",
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
        LOGGER.debug(playerContext(player), "Player {} disconnected, cleaned up client state", player.getUsername());
    }

    private void sendClientScripts(Player player) {
        LogContext baseContext = playerContext(player);
        if (!clientScriptManager.hasClientScripts()) {
            LOGGER.info(baseContext, "No client scripts available to send to {}", player.getUsername());
            return;
        }
        try {
            byte[] scriptData = clientScriptManager.getCompiledScripts();
            String hash = clientScriptManager.getScriptsHash();
            ClientSession session = moudClients.get(player.getUuid());

            if (session != null && hash != null && hash.equals(session.getResourcesHash())) {
                LogContext cachedContext = baseContext.merge(LogContext.builder()
                        .put("hash", hash)
                        .put("payload_bytes", 0)
                        .build());
                LOGGER.info(cachedContext, "Client {} already has resources hash {}, sending cache hint only", player.getUsername(), hash);
                if (!send(player, new SyncClientScriptsPacket(hash, null))) {
                    LOGGER.error(String.valueOf(cachedContext), "Failed to send cache hint payload to {}", player.getUsername());
                }
                return;
            }

            LogContext payloadContext = baseContext.merge(LogContext.builder()
                    .put("hash", hash)
                    .put("payload_bytes", scriptData.length)
                    .build());
            LOGGER.info(payloadContext, "Sending client scripts to {}: hash={}, size={} bytes", player.getUsername(), hash, scriptData.length);
            if (!send(player, new SyncClientScriptsPacket(hash, scriptData))) {
                LOGGER.error(String.valueOf(payloadContext), "Failed to send client scripts payload to {}", player.getUsername());
                return;
            }

            if (session != null) {
                session.setResourcesHash(hash);
            }
        } catch (Exception e) {
            LOGGER.error(baseContext, "Failed to send client scripts to {}", e, player.getUsername());
        }
    }

    public void sendScriptEvent(Player player, String eventName, String eventData) {
        if (!isMoudClient(player)) {
            LOGGER.warn(playerContext(player), "Attempted to send script event to non-Moud client: {}", player.getUsername());
            return;
        }
        LogContext context = playerContext(player).merge(LogContext.builder()
                .put("event", eventName)
                .build());
        if (!send(player, new ClientboundScriptEventPacket(eventName, eventData))) {
            LOGGER.warn(context, "Script event {} dropped for {}", eventName, player.getUsername());
        }
    }

    public boolean isMoudClient(Player player) {
        return moudClients.containsKey(player.getUuid());
    }

    private LogContext playerContext(Player player) {
        return LogContext.builder()
                .put("player", player.getUsername())
                .put("player_uuid", player.getUuid())
                .build();
    }

    private static class ClientSession {
        private final Instant handshakeTime;
        private String resourcesHash;

        private ClientSession(Instant handshakeTime) {
            this.handshakeTime = handshakeTime;
        }

        public Instant handshakeTime() {
            return handshakeTime;
        }

        public String getResourcesHash() {
            return resourcesHash;
        }

        public void setResourcesHash(String resourcesHash) {
            this.resourcesHash = resourcesHash;
        }
    }
}
