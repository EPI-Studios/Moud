package com.meekdev.moud.mod.transport.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SceneFilePayload(int action, String path) implements CustomPacketPayload {

    public static final int OPEN = 0;
    public static final int SAVE_AS = 1;
    public static final int RESTORE = 2;

    public static final Type<SceneFilePayload> TYPE = Payloads.type("scene_file");

    public static final StreamCodec<FriendlyByteBuf, SceneFilePayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> {
                out.writeVarInt(m.action());
                out.writeUtf(m.path());
            },
            in -> new SceneFilePayload(in.readVarInt(), in.readUtf()));

    @Override
    public Type<SceneFilePayload> type() {
        return TYPE;
    }
}
