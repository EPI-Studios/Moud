package com.moud.server.network;

import com.moud.network.MoudPackets;
import com.moud.network.MoudPackets.*;
import com.moud.server.client.ClientScriptManager;
import com.moud.server.cursor.CursorService;
import com.moud.server.entity.DisplayManager;
import com.moud.server.entity.ModelManager;
import com.moud.server.editor.SceneManager;
import com.moud.server.editor.BlueprintStorage;
import com.moud.server.events.EventDispatcher;
import com.moud.server.lighting.ServerLightingManager;
import com.moud.server.camera.CameraRegistry;
import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.movement.PlayerMovementSimService;
import com.moud.server.network.diagnostics.NetworkProbe;
import com.moud.server.network.handler.AnimationPacketHandlers;
import com.moud.server.network.handler.BlueprintPacketHandlers;
import com.moud.server.network.handler.ClientReadyPacketHandler;
import com.moud.server.network.handler.CorePacketHandlers;
import com.moud.server.network.handler.DevPacketHandlers;
import com.moud.server.network.handler.PacketRegistry;
import com.moud.server.network.handler.RuntimeUpdatePacketHandlers;
import com.moud.server.network.handler.ScenePacketHandlers;
import com.moud.server.network.handler.VoicePacketHandlers;
import com.moud.server.player.PlayerCameraManager;
import com.moud.server.player.PlayerCursorDirectionManager;
import com.moud.server.plugin.PluginEventBus;
import com.moud.server.proxy.MediaDisplayProxy;
import com.moud.server.proxy.PlayerModelProxy;
import com.moud.server.network.ResourcePackServer.ResourcePackInfo;
import com.moud.server.permissions.PermissionManager;
import com.moud.server.permissions.ServerPermission;
import com.moud.server.ui.UIOverlayService;
import com.moud.network.limits.NetworkLimits;
import com.moud.network.protocol.MoudProtocol;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerResourcePackStatusEvent;

import java.time.Duration;
import java.time.Instant;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.kyori.adventure.resource.ResourcePackStatus;
import net.minestom.server.network.packet.server.common.ResourcePackPushPacket;

public final class ServerNetworkManager {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(
            ServerNetworkManager.class,
            LogContext.builder().put("subsystem", "network").build()
    );
    private static final String HELLO_PACKET_ID = "moud:hello";
    private static final int SYNC_PACKETS_PER_TICK = Integer.getInteger("moud.network.syncPacketsPerTick", 20);
    private static final int BROADCAST_PACKETS_PER_TICK = Integer.getInteger("moud.network.broadcastPacketsPerTick", 15);

    private final EventDispatcher eventDispatcher;
    private final ClientScriptManager clientScriptManager;
    private final ResourcePackService resourcePackService;
    private final ConcurrentMap<UUID, ClientSession> moudClients = new ConcurrentHashMap<>();
    private final Set<UUID> resourcePackRequested = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, Integer> resourcePackAttempts = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, java.util.Queue<Object>> broadcastQueues = new ConcurrentHashMap<>();
    private volatile boolean broadcastFlushScheduled = false;
    private final BlueprintStorage blueprintStorage;
    private static ServerNetworkManager instance;

    public ServerNetworkManager(
            EventDispatcher eventDispatcher,
            ClientScriptManager clientScriptManager,
            ResourcePackService resourcePackService
    ) {
        this.eventDispatcher = eventDispatcher;
        this.clientScriptManager = clientScriptManager;
        this.resourcePackService = Objects.requireNonNull(resourcePackService, "resourcePackService");
        this.blueprintStorage = new BlueprintStorage(SceneManager.getInstance().getProjectRoot());
        instance = this;
    }

    public static ServerNetworkManager getInstance() {
        return instance;
    }

