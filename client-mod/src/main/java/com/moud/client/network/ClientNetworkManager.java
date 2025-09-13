package com.moud.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClientNetworkManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientNetworkManager.class);

    private final ClientPacketHandler packetHandler;

    public ClientNetworkManager(ClientPacketHandler packetHandler) {
        this.packetHandler = packetHandler;
    }

    public void registerPackets() {
        LOGGER.info("Registering client packet types");

        PayloadTypeRegistry.playC2S().register(
                MoudPackets.HelloPacket.ID,
                MoudPackets.HelloPacket.CODEC
        );
        PayloadTypeRegistry.playC2S().register(
                MoudPackets.ServerboundScriptEvent.ID,
                MoudPackets.ServerboundScriptEvent.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                MoudPackets.SyncClientScripts.ID,
                MoudPackets.SyncClientScripts.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                MoudPackets.ClientboundScriptEvent.ID,
                MoudPackets.ClientboundScriptEvent.CODEC
        );

        LOGGER.info("Client packet types registered");
    }

    public void registerHandlers() {
        LOGGER.info("Registering client packet handlers");

        ClientPlayNetworking.registerGlobalReceiver(
                MoudPackets.SyncClientScripts.ID,
                packetHandler::handleSyncScripts
        );
        ClientPlayNetworking.registerGlobalReceiver(
                MoudPackets.ClientboundScriptEvent.ID,
                packetHandler::handleScriptEvent
        );

        LOGGER.info("Client packet handlers registered");
    }

    public static void sendToServer(String eventName, String eventData) {
        LOGGER.info("Sending script event packet to server: '{}'", eventName);
        ClientPlayNetworking.send(new MoudPackets.ServerboundScriptEvent(eventName, eventData));
    }

    public static void sendHello(int protocolVersion) {
        ClientPlayNetworking.send(new MoudPackets.HelloPacket(protocolVersion));
    }
}