package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DebugPayload(int kind, double[] numbers, String text, int argb, double seconds) implements CustomPacketPayload {

    public static final Type<DebugPayload> TYPE = Payloads.type("debug_down");

    public static final StreamCodec<FriendlyByteBuf, DebugPayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> {
                out.writeVarInt(m.kind());
                out.writeVarInt(m.numbers().length);
                for (double n : m.numbers()) out.writeDouble(n);
                out.writeUtf(m.text(), 1024);
                out.writeInt(m.argb());
                out.writeDouble(m.seconds());
            },
            in -> {
                int kind = in.readVarInt();
                int count = Math.min(in.readVarInt(), 16);
                double[] numbers = new double[count];
                for (int n = 0; n < count; n++) numbers[n] = in.readDouble();
                return new DebugPayload(kind, numbers, in.readUtf(1024), in.readInt(), in.readDouble());
            });

    @Override
    public Type<DebugPayload> type() {
        return TYPE;
    }
}