    public void initialize() {
        registerMinestomListeners();
        registerPacketHandlers();
        com.moud.server.primitives.PrimitiveServiceImpl.getInstance().setPacketSender(new com.moud.server.primitives.PrimitiveServiceImpl.PrimitivePacketSender() {
            @Override
            public void broadcastToAll(Object packet) {
                broadcast(packet);
            }

            @Override
            public void sendToPlayer(Player player, Object packet) {
                send(player, packet);
            }
        });
        com.moud.server.ik.IKServiceImpl.getInstance().setPacketSender(new com.moud.server.ik.IKServiceImpl.IKPacketSender() {
            @Override
            public void broadcastToAll(Object packet) {
                broadcast(packet);
            }

            @Override
            public void sendToPlayer(Player player, Object packet) {
                send(player, packet);
            }
        });
        LOGGER.info("Server network manager initialized");
    }

    private void registerMinestomListeners() {
        MinecraftServer.getGlobalEventHandler()
                .addListener(PlayerPluginMessageEvent.class, this::onPluginMessage)
                .addListener(PlayerDisconnectEvent.class, this::onPlayerDisconnect)
                .addListener(PlayerSpawnEvent.class, this::onPlayerJoin)
                .addListener(PlayerResourcePackStatusEvent.class, this::onResourcePackStatus);
    }

    private void registerPacketHandlers() {
        PacketRegistry registry = new PacketRegistry();
        registry.register(HelloPacket.class, this::handleHelloPacket);
        registry.registerGroup(new CorePacketHandlers(this, eventDispatcher));
        registry.registerGroup(new ScenePacketHandlers(this));
        registry.registerGroup(new AnimationPacketHandlers(this));
        registry.registerGroup(new RuntimeUpdatePacketHandlers(this));
        registry.registerGroup(new BlueprintPacketHandlers(this, blueprintStorage));
        registry.registerGroup(new VoicePacketHandlers(this, eventDispatcher));
        registry.registerGroup(new ClientReadyPacketHandler(this));
        registry.registerGroup(new DevPacketHandlers());
    }
    private void onPluginMessage(PlayerPluginMessageEvent event) {
        String outerChannel = event.getIdentifier();
        Player player = event.getPlayer();

        if ("moud:wrapper".equals(outerChannel)) {
            byte[] message = event.getMessage();
            if (message == null) {
                return;
            }
            if (message.length > NetworkLimits.MAX_WRAPPER_BYTES) {
                LOGGER.warn(
                        playerContext(player),
                        "Dropping oversized wrapper payload from {}: {} bytes > {}",
                        player.getUsername(),
                        message.length,
                        NetworkLimits.MAX_WRAPPER_BYTES
                );
                player.kick(Component.text("Packet too large."));
                return;
            }

            try {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(message);
                String innerChannel = readVarIntString(buffer, NetworkLimits.MAX_CHANNEL_BYTES);
                int payloadLength = readVarInt(buffer);
                if (payloadLength < 0 || payloadLength > NetworkLimits.MAX_PACKET_BYTES) {
                    LOGGER.warn(
                            playerContext(player),
                            "Dropping oversized packet payload from {}: {} bytes > {} (channel={})",
                            player.getUsername(),
                            payloadLength,
                            NetworkLimits.MAX_PACKET_BYTES,
                            innerChannel
                    );
                    player.kick(Component.text("Packet too large."));
                    return;
                }
                if (payloadLength > buffer.remaining()) {
                    throw new BufferUnderflowException();
                }
                if (!isMoudClient(player) && !HELLO_PACKET_ID.equals(innerChannel)) {
                    return;
                }
                byte[] innerData = new byte[payloadLength];
                buffer.get(innerData);
                ServerPacketWrapper.handleIncoming(innerChannel, innerData, player);
            } catch (Exception e) {
                LOGGER.error(playerContext(player), "Failed to unwrap Moud payload from client {}", e, player.getUsername());
            }
        }
    }

    private static int readVarInt(java.nio.ByteBuffer buffer) {
        int numRead = 0;
        int result = 0;

        byte read;
        do {
            if (!buffer.hasRemaining()) {
                throw new BufferUnderflowException();
            }

            read = buffer.get();
            int value = (read & 0b0111_1111);
            result |= (value << (7 * numRead));

            numRead++;
            if (numRead > 5) {
                throw new IllegalArgumentException("VarInt is too big");
            }
        } while ((read & 0b1000_0000) != 0);

        return result;
    }

