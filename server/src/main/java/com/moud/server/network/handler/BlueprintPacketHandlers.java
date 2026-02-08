package com.moud.server.network.handler;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.moud.network.MoudPackets.*;
import com.moud.server.editor.BlueprintStorage;
import com.moud.server.editor.SceneManager;
import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.permissions.PermissionManager;
import com.moud.server.permissions.ServerPermission;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BlueprintPacketHandlers implements PacketHandlerGroup {

    private static final MoudLogger LOGGER = MoudLogger.getLogger(BlueprintPacketHandlers.class);
    private static final Gson GSON = new Gson();

    private static final int CHUNK_SIZE = 64 * 1024; // 64KB
    private static final int INLINE_LIMIT = 32 * 1024;
    private static final int MAX_TOTAL_CHUNKS = 4096;
    private static final long UPLOAD_TIMEOUT_MS = 120_000L;

    private final ServerNetworkManager networkManager;
    private final BlueprintStorage blueprintStorage;

    private final ConcurrentMap<String, PendingUpload> pendingUploads = new ConcurrentHashMap<>();

    public BlueprintPacketHandlers(ServerNetworkManager networkManager, BlueprintStorage blueprintStorage) {
        this.networkManager = networkManager;
        this.blueprintStorage = blueprintStorage;
    }

    @Override
    public void register(PacketRegistry registry) {
        registry.register(SaveBlueprintPacket.class, this::handleSave);
        registry.register(SaveBlueprintChunkPacket.class, this::handleSaveChunk);
        registry.register(RequestBlueprintPacket.class, this::handleRequest);
        registry.register(ListBlueprintsPacket.class, this::handleList);
        registry.register(DeleteBlueprintPacket.class, this::handleDelete);
        registry.register(PlaceBlueprintPacket.class, this::handlePlace);
    }

    private void handleSave(Player player, SaveBlueprintPacket packet) {
        if (!validateRequest(player, packet.name())) return;

        String name = packet.name().trim();
        byte[] data = packet.data();

        if (data == null || data.length == 0) {
            sendSaveAck(player, name, false, "Invalid payload");
            return;
        }

        try {
            blueprintStorage.save(name, data);
            sendSaveAck(player, name, true, "Saved successfully");
        } catch (IOException e) {
            LOGGER.error("Failed to save blueprint " + name, e);
            sendSaveAck(player, name, false, "Storage error");
        }
    }

    private void handleSaveChunk(Player player, SaveBlueprintChunkPacket packet) {
        if (!validateRequest(player, packet.name())) return;

        String name = packet.name().trim();
        String uploadKey = player.getUuid() + ":" + name;

        cleanupPendingUploads();

        PendingUpload upload = pendingUploads.computeIfAbsent(uploadKey, k ->
                new PendingUpload(packet.totalChunks())
        );

        if (upload.totalChunks != packet.totalChunks()) {
            pendingUploads.remove(uploadKey);
            sendSaveAck(player, name, false, "Chunk count mismatch");
            return;
        }

        upload.addChunk(packet.chunkIndex(), packet.data());

        if (upload.isComplete()) {
            pendingUploads.remove(uploadKey);
            try {
                byte[] fullData = upload.assemble();
                blueprintStorage.save(name, fullData);
                sendSaveAck(player, name, true, "Saved successfully");
            } catch (Exception e) {
                LOGGER.error("Failed to assemble blueprint " + name, e);
                sendSaveAck(player, name, false, "Assembly error");
            }
        }
    }

    private void handleRequest(Player player, RequestBlueprintPacket packet) {
        if (!validateRequest(player, packet.name())) return;

        String name = packet.name().trim();

        if (!blueprintStorage.exists(name)) {
            networkManager.send(player, new BlueprintDataPacket(name, null, false, "Not found"));
            return;
        }

        try {
            byte[] data = blueprintStorage.load(name);

            if (data.length <= INLINE_LIMIT) {
                networkManager.send(player, new BlueprintDataPacket(name, data, true, "Loaded"));
                return;
            }

            int totalChunks = (int) Math.ceil((double) data.length / CHUNK_SIZE);

            for (int i = 0; i < totalChunks; i++) {
                int start = i * CHUNK_SIZE;
                int end = Math.min(data.length, start + CHUNK_SIZE);
                byte[] chunk = Arrays.copyOfRange(data, start, end);

                networkManager.send(player, new BlueprintDataChunkPacket(name, totalChunks, i, chunk));
            }

            networkManager.send(player, new BlueprintDataPacket(name, null, true, "Chunked transfer sent"));

        } catch (IOException e) {
            LOGGER.error("Failed to load blueprint " + name, e);
            networkManager.send(player, new BlueprintDataPacket(name, null, false, "Load error"));
        }
    }

    private void handleList(Player player, ListBlueprintsPacket packet) {
        if (!hasPermission(player)) {
            networkManager.send(player, new BlueprintListPacket(Collections.emptyList()));
            return;
        }
        List<String> blueprints = blueprintStorage.list();
        networkManager.send(player, new BlueprintListPacket(blueprints));
    }

    private void handleDelete(Player player, DeleteBlueprintPacket packet) {
        if (!validateRequest(player, packet.name())) return;

        String name = packet.name().trim();
        boolean success = blueprintStorage.delete(name);

        networkManager.send(player, new BlueprintDeleteAckPacket(name, success, success ? "Deleted" : "Not found"));
    }

    private void handlePlace(Player player, PlaceBlueprintPacket packet) {
        if (!validateRequest(player, packet.blueprintName())) return;

        String name = packet.blueprintName().trim();
        String sceneId = packet.sceneId() != null ? packet.sceneId() : "default";

        if (!blueprintStorage.exists(name)) {
            sendPlaceAck(player, name, false, "Blueprint not found", null);
            return;
        }

        try {
            byte[] rawData = blueprintStorage.load(name);
            String jsonString = new String(rawData, StandardCharsets.UTF_8);
            JsonObject blueprint = GSON.fromJson(jsonString, JsonObject.class);

            PlacementSettings settings = new PlacementSettings(blueprint, packet.position(), packet.rotation(), packet.scale());
            List<String> createdObjectIds = new ArrayList<>();

            if (blueprint.has("objects")) {
                JsonArray objects = blueprint.getAsJsonArray("objects");
                for (JsonElement element : objects) {
                    if (element.isJsonObject()) {
                        String id = placeObject(player, sceneId, element.getAsJsonObject(), settings);
                        if (id != null) createdObjectIds.add(id);
                    }
                }
            }

            if (blueprint.has("markers")) {
                JsonArray markers = blueprint.getAsJsonArray("markers");
                for (JsonElement element : markers) {
                    if (element.isJsonObject()) {
                        String id = placeMarker(player, sceneId, element.getAsJsonObject(), settings);
                        if (id != null) createdObjectIds.add(id);
                    }
                }
            }

            if (blueprint.has("blocks")) {
                placeBlocks(player, blueprint.getAsJsonObject("blocks"), settings);
            }

            LOGGER.info("Player {} placed blueprint {}", player.getUsername(), name);
            sendPlaceAck(player, name, true, "Placed successfully", createdObjectIds);

        } catch (Exception e) {
            LOGGER.error("Failed to place blueprint " + name, e);
            sendPlaceAck(player, name, false, "Placement error: " + e.getMessage(), null);
        }
    }


    private void placeBlocks(Player player, JsonObject blocksJson, PlacementSettings settings) {
        Instance instance = player.getInstance();
        if (instance == null) return;

        int sizeX = getInt(blocksJson, "sizeX", 0);
        int sizeY = getInt(blocksJson, "sizeY", 0);
        int sizeZ = getInt(blocksJson, "sizeZ", 0);

        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) return;

        List<String> palette = new ArrayList<>();
        blocksJson.getAsJsonArray("palette").forEach(e -> palette.add(e.getAsString()));

        byte[] voxels = decodeBase64(blocksJson.get("voxels"));
        if (voxels == null || palette.isEmpty()) return;

        boolean shortIndices = getBoolean(blocksJson, "useShortIndices", false);

        Block[] transformedPalette = new Block[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            transformedPalette[i] = resolveTransformedBlock(palette.get(i), settings);
        }

        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {

                    int paletteIndex = readVoxelIndex(voxels, shortIndices, sizeX, sizeZ, x, y, z);

                    if (paletteIndex <= 0 || paletteIndex >= transformedPalette.length) {
                        continue;
                    }

                    Block block = transformedPalette[paletteIndex];
                    if (block == null) continue;

                    int[] localPos = transformGridCoordinate(x, y, z, sizeX, sizeZ, settings);

                    int worldX = settings.originX + localPos[0];
                    int worldY = settings.originY + localPos[1];
                    int worldZ = settings.originZ + localPos[2];

                    instance.setBlock(worldX, worldY, worldZ, block);
                }
            }
        }
    }

    private int[] transformGridCoordinate(int x, int y, int z, int sizeX, int sizeZ, PlacementSettings settings) {
        int mx = settings.mirrorX ? (sizeX - 1 - x) : x;
        int mz = settings.mirrorZ ? (sizeZ - 1 - z) : z;

        int tx, tz;

        switch (settings.rotationSteps) {
            case 1 -> { // 90 degrees
                tx = settings.outSizeX - 1 - mz;
                tz = mx;
            }
            case 2 -> { // 180 degrees
                tx = settings.outSizeX - 1 - mx;
                tz = settings.outSizeZ - 1 - mz;
            }
            case 3 -> { // 270 degrees
                tx = mz;
                tz = settings.outSizeZ - 1 - mx;
            }
            default -> { // 0 degrees
                tx = mx;
                tz = mz;
            }
        }

        return new int[]{tx, y, tz};
    }


    private String placeObject(Player player, String sceneId, JsonObject obj, PlacementSettings settings) {
        String type = getString(obj, "type", "group");
        return createEntityInScene(sceneId, type, obj, settings);
    }

    private String placeMarker(Player player, String sceneId, JsonObject marker, PlacementSettings settings) {
        return createEntityInScene(sceneId, "marker", marker, settings);
    }

    private String createEntityInScene(String sceneId, String type, JsonObject json, PlacementSettings settings) {
        float[] localPos = getVec3(json, "position");
        float[] localRot = getVec3(json, "rotation");
        float[] localScale = getVec3(json, "scale", new float[]{1, 1, 1});

        float[] worldPos = settings.transformPosition(localPos);

        float[] worldRot = new float[]{
                localRot[0] + settings.targetRot[0],
                localRot[1] + settings.targetRot[1],
                localRot[2] + settings.targetRot[2]
        };

        float[] worldScale = new float[]{
                localScale[0] * settings.targetScale[0],
                localScale[1] * settings.targetScale[1],
                localScale[2] * settings.targetScale[2]
        };

        Map<String, Object> properties = new HashMap<>();
        if (json.has("properties")) {
            properties.putAll(jsonToMap(json.getAsJsonObject("properties")));
        }

        if (json.has("label")) properties.put("label", json.get("label").getAsString());
        if (json.has("name")) properties.put("name", json.get("name").getAsString());
        if (json.has("modelPath")) properties.put("modelPath", json.get("modelPath").getAsString());
        if (json.has("texture")) properties.put("texture", json.get("texture").getAsString());

        properties.put("position", mapVec3(worldPos));
        properties.put("rotation", mapVec3Rotation(worldRot));
        properties.put("scale", mapVec3(worldScale));

        if (type.equals("zone")) {
            transformZoneProperty(properties, "corner1", settings);
            transformZoneProperty(properties, "corner2", settings);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("properties", properties);

        SceneManager.SceneEditResult result = SceneManager.getInstance()
                .applyEdit(sceneId, "create", payload, System.currentTimeMillis());

        return result.success() ? result.objectId() : null;
    }

    private void transformZoneProperty(Map<String, Object> props, String key, PlacementSettings settings) {
        if (props.containsKey(key)) {
            Object val = props.get(key);
            float[] vec = objectToVec3(val);
            float[] transformed = settings.transformPosition(vec);
            props.put(key, mapVec3(transformed));
        }
    }

    private static class PlacementSettings {
        final int originX, originY, originZ;
        final int schematicSizeX, schematicSizeZ;
        final int outSizeX, outSizeZ;
        final int rotationSteps;
        final boolean mirrorX, mirrorZ;

        final float[] targetRot;
        final float[] targetScale;

        PlacementSettings(JsonObject blueprint, float[] pos, float[] rot, float[] scale) {
            JsonObject blocks = blueprint.getAsJsonObject("blocks");
            this.schematicSizeX = getInt(blocks, "sizeX", 1);
            this.schematicSizeZ = getInt(blocks, "sizeZ", 1);

            this.targetRot = rot != null ? rot : new float[3];
            this.targetScale = scale != null ? scale : new float[]{1, 1, 1};

            float[] p = pos != null ? pos : new float[3];
            this.originX = (int) Math.floor(p[0]);
            this.originY = (int) Math.floor(p[1]);
            this.originZ = (int) Math.floor(p[2]);

            this.rotationSteps = Math.floorMod(Math.round(this.targetRot[1] / 90.0f), 4);

            this.mirrorX = this.targetScale[0] < 0;
            this.mirrorZ = this.targetScale[2] < 0;

            if (this.rotationSteps % 2 != 0) {
                this.outSizeX = this.schematicSizeZ;
                this.outSizeZ = this.schematicSizeX;
            } else {
                this.outSizeX = this.schematicSizeX;
                this.outSizeZ = this.schematicSizeZ;
            }
        }

        float[] transformPosition(float[] vec) {
            float x = vec[0];
            float y = vec[1];
            float z = vec[2];

            if (mirrorX) x = schematicSizeX - x;
            if (mirrorZ) z = schematicSizeZ - z;

            float tx, tz;
            switch (rotationSteps) {
                case 1 -> { tx = outSizeX - z; tz = x; }
                case 2 -> { tx = outSizeX - x; tz = outSizeZ - z; }
                case 3 -> { tx = z; tz = outSizeZ - x; }
                default -> { tx = x; tz = z; }
            }

            return new float[]{
                    originX + tx,
                    originY + y,
                    originZ + tz
            };
        }
    }

    private Block resolveTransformedBlock(String rawState, PlacementSettings s) {
        Block block = Block.fromNamespaceId(rawState);
        if (block == null) return null;

        Map<String, String> properties = new HashMap<>(block.properties());

        if (s.mirrorX) {
            mirrorProperty(properties, "facing", true);
            mirrorProperty(properties, "east", "west");
        }
        if (s.mirrorZ) {
            mirrorProperty(properties, "facing", false);
            mirrorProperty(properties, "north", "south");
        }

        for (int i = 0; i < s.rotationSteps; i++) {
            rotateProperty(properties, "facing");
            rotateProperty(properties, "axis");
            String n = properties.get("north");
            String e = properties.get("east");
            String sVal = properties.get("south");
            String w = properties.get("west");

            if (n != null || e != null || sVal != null || w != null) {
                putOrRemove(properties, "north", w);
                putOrRemove(properties, "east", n);
                putOrRemove(properties, "south", e);
                putOrRemove(properties, "west", sVal);
            }
        }

        return block.withProperties(properties);
    }

    private void mirrorProperty(Map<String, String> props, String key, boolean axisX) {
        String val = props.get(key);
        if (val == null) return;

        if (axisX) {
            if (val.equals("east")) props.put(key, "west");
            else if (val.equals("west")) props.put(key, "east");
        } else {
            if (val.equals("north")) props.put(key, "south");
            else if (val.equals("south")) props.put(key, "north");
        }
    }

    private void mirrorProperty(Map<String, String> props, String key1, String key2) {
        String v1 = props.get(key1);
        String v2 = props.get(key2);
        putOrRemove(props, key1, v2);
        putOrRemove(props, key2, v1);
    }

    private void rotateProperty(Map<String, String> props, String key) {
        String val = props.get(key);
        if (val == null) return;

        switch (val) {
            case "north" -> props.put(key, "east");
            case "east" -> props.put(key, "south");
            case "south" -> props.put(key, "west");
            case "west" -> props.put(key, "north");
            case "x" -> props.put(key, "z");
            case "z" -> props.put(key, "x");
        }
    }

    private void putOrRemove(Map<String, String> map, String key, String value) {
        if (value != null) map.put(key, value);
        else map.remove(key);
    }

    private static class PendingUpload {
        final int totalChunks;
        final byte[][] chunks;
        int received = 0;
        long lastUpdate = System.currentTimeMillis();

        PendingUpload(int totalChunks) {
            this.totalChunks = totalChunks;
            this.chunks = new byte[totalChunks][];
        }

        void addChunk(int index, byte[] data) {
            if (chunks[index] == null) {
                chunks[index] = data;
                received++;
            }
            lastUpdate = System.currentTimeMillis();
        }

        boolean isComplete() {
            return received == totalChunks;
        }

        byte[] assemble() {
            int size = 0;
            for (byte[] c : chunks) size += c.length;
            byte[] out = new byte[size];
            int offset = 0;
            for (byte[] c : chunks) {
                System.arraycopy(c, 0, out, offset, c.length);
                offset += c.length;
            }
            return out;
        }
    }

    private void cleanupPendingUploads() {
        long now = System.currentTimeMillis();
        pendingUploads.values().removeIf(u -> now - u.lastUpdate > UPLOAD_TIMEOUT_MS);
    }

    private boolean validateRequest(Player player, String name) {
        if (player == null || !hasPermission(player)) return false;
        return name != null && !name.isBlank();
    }

    private boolean hasPermission(Player player) {
        return networkManager.isMoudClient(player) &&
                PermissionManager.getInstance().has(player, ServerPermission.EDITOR);
    }

    private void sendSaveAck(Player p, String name, boolean success, String msg) {
        networkManager.send(p, new BlueprintSaveAckPacket(name, success, msg));
    }

    private void sendPlaceAck(Player p, String name, boolean success, String msg, List<String> ids) {
        networkManager.send(p, new BlueprintPlaceAckPacket(name, success, msg, ids));
    }

    private int readVoxelIndex(byte[] voxels, boolean useShort, int sx, int sz, int x, int y, int z) {
        int index = (y * sz + z) * sx + x;
        if (useShort) {
            int offset = index * 2;
            if (offset + 1 >= voxels.length) return 0;
            return (voxels[offset] & 0xFF) | ((voxels[offset + 1] & 0xFF) << 8);
        } else {
            if (index >= voxels.length) return 0;
            return voxels[index] & 0xFF;
        }
    }

    private static int getInt(JsonObject o, String key, int def) {
        return o.has(key) ? o.get(key).getAsInt() : def;
    }

    private static String getString(JsonObject o, String key, String def) {
        return o.has(key) ? o.get(key).getAsString() : def;
    }

    private static boolean getBoolean(JsonObject o, String key, boolean def) {
        return o.has(key) ? o.get(key).getAsBoolean() : def;
    }

    private static float[] getVec3(JsonObject o, String key) {
        return getVec3(o, key, new float[3]);
    }

    private static float[] getVec3(JsonObject o, String key, float[] def) {
        if (o.has(key) && o.get(key).isJsonArray()) {
            JsonArray a = o.getAsJsonArray(key);
            if (a.size() >= 3) {
                return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
            }
        }
        return def;
    }

    private static byte[] decodeBase64(JsonElement element) {
        if (element != null && !element.isJsonNull()) {
            try {
                return Base64.getDecoder().decode(element.getAsString());
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static Map<String, Object> mapVec3(float[] v) {
        Map<String, Object> m = new HashMap<>();
        m.put("x", v[0]);
        m.put("y", v[1]);
        m.put("z", v[2]);
        return m;
    }

    private static Map<String, Object> mapVec3Rotation(float[] v) {
        Map<String, Object> m = new HashMap<>();
        m.put("pitch", v[0]);
        m.put("yaw", v[1]);
        m.put("roll", v[2]);
        return m;
    }

    private float[] objectToVec3(Object o) {
        if (o instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) o;
            Number x = (Number) m.get("x");
            Number y = (Number) m.get("y");
            Number z = (Number) m.get("z");
            if (x != null && y != null && z != null) {
                return new float[]{x.floatValue(), y.floatValue(), z.floatValue()};
            }
        }
        return new float[3];
    }

    private Map<String, Object> jsonToMap(JsonObject obj) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            JsonElement el = entry.getValue();
            if (el.isJsonPrimitive()) {
                if (el.getAsJsonPrimitive().isBoolean()) result.put(entry.getKey(), el.getAsBoolean());
                else if (el.getAsJsonPrimitive().isNumber()) result.put(entry.getKey(), el.getAsDouble());
                else result.put(entry.getKey(), el.getAsString());
            }
        }
        return result;
    }
}