package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ChatDownPayload(int kind, long id, int channel, int source, int body, String text, String prefix,
                       String metadata, long timestamp, String status) implements CustomPacketPayload {

    private static final int MAX_TEXT = 16384;

    public static final int LINE = 0;
    public static final int EDIT = 1;
    public static final int DELETE = 2;
    public static final int STATUS = 3;
    public static final int CLEAR = 4;

    public static final Type<ChatDownPayload> TYPE = Payloads.type("chat_down");

    public static final StreamCodec<FriendlyByteBuf, ChatDownPayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> {
                out.writeVarInt(m.kind());
                out.writeVarLong(m.id());
                out.writeVarInt(m.channel() + 1);
                out.writeVarInt(m.source() + 1);
                out.writeVarInt(m.body() + 1);
                out.writeUtf(m.text(), MAX_TEXT);
                out.writeUtf(m.prefix(), MAX_TEXT);
                out.writeUtf(m.metadata(), MAX_TEXT);
                out.writeVarLong(m.timestamp());
                out.writeUtf(m.status(), 64);
            },
            in -> new ChatDownPayload(in.readVarInt(), in.readVarLong(), in.readVarInt() - 1, in.readVarInt() - 1,
                    in.readVarInt() - 1, in.readUtf(MAX_TEXT), in.readUtf(MAX_TEXT), in.readUtf(MAX_TEXT),
                    in.readVarLong(), in.readUtf(64)));

    @Override
    public Type<ChatDownPayload> type() {
        return TYPE;
    }
}
