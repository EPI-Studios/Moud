package com.moud.client;

import com.moud.client.api.service.ClientAPIService;
import com.moud.client.network.MoudPackets;
import com.moud.client.resources.InMemoryPackResources;
import com.moud.client.runtime.ClientScriptingRuntime;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.resource.ResourcePackCompatibility;
import net.minecraft.resource.ResourcePackInfo;
import net.minecraft.resource.ResourcePackPosition;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackProvider;
import net.minecraft.resource.ResourcePackSource;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class MoudClientMod implements ClientModInitializer, ResourcePackProvider {
    public static final int PROTOCOL_VERSION = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger(MoudClientMod.class);
    private static final Identifier MOUDPACK_ID = Identifier.of("moud", "dynamic_resources");

    private static boolean smoothCameraActive = false;

    private ClientScriptingRuntime scriptingRuntime;
    private final AtomicReference<InMemoryPackResources> dynamicPack = new AtomicReference<>(null);

    public static boolean isSmoothCameraActive() {
        return smoothCameraActive;
    }

    public static void setSmoothCameraActive(boolean active) {
        smoothCameraActive = active;
    }

    public static float getSmoothPitch() {
        // TODO : Using lerp function to have smooth pitch values
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Moud client");
        initializeServices();
        registerResourcePackProvider();
        setupNetworking();
        registerEventHandlers();
        LOGGER.info("Moud client initialized successfully");
    }

    @Override
    public void register(Consumer<ResourcePackProfile> profileAdder) {
        InMemoryPackResources pack = dynamicPack.get();
        if (pack != null) {
            ResourcePackInfo info = new ResourcePackInfo(MOUDPACK_ID.toString(), Text.of("Moud Dynamic Server Resources"), ResourcePackSource.BUILTIN, null);

            ResourcePackProfile.PackFactory factory = new ResourcePackProfile.PackFactory() {
                @Override
                public net.minecraft.resource.ResourcePack open(ResourcePackInfo info) {
                    return pack;
                }

                @Override
                public net.minecraft.resource.ResourcePack openWithOverlays(ResourcePackInfo info, ResourcePackProfile.Metadata metadata) {
                    return pack;
                }
            };

            ResourcePackProfile.Metadata metadata = new ResourcePackProfile.Metadata(
                    Text.of("Moud Dynamic Server Resources"),
                    ResourcePackCompatibility.COMPATIBLE,
                    FeatureSet.empty(),
                    Collections.emptyList()
            );

            ResourcePackProfile profile = new ResourcePackProfile(
                    info,
                    factory,
                    metadata,
                    new ResourcePackPosition(true, ResourcePackProfile.InsertionPosition.TOP, false)
            );

            profileAdder.accept(profile);
        }
    }

    private void registerResourcePackProvider() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            Field providerField = client.getResourcePackManager().getClass().getDeclaredField("providers");
            providerField.setAccessible(true);
            Set<ResourcePackProvider> providers = (Set<ResourcePackProvider>) providerField.get(client.getResourcePackManager());
            providers.add(this);
            LOGGER.info("Registered dynamic resource pack provider");
        } catch (Exception e) {
            LOGGER.error("Failed to register resource pack provider", e);
        }
    }

    private void initializeServices() {
        ClientAPIService apiService = new ClientAPIService();
        scriptingRuntime = new ClientScriptingRuntime(apiService);
    }

    private void setupNetworking() {
        LOGGER.info("Setting up networking");
        PayloadTypeRegistry.playS2C().register(MoudPackets.SyncClientScripts.ID, MoudPackets.SyncClientScripts.CODEC);
        PayloadTypeRegistry.playS2C().register(MoudPackets.ClientboundScriptEvent.ID, MoudPackets.ClientboundScriptEvent.CODEC);
        PayloadTypeRegistry.playC2S().register(MoudPackets.HelloPacket.ID, MoudPackets.HelloPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(MoudPackets.ServerboundScriptEvent.ID, MoudPackets.ServerboundScriptEvent.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(MoudPackets.SyncClientScripts.ID, this::handleSyncScripts);
        ClientPlayNetworking.registerGlobalReceiver(MoudPackets.ClientboundScriptEvent.ID, this::handleScriptEvent);
        LOGGER.info("Networking setup complete");
    }

    private void registerEventHandlers() {
        LOGGER.info("Registering event handlers");
        ClientPlayConnectionEvents.JOIN.register(this::onJoinServer);
        ClientPlayConnectionEvents.DISCONNECT.register(this::onDisconnectServer);
        LOGGER.info("Event handlers registered");
    }

    private void onJoinServer(ClientPlayNetworkHandler handler, PacketSender sender, MinecraftClient client) {
        try {
            LOGGER.info("Connected to server, sending hello packet");
            ClientPlayNetworking.send(new MoudPackets.HelloPacket(PROTOCOL_VERSION));
        } catch (Exception e) {
            LOGGER.error("Failed to send hello packet", e);
        }
    }

    private void onDisconnectServer(ClientPlayNetworkHandler handler, MinecraftClient client) {
        if (dynamicPack.getAndSet(null) != null) {
            LOGGER.info("Deactivating dynamic resources, reloading client resources.");
            client.reloadResources();
        }
        if (scriptingRuntime != null) {
            scriptingRuntime.shutdown();
        }
        smoothCameraActive = false;
    }

    private void handleSyncScripts(MoudPackets.SyncClientScripts packet, ClientPlayNetworking.Context context) {
        LOGGER.info("Received archive: hash={}, size={} bytes", packet.hash(), packet.scriptData().length);
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
            LOGGER.error("Error unpacking archive", e);
            return;
        }
        LOGGER.info("Unpacked {} assets and {} scripts.", assetsData.size(), scriptsData.size());

        InMemoryPackResources newPack = new InMemoryPackResources(MOUDPACK_ID.toString(), Text.of("Moud Dynamic Server Resources"), assetsData);
        dynamicPack.set(newPack);

        context.client().reloadResources().thenRunAsync(() -> {
            LOGGER.info("Resources reloaded; loading scripts");
            if (!scriptingRuntime.isInitialized()) {
                scriptingRuntime.initialize();
            }
            scriptingRuntime.loadScripts(scriptsData)
                    .thenRun(() -> LOGGER.info("Scripts loaded successfully"))
                    .exceptionally(t -> {
                        LOGGER.error("Script load failed", t);
                        return null;
                    });
        }, context.client());
    }

    private void handleScriptEvent(MoudPackets.ClientboundScriptEvent packet, ClientPlayNetworking.Context context) {
        if (scriptingRuntime != null && scriptingRuntime.isInitialized()) {
            scriptingRuntime.triggerNetworkEvent(packet.eventName(), packet.eventData());
        }
    }

    public static void sendToServer(String eventName, String eventData) {
        ClientPlayNetworking.send(new MoudPackets.ServerboundScriptEvent(eventName, eventData));
    }
}