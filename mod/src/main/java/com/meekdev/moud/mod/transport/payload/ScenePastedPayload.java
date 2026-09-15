package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ScenePastedPayload(int token, int[] roots, int[] all) implements CustomPacketPayload {

    public static final Type<ScenePastedPayload> TYPE = Payloads.type("scene_pasted");

    public static final StreamCodec<FriendlyByteBuf, ScenePastedPayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> {
                out.writeVarInt(m.token());
                out.writeVarIntArray(m.roots());
                out.writeVarIntArray(m.all());
            },
            in -> new ScenePastedPayload(in.readVarInt(), in.readVarIntArray(), in.readVarIntArray()));

    @Override
    public Type<ScenePastedPayload> type() {
        return TYPE;
    }
}
