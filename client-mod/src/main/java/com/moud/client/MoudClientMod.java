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
import com.moud.client.util.WindowAnimator;
import com.moud.network.MoudPackets;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackProvider;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public final class MoudClientMod implements ClientModInitializer, ResourcePackProvider {
    public static final int PROTOCOL_VERSION = 1;
    public static final Logger LOGGER = LoggerFactory.getLogger("MoudClient");

    private static MoudClientMod instance;

    private final ClientServiceManager serviceManager = new ClientServiceManager();
    private final ScriptBundleLoader scriptLoader = new ScriptBundleLoader();
    private final ClientRenderController renderController = new ClientRenderController();
    private final ClientNetworkRegistry networkRegistry = new ClientNetworkRegistry();

    private boolean isOnMoudServer = false;

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
        ClientPlayConnectionEvents.JOIN.register(this::onJoinServer);
        ClientPlayConnectionEvents.DISCONNECT.register(this::onDisconnectServer);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            scriptLoader.clear();
            serviceManager.shutdown();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> scriptLoader.tick(this, serviceManager));
        ClientTickEvents.END_CLIENT_TICK.register(VoiceKeybindManager::tick);
    }

    private void registerHudRenderer() {
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            WindowAnimator.tick();

            com.moud.client.api.service.ClientAPIService apiService = serviceManager.getApiService();
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
        WorldViewCapture.initialize();
        EditorSelectionRenderer.initialize();
        BlueprintPreviewRenderer.initialize();
    }

    private void onJoinServer(ClientPlayNetworkHandler handler, PacketSender sender, MinecraftClient client) {
        serviceManager.initializeRuntimeServices();
        scriptLoader.resetState();
        scriptLoader.onJoin(handler);
        ClientPacketWrapper.sendToServer(new MoudPackets.HelloPacket(PROTOCOL_VERSION));
    }

    private void onDisconnectServer(ClientPlayNetworkHandler handler, MinecraftClient client) {
        markAsMoudServer(false);
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
            SceneSelectionManager.getInstance().clear();
            AssetThumbnailCache.getInstance().clear();
        });

        renderController.resetFrameTime();
        serviceManager.cleanupRuntimeServices();
        ClientRenderController.setCustomCameraActive(false);
    }

    public void markAsMoudServer(boolean isMoud) {
        this.isOnMoudServer = isMoud;
    }

    @Override
    public void register(Consumer<ResourcePackProfile> profileAdder) {
        scriptLoader.registerResourcePack(profileAdder);
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
