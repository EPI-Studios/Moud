package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PilotDownPayload(int kind, double[] waypoints) implements CustomPacketPayload {

    public static final int WALK = 0;
    public static final int JUMP = 1;
    public static final int STOP = 2;
    public static final int MOVE = 3;

    public static final int WAYPOINT_STRIDE = 4;
    private static final int MOST = 4096;

    public static final Type<PilotDownPayload> TYPE = Payloads.type("pilot_down");

    public static final StreamCodec<FriendlyByteBuf, PilotDownPayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> {
                out.writeVarInt(m.kind());
                int count = Math.min(m.waypoints().length, WAYPOINT_STRIDE * MOST);
                out.writeVarInt(count);
                for (int n = 0; n < count; n++) out.writeDouble(m.waypoints()[n]);
            },
            in -> {
                int kind = in.readVarInt();
                int count = Math.min(in.readVarInt(), WAYPOINT_STRIDE * MOST);
                double[] waypoints = new double[count];
                for (int n = 0; n < count; n++) waypoints[n] = in.readDouble();
                return new PilotDownPayload(kind, waypoints);
            });

    @Override
    public Type<PilotDownPayload> type() {
        return TYPE;
    }
}
