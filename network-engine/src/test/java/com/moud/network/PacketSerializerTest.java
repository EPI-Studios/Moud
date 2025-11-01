package com.moud.network;

import com.moud.api.math.Vector3;
import com.moud.network.buffer.ByteBuffer;
import com.moud.network.metadata.PacketMetadata;
import com.moud.network.registry.PacketRegistry;
import com.moud.network.serializer.PacketSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PacketSerializerTest {

    private static PacketSerializer serializer;
    private static PacketMetadata cursorMetadata;

    @BeforeAll
    static void setup() {
        serializer = new PacketSerializer();
        PacketRegistry registry = new PacketRegistry();
        registry.scan("com.moud.network");
        cursorMetadata = registry.getByClass(MoudPackets.CursorPositionUpdatePacket.class);
        assertNotNull(cursorMetadata, "Failed to resolve metadata for CursorPositionUpdatePacket");
    }

    @Test
    void roundTripsCursorUpdatePacket() {
        List<MoudPackets.CursorUpdateData> updates = new ArrayList<>();
        updates.add(new MoudPackets.CursorUpdateData(
                UUID.randomUUID(),
                new Vector3(1.25f, 2.5f, -3.75f),
                new Vector3(0.0f, 1.0f, 0.0f),
                true
        ));
        updates.add(new MoudPackets.CursorUpdateData(
                UUID.randomUUID(),
                new Vector3(-10f, 42f, 0.5f),
                new Vector3(1.0f, 0.0f, 0.0f),
                false
        ));

        MoudPackets.CursorPositionUpdatePacket packet = new MoudPackets.CursorPositionUpdatePacket(updates);

        TestByteBuffer writeBuffer = new TestByteBuffer();
        byte[] bytes = serializer.serialize(packet, cursorMetadata, writeBuffer);

        TestByteBuffer readBuffer = new TestByteBuffer(bytes);
        MoudPackets.CursorPositionUpdatePacket decoded = serializer.deserialize(
                bytes,
                MoudPackets.CursorPositionUpdatePacket.class,
                cursorMetadata,
                readBuffer
        );

        assertEquals(packet, decoded, "Packet did not round-trip through PacketSerializer");
    }

    private static final class TestByteBuffer implements ByteBuffer {
        private final ByteArrayOutputStream out;
        private final DataOutputStream dataOut;
        private ByteArrayInputStream in;
        private DataInputStream dataIn;

        TestByteBuffer() {
            this.out = new ByteArrayOutputStream();
            this.dataOut = new DataOutputStream(out);
        }

        TestByteBuffer(byte[] data) {
            this();
            this.in = new ByteArrayInputStream(data);
            this.dataIn = new DataInputStream(in);
        }

        @Override
        public void writeString(String value) {
            try {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                dataOut.writeInt(bytes.length);
                dataOut.write(bytes);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String readString() {
            try {
                int length = dataIn.readInt();
                byte[] bytes = new byte[length];
                dataIn.readFully(bytes);
                return new String(bytes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void writeInt(int value) {
            try {
                dataOut.writeInt(value);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public int readInt() {
            try {
                return dataIn.readInt();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void writeFloat(float value) {
            try {
                dataOut.writeFloat(value);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public float readFloat() {
            try {
                return dataIn.readFloat();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void writeDouble(double value) {
            try {
                dataOut.writeDouble(value);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public double readDouble() {
            try {
                return dataIn.readDouble();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void writeBoolean(boolean value) {
            try {
                dataOut.writeBoolean(value);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public boolean readBoolean() {
            try {
                return dataIn.readBoolean();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void writeLong(long value) {
            try {
                dataOut.writeLong(value);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public long readLong() {
            try {
                return dataIn.readLong();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void writeUuid(UUID value) {
            writeLong(value.getMostSignificantBits());
            writeLong(value.getLeastSignificantBits());
        }

        @Override
        public UUID readUuid() {
            return new UUID(readLong(), readLong());
        }

        @Override
        public void writeByteArray(byte[] value) {
            try {
                dataOut.writeInt(value.length);
                dataOut.write(value);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public byte[] readByteArray() {
            try {
                int length = dataIn.readInt();
                byte[] dst = new byte[length];
                dataIn.readFully(dst);
                return dst;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public int readableBytes() {
            return in != null ? in.available() : out.size();
        }

        @Override
        public void readBytes(byte[] dst) {
            try {
                dataIn.readFully(dst);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public byte[] toByteArray() {
            return out.toByteArray();
        }
    }
}
