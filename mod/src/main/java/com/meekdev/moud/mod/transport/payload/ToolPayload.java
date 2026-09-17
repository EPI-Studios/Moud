package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ToolPayload(boolean down) implements CustomPacketPayload {

    public static final Type<ToolPayload> TYPE = Payloads.type("tool_up");

    public static final StreamCodec<FriendlyByteBuf, ToolPayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> out.writeBoolean(m.down()),
            in -> new ToolPayload(in.readBoolean()));

    @Override
    public Type<ToolPayload> type() {
        return TYPE;
    }
}
