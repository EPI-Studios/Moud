package com.moud.net.transport;

import java.nio.ByteBuffer;
import java.util.Objects;

public final class TransportFrames {
    private static final int MAX_PAYLOAD_BYTES = 1_048_576;

    private TransportFrames() {
    }

    public static byte[] encode(Lane lane, byte[] payload) {
        Objects.requireNonNull(lane);
        Objects.requireNonNull(payload);
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("payload too large: " + payload.length);
        }

        ByteBuffer out = ByteBuffer.allocate(16 + payload.length);
        writeVarInt(out, lane.ordinal());
        writeVarInt(out, payload.length);
        out.put(payload);
        out.flip();

        byte[] bytes = new byte[out.remaining()];
        out.get(bytes);
        return bytes;
    }

    public static DecodedFrame decode(byte[] frameBytes) {
        Objects.requireNonNull(frameBytes);
        ByteBuffer in = ByteBuffer.wrap(frameBytes);
        int laneOrdinal = readVarInt(in);
        if (laneOrdinal < 0 || laneOrdinal >= Lane.values().length) {
            throw new IllegalArgumentException("invalid lane: " + laneOrdinal);
        }
        int len = readVarInt(in);
        if (len < 0 || len > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("invalid payload length: " + len);
        }
        if (in.remaining() != len) {
            throw new IllegalArgumentException("frame truncated: expected " + len + " remaining=" + in.remaining());
        }
        byte[] payload = new byte[len];
        in.get(payload);
        return new DecodedFrame(Lane.values()[laneOrdinal], payload);
    }

    private static void writeVarInt(ByteBuffer out, int value) {
        while ((value & 0xFFFFFF80) != 0) {
            out.put((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.put((byte) (value & 0x7F));
    }

    private static int readVarInt(ByteBuffer in) {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            if (!in.hasRemaining()) {
                throw new IllegalArgumentException("VarInt truncated");
            }
            read = in.get();
            int value = (read & 0x7F);
            result |= (value << (7 * numRead));

            numRead++;
            if (numRead > 5) {
                throw new IllegalArgumentException("VarInt too long");
            }
        } while ((read & 0x80) != 0);
        return result;
    }

    public record DecodedFrame(Lane lane, byte[] payload) {
    }
}
