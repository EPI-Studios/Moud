package com.moud.network;

import com.moud.api.math.Vector3;
import com.moud.api.math.Quaternion;
import com.moud.network.buffer.ByteBuffer;
import com.moud.network.limits.NetworkLimits;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

class PacketSerializerTest {

    private static PacketSerializer serializer;
    private static PacketRegistry registry;
    private static PacketMetadata cursorMetadata;

    @BeforeAll
    static void setup() {
        serializer = new PacketSerializer();
        registry = new PacketRegistry();
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

    @Test
    void roundTripsSceneEditPacketWithNestedPayload() {
        PacketMetadata metadata = requireMetadata(MoudPackets.SceneEditPacket.class);

        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("objectId", "tree-42");
        payload.put("tags", java.util.List.of("foliage", "interactive"));
        payload.put("transform", java.util.Map.of(
                "position", java.util.List.of(1.0, 2.0, 3.0),
                "scale", java.util.Map.of("x", 1.5, "y", 2.0, "z", 0.5),
                "visible", true
        ));

        MoudPackets.SceneEditPacket packet = new MoudPackets.SceneEditPacket(
                "scene-main",
                "update",
                payload,
                1337L
        );

        TestByteBuffer writeBuffer = new TestByteBuffer();
        byte[] bytes = serializer.serialize(packet, metadata, writeBuffer);

        TestByteBuffer readBuffer = new TestByteBuffer(bytes);
        MoudPackets.SceneEditPacket decoded = serializer.deserialize(
                bytes,
                MoudPackets.SceneEditPacket.class,
                metadata,
                readBuffer
        );

        assertEquals(packet, decoded, "SceneEditPacket did not survive nested round-trip");
    }

    @Test
    void roundTripsSceneEditAckPacketWithOptionalSnapshot() {
        PacketMetadata metadata = requireMetadata(MoudPackets.SceneEditAckPacket.class);

        java.util.Map<String, Object> snapshotProps = new java.util.HashMap<>();
        snapshotProps.put("position", java.util.Map.of("x", 10, "y", 64, "z", -4));
        snapshotProps.put("components", java.util.List.of(
                java.util.Map.of("type", "light", "intensity", 0.8),
                java.util.Map.of("type", "mesh", "resource", "moud:oak_branch")
        ));

        MoudPackets.SceneObjectSnapshot snapshot = new MoudPackets.SceneObjectSnapshot(
                "obj-abc",
                "custom-light",
                snapshotProps
        );

        MoudPackets.SceneEditAckPacket packet = new MoudPackets.SceneEditAckPacket(
                "scene-main",
                true,
                "applied",
                snapshot,
                99L,
                "obj-abc"
        );

        TestByteBuffer writeBuffer = new TestByteBuffer();
        byte[] bytes = serializer.serialize(packet, metadata, writeBuffer);

        TestByteBuffer readBuffer = new TestByteBuffer(bytes);
        MoudPackets.SceneEditAckPacket decoded = serializer.deserialize(
                bytes,
                MoudPackets.SceneEditAckPacket.class,
                metadata,
                readBuffer
        );

        assertEquals(packet, decoded, "SceneEditAckPacket did not round-trip with optional data");
    }

    @Test
    void roundTripsPrimitiveCreatePacketWithPhysics() {
        PacketMetadata metadata = requireMetadata(MoudPackets.S2C_PrimitiveCreatePacket.class);

        MoudPackets.S2C_PrimitiveCreatePacket packet = new MoudPackets.S2C_PrimitiveCreatePacket(
                42L,
                MoudPackets.PrimitiveType.CUBE,
                new Vector3(1.0f, 2.0f, 3.0f),
                new Quaternion(0.0f, 0.0f, 0.0f, 1.0f),
                new Vector3(0.8f, 0.8f, 0.8f),
                MoudPackets.PrimitiveMaterial.solid(0.3f, 0.7f, 0.9f),
                null,
                "group-test",
                List.of(0, 1, 2),
                MoudPackets.PrimitivePhysics.dynamic(1.0f)
        );

        TestByteBuffer writeBuffer = new TestByteBuffer();
        byte[] bytes = serializer.serialize(packet, metadata, writeBuffer);

        TestByteBuffer readBuffer = new TestByteBuffer(bytes);
        MoudPackets.S2C_PrimitiveCreatePacket decoded = serializer.deserialize(
                bytes,
                MoudPackets.S2C_PrimitiveCreatePacket.class,
                metadata,
                readBuffer
        );

        assertEquals(packet, decoded, "S2C_PrimitiveCreatePacket did not round-trip with physics");
    }

    @Test
    void rejectsOversizedListPayloads() {
        PacketMetadata metadata = requireMetadata(MoudPackets.UIOverlayRemovePacket.class);

        byte[] bytes;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream dataOut = new DataOutputStream(out);
            dataOut.writeInt(NetworkLimits.MAX_COLLECTION_ELEMENTS + 1);
            dataOut.flush();
            bytes = out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        TestByteBuffer readBuffer = new TestByteBuffer(bytes);
        assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(
                bytes,
                MoudPackets.UIOverlayRemovePacket.class,
                metadata,
                readBuffer
        ));
    }

    @Test
    void rejectsOversizedMapPayloads() {
        PacketMetadata metadata = requireMetadata(MoudPackets.SceneEditPacket.class);

        byte[] bytes;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream dataOut = new DataOutputStream(out);

            writeTestString(dataOut, "scene-main");
            writeTestString(dataOut, "update");
            dataOut.writeInt(NetworkLimits.MAX_MAP_ENTRIES + 1);
            dataOut.flush();
            bytes = out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        TestByteBuffer readBuffer = new TestByteBuffer(bytes);
        assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(
                bytes,
                MoudPackets.SceneEditPacket.class,
                metadata,
                readBuffer
        ));
    }

    private static PacketMetadata requireMetadata(Class<?> packetClass) {
        PacketMetadata metadata = registry.getByClass(packetClass);
        assertNotNull(metadata, "Missing metadata for " + packetClass.getSimpleName());
        return metadata;
    }

    private static void writeTestString(DataOutputStream dataOut, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        dataOut.writeInt(bytes.length);
        dataOut.write(bytes);
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
