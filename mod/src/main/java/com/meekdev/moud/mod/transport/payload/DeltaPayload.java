package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DeltaPayload(byte[] bytes) implements CustomPacketPayload {

    public static final int MAX_BYTES = 8 * 1024 * 1024;

    public static final Type<DeltaPayload> TYPE = Payloads.type("delta");

    public static final StreamCodec<FriendlyByteBuf, DeltaPayload> CODEC = CustomPacketPayload.codec(
            (delta, out) -> out.writeByteArray(delta.bytes()),
            in -> new DeltaPayload(in.readByteArray(MAX_BYTES)));

    @Override
    public Type<DeltaPayload> type() {
        return TYPE;
    }
}
