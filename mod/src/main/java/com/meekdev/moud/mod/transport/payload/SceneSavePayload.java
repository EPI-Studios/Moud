package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SceneSavePayload() implements CustomPacketPayload {

    public static final Type<SceneSavePayload> TYPE = Payloads.type("scene_save");

    public static final StreamCodec<FriendlyByteBuf, SceneSavePayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> {}, in -> new SceneSavePayload());

    @Override
    public Type<SceneSavePayload> type() {
        return TYPE;
    }
}
