package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SeatPayload(int throttle, int steer) implements CustomPacketPayload {

    public static final Type<SeatPayload> TYPE = Payloads.type("seat");

    public static final StreamCodec<FriendlyByteBuf, SeatPayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> {
                out.writeVarInt(m.throttle());
                out.writeVarInt(m.steer());
            },
            in -> new SeatPayload(Math.clamp(in.readVarInt(), -1, 1), Math.clamp(in.readVarInt(), -1, 1)));

    @Override
    public Type<SeatPayload> type() {
        return TYPE;
    }
}
