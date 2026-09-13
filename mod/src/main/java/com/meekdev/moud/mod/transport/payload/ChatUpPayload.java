package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ChatUpPayload(int channel, String text) implements CustomPacketPayload {

    public static final Type<ChatUpPayload> TYPE = Payloads.type("chat_up");

    public static final StreamCodec<FriendlyByteBuf, ChatUpPayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> {
                out.writeVarInt(m.channel() + 1);
                out.writeUtf(m.text(), 4096);
            },
            in -> new ChatUpPayload(in.readVarInt() - 1, in.readUtf(4096)));

    @Override
    public Type<ChatUpPayload> type() {
        return TYPE;
    }
}
