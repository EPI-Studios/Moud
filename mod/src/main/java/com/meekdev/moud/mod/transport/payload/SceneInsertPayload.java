package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SceneInsertPayload(String className, int parent) implements CustomPacketPayload {

    public static final Type<SceneInsertPayload> TYPE = Payloads.type("scene_insert");

    public static final StreamCodec<FriendlyByteBuf, SceneInsertPayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> {
                out.writeUtf(m.className());
                out.writeVarInt(m.parent());
            },
            in -> new SceneInsertPayload(in.readUtf(), in.readVarInt()));

    @Override
    public Type<SceneInsertPayload> type() {
        return TYPE;
    }
}
