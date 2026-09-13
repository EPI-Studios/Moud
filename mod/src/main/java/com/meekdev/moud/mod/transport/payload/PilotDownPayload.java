package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PilotDownPayload(int kind, double[] waypoints) implements CustomPacketPayload {

    public static final int WALK = 0;
    public static final int JUMP = 1;
    public static final int STOP = 2;

    public static final Type<PilotDownPayload> TYPE = Payloads.type("pilot_down");

    public static final StreamCodec<FriendlyByteBuf, PilotDownPayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> {
                out.writeVarInt(m.kind());
                out.writeVarInt(m.waypoints().length);
                for (double n : m.waypoints()) out.writeDouble(n);
            },
            in -> {
                int kind = in.readVarInt();
                int count = Math.min(in.readVarInt(), 3 * 4096);
                double[] waypoints = new double[count];
                for (int n = 0; n < count; n++) waypoints[n] = in.readDouble();
                return new PilotDownPayload(kind, waypoints);
            });

    @Override
    public Type<PilotDownPayload> type() {
        return TYPE;
    }
}
