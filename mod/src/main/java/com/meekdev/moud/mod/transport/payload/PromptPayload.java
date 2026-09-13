package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PromptPayload(int prompt, int kind) implements CustomPacketPayload {

    public static final int TRIGGERED = 0;
    public static final int HOLD_BEGAN = 1;
    public static final int HOLD_ENDED = 2;

    public static final Type<PromptPayload> TYPE = Payloads.type("prompt_up");

    public static final StreamCodec<FriendlyByteBuf, PromptPayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> {
                out.writeVarInt(m.prompt());
                out.writeVarInt(m.kind());
            },
            in -> new PromptPayload(in.readVarInt(), in.readVarInt()));

    @Override
    public Type<PromptPayload> type() {
        return TYPE;
    }
}
