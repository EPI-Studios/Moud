package com.meekdev.moud.net.wire;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

// a growable byte buffer with the handful of encodings §9.1 names, and a reader for the same
//
// written by hand rather than over a ByteBuffer because two of these matter and neither is a
// ByteBuffer primitive: a varint, so a small number costs one byte instead of four, and a bit block,
// so a flag costs a bit instead of a byte. a tree's delta is mostly small numbers and flags
public final class Bytes {

    private byte[] data;
    private int size;
    private int at;

    // a bit block being filled: where it starts in the array, and how many bits are in it
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

    private void room(int more) {
        if (size + more <= data.length) return;
        int want = Math.max(size + more, data.length * 2);
        data = Arrays.copyOf(data, want);
    }

    public void u8(int value) {
        room(1);
        data[size++] = (byte) value;
    }

    public int readU8() {
        if (at >= size) throw new IllegalStateException("ran off the end of the packet");
        return data[at++] & 0xFF;
    }

    // seven bits at a time, high bit set while there is more. a property index, an instance id and a
    // dirty mask are all small in the ordinary case and cost one byte each
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

    // a signed number with small magnitudes costing little, which is what a delta of anything is
    public void zigzag(long value) {
        varint((value << 1) ^ (value >> 63));
    }

    public long readZigzag() {
        long raw = readVarint();
        return (raw >>> 1) ^ -(raw & 1);
    }

    public void f32(double value) {
        int bits = Float.floatToIntBits((float) value);
        room(4);
        data[size++] = (byte) (bits >>> 24);
        data[size++] = (byte) (bits >>> 16);
        data[size++] = (byte) (bits >>> 8);
        data[size++] = (byte) bits;
    }

    public double readF32() {
        int bits = (readU8() << 24) | (readU8() << 16) | (readU8() << 8) | readU8();
        return Float.intBitsToFloat(bits);
    }

    public void u32(int value) {
        room(4);
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
        room(utf8.length);
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

    // a run of flags, one bit each, in whatever order they were added
    //
    // the block is opened lazily and grows a byte at a time, so however many flags an instance's
    // delta carries it costs ceil(n/8) bytes rather than n
    public void flag(boolean value) {
        if (blockAt < 0 || blockBits == 8) {
            room(1);
            blockAt = size;
            data[size++] = 0;
            blockBits = 0;
        }
        if (value) data[blockAt] |= (byte) (1 << blockBits);
        blockBits++;
    }

    // a block ends when anything else is written, and the writer has to say so: a flag written after
    // a value belongs to a new block, and a reader counting bits has to agree about where
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
