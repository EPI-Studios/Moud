package com.moud.net.script;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ScriptPayload {
    public static final int T_NULL   = 0x00;
    public static final int T_FALSE  = 0x01;
    public static final int T_TRUE   = 0x02;
    public static final int T_LONG   = 0x03;
    public static final int T_DOUBLE = 0x04;
    public static final int T_STRING = 0x05;
    public static final int T_BYTES  = 0x06;
    public static final int T_ARRAY  = 0x07;
    public static final int T_MAP    = 0x08;

    private ScriptPayload() {
    }

    public static byte[] encode(Object value) {
        int cap = 64;
        for (int attempt = 0; attempt < 8; attempt++) {
            ByteBuffer buf = ByteBuffer.allocate(cap).order(ByteOrder.LITTLE_ENDIAN);
            try {
                writeValue(buf, value);
                buf.flip();
                byte[] out = new byte[buf.remaining()];
                buf.get(out);
                return out;
            } catch (BufferOverflowException e) {
                cap = Math.min(1 << 20, Math.max(cap * 2, cap + 64));
            }
        }
        throw new IllegalArgumentException("payload too large to encode");
    }

    public static Object decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        return readValue(buf);
    }

    private static void writeValue(ByteBuffer buf, Object value) {
        switch (value) {
            case null -> buf.put((byte) T_NULL);
            case Boolean b -> buf.put((byte) (b ? T_TRUE : T_FALSE));
            case Byte n -> writeLong(buf, n.longValue());
            case Short n -> writeLong(buf, n.longValue());
            case Integer n -> writeLong(buf, n.longValue());
            case Long n -> writeLong(buf, n);
            case Float n -> writeDouble(buf, n);
            case Double n -> writeDouble(buf, n);
            case String s -> writeString(buf, s);
            case byte[] b -> writeBytes(buf, b);
            case List<?> list -> {
                buf.put((byte) T_ARRAY);
                writeVarInt(buf, list.size());
                for (Object item : list) writeValue(buf, item);
            }
            case Map<?, ?> map -> {
                buf.put((byte) T_MAP);
                writeVarInt(buf, map.size());
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    String key = e.getKey() == null ? "" : e.getKey().toString();
                    writeRawString(buf, key);
                    writeValue(buf, e.getValue());
                }
            }
            default -> throw new IllegalArgumentException("Unsupported payload value type: " + value.getClass());
        }
    }

    private static Object readValue(ByteBuffer buf) {
        int tag = buf.get() & 0xFF;
        return switch (tag) {
            case T_NULL -> null;
            case T_FALSE -> Boolean.FALSE;
            case T_TRUE -> Boolean.TRUE;
            case T_LONG -> readVarIntZigzagLong(buf);
            case T_DOUBLE -> buf.getDouble();
            case T_STRING -> readRawString(buf);
            case T_BYTES -> {
                int n = readVarInt(buf);
                byte[] b = new byte[n];
                buf.get(b);
                yield b;
            }
            case T_ARRAY -> {
                int n = readVarInt(buf);
                List<Object> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) list.add(readValue(buf));
                yield list;
            }
            case T_MAP -> {
                int n = readVarInt(buf);
                Map<String, Object> map = new LinkedHashMap<>(n);
                for (int i = 0; i < n; i++) {
                    String k = readRawString(buf);
                    map.put(k, readValue(buf));
                }
                yield map;
            }
            default -> throw new IllegalArgumentException("Unknown payload tag: " + tag);
        };
    }

    private static void writeLong(ByteBuffer buf, long v) {
        buf.put((byte) T_LONG);
        writeVarIntZigzagLong(buf, v);
    }

    private static void writeDouble(ByteBuffer buf, double v) {
        buf.put((byte) T_DOUBLE);
        buf.putDouble(v);
    }

    private static void writeString(ByteBuffer buf, String s) {
        buf.put((byte) T_STRING);
        writeRawString(buf, s);
    }

    private static void writeBytes(ByteBuffer buf, byte[] b) {
        buf.put((byte) T_BYTES);
        writeVarInt(buf, b.length);
        buf.put(b);
    }

    private static void writeRawString(ByteBuffer buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buf, bytes.length);
        buf.put(bytes);
    }

    private static String readRawString(ByteBuffer buf) {
        int n = readVarInt(buf);
        byte[] b = new byte[n];
        buf.get(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static void writeVarInt(ByteBuffer buf, int value) {
        int v = value;
        while ((v & ~0x7F) != 0) {
            buf.put((byte) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        buf.put((byte) v);
    }

    private static int readVarInt(ByteBuffer buf) {
        int result = 0;
        int shift = 0;
        while (true) {
            int b = buf.get() & 0xFF;
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return result;
            shift += 7;
            if (shift > 35) throw new IllegalArgumentException("varint too long");
        }
    }

    private static void writeVarIntZigzagLong(ByteBuffer buf, long value) {
        long v = (value << 1) ^ (value >> 63);
        while ((v & ~0x7FL) != 0) {
            buf.put((byte) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        buf.put((byte) v);
    }

    private static long readVarIntZigzagLong(ByteBuffer buf) {
        long result = 0;
        int shift = 0;
        while (true) {
            long b = buf.get() & 0xFFL;
            result |= (b & 0x7FL) << shift;
            if ((b & 0x80L) == 0) {
                return (result >>> 1) ^ -(result & 1L);
            }
            shift += 7;
            if (shift > 70) throw new IllegalArgumentException("varint too long");
        }
    }
}
