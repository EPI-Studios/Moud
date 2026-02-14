package com.moud.net.wire;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

final class WireIo {
    private WireIo() {
    }

    static void writeVarInt(ByteBuffer out, int value) {
        while ((value & 0xFFFFFF80) != 0) {
            out.put((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.put((byte) (value & 0x7F));
    }

    static int readVarInt(ByteBuffer in) {
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

    static void writeString(ByteBuffer out, String value) {
        if (value == null) {
            value = "";
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.put(bytes);
    }

    static String readString(ByteBuffer in) {
        int len = readVarInt(in);
        if (len < 0 || len > 1_048_576) {
            throw new IllegalArgumentException("Invalid string length: " + len);
        }
        if (in.remaining() < len) {
            throw new IllegalArgumentException("String truncated");
        }
        byte[] bytes = new byte[len];
        in.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
