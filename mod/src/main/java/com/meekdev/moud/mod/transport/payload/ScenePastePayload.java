package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ScenePastePayload(int token, String text, int parent) implements CustomPacketPayload {

    public static final int MAX_CHARS = 1024 * 1024;

    public static final Type<ScenePastePayload> TYPE = Payloads.type("scene_paste");

    public static final StreamCodec<FriendlyByteBuf, ScenePastePayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> {
                out.writeVarInt(m.token());
                out.writeUtf(m.text(), MAX_CHARS);
                out.writeVarInt(m.parent());
            },
            in -> new ScenePastePayload(in.readVarInt(), in.readUtf(MAX_CHARS), in.readVarInt()));

    @Override
    public Type<ScenePastePayload> type() {
        return TYPE;
    }
}
