package com.moud.network.serializer;

import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.network.buffer.ByteBuffer;
import com.moud.network.serializer.PacketSerializer.TypeSerializer;

public final class FakePlayerWaypointSerializer implements PacketSerializer.TypeSerializer<MoudPackets.FakePlayerWaypoint> {
    @Override
    public void write(ByteBuffer buffer, MoudPackets.FakePlayerWaypoint value) {
        Vector3 pos = value.position();
        buffer.writeDouble(pos.x);
        buffer.writeDouble(pos.y);
        buffer.writeDouble(pos.z);
    }

    @Override
    public MoudPackets.FakePlayerWaypoint read(ByteBuffer buffer) {
        double x = buffer.readDouble();
        double y = buffer.readDouble();
        double z = buffer.readDouble();
        return new MoudPackets.FakePlayerWaypoint(new Vector3(x, y, z));
    }
}
