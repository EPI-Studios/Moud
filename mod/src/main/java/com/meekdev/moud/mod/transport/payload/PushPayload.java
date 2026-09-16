package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PushPayload(double x, double y, double z, boolean replace) implements CustomPacketPayload {

    public static final Type<PushPayload> TYPE = Payloads.type("push");

    public static final StreamCodec<FriendlyByteBuf, PushPayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> {
                out.writeDouble(m.x());
                out.writeDouble(m.y());
                out.writeDouble(m.z());
                out.writeBoolean(m.replace());
            },
            in -> new PushPayload(in.readDouble(), in.readDouble(), in.readDouble(), in.readBoolean()));

    @Override
    public Type<PushPayload> type() {
        return TYPE;
    }
}
