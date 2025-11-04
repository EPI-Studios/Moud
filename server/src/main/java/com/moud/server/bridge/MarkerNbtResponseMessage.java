package com.moud.server.bridge;

import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.network.NetworkBuffer;

import java.util.UUID;

final class MarkerNbtResponseMessage {

    private MarkerNbtResponseMessage() {
    }

    static byte[] encode(UUID uuid, CompoundBinaryTag data) {
        return NetworkBuffer.makeArray(buffer -> {
            buffer.write(NetworkBuffer.UUID, uuid);
            buffer.write(NetworkBuffer.NBT, data);
        });
    }
}
