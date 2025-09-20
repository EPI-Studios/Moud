package com.moud.client.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record DataPayload(Identifier channel, byte[] data) implements CustomPayload {

    public static final CustomPayload.Id<DataPayload> ID = new CustomPayload.Id<>(Identifier.of("moud", "wrapper"));

    public static final PacketCodec<PacketByteBuf, DataPayload> CODEC = PacketCodec.of(
            DataPayload::write,
            DataPayload::new
    );

    public DataPayload(PacketByteBuf buf) {
        this(buf.readIdentifier(), buf.readByteArray());
    }

    public void write(PacketByteBuf buf) {
        buf.writeIdentifier(channel);
        buf.writeByteArray(data);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}