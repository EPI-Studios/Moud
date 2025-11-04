package com.moud.server.bridge;

import net.minestom.server.network.NetworkBuffer;

import java.nio.ByteBuffer;
import java.util.UUID;

final class MarkerNbtRequestMessage {

    private MarkerNbtRequestMessage() {
    }

    static Request parse(byte[] payload) {
        NetworkBuffer buffer = new NetworkBuffer(ByteBuffer.wrap(payload));
        UUID uuid = buffer.read(NetworkBuffer.UUID);
        int reason = buffer.read(NetworkBuffer.VAR_INT);
        return new Request(uuid, reason);
    }

    record Request(UUID uuid, int reason) {
    }
}
