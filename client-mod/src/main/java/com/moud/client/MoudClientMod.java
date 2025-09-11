package com.moud.client;

import com.moud.client.api.service.ClientAPIService;
import com.moud.client.network.MoudPackets;
import com.moud.client.network.packets.SharedValuePackets;
import com.moud.client.resources.InMemoryPackResources;
import com.moud.client.runtime.ClientScriptingRuntime;
import com.moud.client.shared.SharedValueManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.resource.*;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
    private final AtomicReference<InMemoryPackResources> dynamicPack = new AtomicReference<>(null);
    private String currentResourcesHash = ""; // Stores the hash of the currently active resource pack

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Moud client...");
        setupNetworking();
        registerEventHandlers();
        registerResourcePackProvider();
        registerTickHandler();
        registerShutdownHandler();
        LOGGER.info("Moud client initialization complete. Waiting for server connection...");
    }

    private void initializeMoudServices() {
        LOGGER.info("Initializing Moud services for new connection...");
        if (apiService != null) {
            LOGGER.warn("Services already exist. Cleaning up before re-initializing.");
            cleanupMoudServices();
        }

        this.apiService = new ClientAPIService();
        this.scriptingRuntime = new ClientScriptingRuntime(this.apiService);
        this.apiService.setRuntime(this.scriptingRuntime);
        this.sharedValueManager = SharedValueManager.getInstance();
        this.sharedValueManager.initialize();
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
    }

    private void registerTickHandler() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (scriptingRuntime != null && scriptingRuntime.isInitialized()) {
                scriptingRuntime.processScriptQueue();
            }
        });
    }

    private void registerShutdownHandler() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            LOGGER.info("Client stopping, cleaning up Moud services...");
            cleanupMoudServices();
        });
    }

    private void setupNetworking() {
        LOGGER.info("Setting up networking...");
        PayloadTypeRegistry.playS2C().register(MoudPackets.SyncClientScripts.ID, MoudPackets.SyncClientScripts.CODEC);
        PayloadTypeRegistry.playS2C().register(MoudPackets.ClientboundScriptEvent.ID, MoudPackets.ClientboundScriptEvent.CODEC);
        PayloadTypeRegistry.playS2C().register(SharedValuePackets.ServerSyncValuePacket.ID, SharedValuePackets.ServerSyncValuePacket.CODEC);
        PayloadTypeRegistry.playC2S().register(MoudPackets.HelloPacket.ID, MoudPackets.HelloPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(MoudPackets.ServerboundScriptEvent.ID, MoudPackets.ServerboundScriptEvent.CODEC);
        PayloadTypeRegistry.playC2S().register(SharedValuePackets.ClientUpdateValuePacket.ID, SharedValuePackets.ClientUpdateValuePacket.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(MoudPackets.SyncClientScripts.ID, this::handleSyncScripts);
        ClientPlayNetworking.registerGlobalReceiver(MoudPackets.ClientboundScriptEvent.ID, this::handleScriptEvent);
        LOGGER.info("Networking setup complete.");
    }

    private void registerEventHandlers() {
        ClientPlayConnectionEvents.JOIN.register(this::onJoinServer);
        ClientPlayConnectionEvents.DISCONNECT.register(this::onDisconnectServer);
    }

    private void onJoinServer(ClientPlayNetworkHandler handler, net.fabricmc.fabric.api.networking.v1.PacketSender sender, MinecraftClient client) {
        initializeMoudServices();
        try {
            LOGGER.info("Connected to server, sending hello packet.");
            ClientPlayNetworking.send(new MoudPackets.HelloPacket(PROTOCOL_VERSION));
        } catch (Exception e) {
            LOGGER.error("Failed to send hello packet", e);
        }
    }

    private void onDisconnectServer(ClientPlayNetworkHandler handler, MinecraftClient client) {
        LOGGER.info("Disconnected from server. Cleaning up client-side state.");
        if (dynamicPack.getAndSet(null) != null) {
            LOGGER.info("Deactivating dynamic resources, reloading client resources.");
            client.reloadResources();
        }
        this.currentResourcesHash = ""; // Clear the hash on disconnect
        cleanupMoudServices();
        setCustomCameraActive(false);
    }

    private void handleSyncScripts(MoudPackets.SyncClientScripts packet, ClientPlayNetworking.Context context) {
        if (currentResourcesHash.equals(packet.hash())) {
            LOGGER.info("Received script & asset archive, but hash is unchanged ({}). Skipping resource reload.", packet.hash());
            loadScriptsOnly(packet.scriptData(), context);
            return;
        }

        LOGGER.info("Received new script & asset archive: oldHash={}, newHash={}, size={} bytes",
                currentResourcesHash.isEmpty() ? "none" : currentResourcesHash, packet.hash(), packet.scriptData().length);
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
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error unpacking script & asset archive", e);
            return;
        }
        LOGGER.info("Unpacked {} assets and {} scripts.", assetsData.size(), scriptsData.size());

        dynamicPack.set(new InMemoryPackResources(MOUDPACK_ID.toString(), Text.of("Moud Dynamic Server Resources"), assetsData));

        context.client().reloadResources().thenRunAsync(() -> {
            loadScriptsOnly(scriptsData, context);
        }, context.client());
    }

    private void loadScriptsOnly(byte[] zippedScriptData, ClientPlayNetworking.Context context) {
        Map<String, byte[]> scriptsData = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zippedScriptData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().startsWith("scripts/")) continue;
                scriptsData.put(entry.getName().substring("scripts/".length()), zis.readAllBytes());
            }
        } catch (IOException e) {
            LOGGER.error("Error unpacking scripts from archive for script-only load", e);
            return;
        }
        loadScriptsOnly(scriptsData, context);
    }

    private void loadScriptsOnly(Map<String, byte[]> scriptsData, ClientPlayNetworking.Context context) {
        LOGGER.info("Dynamic resources reloaded (or skipped); initializing runtime and loading scripts.");

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
        }).thenRun(() -> {
            LOGGER.info("Client scripts loaded and executed successfully.");
        }).exceptionally(t -> {
            LOGGER.error("A failure occurred during runtime initialization or script loading", t);
            return null;
        });
    }


    private void handleScriptEvent(MoudPackets.ClientboundScriptEvent packet, ClientPlayNetworking.Context context) {
        if (scriptingRuntime != null && scriptingRuntime.isInitialized()) {
            scriptingRuntime.triggerNetworkEvent(packet.eventName(), packet.eventData());
        } else {
            LOGGER.debug("Ignoring script event '{}' - runtime not ready", packet.eventName());
        }
    }

    @Override
    public void register(Consumer<ResourcePackProfile> profileAdder) {
        InMemoryPackResources pack = dynamicPack.get();
        if (pack != null) {
            ResourcePackInfo info = new ResourcePackInfo(MOUDPACK_ID.toString(), Text.of("Moud Dynamic Server Resources"), ResourcePackSource.BUILTIN, null);

            ResourcePackProfile.PackFactory factory = new ResourcePackProfile.PackFactory() {
                @Override
                public ResourcePack open(ResourcePackInfo info) {
                    return pack;
                }

                @Override
                public ResourcePack openWithOverlays(ResourcePackInfo info, ResourcePackProfile.Metadata metadata) {
                    return pack;
                }
            };

            ResourcePackProfile.Metadata metadata = new ResourcePackProfile.Metadata(Text.of("Moud Dynamic Server Resources"), ResourcePackCompatibility.COMPATIBLE, FeatureSet.empty(), Collections.emptyList());
            ResourcePackProfile profile = new ResourcePackProfile(info, factory, metadata, new ResourcePackPosition(true, ResourcePackProfile.InsertionPosition.TOP, false));
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
            LOGGER.info("Registered dynamic resource pack provider.");
        } catch (Exception e) {
            LOGGER.error("Failed to register dynamic resource pack provider via reflection", e);
        }
    }

    public static boolean isCustomCameraActive() {
        return customCameraActive;
    }

    public static void setCustomCameraActive(boolean active) {
        customCameraActive = active;
    }

}