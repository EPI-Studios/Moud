package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ResyncPayload() implements CustomPacketPayload {

    public static final Type<ResyncPayload> TYPE = Payloads.type("resync_up");

    public static final StreamCodec<FriendlyByteBuf, ResyncPayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> {}, in -> new ResyncPayload());

    @Override
    public Type<ResyncPayload> type() {
        return TYPE;
    }
}
