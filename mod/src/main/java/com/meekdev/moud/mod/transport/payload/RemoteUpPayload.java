package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RemoteUpPayload(int remote, byte[] args) implements CustomPacketPayload {

    public static final int MOST = 32000;

    public static final Type<RemoteUpPayload> TYPE = Payloads.type("up");

    public static final StreamCodec<FriendlyByteBuf, RemoteUpPayload> CODEC = CustomPacketPayload.codec(
            (up, out) -> {
                out.writeVarInt(up.remote());
                out.writeByteArray(up.args());
            },
            in -> new RemoteUpPayload(in.readVarInt(), in.readByteArray(MOST)));

    @Override
    public Type<RemoteUpPayload> type() {
        return TYPE;
    }
}