    private static String readVarIntString(java.nio.ByteBuffer buffer, int maxBytes) {
        int byteLength = readVarInt(buffer);
        if (byteLength < 0 || byteLength > maxBytes) {
            throw new IllegalArgumentException("String length " + byteLength + " exceeds limit " + maxBytes);
        }
        if (byteLength > buffer.remaining()) {
            throw new BufferUnderflowException();
        }
        byte[] bytes = new byte[byteLength];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
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


        NetworkProbe.getInstance().recordPacketDetail(
                "OUT",
                packet.getClass().getSimpleName(),
                envelope.totalBytes(),
                packet
        );

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
                    packet.getClass().getSimpleName(),
                    packet.getClass().getSimpleName(),
                    envelope.payloadBytes(),
                    envelope.totalBytes(),
                    System.nanoTime() - start,
                    success
            );
        }
    }

    public <T> int broadcast(T packet) {
        return broadcast(packet, true);
    }

    public <T> int broadcastImmediate(T packet) {
        return broadcast(packet, false);
    }

    private <T> int broadcast(T packet, boolean throttle) {
        int queuedCount = 0;
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (!isMoudClient(player)) {
                continue;
            }
            if (throttle) {
                java.util.Queue<Object> queue = broadcastQueues.computeIfAbsent(
                        player.getUuid(), k -> new java.util.concurrent.ConcurrentLinkedQueue<>());
                queue.offer(packet);
                queuedCount++;
            } else {
                if (send(player, packet)) {
                    queuedCount++;
                }
            }
        }

        if (throttle && queuedCount > 0) {
            scheduleBroadcastFlush();
        }

        if (queuedCount == 0) {
            LOGGER.trace("Broadcast {} skipped - no active Moud clients", packet.getClass().getSimpleName());
        }

        return queuedCount;
    }

    private void scheduleBroadcastFlush() {
        if (broadcastFlushScheduled) {
            return;
        }
        broadcastFlushScheduled = true;
        MinecraftServer.getSchedulerManager().scheduleNextTick(this::flushBroadcastQueues);
    }

    private void flushBroadcastQueues() {
        broadcastFlushScheduled = false;
        boolean hasMore = false;
        int perTick = Math.max(1, BROADCAST_PACKETS_PER_TICK);

        for (var entry : broadcastQueues.entrySet()) {
            UUID playerId = entry.getKey();
            java.util.Queue<Object> queue = entry.getValue();
            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerId);
            if (player == null || !player.isOnline()) {
                queue.clear();
                continue;
            }

            int sent = 0;
            while (sent < perTick && !queue.isEmpty()) {
                Object packet = queue.poll();
                if (packet != null) {
                    send(player, packet);
                    sent++;
                }
            }
            if (!queue.isEmpty()) {
                hasMore = true;
            }
        }

        if (hasMore) {
            scheduleBroadcastFlush();
        }
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

    public boolean broadcastMeshDataIfNeeded(String modelPath, MoudPackets.CollisionMode collisionMode,
                                              byte[] compressedVertices, byte[] compressedIndices) {
        if (modelPath == null || modelPath.isBlank()) {
            return false;
        }
        if (collisionMode == null || collisionMode == MoudPackets.CollisionMode.BOX) {
            return false;
        }
        if (compressedVertices == null || compressedVertices.length == 0 ||
            compressedIndices == null || compressedIndices.length == 0) {
            return false;
        }

        MoudPackets.S2C_ModelMeshDataPacket meshPacket = new MoudPackets.S2C_ModelMeshDataPacket(
                modelPath, collisionMode, compressedVertices, compressedIndices);

        boolean sentAny = false;
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            ClientSession session = moudClients.get(player.getUuid());
            if (session == null) {
                continue;
            }
            if (session.markMeshSent(modelPath)) {
                send(player, meshPacket);
                sentAny = true;
            }
        }
        return sentAny;
    }

    public boolean sendMeshDataIfNeeded(Player player, String modelPath, MoudPackets.CollisionMode collisionMode,
                                         byte[] compressedVertices, byte[] compressedIndices) {
        if (player == null || modelPath == null || modelPath.isBlank()) {
            return false;
        }
        if (collisionMode == null || collisionMode == MoudPackets.CollisionMode.BOX) {
            return false;
        }
        if (compressedVertices == null || compressedVertices.length == 0 ||
            compressedIndices == null || compressedIndices.length == 0) {
            return false;
        }

        ClientSession session = moudClients.get(player.getUuid());
        if (session == null) {
            return false;
        }
        if (!session.markMeshSent(modelPath)) {
            return false;
        }

        MoudPackets.S2C_ModelMeshDataPacket meshPacket = new MoudPackets.S2C_ModelMeshDataPacket(
                modelPath, collisionMode, compressedVertices, compressedIndices);
        send(player, meshPacket);
        return true;
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

    private void handleHelloPacket(Player minestomPlayer, HelloPacket packet) {
        int clientVersion = packet.protocolVersion();
        LogContext baseContext = playerContext(minestomPlayer).merge(LogContext.builder()
                .put("client_protocol", clientVersion)
                .build());
        if (clientVersion != MoudProtocol.PROTOCOL_VERSION) {
            LogContext mismatchContext = baseContext.merge(LogContext.builder()
                    .put("expected_protocol", MoudProtocol.PROTOCOL_VERSION)
                    .build());
            LOGGER.warn(mismatchContext, "Player {} has unsupported Moud protocol version: {} (expected: {})",
                    minestomPlayer.getUsername(), clientVersion, MoudProtocol.PROTOCOL_VERSION);
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
        pushResourcePack(minestomPlayer);

        sendClientScripts(minestomPlayer);
        syncPermissionState(minestomPlayer);
        PlayerMovementSimService.getInstance().flushClientMode(minestomPlayer);

        Collection<PlayerModelProxy> playerModels = PlayerModelProxy.getAllModels();
        if (!playerModels.isEmpty()) {
            List<Object> packets = new ArrayList<>(playerModels.size() * 3);
            playerModels.forEach(model -> {
                if (model == null) {
                    return;
                }
                packets.add(new PlayerModelCreatePacket(model.getModelId(), model.getPosition(), model.getSkinUrl()));
                packets.add(new PlayerModelUpdatePacket(model.getModelId(), model.getPosition(), model.getYaw(), model.getPitch(), model.getInstanceName()));

                String skinUrl = model.getSkinUrl();
                if (skinUrl != null && !skinUrl.isEmpty()) {
                    packets.add(new PlayerModelSkinPacket(model.getModelId(), skinUrl));
                }

                String currentAnimation = model.getCurrentAnimation();
                if (currentAnimation != null && !currentAnimation.isEmpty()) {
                    packets.add(new S2C_PlayModelAnimationPacket(model.getModelId(), currentAnimation));
                }
            });
            sendBatched(minestomPlayer, packets, "player_models");

            LogContext playerModelContext = baseContext.merge(LogContext.builder()
                    .put("synced_player_models", playerModels.size())
                    .build());
            LOGGER.info(playerModelContext, "Synced {} existing player models to {}", playerModels.size(), minestomPlayer.getUsername());
        }

        Set<String> sentMeshPaths = new java.util.HashSet<>();
        List<Object> meshPackets = new ArrayList<>();
        ModelManager.getInstance().getAllModels().forEach(model -> {
            if (model == null) {
                return;
            }
            String modelPath = model.getModelPath();
            if (modelPath != null && !modelPath.isBlank() && sentMeshPaths.add(modelPath)) {
                MoudPackets.CollisionMode wireMode = model.getWireCollisionMode();
                byte[] verts = model.getCompressedVertices();
                byte[] indices = model.getCompressedIndices();
                if (wireMode != null && wireMode != MoudPackets.CollisionMode.BOX &&
                    verts != null && verts.length > 0 && indices != null && indices.length > 0) {
                    sendMeshDataIfNeeded(minestomPlayer, modelPath, wireMode, verts, indices);
                }
            }
        });

        List<Object> modelPackets = new ArrayList<>();
        ModelManager.getInstance().getAllModels().forEach(model -> {
            if (model == null) {
                return;
            }
            modelPackets.addAll(model.snapshotPackets());
        });
        sendBatched(minestomPlayer, modelPackets, "models");
        int modelCount = ModelManager.getInstance().getAllModels().size();
        if (modelCount > 0) {
            LogContext modelSyncContext = baseContext.merge(LogContext.builder()
                    .put("synced_models", modelCount)
                    .build());
            LOGGER.info(modelSyncContext, "Synced {} existing models to {}", modelCount, minestomPlayer.getUsername());
        }

        if (!CameraRegistry.getInstance().all().isEmpty()) {
            LOGGER.info(baseContext, "Client {} ready: {} cameras present (editor-only)", minestomPlayer.getUsername(), CameraRegistry.getInstance().all().size());
        }

        Collection<MediaDisplayProxy> displays = DisplayManager.getInstance().getAllDisplays();
        if (!displays.isEmpty()) {
            List<Object> displayPackets = new ArrayList<>();
            for (MediaDisplayProxy display : displays) {
                if (display == null) {
                    continue;
                }
                displayPackets.addAll(display.snapshotPackets());
            }
            sendBatched(minestomPlayer, displayPackets, "displays");
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
            LOGGER.debug(lightingContext, "Syncing lights to player {} after initialization delay", minestomPlayer.getUsername());
            ServerLightingManager.getInstance().syncLightsToPlayer(minestomPlayer);
        }).delay(java.time.Duration.ofSeconds(1)).schedule();
    }

    private void sendBatched(Player player, List<Object> packets, String label) {
        if (player == null || packets == null || packets.isEmpty()) {
            return;
        }
        int perTick = Math.max(1, SYNC_PACKETS_PER_TICK);
        LogContext context = playerContext(player).merge(LogContext.builder()
                .put("phase", "sync-batch")
                .put("label", label)
                .put("packets", packets.size())
                .put("per_tick", perTick)
                .build());
        LOGGER.debug(context, "Sending {} sync packets to {} in batches", packets.size(), player.getUsername());
        MinecraftServer.getSchedulerManager().scheduleNextTick(() -> sendBatchedTick(player, packets, 0, perTick, label));
    }

    private void sendBatchedTick(Player player, List<Object> packets, int start, int perTick, String label) {
        if (player == null || packets == null) {
            return;
        }
        if (!player.isOnline() || !isMoudClient(player)) {
            return;
        }
        int end = Math.min(packets.size(), start + perTick);
        for (int i = start; i < end; i++) {
            Object packet = packets.get(i);
            if (packet != null) {
                send(player, packet);
            }
        }
        if (end >= packets.size()) {
            return;
        }
        MinecraftServer.getSchedulerManager().scheduleNextTick(() -> sendBatchedTick(player, packets, end, perTick, label));
    }

    public void syncPermissionState(Player player) {
        if (player == null) {
            return;
        }
        boolean op = PermissionManager.getInstance().has(player, ServerPermission.OP);
        boolean editor = PermissionManager.getInstance().has(player, ServerPermission.EDITOR);
        boolean devUtils = PermissionManager.getInstance().has(player, ServerPermission.DEV_UTILS);
        send(player, new MoudPackets.PermissionStatePacket(op, editor, devUtils));
    }

    private void pushResourcePack(Player player) {
        ResourcePackInfo resourcePackInfo = resourcePackService.getInfo();
        if (resourcePackInfo == null) {
            LOGGER.debug("Resource pack info unavailable; skipping pack push for {}", player.getUsername());
            return;
        }
        if (!resourcePackRequested.add(player.getUuid())) {
            LOGGER.debug("Resource pack already requested for {}", player.getUsername());
            return;
        }
        int attempt = resourcePackAttempts.merge(player.getUuid(), 1, Integer::sum);
        MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
            try {
                LogContext base = playerContext(player).merge(LogContext.builder()
                        .put("resource_pack_id", resourcePackInfo.id())
                        .put("hash", resourcePackInfo.sha1())
                        .put("url", resourcePackInfo.url())
                        .put("transport", "resource_pack_push_packet")
                        .put("attempt", attempt)
                        .build());
                LOGGER.info(base, "Pushing resource pack to player (forced)");

                player.sendPacket(new ResourcePackPushPacket(
                        resourcePackInfo.id(),
                        resourcePackInfo.url(),
                        resourcePackInfo.sha1(),
                        true,
                        Component.text("Moud needs to apply its resource pack for custom assets.")
                ));
                LOGGER.info(base, "Resource pack push packet sent to {}", player.getUsername());
            } catch (Exception e) {
                LOGGER.warn(playerContext(player), "Failed to push resource pack to {}", player.getUsername(), e);
            }
        });
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            if (resourcePackRequested.contains(player.getUuid())) {
                if (attempt < 3) {
                    LOGGER.warn(playerContext(player), "Resource pack still unacknowledged after attempt {}. Retrying...", attempt);
                    pushResourcePack(player);
                } else {
                    LogContext failure = playerContext(player).merge(LogContext.builder()
                            .put("attempts", attempt)
                            .build());
                    LOGGER.error(String.valueOf(failure), "Resource pack not acknowledged after {} attempts. Giving up.", attempt);
                }
            }
        }).delay(Duration.ofSeconds(5)).schedule();
    }

    private void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        ResourcePackStatus status = event.getStatus();
        ResourcePackInfo resourcePackInfo = resourcePackService.getInfo();
        LogContext context = playerContext(player).merge(LogContext.builder()
                .put("status", status.name())
                .build());
        switch (status) {
            case SUCCESSFULLY_LOADED -> {
                ClientSession session = moudClients.get(player.getUuid());
                if (session != null && resourcePackInfo != null) {
                    session.setResourcePackHash(resourcePackInfo.sha1());
                }
                LOGGER.info(context, "Resource pack applied for {}", player.getUsername());
                resourcePackRequested.remove(player.getUuid());
                resourcePackAttempts.remove(player.getUuid());
            }
            case ACCEPTED, DOWNLOADED -> LOGGER.info(context, "Resource pack progress update for {}", player.getUsername());
            case DECLINED, FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL, DISCARDED -> {
                LOGGER.warn(context, "Resource pack failed with status {}", status);
                player.kick(Component.text("This server requires the Moud resource pack (status: " + status.name() + ")."));
                resourcePackRequested.remove(player.getUuid());
                resourcePackAttempts.remove(player.getUuid());
            }
            default -> LOGGER.debug(context, "Resource pack status {} for {}", status, player.getUsername());
        }
    }
    private List<MoudPackets.CollisionBoxData> toCollisionData(List<com.moud.api.collision.OBB> boxes) {
        List<MoudPackets.CollisionBoxData> boxData = new ArrayList<>();
        if (boxes == null) {
            return boxData;
        }
        for (com.moud.api.collision.OBB obb : boxes) {
            if (obb == null) {
                continue;
            }
            boxData.add(new MoudPackets.CollisionBoxData(obb.center, obb.halfExtents, obb.rotation));
        }
        return boxData;
    }

    private void onPlayerJoin(PlayerSpawnEvent event) {
        LOGGER.info(playerContext(event.getPlayer()), "onPlayerJoin called for {}", event.getPlayer().getUsername());
        if (event.isFirstSpawn()) {
            Player player = event.getPlayer();
            PlayerCursorDirectionManager.getInstance().onPlayerJoin(player);
            CursorService.getInstance().onPlayerJoin(player);
            PluginEventBus.getInstance().dispatchPlayerJoin(player);
        }
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        Player player = event.getPlayer();
        moudClients.remove(player.getUuid());
        resourcePackRequested.remove(player.getUuid());
        resourcePackAttempts.remove(player.getUuid());
        broadcastQueues.remove(player.getUuid());
        PlayerCameraManager.getInstance().onPlayerDisconnect(player);
        PlayerCursorDirectionManager.getInstance().onPlayerDisconnect(player);
        CursorService.getInstance().onPlayerQuit(player);
        UIOverlayService.getInstance().clear(player);
        PluginEventBus.getInstance().dispatchPlayerLeave(player);
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
            int maxPayloadBytes = 2 * 1024 * 1024;
            if (scriptData != null && scriptData.length > maxPayloadBytes) {
                int chunkSize = 512 * 1024;
                int totalChunks = (int) Math.ceil((double) scriptData.length / chunkSize);
                LOGGER.info(payloadContext.merge(LogContext.builder()
                        .put("chunk_size", chunkSize)
                        .put("total_chunks", totalChunks)
                        .build()), "Chunking client scripts for {} to stay under packet limits", player.getUsername());
                for (int i = 0; i < totalChunks; i++) {
                    int start = i * chunkSize;
                    int end = Math.min(scriptData.length, start + chunkSize);
                    byte[] chunk = java.util.Arrays.copyOfRange(scriptData, start, end);
                    if (!send(player, new SyncClientScriptsChunkPacket(hash, totalChunks, i, chunk))) {
                        LOGGER.error(String.valueOf(payloadContext), "Failed to send client scripts chunk {} to {}", i, player.getUsername());
                        return;
                    }
                }
            } else {
                LOGGER.info(payloadContext, "Sending client scripts to {}: hash={}, size={} bytes", player.getUsername(), hash, scriptData.length);
                if (!send(player, new SyncClientScriptsPacket(hash, scriptData))) {
                    LOGGER.error(String.valueOf(payloadContext), "Failed to send client scripts payload to {}", player.getUsername());
                    return;
                }
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

    public boolean isClientReady(Player player) {
        if (player == null) {
            return false;
        }
        ClientSession session = moudClients.get(player.getUuid());
        return session != null && session.isClientReady();
    }

    public void markClientReady(Player player) {
        if (player == null) {
            return;
        }
        ClientSession session = moudClients.get(player.getUuid());
        if (session != null) {
            session.setClientReady(true);
        }
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
        private String resourcePackHash;
        private volatile boolean clientReady;
        private final Set<String> sentMeshPaths = ConcurrentHashMap.newKeySet();

        private ClientSession(Instant handshakeTime) {
            this.handshakeTime = handshakeTime;
        }

        public boolean markMeshSent(String modelPath) {
            return sentMeshPaths.add(modelPath);
        }

        public boolean hasSentMesh(String modelPath) {
            return sentMeshPaths.contains(modelPath);
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

        public String getResourcePackHash() {
            return resourcePackHash;
        }

        public void setResourcePackHash(String resourcePackHash) {
            this.resourcePackHash = resourcePackHash;
        }

        public boolean isClientReady() {
            return clientReady;
        }

        public void setClientReady(boolean clientReady) {
            this.clientReady = clientReady;
        }
    }

    public synchronized void reloadResourcePack() {
        LOGGER.info("Starting resource pack reload...");
        resourcePackService.reloadAsync().whenComplete((newInfo, error) -> {
            if (error != null) {
                LOGGER.error("Failed to reload resource pack", error);
                return;
            }
            if (newInfo == null) {
                LOGGER.warn("Resource pack reload failed; keeping previous pack.");
                return;
            }

            resourcePackRequested.clear();
            resourcePackAttempts.clear();

            LOGGER.info("Resource pack reloaded successfully, pushing to clients...");
            MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(player -> {
                if (moudClients.containsKey(player.getUuid())) {
                    pushResourcePack(player);
                }
            });
        });
    }
}
