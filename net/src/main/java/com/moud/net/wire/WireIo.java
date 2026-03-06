package com.moud.net.wire;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class WireIo {
    private WireIo() {
    }

    public static void writeVarInt(ByteBuffer out, int value) {
        while ((value & 0xFFFFFF80) != 0) {
            out.put((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.put((byte) (value & 0x7F));
    }

    public static int readVarInt(ByteBuffer in) {
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

    public static void writeString(ByteBuffer out, String value) {
        if (value == null) {
            value = "";
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.put(bytes);
    }

    public static String readString(ByteBuffer in) {
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

    public static void writeLong(ByteBuffer out, long value) {
        writeVarInt(out, (int) (value >>> 32));
        writeVarInt(out, (int) value);
    }

    public static long readLong(ByteBuffer in) {
        long hi = Integer.toUnsignedLong(readVarInt(in));
        long lo = Integer.toUnsignedLong(readVarInt(in));
        return (hi << 32) | lo;
    }

    public static int varIntSize(int value) {
        int size = 1;
        while ((value & 0xFFFFFF80) != 0) {
            value >>>= 7;
            size++;
        }
        return size;
    }

    public static int longSize(long value) {
        return varIntSize((int) (value >>> 32)) + varIntSize((int) value);
    }

    public static int stringSize(String value) {
        int len = utf8Length(value == null ? "" : value);
        return varIntSize(len) + len;
    }

    public static int utf8Length(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int len = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 0x7F) {
                len += 1;
                continue;
            }
            if (c <= 0x7FF) {
                len += 2;
                continue;
            }
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1))) {
                    len += 4;
                    i++;
                } else {
                    len += 3;
                }
                continue;
            }
            len += 3;
        }
        return len;
    }
}
