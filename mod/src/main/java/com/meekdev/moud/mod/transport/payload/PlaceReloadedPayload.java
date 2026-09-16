package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PlaceReloadedPayload() implements CustomPacketPayload {

    public static final Type<PlaceReloadedPayload> TYPE = Payloads.type("place_reloaded");

    public static final StreamCodec<FriendlyByteBuf, PlaceReloadedPayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> {}, in -> new PlaceReloadedPayload());

    @Override
    public Type<PlaceReloadedPayload> type() {
        return TYPE;
    }
}
