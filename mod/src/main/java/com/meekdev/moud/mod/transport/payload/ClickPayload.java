package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClickPayload(int detector, int kind) implements CustomPacketPayload {

    public static final int CLICK = 0;
    public static final int RIGHT_CLICK = 1;
    public static final int HOVER_ENTER = 2;
    public static final int HOVER_LEAVE = 3;

    public static final Type<ClickPayload> TYPE = Payloads.type("click_up");

    public static final StreamCodec<FriendlyByteBuf, ClickPayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> {
                out.writeVarInt(m.detector());
                out.writeVarInt(m.kind());
            },
            in -> new ClickPayload(in.readVarInt(), in.readVarInt()));

    @Override
    public Type<ClickPayload> type() {
        return TYPE;
    }
}
