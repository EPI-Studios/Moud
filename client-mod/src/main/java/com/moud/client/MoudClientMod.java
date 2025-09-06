package com.moud.client;

import com.moud.client.api.service.ClientAPIService;
import com.moud.client.network.MoudPackets;
import com.moud.client.runtime.ClientScriptingRuntime;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class MoudClientMod implements ClientModInitializer {
    public static final int PROTOCOL_VERSION = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger(MoudClientMod.class);

    private ClientScriptingRuntime scriptingRuntime;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Moud client");

        try {
            initializeServices();
            setupNetworking();
            registerEventHandlers();

            LOGGER.info("Moud client initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize Moud client", e);
            throw new RuntimeException("Moud client initialization failed", e);
        }
    }

    private void initializeServices() {
        LOGGER.info("Initializing client services");
        ClientAPIService apiService = new ClientAPIService();
        this.scriptingRuntime = new ClientScriptingRuntime(apiService);
        LOGGER.info("Client services initialized");
    }

    private void setupNetworking() {
        LOGGER.info("Setting up networking");



        // Server-to-Client
        PayloadTypeRegistry.playS2C().register(MoudPackets.SyncClientScripts.ID, MoudPackets.SyncClientScripts.CODEC);
        PayloadTypeRegistry.playS2C().register(MoudPackets.ClientboundScriptEvent.ID, MoudPackets.ClientboundScriptEvent.CODEC);

        // Client-to-Server
        PayloadTypeRegistry.playC2S().register(MoudPackets.HelloPacket.ID, MoudPackets.HelloPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(MoudPackets.ServerboundScriptEvent.ID, MoudPackets.ServerboundScriptEvent.CODEC);

        LOGGER.info("Packet types registered");

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
            LOGGER.info("Hello packet sent successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to send hello packet", e);
        }
    }

    private void onDisconnectServer(ClientPlayNetworkHandler handler, MinecraftClient client) {
        LOGGER.info("Disconnected from server, cleaning up");
        try {
            scriptingRuntime.shutdown();
            LOGGER.debug("Client cleanup completed");
        } catch (Exception e) {
            LOGGER.error("Error during client cleanup", e);
        }
    }

    private void handleSyncScripts(MoudPackets.SyncClientScripts packet, ClientPlayNetworking.Context context) {
        String hash = packet.hash();
        byte[] scriptData = packet.scriptData();

        LOGGER.info("Received client scripts: hash={}, size={} bytes", hash, scriptData.length);

        try {
            if (!scriptingRuntime.isInitialized()) {
                scriptingRuntime.initialize();
            }
            scriptingRuntime.loadScripts(scriptData)
                    .thenRun(() -> LOGGER.info("Client scripts loaded and executed successfully"))
                    .exceptionally(throwable -> {
                        LOGGER.error("Failed to load client scripts", throwable);
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.error("Error handling sync scripts packet", e);
        }
    }

    private void handleScriptEvent(MoudPackets.ClientboundScriptEvent packet, ClientPlayNetworking.Context context) {
        String eventName = packet.eventName();
        String eventData = packet.eventData();

        LOGGER.debug("Received script event: {} -> {}", eventName, eventData);

        try {
            scriptingRuntime.triggerNetworkEvent(eventName, eventData);
            LOGGER.debug("Script event triggered successfully");
        } catch (Exception e) {
            LOGGER.error("Error handling script event: {}", eventName, e);
        }
    }

    public static void sendToServer(String eventName, String eventData) {
        try {
            ClientPlayNetworking.send(new MoudPackets.ServerboundScriptEvent(eventName, eventData));
        } catch (Exception e) {
            LOGGER.error("Failed to send event to server: {}", eventName, e);
        }
    }
}