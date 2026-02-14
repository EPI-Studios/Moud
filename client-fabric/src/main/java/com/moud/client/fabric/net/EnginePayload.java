package com.moud.client.fabric.net;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record EnginePayload(byte[] data) implements CustomPayload {
    public static final Id<EnginePayload> ID = new Id<>(Identifier.of("moud", "engine"));

    public static final PacketCodec<PacketByteBuf, EnginePayload> CODEC = PacketCodec.of(
            EnginePayload::write, EnginePayload::new
    );

    public EnginePayload(PacketByteBuf buf) {
        this(readAllBytes(buf));
    }

    public void write(PacketByteBuf buf) {
        buf.writeBytes(this.data);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static byte[] readAllBytes(PacketByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }
}
