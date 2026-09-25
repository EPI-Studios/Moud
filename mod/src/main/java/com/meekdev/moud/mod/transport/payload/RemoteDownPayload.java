package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RemoteDownPayload(int remote, byte[] args) implements CustomPacketPayload {

    public static final int MOST = 1_000_000;

    public static final Type<RemoteDownPayload> TYPE = Payloads.type("down");

    public static final StreamCodec<FriendlyByteBuf, RemoteDownPayload> CODEC = CustomPacketPayload.codec(
            (down, out) -> {
                out.writeVarInt(down.remote());
                out.writeByteArray(down.args());
            },
            in -> new RemoteDownPayload(in.readVarInt(), in.readByteArray(MOST)));

    @Override
    public Type<RemoteDownPayload> type() {
        return TYPE;
    }
}
