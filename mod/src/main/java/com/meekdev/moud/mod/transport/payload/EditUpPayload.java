package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record EditUpPayload(boolean edit) implements CustomPacketPayload {

    public static final Type<EditUpPayload> TYPE = Payloads.type("edit_up");

    public static final StreamCodec<FriendlyByteBuf, EditUpPayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> out.writeBoolean(m.edit()),
            in -> new EditUpPayload(in.readBoolean()));

    @Override
    public Type<EditUpPayload> type() {
        return TYPE;
    }
}
