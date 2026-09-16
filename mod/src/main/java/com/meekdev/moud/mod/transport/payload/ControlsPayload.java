package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ControlsPayload(boolean move, boolean jump, boolean look) implements CustomPacketPayload {

    public static final Type<ControlsPayload> TYPE = Payloads.type("controls");

    public static final StreamCodec<FriendlyByteBuf, ControlsPayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> {
                out.writeBoolean(m.move());
                out.writeBoolean(m.jump());
                out.writeBoolean(m.look());
            },
            in -> new ControlsPayload(in.readBoolean(), in.readBoolean(), in.readBoolean()));

    @Override
    public Type<ControlsPayload> type() {
        return TYPE;
    }
}
