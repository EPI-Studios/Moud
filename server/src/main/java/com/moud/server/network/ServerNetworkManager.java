package com.moud.server.network;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.network.MoudPackets.*;
import com.moud.server.client.ClientScriptManager;
import com.moud.server.cursor.CursorService;
import com.moud.server.editor.AnimationManager;
import com.moud.server.entity.DisplayManager;
import com.moud.server.entity.ModelManager;
import com.moud.server.editor.SceneManager;
import com.moud.server.editor.BlueprintStorage;
import com.moud.server.fakeplayer.FakePlayerManager;
import com.moud.server.events.EventDispatcher;
import com.moud.server.lighting.ServerLightingManager;
import com.moud.server.camera.CameraRegistry;
import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.movement.ServerMovementHandler;
import com.moud.server.ui.UIOverlayService;
import com.moud.server.network.diagnostics.NetworkProbe;
import com.moud.server.player.PlayerCameraManager;
import com.moud.server.plugin.PluginEventBus;
import com.moud.server.proxy.MediaDisplayProxy;
import com.moud.server.proxy.PlayerModelProxy;
import com.moud.server.particle.ParticleEmitterManager;
import com.moud.server.network.ResourcePackServer.ResourcePackInfo;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerResourcePackStatusEvent;
import com.moud.server.player.PlayerCursorDirectionManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
    private static final int SUPPORTED_PROTOCOL_VERSION = 1;

    private final EventDispatcher eventDispatcher;
    private final ClientScriptManager clientScriptManager;
    private final ConcurrentMap<UUID, ClientSession> moudClients = new ConcurrentHashMap<>();
    private final Set<UUID> resourcePackRequested = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, Integer> resourcePackAttempts = new ConcurrentHashMap<>();
    private final BlueprintStorage blueprintStorage;
    private ResourcePackInfo resourcePackInfo;
    private static ServerNetworkManager instance;

    public ServerNetworkManager(EventDispatcher eventDispatcher, ClientScriptManager clientScriptManager) {
        this.eventDispatcher = eventDispatcher;
        this.clientScriptManager = clientScriptManager;
        this.blueprintStorage = new BlueprintStorage(SceneManager.getInstance().getProjectRoot());
        this.resourcePackInfo = initResourcePackServer();
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
                .addListener(PlayerSpawnEvent.class, this::onPlayerJoin)
                .addListener(PlayerResourcePackStatusEvent.class, this::onResourcePackStatus);
    }

    private void registerPacketHandlers() {
        ServerPacketWrapper.registerHandler(HelloPacket.class, this::handleHelloPacket);
        ServerPacketWrapper.registerHandler(ServerboundScriptEventPacket.class, this::handleScriptEvent);
        ServerPacketWrapper.registerHandler(ClientUpdateCameraPacket.class, this::handleCameraUpdate);
        ServerPacketWrapper.registerHandler(MouseMovementPacket.class, this::handleMouseMovement);
        ServerPacketWrapper.registerHandler(PlayerClickPacket.class, this::handlePlayerClick);
        ServerPacketWrapper.registerHandler(PlayerModelClickPacket.class, this::handlePlayerModelClick);
        ServerPacketWrapper.registerHandler(MoudPackets.UIInteractionPacket.class, this::handleUiInteraction);
        ServerPacketWrapper.registerHandler(ClientUpdateValuePacket.class, this::handleSharedValueUpdate);
        ServerPacketWrapper.registerHandler(MovementStatePacket.class, this::handleMovementState);
        ServerPacketWrapper.registerHandler(ClientReadyPacket.class, this::handleClientReady);
        ServerPacketWrapper.registerHandler(RequestSceneStatePacket.class, this::handleSceneStateRequest);
        ServerPacketWrapper.registerHandler(SceneEditPacket.class, this::handleSceneEditRequest);
        ServerPacketWrapper.registerHandler(RequestEditorAssetsPacket.class, this::handleEditorAssetsRequest);
        ServerPacketWrapper.registerHandler(RequestProjectMapPacket.class, this::handleProjectMapRequest);
        ServerPacketWrapper.registerHandler(RequestProjectFilePacket.class, this::handleProjectFileRequest);
        ServerPacketWrapper.registerHandler(MoudPackets.AnimationSavePacket.class, this::handleAnimationSave);
        ServerPacketWrapper.registerHandler(MoudPackets.AnimationLoadPacket.class, this::handleAnimationLoad);
        ServerPacketWrapper.registerHandler(MoudPackets.AnimationListPacket.class, this::handleAnimationList);
        ServerPacketWrapper.registerHandler(MoudPackets.AnimationPlayPacket.class, this::handleAnimationPlay);
        ServerPacketWrapper.registerHandler(MoudPackets.AnimationStopPacket.class, this::handleAnimationStop);
        ServerPacketWrapper.registerHandler(MoudPackets.AnimationSeekPacket.class, this::handleAnimationSeek);
        ServerPacketWrapper.registerHandler(MoudPackets.UpdateRuntimeModelPacket.class, this::handleRuntimeModelUpdate);
        ServerPacketWrapper.registerHandler(MoudPackets.UpdateRuntimeDisplayPacket.class, this::handleRuntimeDisplayUpdate);
        ServerPacketWrapper.registerHandler(MoudPackets.UpdatePlayerTransformPacket.class, this::handlePlayerTransformUpdate);
        ServerPacketWrapper.registerHandler(SaveBlueprintPacket.class, this::handleBlueprintSave);
        ServerPacketWrapper.registerHandler(RequestBlueprintPacket.class, this::handleBlueprintRequest);
        ServerPacketWrapper.registerHandler(MoudPackets.C2S_SpawnFakePlayer.class, this::handleFakePlayerSpawn);
        ServerPacketWrapper.registerHandler(MoudPackets.C2S_RemoveFakePlayer.class, this::handleFakePlayerRemove);
        ServerPacketWrapper.registerHandler(MoudPackets.C2S_SetFakePlayerPose.class, this::handleFakePlayerPose);
        ServerPacketWrapper.registerHandler(MoudPackets.C2S_SetFakePlayerPath.class, this::handleFakePlayerPath);

    }

    private void handleMovementState(Object player, MovementStatePacket packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;

        ServerMovementHandler.getInstance().handleMovementState(minestomPlayer, packet);
        eventDispatcher.dispatchMovementEvent(minestomPlayer, packet);
    }

    private void handleSceneStateRequest(Object player, RequestSceneStatePacket packet) {
        Player minestomPlayer = (Player) player;
        var snapshot = SceneManager.getInstance().createSnapshot(packet.sceneId());
        send(minestomPlayer, new SceneStatePacket(
                packet.sceneId(),
                snapshot.objects(),
                snapshot.version()
        ));
    }

    private void handleSceneEditRequest(Object player, SceneEditPacket packet) {
        Player minestomPlayer = (Player) player;
        var result = SceneManager.getInstance().applyEdit(packet.sceneId(), packet.action(), packet.payload(), packet.clientVersion());
        SceneEditAckPacket ack = new SceneEditAckPacket(
                packet.sceneId(),
                result.success(),
                result.message(),
                result.snapshot(),
                result.version(),
                result.objectId()
        );
        send(minestomPlayer, ack);
        broadcastExcept(ack, minestomPlayer);
    }

    private void handleEditorAssetsRequest(Object player, RequestEditorAssetsPacket packet) {
        Player minestomPlayer = (Player) player;
        var assets = SceneManager.getInstance().getEditorAssets();
        send(minestomPlayer, new EditorAssetListPacket(assets));
    }

    private void handleProjectMapRequest(Object player, RequestProjectMapPacket packet) {
        Player minestomPlayer = (Player) player;
        var entries = SceneManager.getInstance().getProjectFileEntries();
        send(minestomPlayer, new ProjectMapPacket(entries));
    }

    private void handleAnimationSave(Object player, MoudPackets.AnimationSavePacket packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;
        AnimationManager.getInstance().handleSave(packet);
    }

    private void handleAnimationLoad(Object player, MoudPackets.AnimationLoadPacket packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;
        AnimationManager.getInstance().handleLoad(packet, this, minestomPlayer);
    }

    private void handleAnimationList(Object player, MoudPackets.AnimationListPacket packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;
        AnimationManager.getInstance().handleList(this, minestomPlayer);
    }

    private void handleAnimationPlay(Object player, MoudPackets.AnimationPlayPacket packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;
        AnimationManager.getInstance().handlePlay(packet);
    }

    private void handleAnimationStop(Object player, MoudPackets.AnimationStopPacket packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;
        AnimationManager.getInstance().handleStop(packet);
    }

    private void handleAnimationSeek(Object player, MoudPackets.AnimationSeekPacket packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;
        AnimationManager.getInstance().handleSeek(packet);
    }

    private void handleProjectFileRequest(Object player, RequestProjectFilePacket packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) {
            return;
        }
        String requestedPath = packet.path() == null ? "" : packet.path().trim();
        if (requestedPath.isEmpty()) {
            send(minestomPlayer, new ProjectFileContentPacket(requestedPath, null, false, "Empty path", null));
            return;
        }
        Path projectRoot = SceneManager.getInstance().getProjectRoot();
        if (projectRoot == null) {
            send(minestomPlayer, new ProjectFileContentPacket(requestedPath, null, false, "Project root unavailable", null));
            return;
        }
        if (requestedPath.contains("..")) {
            send(minestomPlayer, new ProjectFileContentPacket(requestedPath, null, false, "Invalid path", null));
            return;
        }
        Path resolved = projectRoot.resolve(requestedPath).normalize();
        if (!resolved.startsWith(projectRoot)) {
            send(minestomPlayer, new ProjectFileContentPacket(requestedPath, null, false, "Path outside project", null));
            return;
        }
        if (!Files.exists(resolved) || Files.isDirectory(resolved)) {
            send(minestomPlayer, new ProjectFileContentPacket(requestedPath, null, false, "File not found", null));
            return;
        }
        try {
            long maxBytes = 256 * 1024;
            long size = Files.size(resolved);
            if (size > maxBytes) {
                send(minestomPlayer, new ProjectFileContentPacket(requestedPath, null, false, "File too large (" + size + " bytes)", resolved.toString()));
                return;
            }
            String content = Files.readString(resolved);
            send(minestomPlayer, new ProjectFileContentPacket(requestedPath, content, true, null, resolved.toString()));
        } catch (IOException e) {
            LOGGER.warn("Failed to read project file {}", resolved, e);
            send(minestomPlayer, new ProjectFileContentPacket(requestedPath, null, false, "Failed to read file", resolved.toString()));
        }
    }

    private void handleRuntimeModelUpdate(Object player, MoudPackets.UpdateRuntimeModelPacket packet) {
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

    private void handleRuntimeDisplayUpdate(Object player, MoudPackets.UpdateRuntimeDisplayPacket packet) {
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

    private void handlePlayerTransformUpdate(Object player, MoudPackets.UpdatePlayerTransformPacket packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;
        if (packet.playerId() == null || packet.position() == null) {
            return;
        }
        Player target = MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                .filter(p -> p.getUuid().equals(packet.playerId()))
                .findFirst()
                .orElse(null);
        if (target == null) {
            return;
        }
        Vector3 position = packet.position();
        Pos current = target.getPosition();
        Pos destination = new Pos(position.x, position.y, position.z, current.yaw(), current.pitch());
        target.teleport(destination);
    }

    private void handleBlueprintSave(Object player, SaveBlueprintPacket packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;
        String name = packet.name() == null ? "" : packet.name().trim();
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
                LOGGER.error(playerContext(minestomPlayer), "Failed to save blueprint {}", e, name);
            }
        }
        send(minestomPlayer, new BlueprintSaveAckPacket(name, success, message == null ? "" : message));
    }

    private void handleBlueprintRequest(Object player, RequestBlueprintPacket packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;
        String name = packet.name() == null ? "" : packet.name().trim();
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
                LOGGER.error(playerContext(minestomPlayer), "Failed to load blueprint {}", e, name);
            }
        }
        send(minestomPlayer, new BlueprintDataPacket(name, data, success, message == null ? "" : message));
    }

    private void handleFakePlayerSpawn(Object player, MoudPackets.C2S_SpawnFakePlayer packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;
        FakePlayerManager.getInstance().spawn(packet.descriptor());
    }

    private void handleFakePlayerRemove(Object player, MoudPackets.C2S_RemoveFakePlayer packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;
        FakePlayerManager.getInstance().remove(packet.id());
    }

    private void handleFakePlayerPose(Object player, MoudPackets.C2S_SetFakePlayerPose packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;
        FakePlayerManager.getInstance().updatePose(packet.id(), packet.sneaking(), packet.sprinting(), packet.swinging(), packet.usingItem());
    }

    private void handleFakePlayerPath(Object player, MoudPackets.C2S_SetFakePlayerPath packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;
        FakePlayerManager.getInstance().updatePath(packet.id(), packet.path(), packet.pathSpeed(), packet.pathLoop(), packet.pathPingPong());
    }

    private void handleClientReady(Object player, ClientReadyPacket packet) {
        Player minestomPlayer = (Player) player;
        LogContext context = playerContext(minestomPlayer);
        LOGGER.info(context, "Client {} is ready, syncing lights, fake players, and particle emitters", minestomPlayer.getUsername());
        ServerLightingManager.getInstance().syncLightsToPlayer(minestomPlayer);
        FakePlayerManager.getInstance().syncToPlayer(minestomPlayer);
        ParticleEmitterManager.getInstance().syncToPlayer(minestomPlayer);
        com.moud.server.rendering.PostEffectStateManager.getInstance().syncToPlayer(minestomPlayer);
        UIOverlayService.getInstance().resend(minestomPlayer);
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
        pushResourcePack(minestomPlayer);

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

        FakePlayerManager.getInstance().syncToPlayer(minestomPlayer);

        ModelManager.getInstance().getAllModels().forEach(model -> {
            model.ensureCollisionPayload();
            MoudPackets.S2C_CreateModelPacket createPacket = new MoudPackets.S2C_CreateModelPacket(
                    model.getId(),
                    model.getModelPath(),
                    model.getPosition(),
                    model.getRotation(),
                    model.getScale(),
                    model.getCollisionWidth(),
                    model.getCollisionHeight(),
                    model.getCollisionDepth(),
                    model.getTexture(),
                    toCollisionData(model.getCollisionBoxes()),
                    model.getWireCollisionMode(),
                    model.getCompressedVertices(),
                    model.getCompressedIndices(),
                    List.of()
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

        if (!CameraRegistry.getInstance().all().isEmpty()) {
            LOGGER.info(baseContext, "Client {} ready: {} cameras present (editor-only)", minestomPlayer.getUsername(), CameraRegistry.getInstance().all().size());
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

    private void pushResourcePack(Player player) {
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
    private void handleScriptEvent(Object player, ServerboundScriptEventPacket packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;

        if (packet.eventName().startsWith("audio:microphone:")) {
            com.moud.server.audio.ServerMicrophoneManager.getInstance()
                    .handleEvent(minestomPlayer, packet.eventName(), packet.eventData());
        }

        eventDispatcher.dispatchScriptEvent(packet.eventName(), packet.eventData(), minestomPlayer);
        PluginEventBus.getInstance().dispatchScriptEvent(packet.eventName(), minestomPlayer, packet.eventData());
    }

    private void handleCameraUpdate(Object player, ClientUpdateCameraPacket packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;
        PlayerCameraManager.getInstance().updateCameraDirection(minestomPlayer, packet.direction());
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

    private void handlePlayerModelClick(Object player, PlayerModelClickPacket packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;
        PlayerModelProxy model = PlayerModelProxy.getById(packet.modelId());
        if (model != null) {
            model.triggerClick(minestomPlayer, packet.mouseX(), packet.mouseY(), packet.button());
        }
    }

    private void handleUiInteraction(Object player, MoudPackets.UIInteractionPacket packet) {
        Player minestomPlayer = (Player) player;
        if (!isMoudClient(minestomPlayer)) return;
        UIOverlayService.getInstance().handleInteraction(minestomPlayer, packet);
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

        public String getResourcePackHash() {
            return resourcePackHash;
        }

        public void setResourcePackHash(String resourcePackHash) {
            this.resourcePackHash = resourcePackHash;
        }
    }

    private ResourcePackInfo initResourcePackServer() {
        String packPathEnv = System.getenv("MOUD_RESOURCE_PACK_PATH");
        Path packPath;
        if (packPathEnv == null || packPathEnv.isBlank()) {
            packPath = ResourcePackBuilder.buildFromProjectAssets();
        } else {
            packPath = java.nio.file.Path.of(packPathEnv);
        }
        if (packPath == null) {
            LOGGER.warn("Resource pack unavailable; clients will miss custom assets.");
            return null;
        }
        String bindHost = System.getenv("MOUD_RESOURCE_PACK_BIND_HOST");
        if (bindHost == null || bindHost.isBlank()) {
            bindHost = "0.0.0.0";
        }
        String publicHost = System.getenv("MOUD_RESOURCE_PACK_HOST");
        if (publicHost == null || publicHost.isBlank()) {
            try {
                publicHost = java.net.InetAddress.getLocalHost().getHostAddress();
            } catch (Exception e) {
                publicHost = "127.0.0.1";
            }
        }
        int port = 0;
        try {
            String rawPort = System.getenv("MOUD_RESOURCE_PACK_PORT");
            if (rawPort != null && !rawPort.isBlank()) {
                port = Integer.parseInt(rawPort.trim());
            }
        } catch (NumberFormatException ignored) {
        }
        if (port <= 0) {
            port = 8777;
        }
        String urlPath = "/moud-resourcepack.zip";
        return ResourcePackServer.start(packPath, bindHost, publicHost, port, urlPath);
    }

    public synchronized void reloadResourcePack() {
        ResourcePackInfo newInfo = initResourcePackServer();
        if (newInfo == null) {
            LOGGER.warn("Resource pack reload failed; keeping previous pack.");
            return;
        }
        this.resourcePackInfo = newInfo;
        resourcePackRequested.clear();
        resourcePackAttempts.clear();

        // Force push to connected Moud clients.
        MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(player -> {
            if (moudClients.containsKey(player.getUuid())) {
                pushResourcePack(player);
            }
        });
    }
}
