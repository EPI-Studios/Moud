package com.moud.client.network.packets;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.HashMap;

public record ServerSyncValuePacket(String playerId, String storeName, Map<String, Object> deltaChanges, long timestamp) implements CustomPayload {
    public static final CustomPayload.Id<ServerSyncValuePacket> ID =
            new CustomPayload.Id<>(Identifier.of("moud", "sync_shared_values"));

    public static final PacketCodec<PacketByteBuf, ServerSyncValuePacket> CODEC =
            PacketCodec.of(ServerSyncValuePacket::write, ServerSyncValuePacket::new);

    private ServerSyncValuePacket(PacketByteBuf buf) {
        this(buf.readString(), buf.readString(), readDeltaMap(buf), buf.readLong());
    }

    private void write(PacketByteBuf buf) {
        buf.writeString(playerId);
        buf.writeString(storeName);
        writeDeltaMap(buf, deltaChanges);
        buf.writeLong(timestamp);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static Map<String, Object> readDeltaMap(PacketByteBuf buf) {
        int size = buf.readVarInt();
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String key = buf.readString();
            Object value = readValue(buf);
            map.put(key, value);
        }
        return map;
    }

    private static void writeDeltaMap(PacketByteBuf buf, Map<String, Object> map) {
        buf.writeVarInt(map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            buf.writeString(entry.getKey());
            writeValue(buf, entry.getValue());
        }
    }

    private static Object readValue(PacketByteBuf buf) {
        byte type = buf.readByte();
        return switch (type) {
            case 0 -> buf.readString();
            case 1 -> buf.readInt();
            case 2 -> buf.readDouble();
            case 3 -> buf.readBoolean();
            case 4 -> {
                String json = buf.readString();
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

    private static void writeValue(PacketByteBuf buf, Object value) {
        if (value instanceof String s) {
            buf.writeByte(0);
            buf.writeString(s);
        } else if (value instanceof Integer i) {
            buf.writeByte(1);
            buf.writeInt(i);
        } else if (value instanceof Double d) {
            buf.writeByte(2);
            buf.writeDouble(d);
        } else if (value instanceof Boolean b) {
            buf.writeByte(3);
            buf.writeBoolean(b);
        } else if (value instanceof java.util.Map || value instanceof java.util.List) {
            buf.writeByte(4);
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                String json = mapper.writeValueAsString(value);
                buf.writeString(json);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize complex value", e);
            }
        } else if (value == null) {
            buf.writeByte(5);
        } else {
            throw new IllegalArgumentException("Unsupported value type: " + value.getClass());
        }
    }
}