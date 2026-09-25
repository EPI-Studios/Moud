package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CallDownPayload(int kind, int remote, int call, byte[] values) implements CustomPacketPayload {

    public static final int MOST = 1_000_000;

    public static final Type<CallDownPayload> TYPE = Payloads.type("call_down");

    public static final StreamCodec<FriendlyByteBuf, CallDownPayload> CODEC = CustomPacketPayload.codec(
            (down, out) -> {
                out.writeByte(down.kind());
                out.writeVarInt(down.remote());
                out.writeVarInt(down.call());
                out.writeByteArray(down.values());
            },
            in -> new CallDownPayload(in.readByte(), in.readVarInt(), in.readVarInt(), in.readByteArray(MOST)));

    @Override
    public Type<CallDownPayload> type() {
        return TYPE;
    }
}
