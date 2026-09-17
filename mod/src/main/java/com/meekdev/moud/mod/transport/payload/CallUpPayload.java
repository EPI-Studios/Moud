package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CallUpPayload(int kind, int remote, int call, byte[] values) implements CustomPacketPayload {

    private static final int MAX_VALUES_BYTES = 32767;

    public static final Type<CallUpPayload> TYPE = Payloads.type("call_up");

    public static final StreamCodec<FriendlyByteBuf, CallUpPayload> CODEC = CustomPacketPayload.codec(
            (up, out) -> {
                out.writeByte(up.kind());
                out.writeVarInt(up.remote());
                out.writeVarInt(up.call());
                out.writeByteArray(up.values());
            },
            in -> new CallUpPayload(in.readByte(), in.readVarInt(), in.readVarInt(), in.readByteArray(MAX_VALUES_BYTES)));

    @Override
    public Type<CallUpPayload> type() {
        return TYPE;
    }
}
