package com.moud.server.network.packets;

import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.server.common.PluginMessagePacket;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.HashMap;

import static net.minestom.server.network.NetworkBuffer.*;

public final class ServerSyncValuePacket {

    public static PluginMessagePacket create(String playerId, String storeName, Map<String, Object> deltaChanges, long timestamp) {
        byte[] data = NetworkBuffer.makeArray(buffer -> {
            buffer.write(STRING, playerId);
            buffer.write(STRING, storeName);
            writeDeltaMap(buffer, deltaChanges);
            buffer.write(LONG, timestamp);
        });
        return new PluginMessagePacket("moud:sync_shared_values", data);
    }

    private static void writeDeltaMap(NetworkBuffer buffer, Map<String, Object> map) {
        buffer.write(VAR_INT, map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            buffer.write(STRING, entry.getKey());
            writeValue(buffer, entry.getValue());
        }
    }

    private static void writeValue(NetworkBuffer buffer, Object value) {
        if (value instanceof String s) {
            buffer.write(BYTE, (byte) 0);
            buffer.write(STRING, s);
        } else if (value instanceof Integer i) {
            buffer.write(BYTE, (byte) 1);
            buffer.write(INT, i);
        } else if (value instanceof Double d) {
            buffer.write(BYTE, (byte) 2);
            buffer.write(DOUBLE, d);
        } else if (value instanceof Boolean b) {
            buffer.write(BYTE, (byte) 3);
            buffer.write(BOOLEAN, b);
        } else if (value instanceof Map || value instanceof java.util.List) {
            buffer.write(BYTE, (byte) 4);
            try {
                String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
                buffer.write(STRING, json);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize complex value", e);
            }
        } else if (value == null) {
            buffer.write(BYTE, (byte) 5);
        } else {
            throw new IllegalArgumentException("Unsupported value type: " + value.getClass());
        }
    }

    public static class ServerSyncValueData {
        private final String playerId;
        private final String storeName;
        private final Map<String, Object> deltaChanges;
        private final long timestamp;

        public ServerSyncValueData(byte[] data) {
            NetworkBuffer buffer = new NetworkBuffer(ByteBuffer.wrap(data));
            this.playerId = buffer.read(STRING);
            this.storeName = buffer.read(STRING);
            this.deltaChanges = readDeltaMap(buffer);
            this.timestamp = buffer.read(LONG);
        }

        public String getPlayerId() { return playerId; }
        public String getStoreName() { return storeName; }
        public Map<String, Object> getDeltaChanges() { return deltaChanges; }
        public long getTimestamp() { return timestamp; }

        private static Map<String, Object> readDeltaMap(NetworkBuffer buffer) {
            int size = buffer.read(VAR_INT);
            Map<String, Object> map = new HashMap<>();
            for (int i = 0; i < size; i++) {
                String key = buffer.read(STRING);
                Object value = readValue(buffer);
                map.put(key, value);
            }
            return map;
        }

        private static Object readValue(NetworkBuffer buffer) {
            byte type = buffer.read(BYTE);
            return switch (type) {
                case 0 -> buffer.read(STRING);
                case 1 -> buffer.read(INT);
                case 2 -> buffer.read(DOUBLE);
                case 3 -> buffer.read(BOOLEAN);
                case 4 -> {
                    String json = buffer.read(STRING);
                    try {
                        yield new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Object.class);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to deserialize complex value", e);
                    }
                }
                case 5 -> null;
                default -> throw new IllegalArgumentException("Unknown value type: " + type);
            };
        }
    }
}