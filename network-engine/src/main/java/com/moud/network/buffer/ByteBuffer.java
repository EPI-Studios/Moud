package com.moud.network.buffer;

import java.util.UUID;

public interface ByteBuffer {
    void writeString(String value);
    String readString();

    void writeInt(int value);
    int readInt();

    void writeFloat(float value);
    float readFloat();

    void writeDouble(double value);
    double readDouble();

    void writeBoolean(boolean value);
    boolean readBoolean();

    void writeLong(long value);
    long readLong();

    void writeUuid(UUID value);
    UUID readUuid();

    void writeByteArray(byte[] value);
    byte[] readByteArray();

    int readableBytes();
    void readBytes(byte[] dst);
    byte[] toByteArray();
}