package com.moud.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.moud.api.math.Quaternion;

import com.moud.api.math.Vector3;
import com.moud.client.animation.*;
import com.moud.client.editor.EditorModeManager;
import com.moud.client.editor.ui.EditorImGuiLayer;
import com.moud.client.api.service.ClientAPIService;
import com.moud.client.collision.ModelCollisionManager;
import com.moud.client.cursor.ClientCursorManager;
import com.moud.client.display.ClientDisplayManager;
import com.moud.client.display.DisplayRenderer;
import com.moud.client.display.DisplaySurface;
import com.moud.client.display.VideoDecoder;
import com.moud.client.lighting.ClientLightingService;
import com.moud.client.model.ClientModelManager;
import com.moud.client.model.ModelRenderer;
import com.moud.client.model.RenderableModel;
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
import com.moud.client.editor.runtime.RuntimeObjectRegistry;
import com.moud.client.editor.scene.blueprint.BlueprintPreviewRenderer;
import com.moud.client.editor.scene.SceneSessionManager;
import com.moud.client.editor.scene.blueprint.ClientBlueprintNetwork;
import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.client.ui.UIOverlayManager;
import com.moud.client.util.WindowAnimator;
import com.moud.client.collision.ClientCollisionManager;
import com.moud.client.collision.Triangle;
import com.moud.client.fakeplayer.ClientFakePlayerManager;
import com.moud.network.MoudPackets;
import com.moud.network.MoudPackets.*;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import foundry.veil.api.client.render.CullFrustum;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.VeilRenderer;
import foundry.veil.api.client.render.dynamicbuffer.DynamicBufferType;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.resource.*;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class MoudClientMod implements ClientModInitializer, ResourcePackProvider {
    public static final int PROTOCOL_VERSION = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger(MoudClientMod.class);
    private static final Identifier MOUDPACK_ID = Identifier.of("moud", "dynamic_resources");
    private static final Identifier PHONG_SHADER_ID = Identifier.of("moud", "model_phong");
    private static final float DEFAULT_PHONG_AMBIENT = 0.15f;
    private static final long JOIN_TIMEOUT_MS = 10000;
    private static final int MAX_BUNDLE_SIZE_BYTES = 32 * 1024 * 1024;
    private static final boolean DISABLE_VEIL_BUFFERS = Boolean.getBoolean("moud.disableVeilBuffers");
    private static final boolean DISABLE_VEIL_BLOOM = Boolean.getBoolean("moud.disableVeilBloom");

    private ModelRenderer modelRenderer;
    private DisplayRenderer displayRenderer;

    private static final int MAX_DECOMPRESSED_SIZE_BYTES = 64 * 1024 * 1024;
    private GameJoinS2CPacket pendingGameJoinPacket = null;
    private static MoudClientMod instance;
    private static boolean customCameraActive = false;
    public static Logger getLogger() { return LOGGER; }
    private final AtomicBoolean resourcesLoaded = new AtomicBoolean(false);

    private final AtomicBoolean waitingForResources = new AtomicBoolean(false);
    private ServerInfo.ResourcePackPolicy previousResourcePackPolicy = null;
    private final AtomicReference<InMemoryPackResources> dynamicPack = new AtomicReference<>(null);
    private final Queue<java.util.function.Consumer<ClientPlayNetworkHandler>> deferredPacketHandlers = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private PlayerModelRenderer playerModelRenderer;
    private ClientScriptingRuntime scriptingRuntime;
    private ClientAPIService apiService;
    private SharedValueManager sharedValueManager;
    private ClientCameraManager clientCameraManager;
    private PlayerStateManager playerStateManager;
    private ClientCursorManager clientCursorManager;
    private ClientFakePlayerManager fakePlayerManager;
    private com.moud.client.particle.ParticleSystem particleSystem;
    private com.moud.client.particle.ParticleRenderer particleRenderer;
    private String currentResourcesHash = "";
    private final Map<String, ScriptChunkAccumulator> scriptChunkAccumulators = new HashMap<>();
    private long joinTime = -1L;
    private boolean moudServicesInitialized = false;
    private static boolean isOnMoudServer = false;
    private final Gson builtinEventParser = new Gson();

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
        this.fakePlayerManager = new ClientFakePlayerManager();
        this.playerModelRenderer = new PlayerModelRenderer();
        this.modelRenderer = new ModelRenderer();
        this.displayRenderer = new DisplayRenderer();
        this.particleSystem = new com.moud.client.particle.ParticleSystem(8192);
        this.particleRenderer = new com.moud.client.particle.ParticleRenderer(this.particleSystem);

        registerPacketHandlers();
        registerEventHandlers();
        registerResourcePackProvider();
        registerTickHandler();
        registerRenderHandler();
        registerAnimationLayer();
        registerShutdownHandler();
        com.moud.client.editor.ui.WorldViewCapture.initialize();
        com.moud.client.editor.selection.EditorSelectionRenderer.initialize();
        BlueprintPreviewRenderer.initialize();
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            WindowAnimator.tick();

            if (apiService != null && apiService.events != null) {
                apiService.events.dispatch("render:hud", drawContext, tickCounter.getTickDelta(true));
            }
            UIOverlayManager.getInstance().renderOverlays(drawContext, tickCounter);
            EditorImGuiLayer.getInstance().render();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (scriptingRuntime != null && scriptingRuntime.isInitialized()) {
                scriptingRuntime.processAnimationFrameQueue();
            }
        });
        LOGGER.info("Moud client initialization complete.");
    }

    private void registerPacketHandlers() {
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_PlayPlayerAnimationPacket.class, (player, packet) -> handlePlayPlayerAnimation(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_PlayModelAnimationPacket.class, (player, packet) -> handlePlayModelAnimation(packet));
        ClientPacketWrapper.registerHandler(SyncClientScriptsPacket.class, (player, packet) -> handleSyncScripts(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.SyncClientScriptsChunkPacket.class, (player, packet) -> handleSyncScriptsChunk(packet));
        ClientPacketWrapper.registerHandler(ClientboundScriptEventPacket.class, (player, packet) -> handleScriptEvent(packet));
        ClientPacketWrapper.registerHandler(CameraLockPacket.class, (player, packet) -> handleCameraLock(packet));
        ClientPacketWrapper.registerHandler(PlayerStatePacket.class, (player, packet) -> handlePlayerState(packet));
        ClientPacketWrapper.registerHandler(ExtendedPlayerStatePacket.class, (player, packet) -> handleExtendedPlayerState(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.ParticleBatchPacket.class, (player, packet) -> handleParticleBatch(packet));
        ClientPacketWrapper.registerHandler(SyncSharedValuesPacket.class, (player, packet) -> handleSharedValueSync(packet));
        ClientPacketWrapper.registerHandler(PlayerModelCreatePacket.class, (player, packet) -> handlePlayerModelCreate(packet));
        ClientPacketWrapper.registerHandler(PlayerModelUpdatePacket.class, (player, packet) -> handlePlayerModelUpdate(packet));
        ClientPacketWrapper.registerHandler(PlayerModelSkinPacket.class, (player, packet) -> handlePlayerModelSkin(packet));
        ClientPacketWrapper.registerHandler(PlayerModelRemovePacket.class, (player, packet) -> handlePlayerModelRemove(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_CreateDisplayPacket.class, (player, packet) -> handleCreateDisplay(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_UpdateDisplayTransformPacket.class, (player, packet) -> handleUpdateDisplayTransform(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_UpdateDisplayAnchorPacket.class, (player, packet) -> handleUpdateDisplayAnchor(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_UpdateDisplayContentPacket.class, (player, packet) -> handleUpdateDisplayContent(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_UpdateDisplayPlaybackPacket.class, (player, packet) -> handleUpdateDisplayPlayback(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_RemoveDisplayPacket.class, (player, packet) -> handleRemoveDisplay(packet));
        ClientPacketWrapper.registerHandler(AdvancedCameraLockPacket.class, (player, packet) -> handleAdvancedCameraLock(packet));
        ClientPacketWrapper.registerHandler(CameraUpdatePacket.class, (player, packet) -> handleCameraUpdate(packet));
        ClientPacketWrapper.registerHandler(CameraReleasePacket.class, (player, packet) -> handleCameraRelease(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_CreateFakePlayer.class, (player, packet) -> fakePlayerManager.handleCreate(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_UpdateFakePlayer.class, (player, packet) -> fakePlayerManager.handleUpdate(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_RemoveFakePlayer.class, (player, packet) -> fakePlayerManager.handleRemove(packet));
        ClientPacketWrapper.registerHandler(S2C_ManageWindowPacket.class, (player, packet) -> handleManageWindow(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_TransitionWindowPacket.class, (player, packet) -> handleTransitionWindow(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_PlayPlayerAnimationPacket.class, (player, packet) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                IAnimatedPlayer animatedPlayer = (IAnimatedPlayer) client.player;
                animatedPlayer.getAnimationPlayer().playAnimation(packet.animationId());
            }
        });
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_PlayPlayerAnimationWithFadePacket.class, (player, packet) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                IAnimatedPlayer animatedPlayer = (IAnimatedPlayer) client.player;
                animatedPlayer.getAnimationPlayer().playAnimationWithFade(packet.animationId(), packet.durationTicks());
            }
        });
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
        ClientPacketWrapper.registerHandler(MoudPackets.SceneStatePacket.class,
                (player, packet) -> SceneSessionManager.getInstance().handleSceneState(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.SceneEditAckPacket.class,
                (player, packet) -> SceneSessionManager.getInstance().handleEditAck(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.AnimationEventPacket.class,
                (player, packet) -> {
                    com.moud.client.editor.scene.SceneEditorDiagnostics.log(
                            "Animation event " + packet.eventName() + " on " + packet.objectId() + " payload=" + packet.payload());
                    com.moud.client.editor.ui.SceneEditorOverlay.getInstance().getTimelinePanel()
                            .pushEventIndicator(packet.eventName());
                });
        ClientPacketWrapper.registerHandler(MoudPackets.AnimationPropertyUpdatePacket.class,
                (player, packet) -> com.moud.client.editor.scene.SceneSessionManager.getInstance()
                        .mergeAnimationProperty(packet.sceneId(), packet.objectId(), packet.propertyKey(), packet.propertyType(), packet.value(), packet.payload()));
        ClientPacketWrapper.registerHandler(MoudPackets.EditorAssetListPacket.class,
                (player, packet) -> com.moud.client.editor.assets.EditorAssetCatalog.getInstance().handleAssetList(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.ProjectMapPacket.class,
                (player, packet) -> com.moud.client.editor.assets.ProjectFileIndex.getInstance().handleProjectMap(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.ProjectFileContentPacket.class,
                (player, packet) -> com.moud.client.editor.assets.ProjectFileContentCache.getInstance().handleContent(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.SceneBindingPacket.class,
                (player, packet) -> com.moud.client.editor.selection.SceneSelectionManager.getInstance().handleBindingPacket(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.BlueprintSaveAckPacket.class,
                (player, packet) -> ClientBlueprintNetwork.getInstance().handleSaveAck(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.BlueprintDataPacket.class,
                (player, packet) -> ClientBlueprintNetwork.getInstance().handleBlueprintData(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.AnimationLoadResponsePacket.class,
                (player, packet) -> SceneEditorOverlay.getInstance().handleAnimationLoadResponse(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.AnimationListResponsePacket.class,
                (player, packet) -> SceneEditorOverlay.getInstance().handleAnimationListResponse(packet));
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
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_CreateModelPacket.class, (player, packet) -> handleCreateModel(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_UpdateModelTransformPacket.class, (player, packet) -> handleUpdateModelTransform(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_UpdateModelTexturePacket.class, (player, packet) -> handleUpdateModelTexture(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_UpdateModelCollisionPacket.class, (player, packet) -> handleUpdateModelCollision(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_SyncModelCollisionBoxesPacket.class, (player, packet) -> handleSyncModelCollisionBoxes(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_RemoveModelPacket.class, (player, packet) -> handleRemoveModel(packet));

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
        ClientDisplayManager.getInstance().clear();
        this.clientCameraManager = null;
        this.playerStateManager = null;
        moudServicesInitialized = false;
        deferredPacketHandlers.clear();

    }

    private void handleCameraControl(MoudPackets.CameraControlPacket packet) {
        if (apiService == null || apiService.camera == null) return;

        switch (packet.action()) {
            case ENABLE -> apiService.camera.enableCustomCamera(packet.cameraId());
            case DISABLE -> apiService.camera.disableCustomCamera();
            case TRANSITION_TO -> {
                if (packet.options() != null) {
                    LOGGER.info("Camera TRANSITION_TO options: {}", packet.options());
                    apiService.camera.transitionToFromMap(packet.options());
                }
            }
            case SNAP_TO -> {
                if (packet.options() != null) {
                    LOGGER.info("Camera SNAP_TO options: {}", packet.options());
                    apiService.camera.snapToFromMap(packet.options());
                }
            }
        }
    }

    private void handleParticleBatch(MoudPackets.ParticleBatchPacket packet) {
        if (particleSystem != null) {
            particleSystem.spawnBatch(packet.particles());
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

            if (moudServicesInitialized) {
                ClientDisplayManager.getInstance().getDisplays().forEach(DisplaySurface::tick);
            }

            if (scriptingRuntime != null && scriptingRuntime.isInitialized()) {
                scriptingRuntime.processGeneralTaskQueue();
            }

            if (apiService != null && apiService.events != null) {
                apiService.events.dispatch("core:tick", client.getRenderTickCounter().getTickDelta(true));
            }

            if (apiService != null) {
                apiService.getUpdateManager().tick();
            }
            if (apiService != null && apiService.audio != null) {
                apiService.audio.tick();
            }
            if (apiService != null && apiService.gamepad != null) {
                apiService.gamepad.tick();
            }
            if (clientCameraManager != null) {
                clientCameraManager.tick();
            }
            if (particleSystem != null && client.world != null) {
                float dt = client.getRenderTickCounter().getTickDelta(true);
                particleSystem.tick(dt, client.world);
            }

            ClientPlayerModelManager.getInstance().getModels().forEach(AnimatedPlayerModel::tick);

            EditorModeManager.getInstance().tick(client);

            ClientMovementTracker.getInstance().tick();
            ClientModelManager.getInstance().getModels().forEach(model -> model.tickSmoothing(1.0f));
        });
    }

    private void registerRenderHandler() {
        WorldRenderEvents.AFTER_ENTITIES.register(renderContext -> {
            Camera camera = renderContext.camera();

            int modelCount = ClientPlayerModelManager.getInstance().getModels().size();
            if (modelCount > 0) {
                LOGGER.debug("Render tick: {} player models exist", modelCount);
            }

            var world = MinecraftClient.getInstance().world;
            float tickDelta = renderContext.tickCounter().getTickDelta(true);

            VeilRenderer veilRenderer = VeilRenderSystem.renderer();
            boolean enabledBuffers = false;
            if (veilRenderer != null && !DISABLE_VEIL_BUFFERS) {
                try {
                    enabledBuffers = veilRenderer.enableBuffers(VeilRenderer.COMPOSITE,
                            DynamicBufferType.ALBEDO,
                            DynamicBufferType.NORMAL);
                    if (DISABLE_VEIL_BLOOM) {

                    }
                } catch (Throwable t) {
                    LOGGER.debug("Failed to enable Veil dynamic buffers", t);
                }
            }

            try {
                if (playerModelRenderer != null && !ClientPlayerModelManager.getInstance().getModels().isEmpty()) {

                    for (AnimatedPlayerModel model : ClientPlayerModelManager.getInstance().getModels()) {
                        double x = model.getInterpolatedX(tickDelta) - camera.getPos().getX();
                        double y = model.getInterpolatedY(tickDelta) - camera.getPos().getY();
                        double z = model.getInterpolatedZ(tickDelta) - camera.getPos().getZ();

                        LOGGER.debug("Rendering player model at world({}, {}, {}) camera-rel({}, {}, {})",
                                model.getInterpolatedX(tickDelta), model.getInterpolatedY(tickDelta), model.getInterpolatedZ(tickDelta),
                                x, y, z);

                        MatrixStack matrices = new MatrixStack();
                        matrices.translate(x, y, z);

                        int light = WorldRenderer.getLightmapCoordinates(world, model.getBlockPos());

                        playerModelRenderer.render(model, matrices, renderContext.consumers(), light, tickDelta);
                    }
                }
                Vec3d cameraPos = camera.getPos();

                if (modelRenderer != null && !ClientModelManager.getInstance().getModels().isEmpty()) {
                    MatrixStack matrices = renderContext.matrixStack();
                    var consumers = renderContext.consumers();
                    if (consumers != null) {
                        for (RenderableModel model : ClientModelManager.getInstance().getModels()) {
                            Vector3 interpolatedPos = model.getInterpolatedPosition(tickDelta);
                            if (!isModelVisible(model, interpolatedPos, renderContext)) {
                                continue;
                            }
                            double dx = interpolatedPos.x - cameraPos.x;
                            double dy = interpolatedPos.y - cameraPos.y;
                            double dz = interpolatedPos.z - cameraPos.z;

                            matrices.push();
                            matrices.translate(dx, dy, dz);

                            int light = WorldRenderer.getLightmapCoordinates(world, model.getBlockPos());
                            modelRenderer.render(model, matrices, consumers, light, tickDelta);
                            matrices.pop();
                        }
                    }
                }
                if (displayRenderer != null && !ClientDisplayManager.getInstance().isEmpty()) {
                    MatrixStack matrices = renderContext.matrixStack();
                    var consumers = renderContext.consumers();
                    if (consumers != null) {
                        for (DisplaySurface surface : ClientDisplayManager.getInstance().getDisplays()) {
                            Vector3 interpolatedPos = surface.getInterpolatedPosition(tickDelta);
                            if (!isDisplayVisible(surface, interpolatedPos, renderContext)) {
                                continue;
                            }
                            double dx = interpolatedPos.x - cameraPos.x;
                            double dy = interpolatedPos.y - cameraPos.y;
                            double dz = interpolatedPos.z - cameraPos.z;

                            matrices.push();
                            matrices.translate(dx, dy, dz);

                            int light = WorldRenderer.getLightmapCoordinates(world, surface.getBlockPos());
                            displayRenderer.render(surface, matrices, consumers, light, tickDelta);
                            matrices.pop();
                        }
                    }
                }
                if (particleRenderer != null) {
                    MatrixStack matrices = renderContext.matrixStack();
                    particleRenderer.render(matrices, tickDelta);
                }
                renderModelCollisionHitboxes(renderContext);
                if (clientCursorManager != null) {
                    clientCursorManager.render(
                            renderContext.matrixStack(),
                            renderContext.consumers(),
                            renderContext.tickCounter().getTickDelta(true)
                    );
                }
                SceneEditorOverlay.getInstance().renderCameraGizmos(renderContext);
            } finally {
                if (enabledBuffers && veilRenderer != null) {
                    try {
                        veilRenderer.disableBuffers(VeilRenderer.COMPOSITE,
                                DynamicBufferType.ALBEDO,
                                DynamicBufferType.NORMAL);
                    } catch (Throwable t) {
                        LOGGER.debug("Failed to disable Veil dynamic buffers", t);
                    }
                }
            }
        });
    }

    private boolean isModelVisible(RenderableModel model, Vector3 position, WorldRenderContext context) {
        return isVisible(createModelBounds(model, position), context);
    }

    private boolean isDisplayVisible(DisplaySurface surface, Vector3 position, WorldRenderContext context) {
        return isVisible(createDisplayBounds(surface, position), context);
    }

    private boolean isVisible(Box bounds, WorldRenderContext context) {
        if (bounds == null) {
            return true;
        }
        try {
            var frustum = context.frustum();
            if (frustum == null) {
                return true;
            }
            if (frustum instanceof CullFrustum cullFrustum) {
                return cullFrustum.testAab(bounds.minX, bounds.minY, bounds.minZ,
                        bounds.maxX, bounds.maxY, bounds.maxZ);
            }
            return frustum.isVisible(bounds);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private void renderModelCollisionHitboxes(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        var dispatcher = client.getEntityRenderDispatcher();
        if (dispatcher == null || !dispatcher.shouldRenderHitboxes()) {
            return;
        }
        var boxes = ModelCollisionManager.getInstance().getDebugBoxes();
        var meshBounds = ClientCollisionManager.getDebugMeshBounds();
        var tris = ClientCollisionManager.getDebugTriangles();
        if (boxes.isEmpty() && meshBounds.isEmpty() && tris.isEmpty()) {
            return;
        }
        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) {
            return;
        }
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());
        if (buffer == null) {
            return;
        }
        MatrixStack matrices = context.matrixStack();
        Vec3d cameraPos = context.camera().getPos();
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        for (Box box : boxes) {
            WorldRenderer.drawBox(matrices, buffer, box, 1.0f, 0.2f, 0.2f, 1.0f);
        }
        if (!tris.isEmpty()) {
            for (var tri : tris) {
                drawLine(buffer, matrices, tri.v0, tri.v1, 0.1f, 0.6f, 1.0f, 1.0f);
                drawLine(buffer, matrices, tri.v1, tri.v2, 0.1f, 0.6f, 1.0f, 1.0f);
                drawLine(buffer, matrices, tri.v2, tri.v0, 0.1f, 0.6f, 1.0f, 1.0f);
            }
        } else if (!meshBounds.isEmpty()) {
            for (Box meshBound : meshBounds) {
                WorldRenderer.drawBox(matrices, buffer, meshBound, 0.1f, 0.8f, 0.1f, 1.0f);
            }
        } else {
            for (var mesh : ClientCollisionManager.getAllMeshes()) {
                if (mesh.getBounds() != null) {
                    WorldRenderer.drawBox(matrices, buffer, mesh.getBounds(), 0.1f, 0.6f, 1.0f, 1.0f);
                }
            }
        }
        matrices.pop();
    }

    private static void drawLine(VertexConsumer buffer, MatrixStack matrices, Vec3d a, Vec3d b,
                                 float r, float g, float bCol, float aCol) {
        var entry = matrices.peek();
        buffer.vertex(entry.getPositionMatrix(), (float) a.x, (float) a.y, (float) a.z)
                .color(r, g, bCol, aCol)
                .normal(entry, 0.0f, 1.0f, 0.0f);

        buffer.vertex(entry.getPositionMatrix(), (float) b.x, (float) b.y, (float) b.z)
                .color(r, g, bCol, aCol)
                .normal(entry, 0.0f, 1.0f, 0.0f);

    }

    private Box createModelBounds(RenderableModel model, Vector3 position) {
        if (model == null || position == null) {
            return null;
        }
        Vector3 scale = model.getScale();
        Vector3 meshHalf = model.getMeshHalfExtents();
        double halfX = computeHalfExtent(meshHalf != null ? meshHalf.x : Double.NaN, scale.x, model.getCollisionWidth());
        double halfY = computeHalfExtent(meshHalf != null ? meshHalf.y : Double.NaN, scale.y, model.getCollisionHeight());
        double halfZ = computeHalfExtent(meshHalf != null ? meshHalf.z : Double.NaN, scale.z, model.getCollisionDepth());
        return new Box(position.x - halfX, position.y - halfY, position.z - halfZ,
                position.x + halfX, position.y + halfY, position.z + halfZ);
    }

    private Box createDisplayBounds(DisplaySurface surface, Vector3 position) {
        if (surface == null || position == null) {
            return null;
        }
        Vector3 scale = surface.getScale();
        double halfX = Math.max(0.25, Math.abs(scale.x) * 0.5);
        double halfY = Math.max(0.25, Math.abs(scale.y) * 0.5);
        double halfZ = Math.max(0.0625, Math.abs(scale.z) * 0.5);
        return new Box(position.x - halfX, position.y - halfY, position.z - halfZ,
                position.x + halfX, position.y + halfY, position.z + halfZ);
    }

    private double computeHalfExtent(double meshValue, double scaleAxis, double collisionSize) {
        double base = !Double.isNaN(meshValue) && meshValue > 0 ? meshValue :
                (collisionSize > 0 ? collisionSize / 2.0 : 0.5);
        double scaleAbs = Math.abs(scaleAxis);
        if (scaleAbs < 1.0e-3) {
            scaleAbs = 1.0;
        }
        return Math.max(0.25, base * scaleAbs);
    }

    private static Identifier parseTextureId(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        String normalized = rawPath.trim();
        if (normalized.startsWith("moud:moud/")) {
            normalized = "moud:" + normalized.substring("moud:moud/".length());
        }
        Identifier parsed = Identifier.tryParse(normalized);
        if (parsed != null && "moud".equals(parsed.getNamespace())) {
            String path = parsed.getPath();
            if (path.startsWith("moud/") && path.length() > 5) {
                return Identifier.of("moud", path.substring(5));
            }
        }
        return parsed;
    }

    private void registerShutdownHandler() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> cleanupMoudServices());
    }

    private void onJoinServer(ClientPlayNetworkHandler handler, net.fabricmc.fabric.api.networking.v1.PacketSender sender, MinecraftClient client) {
        initializeMoudServices();
        enableServerResourcePackAutoAccept(handler);

        resourcesLoaded.set(false);
        waitingForResources.set(true);
        joinTime = System.currentTimeMillis();

        ClientPacketWrapper.sendToServer(new MoudPackets.HelloPacket(PROTOCOL_VERSION));
    }

    private void onDisconnectServer(ClientPlayNetworkHandler handler, MinecraftClient client) {
        isOnMoudServer = false;
        restoreServerResourcePackPolicy(handler);
        if (apiService != null && apiService.events != null) {
            apiService.events.dispatch("core:disconnect", "Player disconnected");
        }

        joinTime = -1L;

        if (client.player != null) {
            PlayerPartConfigManager.getInstance().clearConfig(client.player.getUuid());
        }

        LOGGER.info("Disconnecting from server, cleaning up...");
        MinecraftClient.getInstance().execute(() -> {
            ClientModelManager.getInstance().clear();
            ModelCollisionManager.getInstance().clear();
            ClientPlayerModelManager.getInstance().clear();
            ClientDisplayManager.getInstance().clear();
        });
        dynamicPack.set(null);
        this.currentResourcesHash = "";
        cleanupMoudServices();

        setCustomCameraActive(false);
    }

    private void enableServerResourcePackAutoAccept(ClientPlayNetworkHandler handler) {
        ServerInfo serverInfo = handler != null ? handler.getServerInfo() : null;
        if (serverInfo == null) {
            previousResourcePackPolicy = null;
            return;
        }
        previousResourcePackPolicy = serverInfo.getResourcePackPolicy();
        if (previousResourcePackPolicy != ServerInfo.ResourcePackPolicy.ENABLED) {
            serverInfo.setResourcePackPolicy(ServerInfo.ResourcePackPolicy.ENABLED);
            LOGGER.info("Auto-enabled server resource packs for seamless Moud experience.");
        }
    }

    private void restoreServerResourcePackPolicy(ClientPlayNetworkHandler handler) {
        if (handler == null) {
            previousResourcePackPolicy = null;
            return;
        }
        ServerInfo serverInfo = handler.getServerInfo();
        if (serverInfo != null && previousResourcePackPolicy != null) {
            serverInfo.setResourcePackPolicy(previousResourcePackPolicy);
            LOGGER.info("Restored previous server resource pack policy after disconnect.");
        }
        previousResourcePackPolicy = null;
    }

    private void registerAnimationLayer() {
        PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, manager) -> {
            manager.addAnimLayer(10000, new ExternalPartConfigLayer(player.getUuid()));
        });
    }

    private void handleSyncScriptsChunk(MoudPackets.SyncClientScriptsChunkPacket packet) {
        ScriptChunkAccumulator accumulator = scriptChunkAccumulators.computeIfAbsent(packet.hash(), h -> new ScriptChunkAccumulator(packet.totalChunks()));
        accumulator.accept(packet.chunkIndex(), packet.data());
        if (accumulator.isComplete()) {
            byte[] assembled = accumulator.assemble();
            scriptChunkAccumulators.remove(packet.hash());
            handleSyncScripts(new SyncClientScriptsPacket(packet.hash(), assembled));
        }
    }

    private void handleSyncScripts(SyncClientScriptsPacket packet) {
        isOnMoudServer = true;
        joinTime = -1L;
        scriptChunkAccumulators.remove(packet.hash());

        if (apiService != null && apiService.events != null) {
            apiService.events.dispatch("core:scriptsReceived", packet.hash());
        }

        byte[] scriptPayload = packet.scriptData();
        String expectedHash = packet.hash();

        if (scriptPayload == null || scriptPayload.length == 0) {
            if (expectedHash != null && expectedHash.equals(currentResourcesHash)) {
                LOGGER.info("Server confirmed cached client resources (hash {}).", expectedHash);
                resourcesLoaded.set(true);
                waitingForResources.set(false);
                processPendingGameJoinPacket();
            } else {
                LOGGER.warn("Received empty client bundle with hash {}. Retaining existing resources.", expectedHash);
            }
            return;
        }

        if (!currentResourcesHash.isEmpty() && expectedHash != null && expectedHash.equals(currentResourcesHash)) {
            LOGGER.warn("Server resent client bundle with already-applied hash {}. Skipping reload.", expectedHash);
            resourcesLoaded.set(true);
            waitingForResources.set(false);
            processPendingGameJoinPacket();
            return;
        }

        if (scriptPayload.length > MAX_BUNDLE_SIZE_BYTES) {
            LOGGER.error("Client bundle from server exceeds safe size ({} bytes > {} bytes).", scriptPayload.length, MAX_BUNDLE_SIZE_BYTES);
            waitingForResources.set(false);
            return;
        }

        waitingForResources.set(true);

        String computedHash = sha256(scriptPayload);
        if (expectedHash != null && !expectedHash.isEmpty() && !expectedHash.equals(computedHash)) {
            LOGGER.error("Client bundle checksum mismatch. Expected {} but computed {}.", expectedHash, computedHash);
            waitingForResources.set(false);
            return;
        }

        this.currentResourcesHash = expectedHash != null && !expectedHash.isEmpty() ? expectedHash : computedHash;

        Map<String, byte[]> scriptsData = new HashMap<>();
        Map<String, byte[]> assetsData = new HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(scriptPayload))) {
            ZipEntry entry;
            long totalExtracted = 0;
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                ByteArrayOutputStream entryBuffer = new ByteArrayOutputStream();
                int read;
                while ((read = zis.read(buffer, 0, buffer.length)) != -1) {
                    totalExtracted += read;
                    if (totalExtracted > MAX_DECOMPRESSED_SIZE_BYTES) {
                        throw new IOException("Decompressed client bundle exceeds safe limit of " + MAX_DECOMPRESSED_SIZE_BYTES + " bytes");
                    }
                    entryBuffer.write(buffer, 0, read);
                }

                byte[] data = entryBuffer.toByteArray();
                String name = entry.getName();
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
            if (assetsData.isEmpty()) {
                LOGGER.info("No assets bundled with client sync payload; assuming server resource pack covers assets.");
                dynamicPack.set(null);
                loadScriptsOnly(scriptsData);
                resourcesLoaded.set(true);
                waitingForResources.set(false);
                if (apiService != null && apiService.events != null) {
                    apiService.events.dispatch("core:resourcesReloaded");
                }
                processPendingGameJoinPacket();
                LOGGER.info("Scripts loaded without bundled assets. Hash {}.", currentResourcesHash);
                return;
            }

            InMemoryPackResources newPack = new InMemoryPackResources(MOUDPACK_ID.toString(), Text.of("Moud Dynamic Server Resources"), assetsData);
            dynamicPack.set(newPack);

            ResourcePackManager manager = client.getResourcePackManager();
            manager.scanPacks();

            List<String> enabledPacks = new ArrayList<>(manager.getEnabledIds());
            if (!enabledPacks.contains(MOUDPACK_ID.toString())) {
                enabledPacks.add(MOUDPACK_ID.toString());
            }

            manager.setEnabledProfiles(enabledPacks);

            client.reloadResources().thenRunAsync(() -> {
                LOGGER.info("Resource reload complete. Proceeding with script loading.");
                loadScriptsOnly(scriptsData);
                resourcesLoaded.set(true);
                waitingForResources.set(false);
                if (apiService != null && apiService.events != null) {
                    apiService.events.dispatch("core:resourcesReloaded");
                }
                processPendingGameJoinPacket();
                LOGGER.info("Dynamic resources enabled and scripts loaded. Hash {}.", currentResourcesHash);
            }, client);
        });
    }

    public boolean shouldBlockJoin() {
        return false;
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
        LOGGER.info("RECEIVED PlayerModelCreatePacket for model ID: {} at position: {}", packet.modelId(), packet.position());
        MinecraftClient.getInstance().execute(() -> {
            LOGGER.info("Executing PlayerModelCreate on main thread for model {}", packet.modelId());
            AnimatedPlayerModel model = ClientPlayerModelManager.getInstance().createModel(packet.modelId());
            if (model == null) {
                LOGGER.warn("Failed to create player model {} because the client world is unavailable", packet.modelId());
                return;
            }

            LOGGER.info("Model created successfully, updating position to {}", packet.position());
            model.updatePositionAndRotation(packet.position(), 0, 0);
            if (packet.skinUrl() != null && !packet.skinUrl().isEmpty()) {
                model.updateSkin(packet.skinUrl());
            }
            RuntimeObjectRegistry.getInstance().syncPlayerModel(packet.modelId(),
                    new Vec3d(packet.position().x, packet.position().y, packet.position().z),
                    new Vec3d(0, 0, 0));
            LOGGER.info("Created player model with ID: {} at position: {}", packet.modelId(), packet.position());
        });
    }

    private void handlePlayerModelRemove(PlayerModelRemovePacket packet) {
        MinecraftClient.getInstance().execute(() -> {
            ClientPlayerModelManager.getInstance().removeModel(packet.modelId());
            RuntimeObjectRegistry.getInstance().removePlayerModel(packet.modelId());
            LOGGER.info("Removed player model with ID: {}", packet.modelId());
        });
    }

    private void loadScriptsOnly(byte[] zippedScriptData) {
        if (zippedScriptData == null || zippedScriptData.length == 0) {
            LOGGER.warn("Requested to load scripts from empty archive, skipping.");
            return;
        }
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

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    private static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            char[] hexChars = new char[hash.length * 2];
            for (int i = 0; i < hash.length; i++) {
                int v = hash[i] & 0xFF;
                hexChars[i * 2] = HEX_ARRAY[v >>> 4];
                hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
            }
            return new String(hexChars);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void handleScriptEvent(ClientboundScriptEventPacket packet) {
        if (handleBuiltinScriptEvent(packet.eventName(), packet.eventData())) {
            return;
        }
        if (scriptingRuntime != null && scriptingRuntime.isInitialized()) {
            scriptingRuntime.triggerNetworkEvent(packet.eventName(), packet.eventData());
        }
    }

    private boolean handleBuiltinScriptEvent(String eventName, String payload) {
        if (apiService == null) {
            return false;
        }
        try {
            switch (eventName) {
                case "rendering:post:apply" -> {
                    JsonObject json = builtinEventParser.fromJson(payload, JsonObject.class);
                    if (json != null && json.has("id")) {
                        apiService.rendering.applyPostEffect(json.get("id").getAsString());
                    }
                    return true;
                }
                case "rendering:post:remove" -> {
                    JsonObject json = builtinEventParser.fromJson(payload, JsonObject.class);
                    if (json != null && json.has("id")) {
                        apiService.rendering.removePostEffect(json.get("id").getAsString());
                    }
                    return true;
                }
                case "rendering:post:clear" -> {
                    apiService.rendering.clearPostEffects();
                    return true;
                }
                case "ui:toast" -> {
                    JsonObject json = builtinEventParser.fromJson(payload, JsonObject.class);
                    String title = json != null && json.has("title") ? json.get("title").getAsString() : "";
                    String body = json != null && json.has("body") ? json.get("body").getAsString() : "";
                    apiService.ui.showToast(title, body);
                    return true;
                }
                default -> {
                    return false;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to handle builtin client event {}: {}", eventName, e.getMessage());
            return false;
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
                RuntimeObjectRegistry.getInstance().syncPlayerModel(packet.modelId(),
                        new Vec3d(packet.position().x, packet.position().y, packet.position().z),
                        new Vec3d(packet.pitch(), packet.yaw(), 0));
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

    private void handleCreateModel(MoudPackets.S2C_CreateModelPacket packet) {
        MinecraftClient.getInstance().execute(() -> {
            ClientModelManager.getInstance().createModel(packet.modelId(), packet.modelPath());
            RenderableModel model = ClientModelManager.getInstance().getModel(packet.modelId());
            if (model != null) {
                model.updateTransform(packet.position(), packet.rotation(), packet.scale());
                model.updateCollisionBox(packet.collisionWidth(), packet.collisionHeight(), packet.collisionDepth());
                if (packet.collisionBoxes() != null && !packet.collisionBoxes().isEmpty()) {
                    List<com.moud.api.collision.OBB> mapped = mapCollisionBoxes(packet.collisionBoxes());
                    LOGGER.info("Model {} created with {} collision boxes from server", packet.modelId(), mapped.size());
                    model.setCollisionBoxes(mapped);
                }
                Identifier textureId = parseTextureId(packet.texturePath());
                if (textureId != null) {
                    model.setTexture(textureId);
                } else if (packet.texturePath() != null && !packet.texturePath().isEmpty()) {
                    LOGGER.warn("Received invalid texture identifier '{}' for model {}", packet.texturePath(), packet.modelId());
                }
                ModelCollisionManager.getInstance().sync(model);
                RuntimeObjectRegistry.getInstance().syncModel(model);
            }
            // Register client-side collision mesh if present
            int vLen = packet.compressedMeshVertices() != null ? packet.compressedMeshVertices().length : -1;
            int iLen = packet.compressedMeshIndices() != null ? packet.compressedMeshIndices().length : -1;
            MoudPackets.CollisionMode mode = packet.collisionMode();
            if ((mode == null || mode == MoudPackets.CollisionMode.BOX) && vLen > 0 && iLen > 0) {
                mode = MoudPackets.CollisionMode.MESH; // fail-safe: payload present but mode missing/box
            }
            LOGGER.info("CreateModel collision payload: mode={}, vertsBytes={}, indicesBytes={}", mode, vLen, iLen);
            ClientCollisionManager.registerModel(
                    packet.modelId(),
                    mode,
                    packet.compressedMeshVertices(),
                    packet.compressedMeshIndices(),
                    packet.position(),
                    packet.rotation(),
                    packet.scale()
            );
        });
    }

    private void handleUpdateModelTransform(MoudPackets.S2C_UpdateModelTransformPacket packet) {
        MinecraftClient.getInstance().execute(() -> {
            RenderableModel model = ClientModelManager.getInstance().getModel(packet.modelId());
            if (model != null) {
                model.updateTransform(packet.position(), packet.rotation(), packet.scale());
                ModelCollisionManager.getInstance().sync(model);
                RuntimeObjectRegistry.getInstance().syncModel(model);
            }
            ClientCollisionManager.updateTransform(packet.modelId(), packet.position(), packet.rotation(), packet.scale());
        });
    }

    private void handleUpdateModelTexture(MoudPackets.S2C_UpdateModelTexturePacket packet) {
        MinecraftClient.getInstance().execute(() -> {
            RenderableModel model = ClientModelManager.getInstance().getModel(packet.modelId());
            if (model != null) {
                Identifier textureId = parseTextureId(packet.texturePath());
                if (textureId != null) {
                    model.setTexture(textureId);
                    RuntimeObjectRegistry.getInstance().syncModel(model);
                } else if (packet.texturePath() != null && !packet.texturePath().isEmpty()) {
                    LOGGER.warn("Received invalid texture identifier '{}' for model {}", packet.texturePath(), packet.modelId());
                }
            }
        });
    }

    private void handleUpdateModelCollision(MoudPackets.S2C_UpdateModelCollisionPacket packet) {
        MinecraftClient.getInstance().execute(() -> {
            RenderableModel model = ClientModelManager.getInstance().getModel(packet.modelId());
            if (model != null) {
                model.updateCollisionBox(packet.collisionWidth(), packet.collisionHeight(), packet.collisionDepth());
                ModelCollisionManager.getInstance().sync(model);
                RuntimeObjectRegistry.getInstance().syncModel(model);
            }
        });
    }

    private void handleSyncModelCollisionBoxes(MoudPackets.S2C_SyncModelCollisionBoxesPacket packet) {
        MinecraftClient.getInstance().execute(() -> {
            RenderableModel model = ClientModelManager.getInstance().getModel(packet.modelId());
            if (model != null) {
                List<com.moud.api.collision.OBB> mapped = mapCollisionBoxes(packet.collisionBoxes());
                LOGGER.info("Model {} received {} collision boxes from server", packet.modelId(), mapped.size());
                model.setCollisionBoxes(mapped);
                ModelCollisionManager.getInstance().sync(model);
            }
        });
    }

    private List<com.moud.api.collision.OBB> mapCollisionBoxes(List<MoudPackets.CollisionBoxData> packetBoxes) {
        List<com.moud.api.collision.OBB> collisionBoxes = new ArrayList<>();
        if (packetBoxes == null) {
            return collisionBoxes;
        }
        for (MoudPackets.CollisionBoxData boxData : packetBoxes) {
            if (boxData == null) {
                continue;
            }
            collisionBoxes.add(new com.moud.api.collision.OBB(
                boxData.center(),
                boxData.halfExtents(),
                boxData.rotation()
            ));
        }
        return collisionBoxes;
    }

    private void handleRemoveModel(MoudPackets.S2C_RemoveModelPacket packet) {
        MinecraftClient.getInstance().execute(() -> {
            ClientModelManager.getInstance().removeModel(packet.modelId());
            ClientCollisionManager.unregisterModel(packet.modelId());
        });
    }

    private void handleCreateDisplay(MoudPackets.S2C_CreateDisplayPacket packet) {
        MinecraftClient.getInstance().execute(() -> {
            ClientDisplayManager.getInstance().handleCreate(packet);
        });
    }

    private void handleUpdateDisplayTransform(MoudPackets.S2C_UpdateDisplayTransformPacket packet) {
        MinecraftClient.getInstance().execute(() -> ClientDisplayManager.getInstance().handleTransform(packet));
    }

    private void handleUpdateDisplayAnchor(MoudPackets.S2C_UpdateDisplayAnchorPacket packet) {
        MinecraftClient.getInstance().execute(() -> ClientDisplayManager.getInstance().handleAnchor(packet));
    }

    private void handleUpdateDisplayContent(MoudPackets.S2C_UpdateDisplayContentPacket packet) {
        MinecraftClient.getInstance().execute(() -> ClientDisplayManager.getInstance().handleContent(packet));
    }

    private void handleUpdateDisplayPlayback(MoudPackets.S2C_UpdateDisplayPlaybackPacket packet) {
        MinecraftClient.getInstance().execute(() -> ClientDisplayManager.getInstance().handlePlayback(packet));
    }

    private void handleRemoveDisplay(MoudPackets.S2C_RemoveDisplayPacket packet) {
        MinecraftClient.getInstance().execute(() -> ClientDisplayManager.getInstance().remove(packet.displayId()));
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
        MinecraftClient client = MinecraftClient.getInstance();
        ResourcePackManager manager = client.getResourcePackManager();
        if (manager instanceof com.moud.client.mixin.accessor.ResourcePackManagerAccessor accessor) {
            accessor.moud$getProviders().add(this);
            LOGGER.info("Registered dynamic resource pack provider.");
        } else {
            LOGGER.warn("Could not register dynamic resource pack provider: missing accessor.");
        }
    }

    public static boolean isOnMoudServer() {
        return isOnMoudServer;
    }
    public void processPendingGameJoinPacket() {
        if (pendingGameJoinPacket != null) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.getNetworkHandler() != null) {
                LOGGER.info("Processing delayed GameJoin packet now that resources are loaded.");
                client.getNetworkHandler().onGameJoin(pendingGameJoinPacket);
                pendingGameJoinPacket = null;
                drainDeferredPackets(client.getNetworkHandler());
            } else {
                LOGGER.error("Could not process pending game join packet: network handler is null!");
                client.disconnect();
            }
        }
    }
    public void setPendingGameJoinPacket(GameJoinS2CPacket packet) {
        this.pendingGameJoinPacket = packet;
        LOGGER.info("Moud client is delaying game join, waiting for server resources...");
    }

    public void enqueuePostJoinPacket(java.util.function.Consumer<ClientPlayNetworkHandler> consumer) {
        deferredPacketHandlers.offer(consumer);
    }

    private void drainDeferredPackets(ClientPlayNetworkHandler handler) {
        java.util.function.Consumer<ClientPlayNetworkHandler> task;
        while ((task = deferredPacketHandlers.poll()) != null) {
            try {
                task.accept(handler);
            } catch (Exception ex) {
                LOGGER.error("Failed to replay deferred packet", ex);
            }
        }
    }

    private static final class ScriptChunkAccumulator {
        private final byte[][] chunks;
        private int received;

        ScriptChunkAccumulator(int totalChunks) {
            this.chunks = new byte[totalChunks][];
            this.received = 0;
        }

        void accept(int index, byte[] data) {
            if (index < 0 || index >= chunks.length) {
                LOGGER.warn("Received out-of-range script chunk {} of {}", index, chunks.length);
                return;
            }
            if (chunks[index] == null) {
                chunks[index] = data;
                received++;
            }
        }

        boolean isComplete() {
            return received == chunks.length;
        }

        byte[] assemble() {
            int totalSize = 0;
            for (byte[] chunk : chunks) {
                totalSize += chunk == null ? 0 : chunk.length;
            }
            byte[] result = new byte[totalSize];
            int pos = 0;
            for (byte[] chunk : chunks) {
                if (chunk != null) {
                    System.arraycopy(chunk, 0, result, pos, chunk.length);
                    pos += chunk.length;
                }
            }
            return result;
        }
    }
}
