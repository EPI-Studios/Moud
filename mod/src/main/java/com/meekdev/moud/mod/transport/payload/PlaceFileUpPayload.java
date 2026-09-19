package com.meekdev.moud.mod.transport.payload;

import com.meekdev.moud.mod.place.SyncedFiles;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PlaceFileUpPayload(String res, boolean exists, byte[] bytes) implements CustomPacketPayload {

    public static final Type<PlaceFileUpPayload> TYPE = Payloads.type("place_file_up");

    public static final StreamCodec<FriendlyByteBuf, PlaceFileUpPayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> {
                out.writeUtf(m.res());
                out.writeBoolean(m.exists());
                out.writeByteArray(m.bytes());
            },
            in -> new PlaceFileUpPayload(in.readUtf(), in.readBoolean(), in.readByteArray(SyncedFiles.MAX_BYTES)));

    public static PlaceFileUpPayload write(String res, byte[] bytes) {
        return new PlaceFileUpPayload(res, true, bytes);
    }

    public static PlaceFileUpPayload remove(String res, byte[] expected) {
        return new PlaceFileUpPayload(res, false, expected);
    }

    @Override
    public Type<PlaceFileUpPayload> type() {
        return TYPE;
    }
}
