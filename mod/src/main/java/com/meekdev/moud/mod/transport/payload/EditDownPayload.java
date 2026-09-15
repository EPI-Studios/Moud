package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record EditDownPayload(boolean editing, boolean allowed) implements CustomPacketPayload {

    public static final Type<EditDownPayload> TYPE = Payloads.type("edit_down");

    public static final StreamCodec<FriendlyByteBuf, EditDownPayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> {
                out.writeBoolean(m.editing());
                out.writeBoolean(m.allowed());
            },
            in -> new EditDownPayload(in.readBoolean(), in.readBoolean()));

    @Override
    public Type<EditDownPayload> type() {
        return TYPE;
    }
}
