package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RemoteDownPayload(int remote, byte[] args) implements CustomPacketPayload {

    private static final int MAX_ARGS_BYTES = 32767;

    public static final Type<RemoteDownPayload> TYPE = Payloads.type("down");

    public static final StreamCodec<FriendlyByteBuf, RemoteDownPayload> CODEC = CustomPacketPayload.codec(
            (down, out) -> {
                out.writeVarInt(down.remote());
                out.writeByteArray(down.args());
            },
            in -> new RemoteDownPayload(in.readVarInt(), in.readByteArray(MAX_ARGS_BYTES)));

    @Override
    public Type<RemoteDownPayload> type() {
        return TYPE;
    }
}
