package com.moud.client.editor.scene.blueprint;

import com.moud.client.editor.scene.SceneEditorDiagnostics;
import com.moud.client.network.ClientPacketWrapper;
import com.moud.network.MoudPackets;
import com.moud.network.limits.NetworkLimits;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class ClientBlueprintNetwork {
    private static final ClientBlueprintNetwork INSTANCE = new ClientBlueprintNetwork();
    private static final int CHUNK_SIZE = 64 * 1024;
    private static final int INLINE_LIMIT = Math.max(CHUNK_SIZE, NetworkLimits.MAX_PACKET_BYTES - CHUNK_SIZE);
    private static final String DEFAULT_SCENE = "default";

    public static ClientBlueprintNetwork getInstance() {
        return INSTANCE;
    }

    public record PlaceResult(boolean success, String message, List<String> createdObjectIds) {}

    private final Map<String, List<Consumer<Boolean>>> saveCallbacks = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<Blueprint>>> requestCallbacks = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<PlaceResult>>> placeCallbacks = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<Boolean>>> deleteCallbacks = new ConcurrentHashMap<>();
    private final List<Consumer<List<String>>> listCallbacks = new CopyOnWriteArrayList<>();
    private final Map<String, PendingDownload> pendingDownloads = new ConcurrentHashMap<>();

    private ClientBlueprintNetwork() {}

    public void saveBlueprint(String name, Blueprint blueprint, Consumer<Boolean> callback) {
        if (!isValid(name) || blueprint == null) {
            safeAccept(callback, false);
            return;
        }

        String key = normalize(name);
        registerCallback(saveCallbacks, key, callback);

        byte[] data = BlueprintIO.toJsonString(blueprint).getBytes(StandardCharsets.UTF_8);

        if (data.length <= INLINE_LIMIT) {
            ClientPacketWrapper.sendToServer(new MoudPackets.SaveBlueprintPacket(name, data));
            return;
        }

        int totalChunks = (data.length + CHUNK_SIZE - 1) / CHUNK_SIZE;
        for (int i = 0; i < totalChunks; i++) {
            int start = i * CHUNK_SIZE;
            int end = Math.min(data.length, start + CHUNK_SIZE);
            byte[] chunk = Arrays.copyOfRange(data, start, end);
            ClientPacketWrapper.sendToServer(new MoudPackets.SaveBlueprintChunkPacket(name, totalChunks, i, chunk));
        }
    }

    public void requestBlueprint(String name, Consumer<Blueprint> callback) {
        if (!isValid(name)) {
            safeAccept(callback, null);
            return;
        }
        String key = normalize(name);
        registerCallback(requestCallbacks, key, callback);
        ClientPacketWrapper.sendToServer(new MoudPackets.RequestBlueprintPacket(name));
    }

    public void placeBlueprint(String sceneId, String name, float[] pos, float[] rot, float[] scale, Consumer<PlaceResult> callback) {
        if (!isValid(name)) {
            safeAccept(callback, new PlaceResult(false, "Invalid name", List.of()));
            return;
        }
        String key = normalize(name);
        registerCallback(placeCallbacks, key, callback);

        ClientPacketWrapper.sendToServer(new MoudPackets.PlaceBlueprintPacket(
                sceneId != null ? sceneId : DEFAULT_SCENE,
                name,
                pos != null ? pos : new float[3],
                rot != null ? rot : new float[3],
                scale != null ? scale : new float[]{1, 1, 1}
        ));
    }

    public void deleteBlueprint(String name, Consumer<Boolean> callback) {
        if (!isValid(name)) {
            safeAccept(callback, false);
            return;
        }
        String key = normalize(name);
        registerCallback(deleteCallbacks, key, callback);
        ClientPacketWrapper.sendToServer(new MoudPackets.DeleteBlueprintPacket(name));
    }

    public void listBlueprints(Consumer<List<String>> callback) {
        if (callback != null) {
            listCallbacks.add(callback);
        }
        ClientPacketWrapper.sendToServer(new MoudPackets.ListBlueprintsPacket());
    }

    public void handleSaveAck(MoudPackets.BlueprintSaveAckPacket packet) {
        String key = normalize(packet.name());
        dispatch(saveCallbacks, key, packet.success());

        if (packet.success()) {
            SceneEditorDiagnostics.log("Blueprint '" + packet.name() + "' saved on server");
        } else {
            SceneEditorDiagnostics.log("Failed to save blueprint " + packet.name() + ": " + packet.message());
        }
    }

    public void handlePlaceAck(MoudPackets.BlueprintPlaceAckPacket packet) {
        String key = normalize(packet.blueprintName());
        List<String> ids = packet.createdObjectIds() != null ? packet.createdObjectIds() : List.of();
        dispatch(placeCallbacks, key, new PlaceResult(packet.success(), packet.message(), ids));

        if (packet.success()) {
            SceneEditorDiagnostics.log("Placed blueprint '" + packet.blueprintName() + "': " + ids.size() + " objects");
        } else {
            SceneEditorDiagnostics.log("Failed to place blueprint '" + packet.blueprintName() + "': " + packet.message());
        }
    }

    public void handleBlueprintList(MoudPackets.BlueprintListPacket packet) {
        List<String> names = packet.names() != null ? packet.names() : List.of();
        List<Consumer<List<String>>> callbacks = new CopyOnWriteArrayList<>(listCallbacks);
        listCallbacks.clear();
        callbacks.forEach(cb -> cb.accept(names));
        SceneEditorDiagnostics.log("Received " + names.size() + " blueprints from server");
    }

    public void handleDeleteAck(MoudPackets.BlueprintDeleteAckPacket packet) {
        String key = normalize(packet.name());
        dispatch(deleteCallbacks, key, packet.success());

        if (packet.success()) {
            SceneEditorDiagnostics.log("Deleted blueprint '" + packet.name() + "'");
        } else {
            SceneEditorDiagnostics.log("Failed to delete blueprint '" + packet.name() + "': " + packet.message());
        }
    }

    public void handleBlueprintDataChunk(MoudPackets.BlueprintDataChunkPacket packet) {
        if (packet == null || packet.name() == null) return;

        String key = normalize(packet.name());
        pendingDownloads.compute(key, (k, existing) -> {
            if (existing == null || existing.totalChunks != packet.totalChunks()) {
                existing = new PendingDownload(packet.totalChunks());
            }
            existing.addChunk(packet.chunkIndex(), packet.data());
            return existing;
        });
    }

    public void handleBlueprintData(MoudPackets.BlueprintDataPacket packet) {
        String key = normalize(packet.name());

        if (!packet.success()) {
            pendingDownloads.remove(key);
            dispatch(requestCallbacks, key, null);
            SceneEditorDiagnostics.log("Blueprint '" + packet.name() + "' unavailable: " + packet.message());
            return;
        }

        if (packet.data() != null && packet.data().length > 0) {
            parseAndDeliver(packet.name(), packet.data(), key);
            return;
        }

        PendingDownload pending = pendingDownloads.remove(key);
        if (pending != null && pending.isComplete()) {
            parseAndDeliver(packet.name(), pending.assemble(), key);
        } else {
            dispatch(requestCallbacks, key, null);
            SceneEditorDiagnostics.log("Blueprint '" + packet.name() + "' download incomplete: " + packet.message());
        }
    }

    private void parseAndDeliver(String name, byte[] data, String key) {
        Blueprint blueprint = BlueprintIO.fromJsonString(new String(data, StandardCharsets.UTF_8));
        dispatch(requestCallbacks, key, blueprint);

        if (blueprint == null) {
            SceneEditorDiagnostics.log("Failed to parse blueprint '" + name + "'");
        }
    }

    private <T> void registerCallback(Map<String, List<Consumer<T>>> map, String key, Consumer<T> callback) {
        if (callback != null) {
            map.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(callback);
        }
    }

    private <T> void dispatch(Map<String, List<Consumer<T>>> map, String key, T result) {
        List<Consumer<T>> callbacks = map.remove(key);
        if (callbacks != null) {
            callbacks.forEach(cb -> cb.accept(result));
        }
    }

    private <T> void safeAccept(Consumer<T> callback, T value) {
        if (callback != null) callback.accept(value);
    }

    private boolean isValid(String name) {
        return name != null && !name.isBlank();
    }

    private String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    private static final class PendingDownload {
        private final int totalChunks;
        private final byte[][] chunks;
        private int receivedCount;

        PendingDownload(int totalChunks) {
            this.totalChunks = totalChunks;
            this.chunks = new byte[totalChunks][];
        }

        void addChunk(int index, byte[] data) {
            if (index >= 0 && index < totalChunks && chunks[index] == null) {
                chunks[index] = data;
                receivedCount++;
            }
        }

        boolean isComplete() {
            return receivedCount == totalChunks;
        }

        byte[] assemble() {
            int totalSize = 0;
            for (byte[] chunk : chunks) {
                if (chunk != null) totalSize += chunk.length;
            }
            byte[] result = new byte[totalSize];
            int offset = 0;
            for (byte[] chunk : chunks) {
                if (chunk != null) {
                    System.arraycopy(chunk, 0, result, offset, chunk.length);
                    offset += chunk.length;
                }
            }
            return result;
        }
    }
}