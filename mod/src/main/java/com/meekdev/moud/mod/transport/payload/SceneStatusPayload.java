package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SceneStatusPayload(boolean dirty, String file, String message) implements CustomPacketPayload {

    public static final Type<SceneStatusPayload> TYPE = Payloads.type("scene_status");

    public static final StreamCodec<FriendlyByteBuf, SceneStatusPayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> {
                out.writeBoolean(m.dirty());
                out.writeUtf(m.file());
                out.writeUtf(m.message());
            },
            in -> new SceneStatusPayload(in.readBoolean(), in.readUtf(), in.readUtf()));

    @Override
    public Type<SceneStatusPayload> type() {
        return TYPE;
    }
}
