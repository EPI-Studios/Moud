package com.moud.client.network;

import com.moud.client.runtime.ClientScriptingRuntime;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ClientPacketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientPacketHandler.class);

    private final ClientScriptingRuntime scriptingRuntime;

    public ClientPacketHandler(ClientScriptingRuntime scriptingRuntime) {
        this.scriptingRuntime = scriptingRuntime;
    }

    public void handleSyncScripts(MoudPackets.SyncClientScripts packet,
                                  ClientPlayNetworking.Context context) {
        LOGGER.info("Received client scripts: hash={}, size={} bytes",
                packet.hash(), packet.scriptData().length);

        try {
            Map<String, byte[]> scriptsData = new HashMap<>();

            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(packet.scriptData()))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    String name = entry.getName();
                    byte[] data = zis.readAllBytes();
                    if (name.startsWith("scripts/")) {
                        scriptsData.put(name.substring("scripts/".length()), data);
                    }
                }
            }

            if (!scriptingRuntime.isInitialized()) {
                LOGGER.debug("Initializing client scripting runtime");
                scriptingRuntime.initialize();
            }

            LOGGER.debug("Loading client scripts into runtime");
            scriptingRuntime.loadScripts(scriptsData)
                    .thenAccept(result -> {
                        LOGGER.info("Client scripts loaded and executed successfully");
                    })
                    .exceptionally(throwable -> {
                        LOGGER.error("Failed to load client scripts", throwable);
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.error("Error handling sync scripts packet", e);
        }
    }

    public void handleScriptEvent(MoudPackets.ClientboundScriptEvent packet,
                                  ClientPlayNetworking.Context context) {
        LOGGER.debug("Received script event: {} -> {}", packet.eventName(), packet.eventData());

        try {
            scriptingRuntime.triggerNetworkEvent(packet.eventName(), packet.eventData());
            LOGGER.debug("Script event triggered successfully");
        } catch (Exception e) {
            LOGGER.error("Error handling script event: {}", packet.eventName(), e);
        }
    }
}