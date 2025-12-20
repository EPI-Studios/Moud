package com.moud.server.network;

import com.moud.network.buffer.ByteBuffer;
import com.moud.network.limits.NetworkLimits;

import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class InboundPacketByteBuffer implements ByteBuffer {
    private final java.nio.ByteBuffer buffer;

    InboundPacketByteBuffer(byte[] data) {
        this.buffer = java.nio.ByteBuffer.wrap(data);
    }

    @Override
    public void writeString(String value) {
        throw new UnsupportedOperationException("InboundPacketByteBuffer is read-only");
    }

    @Override
    public String readString() {
        int byteLength = readVarInt();
        if (byteLength < 0 || byteLength > NetworkLimits.MAX_STRING_BYTES) {
            throw new IllegalArgumentException(
                    "String length " + byteLength + " exceeds limit " + NetworkLimits.MAX_STRING_BYTES
            );
        }
        if (byteLength > buffer.remaining()) {
            throw new IllegalArgumentException(
                    "String length " + byteLength + " exceeds remaining bytes " + buffer.remaining()
            );
        }
        byte[] bytes = new byte[byteLength];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public void writeInt(int value) {
        throw new UnsupportedOperationException("InboundPacketByteBuffer is read-only");
    }

    @Override
    public int readInt() {
        return buffer.getInt();
    }

    @Override
    public void writeFloat(float value) {
        throw new UnsupportedOperationException("InboundPacketByteBuffer is read-only");
    }

    @Override
    public float readFloat() {
        return buffer.getFloat();
    }

    @Override
    public void writeDouble(double value) {
        throw new UnsupportedOperationException("InboundPacketByteBuffer is read-only");
    }

    @Override
    public double readDouble() {
        return buffer.getDouble();
    }

    @Override
    public void writeBoolean(boolean value) {
        throw new UnsupportedOperationException("InboundPacketByteBuffer is read-only");
    }

    @Override
    public boolean readBoolean() {
        return buffer.get() != 0;
    }

    @Override
    public void writeLong(long value) {
        throw new UnsupportedOperationException("InboundPacketByteBuffer is read-only");
    }

    @Override
    public long readLong() {
        return buffer.getLong();
    }

    @Override
    public void writeUuid(UUID value) {
        throw new UnsupportedOperationException("InboundPacketByteBuffer is read-only");
    }

    @Override
    public UUID readUuid() {
        return new UUID(readLong(), readLong());
    }

    @Override
    public void writeByteArray(byte[] value) {
        throw new UnsupportedOperationException("InboundPacketByteBuffer is read-only");
    }

    @Override
    public byte[] readByteArray() {
        int length = readVarInt();
        if (length < 0 || length > NetworkLimits.MAX_BYTE_ARRAY_BYTES) {
            throw new IllegalArgumentException(
                    "Byte array length " + length + " exceeds limit " + NetworkLimits.MAX_BYTE_ARRAY_BYTES
            );
        }
        if (length > buffer.remaining()) {
            throw new IllegalArgumentException(
                    "Byte array length " + length + " exceeds remaining bytes " + buffer.remaining()
            );
        }
        byte[] dst = new byte[length];
        buffer.get(dst);
        return dst;
    }

    @Override
    public int readableBytes() {
        return buffer.remaining();
    }

    @Override
    public void readBytes(byte[] dst) {
        if (dst.length > buffer.remaining()) {
            throw new BufferUnderflowException();
        }
        buffer.get(dst);
    }

    @Override
    public byte[] toByteArray() {
        java.nio.ByteBuffer slice = buffer.slice();
        byte[] dst = new byte[slice.remaining()];
        slice.get(dst);
        return dst;
    }

    private int readVarInt() {
        int numRead = 0;
        int result = 0;

        byte read;
        do {
            if (!buffer.hasRemaining()) {
                throw new BufferUnderflowException();
            }

            read = buffer.get();
            int value = (read & 0b0111_1111);
            result |= (value << (7 * numRead));

            numRead++;
            if (numRead > 5) {
                throw new IllegalArgumentException("VarInt is too big");
            }
        } while ((read & 0b1000_0000) != 0);

        return result;
    }
}

