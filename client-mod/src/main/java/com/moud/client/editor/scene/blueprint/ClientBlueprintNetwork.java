package com.moud.client.editor.scene.blueprint;


import com.moud.client.editor.scene.SceneEditorDiagnostics;
import com.moud.client.network.ClientPacketWrapper;
import com.moud.network.MoudPackets;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class ClientBlueprintNetwork {
    private static final ClientBlueprintNetwork INSTANCE = new ClientBlueprintNetwork();

    public static ClientBlueprintNetwork getInstance() {
        return INSTANCE;
    }

    private final Map<String, List<Consumer<Boolean>>> saveCallbacks = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<Blueprint>>> requestCallbacks = new ConcurrentHashMap<>();

    private ClientBlueprintNetwork() {}

    public void saveBlueprint(String name, Blueprint blueprint, Consumer<Boolean> callback) {
        if (name == null || name.isBlank() || blueprint == null) {
            if (callback != null) callback.accept(false);
            return;
        }
        String key = normalize(name);
        if (callback != null) {
            saveCallbacks.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(callback);
        }
        String json = BlueprintIO.toJsonString(blueprint);
        ClientPacketWrapper.sendToServer(new MoudPackets.SaveBlueprintPacket(name, json.getBytes(StandardCharsets.UTF_8)));
    }

    public void requestBlueprint(String name, Consumer<Blueprint> callback) {
        if (name == null || name.isBlank()) {
            if (callback != null) callback.accept(null);
            return;
        }
        String key = normalize(name);
        if (callback != null) {
            requestCallbacks.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(callback);
        }
        ClientPacketWrapper.sendToServer(new MoudPackets.RequestBlueprintPacket(name));
    }

    public void handleSaveAck(MoudPackets.BlueprintSaveAckPacket packet) {
        String key = normalize(packet.name());
        List<Consumer<Boolean>> callbacks = saveCallbacks.remove(key);
        if (callbacks != null) {
            for (Consumer<Boolean> cb : callbacks) {
                cb.accept(packet.success());
            }
        }
        if (!packet.success()) {
            SceneEditorDiagnostics.log("Server failed to save blueprint " + packet.name() + ": " + packet.message());
        } else {
            SceneEditorDiagnostics.log("Blueprint '" + packet.name() + "' saved on server");
        }
    }

    public void handleBlueprintData(MoudPackets.BlueprintDataPacket packet) {
        String key = normalize(packet.name());
        List<Consumer<Blueprint>> callbacks = requestCallbacks.remove(key);
        if (packet.success() && packet.data() != null) {
            String json = new String(packet.data(), StandardCharsets.UTF_8);
            Blueprint blueprint = BlueprintIO.fromJsonString(json);
            if (blueprint == null) {
                if (callbacks != null) {
                    for (Consumer<Blueprint> cb : callbacks) {
                        cb.accept(null);
                    }
                }
                SceneEditorDiagnostics.log("Failed to parse blueprint '" + packet.name() + "'");
                return;
            }
            if (callbacks != null) {
                for (Consumer<Blueprint> cb : callbacks) {
                    cb.accept(blueprint);
                }
            }
        } else {
            if (callbacks != null) {
                for (Consumer<Blueprint> cb : callbacks) {
                    cb.accept(null);
                }
            }
            SceneEditorDiagnostics.log("Blueprint '" + packet.name() + "' unavailable: " + packet.message());
        }
    }

    private String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }
}
