package com.moud.client;

import com.moud.client.api.service.ClientAPIService;
import com.moud.client.cursor.ClientCursorManager;
import com.moud.client.network.ClientPacketReceiver;
import com.moud.client.network.DataPayload;
import com.moud.client.network.ClientPacketWrapper;
import com.moud.client.network.MoudPayload;
import com.moud.client.player.ClientCameraManager;
import com.moud.client.player.ClientPlayerModelManager;
import com.moud.client.player.PlayerModelRenderer;
import com.moud.client.player.PlayerStateManager;
import com.moud.client.resources.InMemoryPackResources;
import com.moud.client.runtime.ClientScriptingRuntime;
import com.moud.client.shared.SharedValueManager;
import com.moud.network.MoudPackets;
import com.moud.network.MoudPackets.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.resource.*;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class MoudClientMod implements ClientModInitializer, ResourcePackProvider {
    public static final int PROTOCOL_VERSION = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger(MoudClientMod.class);
    private static final Identifier MOUDPACK_ID = Identifier.of("moud", "dynamic_resources");

    private static boolean customCameraActive = false;

    private ClientScriptingRuntime scriptingRuntime;
    private ClientAPIService apiService;
    private SharedValueManager sharedValueManager;
    private ClientCameraManager clientCameraManager;
    private PlayerStateManager playerStateManager;
    private PlayerModelRenderer playerModelRenderer;
    private ClientPlayerModelManager playerModelManager;
    private ClientCursorManager clientCursorManager;

    private final AtomicReference<InMemoryPackResources> dynamicPack = new AtomicReference<>(null);
    private String currentResourcesHash = "";

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Moud client...");

        PayloadTypeRegistry.playS2C().register(DataPayload.ID, DataPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MoudPayload.ID, MoudPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DataPayload.ID, DataPayload.CODEC);

        ClientPacketReceiver.registerS2CPackets();

        this.playerModelManager = ClientPlayerModelManager.getInstance();
        this.playerModelRenderer = new PlayerModelRenderer();
        this.clientCursorManager = ClientCursorManager.getInstance();

        registerPacketHandlers();
        registerEventHandlers();
        registerResourcePackProvider();
        registerTickHandler();
        registerRenderHandler();
        registerShutdownHandler();
        LOGGER.info("Moud client initialization complete.");
    }

    private void registerPacketHandlers() {
        ClientPacketWrapper.registerHandler(SyncClientScriptsPacket.class, (player, packet) -> handleSyncScripts(packet));
        ClientPacketWrapper.registerHandler(ClientboundScriptEventPacket.class, (player, packet) -> handleScriptEvent(packet));
        ClientPacketWrapper.registerHandler(CameraLockPacket.class, (player, packet) -> handleCameraLock(packet));
        ClientPacketWrapper.registerHandler(PlayerStatePacket.class, (player, packet) -> handlePlayerState(packet));
        ClientPacketWrapper.registerHandler(SyncSharedValuesPacket.class, (player, packet) -> handleSharedValueSync(packet));
        ClientPacketWrapper.registerHandler(PlayerModelCreatePacket.class, (player, packet) -> handlePlayerModelCreate(packet));
        ClientPacketWrapper.registerHandler(PlayerModelUpdatePacket.class, (player, packet) -> handlePlayerModelUpdate(packet));
        ClientPacketWrapper.registerHandler(PlayerModelSkinPacket.class, (player, packet) -> handlePlayerModelSkin(packet));
        ClientPacketWrapper.registerHandler(PlayerModelAnimationPacket.class, (player, packet) -> handlePlayerModelAnimation(packet));
        ClientPacketWrapper.registerHandler(PlayerModelRemovePacket.class, (player, packet) -> handlePlayerModelRemove(packet));
        ClientPacketWrapper.registerHandler(AdvancedCameraLockPacket.class, (player, packet) -> handleAdvancedCameraLock(packet));
        ClientPacketWrapper.registerHandler(CameraUpdatePacket.class, (player, packet) -> handleCameraUpdate(packet));
        ClientPacketWrapper.registerHandler(CameraReleasePacket.class, (player, packet) -> handleCameraRelease(packet));

        ClientPacketWrapper.registerHandler(CursorPositionUpdatePacket.class, (player, packet) -> {
            LOGGER.debug("Received CursorPositionUpdatePacket with {} updates", packet.updates().size());
            if (clientCursorManager != null) {
                clientCursorManager.handlePositionUpdates(packet.updates());
            } else {
                LOGGER.warn("ClientCursorManager is null when handling position updates");
            }
        });

        ClientPacketWrapper.registerHandler(CursorAppearancePacket.class, (player, packet) -> {
            LOGGER.info("Received CursorAppearancePacket for player {}: texture={}, scale={}",
                    packet.playerId(), packet.texture(), packet.scale());
            if (clientCursorManager != null) {
                clientCursorManager.handleAppearanceUpdate(packet.playerId(), packet.texture(), packet.color(), packet.scale(), packet.renderMode());
            } else {
                LOGGER.warn("ClientCursorManager is null when handling appearance update");
            }
        });

        ClientPacketWrapper.registerHandler(CursorVisibilityPacket.class, (player, packet) -> {
            LOGGER.info("Received CursorVisibilityPacket for player {}: visible={}",
                    packet.playerId(), packet.visible());
            if (clientCursorManager != null) {
                clientCursorManager.handleVisibilityUpdate(packet.playerId(), packet.visible());
            } else {
                LOGGER.warn("ClientCursorManager is null when handling visibility update");
            }
        });

        ClientPacketWrapper.registerHandler(RemoveCursorsPacket.class, (player, packet) -> {
            LOGGER.info("Received RemoveCursorsPacket for {} players", packet.playerIds().size());
            if (clientCursorManager != null) {
                clientCursorManager.handleRemoveCursors(packet.playerIds());
            } else {
                LOGGER.warn("ClientCursorManager is null when handling cursor removal");
            }
        });

        LOGGER.info("Internal packet handlers registered.");
    }

    private void initializeMoudServices() {
        LOGGER.info("Initializing Moud services for new connection...");
        if (apiService != null) { cleanupMoudServices(); }
        this.apiService = new ClientAPIService();
        this.scriptingRuntime = new ClientScriptingRuntime(this.apiService);
        this.apiService.setRuntime(this.scriptingRuntime);
        this.sharedValueManager = SharedValueManager.getInstance();
        this.sharedValueManager.initialize();
        this.clientCameraManager = new ClientCameraManager();
        this.playerStateManager = PlayerStateManager.getInstance();
        this.playerModelManager = ClientPlayerModelManager.getInstance();
        this.clientCursorManager = ClientCursorManager.getInstance();
        LOGGER.info("ClientCursorManager initialized: {}", clientCursorManager != null);
    }

    private void cleanupMoudServices() {
        LOGGER.info("Cleaning up Moud services...");
        if (scriptingRuntime != null) { scriptingRuntime.shutdown(); scriptingRuntime = null; }
        if (apiService != null) { apiService.cleanup(); apiService = null; }
        if (sharedValueManager != null) { sharedValueManager.cleanup(); }
        if (playerStateManager != null) { playerStateManager.reset(); }
        if (playerModelManager != null) { playerModelManager.cleanup(); }
        if (clientCursorManager != null) { clientCursorManager.clear(); }
        this.clientCameraManager = null;
        this.playerStateManager = null;
    }

    private void registerEventHandlers() {
        ClientPlayConnectionEvents.JOIN.register(this::onJoinServer);
        ClientPlayConnectionEvents.DISCONNECT.register(this::onDisconnectServer);
    }

    private void registerTickHandler() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (scriptingRuntime != null && scriptingRuntime.isInitialized()) { scriptingRuntime.processScriptQueue(); }
            if (apiService != null) { apiService.getUpdateManager().tick(); }
            if (clientCameraManager != null) { clientCameraManager.tick(); }
            if (clientCursorManager != null) {
                float delta = client.getRenderTickCounter().getTickDelta(false);
                clientCursorManager.tick(delta);
            }
        });
    }

    private void registerRenderHandler() {
        LOGGER.info("Registering WorldRenderEvents.LAST handler");

        WorldRenderEvents.LAST.register(context -> {
            if (clientCursorManager != null) {
                if (context.consumers() instanceof VertexConsumerProvider.Immediate immediate) {
                    immediate.draw();
                }
                clientCursorManager.render(context.matrixStack(), context.consumers(), context.tickCounter().getTickDelta(true));
            }
        });

        LOGGER.info("WorldRenderEvents.LAST handler registered successfully");
    }

    private void registerShutdownHandler() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> cleanupMoudServices());
    }

    private void onJoinServer(ClientPlayNetworkHandler handler, net.fabricmc.fabric.api.networking.v1.PacketSender sender, MinecraftClient client) {
        LOGGER.info("Joining server, initializing Moud services...");
        initializeMoudServices();
        ClientPacketWrapper.sendToServer(new MoudPackets.HelloPacket(PROTOCOL_VERSION));
    }

    private void onDisconnectServer(ClientPlayNetworkHandler handler, MinecraftClient client) {
        LOGGER.info("Disconnecting from server, cleaning up...");
        if (dynamicPack.getAndSet(null) != null) {
            client.execute(client::reloadResources);
        }
        this.currentResourcesHash = "";
        cleanupMoudServices();
        setCustomCameraActive(false);
    }

    private void handleSyncScripts(SyncClientScriptsPacket packet) {
        if (currentResourcesHash.equals(packet.hash())) {
            loadScriptsOnly(packet.scriptData());
            return;
        }
        this.currentResourcesHash = packet.hash();
        Map<String, byte[]> scriptsData = new HashMap<>();
        Map<String, byte[]> assetsData = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(packet.scriptData()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                byte[] data = zis.readAllBytes();
                if (name.startsWith("scripts/")) { scriptsData.put(name.substring("scripts/".length()), data); }
                else if (name.startsWith("assets/")) { assetsData.put(name, data); }
            }
        } catch (IOException e) {
            LOGGER.error("Error unpacking script & asset archive", e);
            return;
        }

        dynamicPack.set(new InMemoryPackResources(MOUDPACK_ID.toString(), Text.of("Moud Dynamic Server Resources"), assetsData));

        MinecraftClient.getInstance().getResourcePackManager().scanPacks();
        MinecraftClient.getInstance().reloadResources().thenRunAsync(() -> loadScriptsOnly(scriptsData), MinecraftClient.getInstance());
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
                    if (!scriptsData.isEmpty()) { return scriptingRuntime.loadScripts(scriptsData); }
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

    private void handleCameraLock(CameraLockPacket packet) { if (apiService != null && apiService.camera != null) { if (packet.isLocked()) { apiService.camera.enableCustomCamera(); apiService.camera.setLockedPosition(packet.position()); apiService.camera.setRenderYawOverride(packet.yaw()); apiService.camera.setRenderPitchOverride(packet.pitch()); } else { apiService.camera.disableCustomCamera(); } } }
    private void handlePlayerState(PlayerStatePacket packet) { if (playerStateManager != null) { playerStateManager.updatePlayerState(packet.hideHotbar(), packet.hideHand(), packet.hideExperience()); } }
    private void handleSharedValueSync(SyncSharedValuesPacket packet) { if (sharedValueManager != null) { sharedValueManager.handleServerSync(packet); } }
    private void handlePlayerModelCreate(PlayerModelCreatePacket packet) { if (playerModelManager != null) { playerModelManager.createModel(packet.modelId(), packet.position(), packet.skinUrl()); } }
    private void handlePlayerModelUpdate(PlayerModelUpdatePacket packet) { if (playerModelManager != null) { playerModelManager.updateModel(packet.modelId(), packet.position(), packet.yaw(), packet.pitch()); } }
    private void handlePlayerModelSkin(PlayerModelSkinPacket packet) { if (playerModelManager != null) { playerModelManager.updateSkin(packet.modelId(), packet.skinUrl()); } }
    private void handlePlayerModelAnimation(PlayerModelAnimationPacket packet) { if (playerModelManager != null) { playerModelManager.playAnimation(packet.modelId(), packet.animationName()); } }
    private void handlePlayerModelRemove(PlayerModelRemovePacket packet) { if (playerModelManager != null) { playerModelManager.removeModel(packet.modelId()); } }

    private void handleAdvancedCameraLock(AdvancedCameraLockPacket packet) {
        if (apiService != null && apiService.camera != null) {
            if (packet.isLocked()) {
                apiService.camera.setAdvancedCameraLock(
                        packet.position(),
                        packet.rotation(),
                        packet.smoothTransitions(),
                        packet.transitionSpeed(),
                        packet.disableViewBobbing(),
                        packet.disableHandMovement()
                );
            } else {
                apiService.camera.disableCustomCamera();
            }
        }
    }

    private void handleCameraUpdate(CameraUpdatePacket packet) {
        if (apiService != null && apiService.camera != null && apiService.camera.isCustomCameraActive()) {
            apiService.camera.setLockedPosition(packet.position());
            apiService.camera.setRenderYawOverride(packet.rotation().x);
            apiService.camera.setRenderPitchOverride(packet.rotation().y);
            apiService.camera.setRenderRollOverride(packet.rotation().z);
        }
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

    public static boolean isCustomCameraActive() { return customCameraActive; }
    public static void setCustomCameraActive(boolean active) { customCameraActive = active; }
}