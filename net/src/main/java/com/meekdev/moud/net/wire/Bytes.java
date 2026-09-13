package com.meekdev.moud.net.wire;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class Bytes {

    private byte[] data;
    private int size;
    private int at;

    private int blockAt = -1;
    private int blockBits;

    public Bytes() {
        this(64);
    }

    public Bytes(int capacity) {
        this.data = new byte[Math.max(8, capacity)];
    }

    public static Bytes reading(byte[] from) {
        Bytes bytes = new Bytes(Math.max(8, from.length));
        System.arraycopy(from, 0, bytes.data, 0, from.length);
        bytes.size = from.length;
        return bytes;
    }

    public byte[] toArray() {
        return Arrays.copyOf(data, size);
    }

    public int size() {
        return size;
    }

    public boolean more() {
        return at < size;
    }

    private void ensureCapacity(int more) {
        if (size + more <= data.length) return;
        int want = Math.max(size + more, data.length * 2);
        data = Arrays.copyOf(data, want);
    }

    public void u8(int value) {
        ensureCapacity(1);
        data[size++] = (byte) value;
    }

    public int readU8() {
        if (at >= size) throw new IllegalStateException("ran off the end of the packet");
        return data[at++] & 0xFF;
    }

    public void varint(long value) {
        long left = value;
        while ((left & ~0x7FL) != 0) {
            u8((int) ((left & 0x7F) | 0x80));
            left >>>= 7;
        }
        u8((int) left);
    }

    public long readVarint() {
        long value = 0;
        int shift = 0;
        while (true) {
            int part = readU8();
            value |= (long) (part & 0x7F) << shift;
            if ((part & 0x80) == 0) return value;
            shift += 7;
            if (shift > 63) throw new IllegalStateException("a varint that long is corrupt");
        }
    }

    public void zigzag(long value) {
        varint((value << 1) ^ (value >> 63));
    }

    public long readZigzag() {
        long raw = readVarint();
        return (raw >>> 1) ^ -(raw & 1);
    }

    public void f32(double value) {
        int bits = Float.floatToIntBits((float) value);
        ensureCapacity(4);
        data[size++] = (byte) (bits >>> 24);
        data[size++] = (byte) (bits >>> 16);
        data[size++] = (byte) (bits >>> 8);
        data[size++] = (byte) bits;
    }

    public double readF32() {
        int bits = (readU8() << 24) | (readU8() << 16) | (readU8() << 8) | readU8();
        return Float.intBitsToFloat(bits);
    }

    public void f64(double value) {
        long bits = Double.doubleToLongBits(value);
        for (int shift = 0; shift < 64; shift += 8) u8((int) (bits >>> shift));
    }

    public double readF64() {
        long bits = 0;
        for (int shift = 0; shift < 64; shift += 8) bits |= (long) readU8() << shift;
        return Double.longBitsToDouble(bits);
    }

    public void u32(int value) {
        ensureCapacity(4);
        data[size++] = (byte) (value >>> 24);
        data[size++] = (byte) (value >>> 16);
        data[size++] = (byte) (value >>> 8);
        data[size++] = (byte) value;
    }

    public int readU32() {
        return (readU8() << 24) | (readU8() << 16) | (readU8() << 8) | readU8();
    }

    public void text(String value) {
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        varint(utf8.length);
        ensureCapacity(utf8.length);
        System.arraycopy(utf8, 0, data, size, utf8.length);
        size += utf8.length;
    }

    public String readText() {
        int length = (int) readVarint();
        if (length < 0 || at + length > size) throw new IllegalStateException("a string that long is corrupt");
        String value = new String(data, at, length, StandardCharsets.UTF_8);
        at += length;
        return value;
    }

    public void flag(boolean value) {
        if (blockAt < 0 || blockBits == 8) {
            ensureCapacity(1);
            blockAt = size;
            data[size++] = 0;
            blockBits = 0;
        }
        if (value) data[blockAt] |= (byte) (1 << blockBits);
        blockBits++;
    }

    public void endFlags() {
        blockAt = -1;
        blockBits = 0;
    }

    public boolean readFlag() {
        if (blockAt < 0 || blockBits == 8) {
            blockAt = at;
            readU8();
            blockBits = 0;
        }
        boolean value = (data[blockAt] & (1 << blockBits)) != 0;
        blockBits++;
        return value;
    }

    public void endReadFlags() {
        blockAt = -1;
        blockBits = 0;
    }
}
