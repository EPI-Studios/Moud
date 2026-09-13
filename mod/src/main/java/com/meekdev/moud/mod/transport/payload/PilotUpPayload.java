package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PilotUpPayload(int kind) implements CustomPacketPayload {

    public static final int CANCELLED = 0;

    public static final Type<PilotUpPayload> TYPE = Payloads.type("pilot_up");

    public static final StreamCodec<FriendlyByteBuf, PilotUpPayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> out.writeVarInt(m.kind()), in -> new PilotUpPayload(in.readVarInt()));

    @Override
    public Type<PilotUpPayload> type() {
        return TYPE;
    }
}
