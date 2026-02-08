package com.moud.client;

import com.moud.client.animation.ClientPlayerModelManager;
import com.moud.client.animation.PlayerPartConfigManager;
import com.moud.client.api.service.ClientAPIService;
import com.moud.client.audio.VoiceKeybindManager;
import com.moud.client.display.ClientDisplayManager;
import com.moud.client.editor.assets.AssetThumbnailCache;
import com.moud.client.editor.selection.EditorSelectionRenderer;
import com.moud.client.editor.selection.SceneSelectionManager;
import com.moud.client.editor.scene.blueprint.BlueprintPreviewRenderer;
import com.moud.client.editor.scene.blueprint.BlueprintSchematicPreviewRenderer;
import com.moud.client.editor.scene.blueprint.BlueprintGhostObjectPreviewRenderer;
import com.moud.client.editor.ui.EditorImGuiLayer;
import com.moud.client.editor.ui.WorldViewCapture;
import com.moud.client.init.ClientNetworkRegistry;
import com.moud.client.init.ClientRenderController;
import com.moud.client.init.ClientServiceManager;
import com.moud.client.init.ScriptBundleLoader;
import com.moud.client.mixin.accessor.ResourcePackManagerAccessor;
import com.moud.client.model.ClientModelManager;
import com.moud.client.network.ClientPacketWrapper;
import com.moud.client.particle.ParticleEmitterSystem;
import com.moud.client.particle.ParticleSystem;
import com.moud.client.permissions.ClientPermissionState;
import com.moud.client.rendering.FramebufferTextureExports;
import com.moud.client.resources.InMemoryPackResources;
import com.moud.client.util.WindowAnimator;
import com.moud.client.ui.loading.MoudPreloadState;
import com.moud.client.ui.screen.MoudPreloadScreen;
import com.moud.client.zone.ClientZoneManager;
import com.moud.network.MoudPackets;
import com.moud.network.protocol.MoudProtocol;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.resource.*;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class MoudClientMod implements ClientModInitializer, ResourcePackProvider {
    public static final Logger LOGGER = LoggerFactory.getLogger("MoudClient");

    private static MoudClientMod instance;
    private static final String MOUD_SHADER_PACK_ID = "moud_shaders";
    private static final boolean ENABLE_MOUD_SHADER_PACK = !"false".equalsIgnoreCase(System.getProperty("moud.enableMoudShaders", "true"));

    private final ClientServiceManager serviceManager = new ClientServiceManager();
    private final ScriptBundleLoader scriptLoader = new ScriptBundleLoader();
    private final ClientRenderController renderController = new ClientRenderController();
    private final ClientNetworkRegistry networkRegistry = new ClientNetworkRegistry();
    private InMemoryPackResources moudShaderPack;

    private boolean isOnMoudServer = false;
    private long joinAttemptStartMillis = 0L;
    private static final long PRELOAD_NON_MOUD_TIMEOUT_MILLIS = 3_000L;

    public static Logger getLogger() {
        return LOGGER;
    }

    public static MoudClientMod getInstance() {
        return instance;
    }

    public static boolean isOnMoudServer() {
        return instance != null && instance.isOnMoudServer;
    }

    public static boolean isCustomCameraActive() {
        return ClientRenderController.isCustomCameraActive();
    }

    public static void setCustomCameraActive(boolean active) {
        ClientRenderController.setCustomCameraActive(active);
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        VoiceKeybindManager.init();
        LOGGER.info("Initializing Moud client...");

        initializeMoudShaderPack();

        serviceManager.initializeBaseSystems();
        networkRegistry.registerPackets(this, serviceManager, scriptLoader);
        renderController.register(serviceManager);

        registerLifecycleEvents();
        registerHudRenderer();
        registerAnimationLayer();
        registerResourcePackProvider();
        registerResourceReloadListener();
        initializeEditorHelpers();

        LOGGER.info("Moud client initialization complete.");
    }

    private void registerResourceReloadListener() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.of("moud", "model_reloader");
            }

            @Override
            public void reload(ResourceManager manager) {
                ClientModelManager.getInstance().reloadModels();
            }
        });
    }

    private void registerLifecycleEvents() {
        ClientLoginConnectionEvents.INIT.register((handler, client) -> onLoginStart(client));
        ClientPlayConnectionEvents.JOIN.register(this::onJoinServer);
        ClientPlayConnectionEvents.DISCONNECT.register(this::onDisconnectServer);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            scriptLoader.clear();
            serviceManager.shutdown();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> scriptLoader.tick(this, serviceManager));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!MoudPreloadState.isActive()) {
                return;
            }
            if (isOnMoudServer) {
                return;
            }
            long started = joinAttemptStartMillis;
            if (started <= 0L) {
                return;
            }
            long elapsed = System.currentTimeMillis() - started;
            if (elapsed >= PRELOAD_NON_MOUD_TIMEOUT_MILLIS) {
                MoudPreloadState.reset();
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(VoiceKeybindManager::tick);
    }

    private void onLoginStart(MinecraftClient client) {
        joinAttemptStartMillis = System.currentTimeMillis();
        MoudPreloadState.begin();
        MoudPreloadState.setPhase("Joining server...");
        MoudPreloadState.setProgress(0.01f);
        client.execute(() -> {
            MinecraftClient.getInstance().setScreen(new MoudPreloadScreen());
        });
    }

    private void registerHudRenderer() {
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            WindowAnimator.tick();

            ClientAPIService apiService = serviceManager.getApiService();
            if (apiService != null && apiService.events != null) {
                apiService.events.dispatch("render:hud", tickCounter.getTickDelta(true));
            }
            EditorImGuiLayer.getInstance().render();
        });
    }

    private void registerAnimationLayer() {
        PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, manager) -> manager.addAnimLayer(10000, new com.moud.client.animation.ExternalPartConfigLayer(player.getUuid())));
    }

    private void initializeEditorHelpers() {
        try {
            WorldViewCapture.initialize();
        } catch (Throwable t) {
            LOGGER.error("Failed to initialize WorldViewCapture", t);
        }
        try {
            EditorSelectionRenderer.initialize();
        } catch (Throwable t) {
            LOGGER.error("Failed to initialize EditorSelectionRenderer", t);
        }
        try {
            BlueprintPreviewRenderer.initialize();
        } catch (Throwable t) {
            LOGGER.error("Failed to initialize BlueprintPreviewRenderer", t);
        }
        try {
            BlueprintSchematicPreviewRenderer.initialize();
        } catch (Throwable t) {
            LOGGER.error("Failed to initialize BlueprintSchematicPreviewRenderer", t);
        }
        try {
            BlueprintGhostObjectPreviewRenderer.initialize();
        } catch (Throwable t) {
            LOGGER.error("Failed to initialize BlueprintGhostObjectPreviewRenderer", t);
        }
    }

    private void onJoinServer(ClientPlayNetworkHandler handler, PacketSender sender, MinecraftClient client) {
        if (!MoudPreloadState.isActive()) {
            MoudPreloadState.begin();
        }
        MoudPreloadState.setPhase("Connected, waiting for data...");
        MoudPreloadState.setProgress(0.03f);
        client.execute(() -> {
            MinecraftClient.getInstance().setScreen(new MoudPreloadScreen());
        });

        serviceManager.initializeRuntimeServices();
        try {
            if (serviceManager.getRuntime() != null) {
                serviceManager.getRuntime().initialize();
            }
        } catch (Throwable ignored) {
        }
        scriptLoader.resetState();
        scriptLoader.onJoin(handler);
        ClientZoneManager.clear();
        ClientPermissionState.getInstance().reset();
        ClientPacketWrapper.sendToServer(new MoudPackets.HelloPacket(MoudProtocol.PROTOCOL_VERSION));
    }

    private void onDisconnectServer(ClientPlayNetworkHandler handler, MinecraftClient client) {
        markAsMoudServer(false);
        MoudPreloadState.reset();
        joinAttemptStartMillis = 0L;
        ClientPermissionState.getInstance().reset();
        scriptLoader.onDisconnect(handler);

        com.moud.client.api.service.ClientAPIService apiService = serviceManager.getApiService();
        if (apiService != null && apiService.events != null) {
            apiService.events.dispatch("core:disconnect", "Player disconnected");
        }

        if (client.player != null) {
            PlayerPartConfigManager.getInstance().clearConfig(client.player.getUuid());
        }

        LOGGER.info("Disconnecting from server, cleaning up...");
        MinecraftClient.getInstance().execute(() -> {
            ClientModelManager.getInstance().clear();
            ClientPlayerModelManager.getInstance().clear();
            ClientDisplayManager.getInstance().clear();
            ClientZoneManager.clear();
            FramebufferTextureExports.clear();
            SceneSelectionManager.getInstance().clear();
            AssetThumbnailCache.getInstance().clear();
            renderController.resetFrameTime();
            serviceManager.cleanupRuntimeServices();
            ClientRenderController.setCustomCameraActive(false);
        });
    }

    public void markAsMoudServer(boolean isMoud) {
        this.isOnMoudServer = isMoud;
    }

    @Override
    public void register(Consumer<ResourcePackProfile> profileAdder) {
        scriptLoader.registerResourcePack(profileAdder);
        registerMoudShaderPack(profileAdder);
    }

    private void registerResourcePackProvider() {
        MinecraftClient client = MinecraftClient.getInstance();
        ResourcePackManager manager = client.getResourcePackManager();
        if (manager instanceof ResourcePackManagerAccessor accessor) {
            accessor.moud$getProviders().add(this);
            LOGGER.info("Registered dynamic resource pack provider.");
        } else {
            LOGGER.warn("Could not register dynamic resource pack provider: missing accessor.");
        }
    }

    private void initializeMoudShaderPack() {
        if (!ENABLE_MOUD_SHADER_PACK) {
            return;
        }

        Map<String, byte[]> resources = new HashMap<>();
        addClasspathResource(resources, "assets/veil/pinwheel/shaders/program/light/point.fsh");
        addClasspathResource(resources, "assets/veil/pinwheel/shaders/program/light/point.json");
        addClasspathResource(resources, "assets/veil/pinwheel/shaders/program/light/directional.fsh");
        addClasspathResource(resources, "assets/veil/pinwheel/shaders/program/light/directional.json");
        addClasspathResource(resources, "assets/veil/pinwheel/shaders/program/light/area.fsh");
        addClasspathResource(resources, "assets/veil/pinwheel/shaders/program/light/area.json");

        if (resources.isEmpty()) {
            LOGGER.warn("Moud shaders not found in client resources; skipping override pack");
            return;
        }

        moudShaderPack = new InMemoryPackResources(
                MOUD_SHADER_PACK_ID,
                Text.of("Moud Shaders"),
                resources
        );
        LOGGER.info("Initialized Moud shader override pack ({} resources)", resources.size());
    }

    private void registerMoudShaderPack(Consumer<ResourcePackProfile> profileAdder) {
        if (moudShaderPack == null) {
            return;
        }

        ResourcePackProfile.PackFactory factory = new ResourcePackProfile.PackFactory() {
            @Override
            public ResourcePack open(ResourcePackInfo info) {
                return moudShaderPack;
            }

            @Override
            public ResourcePack openWithOverlays(ResourcePackInfo info, ResourcePackProfile.Metadata metadata) {
                return moudShaderPack;
            }
        };

        var info = new ResourcePackInfo(
                MOUD_SHADER_PACK_ID,
                Text.of("Moud Shaders"),
                ResourcePackSource.BUILTIN,
                null
        );
        var metadata = new ResourcePackProfile.Metadata(
                Text.of("Moud Shaders"),
                ResourcePackCompatibility.COMPATIBLE,
                FeatureSet.empty(),
                Collections.emptyList()
        );
        var position = new ResourcePackPosition(true, ResourcePackProfile.InsertionPosition.TOP, true);
        profileAdder.accept(new ResourcePackProfile(info, factory, metadata, position));
    }

    private static void addClasspathResource(Map<String, byte[]> resources, String path) {
        try (InputStream in = MoudClientMod.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                return;
            }
            resources.put(path, in.readAllBytes());
        } catch (IOException ignored) {
        }
    }


    public ClientServiceManager getServiceManager() {
        return serviceManager;
    }

    public ScriptBundleLoader getScriptLoader() {
        return scriptLoader;
    }

    public ParticleEmitterSystem getParticleEmitterSystem() {
        return serviceManager.getParticleEmitterSystem();
    }

    public ParticleSystem getParticleSystem() {
        return serviceManager.getParticleSystem();
    }
}