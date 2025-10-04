package com.moud.client;

import com.moud.client.animation.*;
import com.moud.client.api.service.ClientAPIService;
import com.moud.client.cursor.ClientCursorManager;
import com.moud.client.lighting.ClientLightingService;
import com.moud.client.movement.ClientMovementTracker;
import com.moud.client.network.ClientPacketReceiver;
import com.moud.client.network.ClientPacketWrapper;
import com.moud.client.network.DataPayload;
import com.moud.client.network.MoudPayload;
import com.moud.client.player.ClientCameraManager;
import com.moud.client.player.PlayerStateManager;
import com.moud.client.resources.InMemoryPackResources;
import com.moud.client.runtime.ClientScriptingRuntime;
import com.moud.client.shared.SharedValueManager;
import com.moud.client.ui.UIOverlayManager;
import com.moud.client.util.WindowAnimator;
import com.moud.network.MoudPackets;
import com.moud.network.MoudPackets.*;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.*;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class MoudClientMod implements ClientModInitializer, ResourcePackProvider {
    public static final int PROTOCOL_VERSION = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger(MoudClientMod.class);
    private static final Identifier MOUDPACK_ID = Identifier.of("moud", "dynamic_resources");
    private static final long JOIN_TIMEOUT_MS = 10000;
    private static MoudClientMod instance;
    private static boolean customCameraActive = false;
    private final AtomicBoolean resourcesLoaded = new AtomicBoolean(false);
    private final AtomicBoolean waitingForResources = new AtomicBoolean(false);
    private final AtomicReference<InMemoryPackResources> dynamicPack = new AtomicReference<>(null);
    private PlayerModelRenderer playerModelRenderer;
    private ClientScriptingRuntime scriptingRuntime;
    private ClientAPIService apiService;
    private SharedValueManager sharedValueManager;
    private ClientCameraManager clientCameraManager;
    private PlayerStateManager playerStateManager;
    private ClientCursorManager clientCursorManager;
    private String currentResourcesHash = "";
    private long joinTime = -1L;
    private boolean moudServicesInitialized = false;
    private static boolean isOnMoudServer = false;

    public static boolean isCustomCameraActive() {
        return customCameraActive;
    }

    public static void setCustomCameraActive(boolean active) {
        customCameraActive = active;
    }

    public static MoudClientMod getInstance() {
        return instance;
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        LOGGER.info("Initializing Moud client...");

        PayloadTypeRegistry.playS2C().register(DataPayload.ID, DataPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MoudPayload.ID, MoudPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DataPayload.ID, DataPayload.CODEC);

        ClientPacketReceiver.registerS2CPackets();

        this.clientCursorManager = ClientCursorManager.getInstance();
        this.playerModelRenderer = new PlayerModelRenderer();

        registerPacketHandlers();
        registerEventHandlers();
        registerResourcePackProvider();
        registerTickHandler();
        registerRenderHandler();
        registerAnimationLayer();
        registerShutdownHandler();
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            WindowAnimator.tick();

            if (scriptingRuntime != null && scriptingRuntime.isInitialized()) {
                scriptingRuntime.processAnimationFrameQueue();
            }

            if (apiService != null && apiService.events != null) {
                apiService.events.dispatch("render:hud", drawContext, tickCounter.getTickDelta(true));
            }
            UIOverlayManager.getInstance().renderOverlays(drawContext, tickCounter);
        });
        LOGGER.info("Moud client initialization complete.");
    }

    private void registerPacketHandlers() {
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_PlayPlayerAnimationPacket.class, (player, packet) -> handlePlayPlayerAnimation(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_PlayModelAnimationPacket.class, (player, packet) -> handlePlayModelAnimation(packet));
        ClientPacketWrapper.registerHandler(SyncClientScriptsPacket.class, (player, packet) -> handleSyncScripts(packet));
        ClientPacketWrapper.registerHandler(ClientboundScriptEventPacket.class, (player, packet) -> handleScriptEvent(packet));
        ClientPacketWrapper.registerHandler(CameraLockPacket.class, (player, packet) -> handleCameraLock(packet));
        ClientPacketWrapper.registerHandler(PlayerStatePacket.class, (player, packet) -> handlePlayerState(packet));
        ClientPacketWrapper.registerHandler(ExtendedPlayerStatePacket.class, (player, packet) -> handleExtendedPlayerState(packet));
        ClientPacketWrapper.registerHandler(SyncSharedValuesPacket.class, (player, packet) -> handleSharedValueSync(packet));
        ClientPacketWrapper.registerHandler(PlayerModelCreatePacket.class, (player, packet) -> handlePlayerModelCreate(packet));
        ClientPacketWrapper.registerHandler(PlayerModelUpdatePacket.class, (player, packet) -> handlePlayerModelUpdate(packet));
        ClientPacketWrapper.registerHandler(PlayerModelSkinPacket.class, (player, packet) -> handlePlayerModelSkin(packet));
        ClientPacketWrapper.registerHandler(PlayerModelRemovePacket.class, (player, packet) -> handlePlayerModelRemove(packet));
        ClientPacketWrapper.registerHandler(AdvancedCameraLockPacket.class, (player, packet) -> handleAdvancedCameraLock(packet));
        ClientPacketWrapper.registerHandler(CameraUpdatePacket.class, (player, packet) -> handleCameraUpdate(packet));
        ClientPacketWrapper.registerHandler(CameraReleasePacket.class, (player, packet) -> handleCameraRelease(packet));
        ClientPacketWrapper.registerHandler(S2C_ManageWindowPacket.class, (player, packet) -> handleManageWindow(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_TransitionWindowPacket.class, (player, packet) -> handleTransitionWindow(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_RestoreWindowPacket.class, (player, packet) -> {
            MinecraftClient.getInstance().execute(() -> {
                WindowAnimator.restore(packet.duration(), packet.easing());
            });
        });
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_WindowSequencePacket.class, (player, packet) -> {
            MinecraftClient.getInstance().execute(() -> {
                WindowAnimator.startSequence(packet.steps());
            });
        });
        ClientPacketWrapper.registerHandler(CursorPositionUpdatePacket.class, (player, packet) -> {
            if (clientCursorManager != null) {
                clientCursorManager.handlePositionUpdates(packet.updates());
            }
        });
        ClientPacketWrapper.registerHandler(CursorAppearancePacket.class, (player, packet) -> {
            if (clientCursorManager != null) {
                clientCursorManager.handleAppearanceUpdate(packet.playerId(), packet.texture(), packet.color(), packet.scale(), packet.renderMode());
            }
        });
        ClientPacketWrapper.registerHandler(CursorVisibilityPacket.class, (player, packet) -> {
            if (clientCursorManager != null) {
                clientCursorManager.handleVisibilityUpdate(packet.playerId(), packet.visible());
            }
        });
        ClientPacketWrapper.registerHandler(RemoveCursorsPacket.class, (player, packet) -> {
            if (clientCursorManager != null) {
                clientCursorManager.handleRemoveCursors(packet.playerIds());
            }
        });
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_SetPlayerPartConfigPacket.class, (player, packet) -> {
            PlayerPartConfigManager.getInstance().updatePartConfig(
                    packet.playerId(),
                    packet.partName(),
                    packet.properties()
            );
        });
        ClientPacketWrapper.registerHandler(MoudPackets.InterpolationSettingsPacket.class, (player, packet) -> {
            handleInterpolationSettings(packet);
        });
        ClientPacketWrapper.registerHandler(MoudPackets.FirstPersonConfigPacket.class, (player, packet) -> {
            handleFirstPersonConfig(packet);
        });
        ClientPacketWrapper.registerHandler(MoudPackets.CameraControlPacket.class, (player, packet) -> handleCameraControl(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_PlayModelAnimationWithFadePacket.class, (player, packet) -> {
            MinecraftClient.getInstance().execute(() -> {
                AnimatedPlayerModel model = ClientPlayerModelManager.getInstance().getModel(packet.modelId());
                if (model != null) {
                    model.playAnimationWithFade(packet.animationId(), packet.durationTicks());
                    LOGGER.info("Playing animation '{}' with fade on model {}.", packet.animationId(), packet.modelId());
                } else {
                    LOGGER.warn("Received faded animation for unknown model ID: {}", packet.modelId());
                }
            });
        });

        LOGGER.info("Internal packet handlers registered.");
    }

    private void handleInterpolationSettings(MoudPackets.InterpolationSettingsPacket packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.getUuid().equals(packet.playerId())) {
            PlayerPartConfigManager.InterpolationSettings settings = new PlayerPartConfigManager.InterpolationSettings();

            Map<String, Object> data = packet.settings();
            if (data.containsKey("enabled")) {
                settings.enabled = (Boolean) data.get("enabled");
            }
            if (data.containsKey("duration")) {
                settings.duration = ((Number) data.get("duration")).longValue();
            }
            if (data.containsKey("easing")) {
                String easingStr = (String) data.get("easing");
                try {
                    settings.easing = PlayerPartConfigManager.EasingType.valueOf(easingStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Unknown easing type: {}", easingStr);
                }
            }
            if (data.containsKey("speed")) {
                settings.speed = ((Number) data.get("speed")).floatValue();
            }

            PlayerPartConfigManager.getInstance().setPlayerInterpolationSettings(packet.playerId(), settings);
        }
    }

    private void handleManageWindow(MoudPackets.S2C_ManageWindowPacket packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            long handle = client.getWindow().getHandle();
            switch (packet.action()) {
                case SET_SIZE -> GLFW.glfwSetWindowSize(handle, packet.int1(), packet.int2());
                case SET_POSITION -> GLFW.glfwSetWindowPos(handle, packet.int1(), packet.int2());
                case SET_TITLE -> GLFW.glfwSetWindowTitle(handle, packet.string1());
                case SET_BORDERLESS ->
                        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, packet.bool1() ? GLFW.GLFW_FALSE : GLFW.GLFW_TRUE);
                case MAXIMIZE -> GLFW.glfwMaximizeWindow(handle);
                case MINIMIZE -> GLFW.glfwIconifyWindow(handle);
                case RESTORE -> GLFW.glfwRestoreWindow(handle);
            }
        });
    }

    private void handleTransitionWindow(MoudPackets.S2C_TransitionWindowPacket packet) {
        MinecraftClient.getInstance().execute(() -> {
            WindowAnimator.startAnimation(
                    packet.targetX(),
                    packet.targetY(),
                    packet.targetWidth(),
                    packet.targetHeight(),
                    packet.duration(),
                    packet.easing()
            );
        });
    }

    private void handleFirstPersonConfig(MoudPackets.FirstPersonConfigPacket packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.getUuid().equals(packet.playerId())) {
            ExternalPartConfigLayer.updateFirstPersonConfig(packet.playerId(), packet.config());
        }
    }

    private void handleExtendedPlayerState(MoudPackets.ExtendedPlayerStatePacket packet) {
        if (playerStateManager != null) {
            playerStateManager.updateExtendedPlayerState(
                    packet.hideHotbar(), packet.hideHand(), packet.hideExperience(),
                    packet.hideHealth(), packet.hideFood(), packet.hideCrosshair(),
                    packet.hideChat(), packet.hidePlayerList(), packet.hideScoreboard()
            );
        }
    }

    private void initializeMoudServices() {
        if (moudServicesInitialized) {
            return;
        }
        LOGGER.info("Initializing Moud services for new connection...");
        this.apiService = new ClientAPIService();
        this.scriptingRuntime = new ClientScriptingRuntime(this.apiService);
        this.apiService.setRuntime(this.scriptingRuntime);
        this.sharedValueManager = SharedValueManager.getInstance();
        this.sharedValueManager.initialize();
        this.clientCameraManager = new ClientCameraManager();
        this.playerStateManager = PlayerStateManager.getInstance();
        this.clientCursorManager = ClientCursorManager.getInstance();
        LOGGER.info("ClientCursorManager initialized: {}", clientCursorManager != null);
        moudServicesInitialized = true;
    }

    private void cleanupMoudServices() {
        LOGGER.info("Cleaning up Moud services...");
        if (scriptingRuntime != null) {
            scriptingRuntime.shutdown();
            scriptingRuntime = null;
        }
        if (apiService != null) {
            apiService.cleanup();
            apiService = null;
        }
        if (sharedValueManager != null) {
            sharedValueManager.cleanup();
        }
        if (playerStateManager != null) {
            playerStateManager.reset();
        }

        if (clientCursorManager != null) {
            clientCursorManager.clear();
        }
        this.clientCameraManager = null;
        this.playerStateManager = null;
        moudServicesInitialized = false;
    }

    private void handleCameraControl(MoudPackets.CameraControlPacket packet) {
        if (apiService == null || apiService.camera == null) return;

        switch (packet.action()) {
            case ENABLE -> apiService.camera.enableCustomCamera();
            case DISABLE -> apiService.camera.disableCustomCamera();
            case TRANSITION_TO -> {
                if (packet.options() != null) {
                    apiService.camera.transitionTo(Value.asValue(packet.options()));
                }
            }
            case SNAP_TO -> {
                if (packet.options() != null) {
                    apiService.camera.snapTo(Value.asValue(packet.options()));
                }
            }
        }
    }

    private void registerEventHandlers() {
        ClientPlayConnectionEvents.JOIN.register(this::onJoinServer);
        ClientPlayConnectionEvents.DISCONNECT.register(this::onDisconnectServer);
    }

    private void registerTickHandler() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (joinTime != -1L && waitingForResources.get() && (System.currentTimeMillis() - joinTime > JOIN_TIMEOUT_MS)) {
                LOGGER.warn("Timed out waiting for Moud server handshake. Assuming non-Moud server and proceeding with join.");
                waitingForResources.set(false);
                resourcesLoaded.set(true);
                joinTime = -1L;
                cleanupMoudServices();
            }

            ClientLightingService.getInstance().tick();

            if (scriptingRuntime != null && scriptingRuntime.isInitialized()) {
                scriptingRuntime.processGeneralTaskQueue();
            }

            if (apiService != null && apiService.events != null) {
                apiService.events.dispatch("core:tick", client.getRenderTickCounter().getTickDelta(true));
            }

            if (apiService != null) {
                apiService.getUpdateManager().tick();
            }
            if (clientCameraManager != null) {
                clientCameraManager.tick();
            }

            ClientPlayerModelManager.getInstance().getModels().forEach(AnimatedPlayerModel::tick);

            ClientMovementTracker.getInstance().tick();
        });
    }

    private void registerRenderHandler() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (playerModelRenderer != null && !ClientPlayerModelManager.getInstance().getModels().isEmpty()) {
                var camera = context.camera();
                var world = MinecraftClient.getInstance().world;
                float tickDelta = context.tickCounter().getTickDelta(true);

                for (AnimatedPlayerModel model : ClientPlayerModelManager.getInstance().getModels()) {
                    double x = model.getInterpolatedX(tickDelta) - camera.getPos().getX();
                    double y = model.getInterpolatedY(tickDelta) - camera.getPos().getY();
                    double z = model.getInterpolatedZ(tickDelta) - camera.getPos().getZ();

                    MatrixStack matrices = new MatrixStack();
                    matrices.translate(x, y, z);

                    int light = WorldRenderer.getLightmapCoordinates(world, model.getBlockPos());

                    playerModelRenderer.render(model, matrices, context.consumers(), light, tickDelta);
                }
            }
            if (clientCursorManager != null) {
                clientCursorManager.render(
                        context.matrixStack(),
                        context.consumers(),
                        context.tickCounter().getTickDelta(true)
                );
            }
        });
    }

    private void registerShutdownHandler() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> cleanupMoudServices());
    }
    private void onJoinServer(ClientPlayNetworkHandler handler, net.fabricmc.fabric.api.networking.v1.PacketSender sender, MinecraftClient client) {
        initializeMoudServices();

        resourcesLoaded.set(false);
        waitingForResources.set(true);
        joinTime = System.currentTimeMillis();

        ClientPacketWrapper.sendToServer(new MoudPackets.HelloPacket(PROTOCOL_VERSION));
    }

    private void onDisconnectServer(ClientPlayNetworkHandler handler, MinecraftClient client) {
        isOnMoudServer = false;
        if (apiService != null && apiService.events != null) {
            apiService.events.dispatch("core:disconnect", "Player disconnected");
        }

        joinTime = -1L;

        if (client.player != null) {
            PlayerPartConfigManager.getInstance().clearConfig(client.player.getUuid());
        }

        LOGGER.info("Disconnecting from server, cleaning up...");
        ClientPlayerModelManager.getInstance().clear();
        dynamicPack.set(null);
        this.currentResourcesHash = "";
        cleanupMoudServices();

        setCustomCameraActive(false);
    }

    private void registerAnimationLayer() {
        PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, manager) -> {
            manager.addAnimLayer(10000, new ExternalPartConfigLayer(player.getUuid()));
        });
    }

    private void handleSyncScripts(SyncClientScriptsPacket packet) {
        isOnMoudServer = true;
        joinTime = -1L;

        if (apiService != null && apiService.events != null) {
            apiService.events.dispatch("core:scriptsReceived", packet.hash());
        }

        if (currentResourcesHash.equals(packet.hash())) {
            loadScriptsOnly(packet.scriptData());
            resourcesLoaded.set(true);
            waitingForResources.set(false);
            return;
        }

        waitingForResources.set(true);
        this.currentResourcesHash = packet.hash();
        Map<String, byte[]> scriptsData = new HashMap<>();
        Map<String, byte[]> assetsData = new HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(packet.scriptData()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                byte[] data = zis.readAllBytes();
                if (name.startsWith("scripts/")) {
                    scriptsData.put(name.substring("scripts/".length()), data);
                } else if (name.startsWith("assets/")) {
                    assetsData.put(name, data);
                    if (name.contains("animation") && name.endsWith(".json")) {
                        String animationName = name.substring(name.lastIndexOf('/') + 1, name.lastIndexOf('.'));
                        LOGGER.info("Loading animation: {} from path: {}", animationName, name);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error unpacking script & asset archive", e);
            waitingForResources.set(false);
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            InMemoryPackResources newPack = new InMemoryPackResources(MOUDPACK_ID.toString(), Text.of("Moud Dynamic Server Resources"), assetsData);
            dynamicPack.set(newPack);

            ResourcePackManager manager = client.getResourcePackManager();

            manager.scanPacks();

            List<String> enabledPacks = new ArrayList<>(manager.getEnabledIds());
            if (!enabledPacks.contains(MOUDPACK_ID.toString())) {
                enabledPacks.add(MOUDPACK_ID.toString());
            }


            manager.setEnabledProfiles(enabledPacks);

            loadScriptsOnly(scriptsData);
            resourcesLoaded.set(true);
            waitingForResources.set(false);
            if (apiService != null && apiService.events != null) {
                apiService.events.dispatch("core:resourcesReloaded");
            }
            LOGGER.info("Dynamic resources enabled and scripts loaded.");
        });
    }

    public boolean shouldBlockJoin() {
        return waitingForResources.get() && !resourcesLoaded.get();
    }

    private void handlePlayPlayerAnimation(S2C_PlayPlayerAnimationPacket packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            IAnimatedPlayer animatedPlayer = (IAnimatedPlayer) client.player;
            animatedPlayer.getAnimationPlayer().playAnimation(packet.animationId());
        }
    }

    private void handlePlayModelAnimation(MoudPackets.S2C_PlayModelAnimationPacket packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            AnimatedPlayerModel model = ClientPlayerModelManager.getInstance().getModel(packet.modelId());
            if (model != null) {
                model.playAnimation(packet.animationId());
                LOGGER.info("Playing animation '{}' on model {}.", packet.animationId(), packet.modelId());
            } else {
                LOGGER.warn("Received animation for unknown model ID: {}", packet.modelId());
            }
        });
    }

    private void handlePlayerModelCreate(PlayerModelCreatePacket packet) {
        MinecraftClient.getInstance().execute(() -> {
            AnimatedPlayerModel model = ClientPlayerModelManager.getInstance().createModel(packet.modelId());
            if (model == null) {
                LOGGER.warn("Failed to create player model {} because the client world is unavailable", packet.modelId());
                return;
            }

            model.updatePositionAndRotation(packet.position(), 0, 0);
            if (packet.skinUrl() != null && !packet.skinUrl().isEmpty()) {
                model.updateSkin(packet.skinUrl());
            }
            LOGGER.info("Created player model with ID: {} at position: {}", packet.modelId(), packet.position());
        });
    }

    private void handlePlayerModelRemove(PlayerModelRemovePacket packet) {
        MinecraftClient.getInstance().execute(() -> {
            ClientPlayerModelManager.getInstance().removeModel(packet.modelId());
            LOGGER.info("Removed player model with ID: {}", packet.modelId());
        });
    }

    private void loadScriptsOnly(byte[] zippedScriptData) {
        Map<String, byte[]> scriptsData = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zippedScriptData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().startsWith("scripts/")) continue;
                scriptsData.put(entry.getName().substring("scripts/".length()), zis.readAllBytes());
            }
        } catch (IOException e) {
            LOGGER.error("Error unpacking scripts from archive for script-only load", e);
        }
        loadScriptsOnly(scriptsData);
    }

    private void loadScriptsOnly(Map<String, byte[]> scriptsData) {
        if (scriptingRuntime == null || apiService == null) {
            LOGGER.error("Scripting runtime or API service is null. Aborting script load.");
            return;
        }
        scriptingRuntime.initialize().thenCompose(v -> {
                    apiService.updateScriptingContext(scriptingRuntime.getContext());
                    if (!scriptsData.isEmpty()) {
                        return scriptingRuntime.loadScripts(scriptsData);
                    }
                    return CompletableFuture.completedFuture(null);
                }).thenRun(() -> LOGGER.info("Client scripts loaded successfully"))
                .exceptionally(t -> {
                    LOGGER.error("A failure occurred during runtime initialization or script loading", t);
                    return null;
                });
    }

    private void handleScriptEvent(ClientboundScriptEventPacket packet) {
        if (scriptingRuntime != null && scriptingRuntime.isInitialized()) {
            scriptingRuntime.triggerNetworkEvent(packet.eventName(), packet.eventData());
        }
    }

    private void handleCameraLock(CameraLockPacket packet) {

        if (apiService != null && apiService.camera != null) {
            if (packet.isLocked()) {
                apiService.camera.enableCustomCamera();
            } else {
                apiService.camera.disableCustomCamera();
            }
        }
    }

    private void handleAdvancedCameraLock(AdvancedCameraLockPacket packet) {
        if (apiService != null && apiService.camera != null) {
            if (packet.isLocked()) {
                apiService.camera.enableCustomCamera();
                Value options = Value.asValue(ProxyObject.fromMap(Map.of(
                        "position", packet.position(),
                        "yaw", packet.rotation().x,
                        "pitch", packet.rotation().y,
                        "roll", packet.rotation().z
                )));
                apiService.camera.snapTo(options);
            } else {
                apiService.camera.disableCustomCamera();
            }
        }
    }

    private void handleCameraUpdate(CameraUpdatePacket packet) {
        if (apiService != null && apiService.camera != null && apiService.camera.isCustomCameraActive()) {
            Value options = Value.asValue(ProxyObject.fromMap(Map.of(
                    "position", packet.position(),
                    "yaw", packet.rotation().x,
                    "pitch", packet.rotation().y,
                    "roll", packet.rotation().z
            )));
            apiService.camera.snapTo(options);
        }
    }

    private void handlePlayerState(PlayerStatePacket packet) {
        if (playerStateManager != null) {
            playerStateManager.updatePlayerState(packet.hideHotbar(), packet.hideHand(), packet.hideExperience());
        }
    }

    private void handleSharedValueSync(SyncSharedValuesPacket packet) {
        if (sharedValueManager != null) {
            sharedValueManager.handleServerSync(packet);
        }
    }

    private void handlePlayerModelUpdate(PlayerModelUpdatePacket packet) {
        MinecraftClient.getInstance().execute(() -> {
            AnimatedPlayerModel model = ClientPlayerModelManager.getInstance().getModel(packet.modelId());
            if (model != null) {
                model.updatePositionAndRotation(packet.position(), packet.yaw(), packet.pitch());
            } else {
                LOGGER.warn("Received update for unknown model ID: {}", packet.modelId());
            }
        });
    }

    private void handlePlayerModelSkin(PlayerModelSkinPacket packet) {
        MinecraftClient.getInstance().execute(() -> {
            AnimatedPlayerModel model = ClientPlayerModelManager.getInstance().getModel(packet.modelId());
            if (model != null) {
                model.updateSkin(packet.skinUrl());
            } else {
                LOGGER.warn("Received skin update for unknown model ID: {}", packet.modelId());
            }
        });
    }

    private void handleCameraRelease(CameraReleasePacket packet) {
        if (apiService != null && apiService.camera != null) {
            apiService.camera.disableCustomCamera();
        }
    }

    @Override
    public void register(Consumer<ResourcePackProfile> profileAdder) {
        InMemoryPackResources pack = dynamicPack.get();
        if (pack != null) {
            ResourcePackProfile.PackFactory factory = new ResourcePackProfile.PackFactory() {
                @Nullable
                @Override
                public ResourcePack open(ResourcePackInfo info) {
                    return pack;
                }

                @Nullable
                @Override
                public ResourcePack openWithOverlays(ResourcePackInfo info, ResourcePackProfile.Metadata metadata) {
                    return pack;
                }
            };

            ResourcePackInfo info = new ResourcePackInfo(MOUDPACK_ID.toString(), Text.of("Moud Dynamic Server Resources"), ResourcePackSource.SERVER, null);
            ResourcePackProfile.Metadata metadata = new ResourcePackProfile.Metadata(Text.of("Moud Dynamic Server Resources"), ResourcePackCompatibility.COMPATIBLE, FeatureSet.empty(), Collections.emptyList());
            ResourcePackPosition position = new ResourcePackPosition(true, ResourcePackProfile.InsertionPosition.TOP, false);
            ResourcePackProfile profile = new ResourcePackProfile(info, factory, metadata, position);

            profileAdder.accept(profile);
        }
    }

    private void registerResourcePackProvider() {
        try {
            Field providerField = MinecraftClient.getInstance().getResourcePackManager().getClass().getDeclaredField("providers");
            providerField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<ResourcePackProvider> providers = (Set<ResourcePackProvider>) providerField.get(MinecraftClient.getInstance().getResourcePackManager());
            providers.add(this);
            LOGGER.info("Successfully registered dynamic resource pack provider.");
        } catch (Exception e) {
            LOGGER.error("Failed to register dynamic resource pack provider via reflection", e);
        }
    }

    public static boolean isOnMoudServer() {
        return isOnMoudServer;
    }

}