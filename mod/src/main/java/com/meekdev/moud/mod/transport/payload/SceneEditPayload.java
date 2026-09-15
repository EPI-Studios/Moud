package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SceneEditPayload(byte[] changes) implements CustomPacketPayload {

    public static final int MAX_BYTES = 1024 * 1024;

    public static final Type<SceneEditPayload> TYPE = Payloads.type("scene_edit");

    public static final StreamCodec<FriendlyByteBuf, SceneEditPayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> out.writeByteArray(m.changes()),
            in -> new SceneEditPayload(in.readByteArray(MAX_BYTES)));

    @Override
    public Type<SceneEditPayload> type() {
        return TYPE;
    }
}
