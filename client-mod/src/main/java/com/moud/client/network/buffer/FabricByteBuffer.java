package com.moud.client.network.buffer;

import com.moud.network.buffer.ByteBuffer;
import net.minecraft.network.PacketByteBuf;
import io.netty.buffer.Unpooled;

import java.util.UUID;

public class FabricByteBuffer implements ByteBuffer {
    private final PacketByteBuf buffer;

    public FabricByteBuffer() {
        this.buffer = new PacketByteBuf(Unpooled.buffer());
    }

    public FabricByteBuffer(byte[] data) {
        this.buffer = new PacketByteBuf(Unpooled.wrappedBuffer(data));
    }

    @Override
    public void writeString(String value) {
        buffer.writeString(value);
    }

    @Override
    public String readString() {
        return buffer.readString();
    }

    @Override
    public void writeInt(int value) {
        buffer.writeInt(value);
    }

    @Override
    public int readInt() {
        return buffer.readInt();
    }

    @Override
    public void writeFloat(float value) {
        buffer.writeFloat(value);
    }

    @Override
    public float readFloat() {
        return buffer.readFloat();
    }

    @Override
    public void writeDouble(double value) {
        buffer.writeDouble(value);
    }

    @Override
    public double readDouble() {
        return buffer.readDouble();
    }

    @Override
    public void writeBoolean(boolean value) {
        buffer.writeBoolean(value);
    }

    @Override
    public boolean readBoolean() {
        return buffer.readBoolean();
    }

    @Override
    public void writeLong(long value) {
        buffer.writeLong(value);
    }

    @Override
    public long readLong() {
        return buffer.readLong();
    }

    @Override
    public void writeUuid(UUID value) {
        buffer.writeUuid(value);
    }

    @Override
    public UUID readUuid() {
        return buffer.readUuid();
    }

    @Override
    public void writeByteArray(byte[] value) {
        buffer.writeByteArray(value);
    }

    @Override
    public byte[] readByteArray() {
        return buffer.readByteArray();
    }

    @Override
    public int readableBytes() {
        return buffer.readableBytes();
    }

    @Override
    public void readBytes(byte[] dst) {
        buffer.readBytes(dst);
    }

    @Override
    public byte[] toByteArray() {
        byte[] result = new byte[buffer.readableBytes()];
        buffer.readBytes(result);
        return result;
    }
}