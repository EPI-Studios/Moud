package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record WorldPayload(double gravity) implements CustomPacketPayload {

    public static final Type<WorldPayload> TYPE = Payloads.type("world");

    public static final StreamCodec<FriendlyByteBuf, WorldPayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> out.writeDouble(m.gravity()),
            in -> new WorldPayload(in.readDouble()));

    @Override
    public Type<WorldPayload> type() {
        return TYPE;
    }
}
