package com.moud.network.serializer;

import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.network.buffer.ByteBuffer;

public final class ZoneDefinitionSerializer implements PacketSerializer.TypeSerializer<MoudPackets.ZoneDefinition> {
    @Override
    public void write(ByteBuffer buffer, MoudPackets.ZoneDefinition value) {
        buffer.writeString(value.id());
        writeVec3(buffer, value.min());
        writeVec3(buffer, value.max());
    }

    @Override
    public MoudPackets.ZoneDefinition read(ByteBuffer buffer) {
        String id = buffer.readString();
        Vector3 min = readVec3(buffer);
        Vector3 max = readVec3(buffer);
        return new MoudPackets.ZoneDefinition(id, min, max);
    }

    private static void writeVec3(ByteBuffer buffer, Vector3 vec) {
        Vector3 v = vec != null ? vec : Vector3.zero();
        buffer.writeFloat(v.x);
        buffer.writeFloat(v.y);
        buffer.writeFloat(v.z);
    }

    private static Vector3 readVec3(ByteBuffer buffer) {
        return new Vector3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }
}

