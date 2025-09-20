package com.moud.client.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record MoudPayload(Identifier channel, byte[] data) implements CustomPayload {

    public static final CustomPayload.Id<MoudPayload> ID = new CustomPayload.Id<>(Identifier.of("moud", "generic_payload"));

    public static final PacketCodec<PacketByteBuf, MoudPayload> CODEC = PacketCodec.of(
            MoudPayload::write, MoudPayload::new);

    public MoudPayload(PacketByteBuf buf) {
        this(buf.readIdentifier(), buf.readByteArray());
    }

    public void write(PacketByteBuf buf) {
        buf.writeIdentifier(this.channel);
        buf.writeByteArray(this.data);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}