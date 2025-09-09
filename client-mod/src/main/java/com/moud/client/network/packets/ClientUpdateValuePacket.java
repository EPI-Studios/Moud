package com.moud.client.network.packets;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ClientUpdateValuePacket(String storeName, String key, Object value, long clientTimestamp) implements CustomPayload {
    public static final CustomPayload.Id<ClientUpdateValuePacket> ID =
            new CustomPayload.Id<>(Identifier.of("moud", "update_shared_value"));

    public static final PacketCodec<PacketByteBuf, ClientUpdateValuePacket> CODEC =
            PacketCodec.of(ClientUpdateValuePacket::write, ClientUpdateValuePacket::new);

    private ClientUpdateValuePacket(PacketByteBuf buf) {
        this(buf.readString(), buf.readString(), readValue(buf), buf.readLong());
    }

    private void write(PacketByteBuf buf) {
        buf.writeString(storeName);
        buf.writeString(key);
        writeValue(buf, value);
        buf.writeLong(clientTimestamp);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
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
                String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
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