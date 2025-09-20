package com.moud.server.network;

import com.moud.network.buffer.ByteBuffer;
import net.minestom.server.network.NetworkBuffer;

import java.util.UUID;

import static net.minestom.server.network.NetworkBuffer.*;

public class MinestomByteBuffer implements ByteBuffer {
    private final NetworkBuffer buffer;

    public MinestomByteBuffer() {
        this.buffer = new NetworkBuffer(java.nio.ByteBuffer.allocate(1024));
    }

    public MinestomByteBuffer(byte[] data) {
        this.buffer = new NetworkBuffer(java.nio.ByteBuffer.wrap(data));
    }

    @Override
    public void writeString(String value) {
        buffer.write(STRING, value);
    }

    @Override
    public String readString() {
        return buffer.read(STRING);
    }

    @Override
    public void writeInt(int value) {
        buffer.write(INT, value);
    }

    @Override
    public int readInt() {
        return buffer.read(INT);
    }

    @Override
    public void writeFloat(float value) {
        buffer.write(FLOAT, value);
    }

    @Override
    public float readFloat() {
        return buffer.read(FLOAT);
    }

    @Override
    public void writeDouble(double value) {
        buffer.write(DOUBLE, value);
    }

    @Override
    public double readDouble() {
        return buffer.read(DOUBLE);
    }

    @Override
    public void writeBoolean(boolean value) {
        buffer.write(BOOLEAN, value);
    }

    @Override
    public boolean readBoolean() {
        return buffer.read(BOOLEAN);
    }

    @Override
    public void writeLong(long value) {
        buffer.write(LONG, value);
    }

    @Override
    public long readLong() {
        return buffer.read(LONG);
    }

    @Override
    public void writeUuid(UUID value) {
        buffer.write(UUID, value);
    }

    @Override
    public UUID readUuid() {
        return buffer.read(UUID);
    }

    @Override
    public void writeByteArray(byte[] value) {
        buffer.write(BYTE_ARRAY, value);
    }

    @Override
    public byte[] readByteArray() {
        return buffer.read(BYTE_ARRAY);
    }

    @Override
    public int readableBytes() {
        return buffer.readableBytes();
    }

    @Override
    public void readBytes(byte[] dst) {
        byte[] temp = buffer.readBytes(dst.length);
        System.arraycopy(temp, 0, dst, 0, temp.length);
    }

    @Override
    public byte[] toByteArray() {
        return buffer.readBytes(buffer.readableBytes());
    }
}