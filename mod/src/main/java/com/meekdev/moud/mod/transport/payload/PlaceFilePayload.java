package com.meekdev.moud.mod.transport.payload;

import com.meekdev.moud.mod.place.SyncedFiles;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PlaceFilePayload(String res, boolean exists, byte[] bytes) implements CustomPacketPayload {

    public static final int MAX_BYTES = SyncedFiles.MAX_BYTES + 4096;

    public static final Type<PlaceFilePayload> TYPE = Payloads.type("place_file");

    public static final StreamCodec<FriendlyByteBuf, PlaceFilePayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> {
                out.writeUtf(m.res());
                out.writeBoolean(m.exists());
                out.writeByteArray(m.bytes());
            },
            in -> new PlaceFilePayload(in.readUtf(), in.readBoolean(), in.readByteArray(SyncedFiles.MAX_BYTES)));

    @Override
    public Type<PlaceFilePayload> type() {
        return TYPE;
    }
}
