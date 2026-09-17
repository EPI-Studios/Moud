package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CallDownPayload(int kind, int remote, int call, byte[] values) implements CustomPacketPayload {

    private static final int MAX_VALUES_BYTES = 32767;

    public static final Type<CallDownPayload> TYPE = Payloads.type("call_down");

    public static final StreamCodec<FriendlyByteBuf, CallDownPayload> CODEC = CustomPacketPayload.codec(
            (down, out) -> {
                out.writeByte(down.kind());
                out.writeVarInt(down.remote());
                out.writeVarInt(down.call());
                out.writeByteArray(down.values());
            },
            in -> new CallDownPayload(in.readByte(), in.readVarInt(), in.readVarInt(), in.readByteArray(MAX_VALUES_BYTES)));

    @Override
    public Type<CallDownPayload> type() {
        return TYPE;
    }
}
