package com.moud.network.serializer;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.network.buffer.ByteBuffer;
import com.moud.network.serializer.PacketSerializer.TypeSerializer;

import java.util.ArrayList;
import java.util.List;

public final class FakePlayerDescriptorSerializer implements TypeSerializer<MoudPackets.FakePlayerDescriptor> {
    private final FakePlayerWaypointSerializer waypointSerializer = new FakePlayerWaypointSerializer();

    @Override
    public void write(ByteBuffer buffer, MoudPackets.FakePlayerDescriptor value) {
        buffer.writeLong(value.id());
        buffer.writeString(value.label());
        buffer.writeString(value.skinUrl());
        writeVec(buffer, value.position());
        writeQuat(buffer, value.rotation());
        buffer.writeDouble(value.width());
        buffer.writeDouble(value.height());
        buffer.writeBoolean(value.physicsEnabled());
        buffer.writeBoolean(value.sneaking());
        buffer.writeBoolean(value.sprinting());
        buffer.writeBoolean(value.swinging());
        buffer.writeBoolean(value.usingItem());
        List<MoudPackets.FakePlayerWaypoint> path = value.path();
        buffer.writeInt(path != null ? path.size() : 0);
        if (path != null) {
            for (MoudPackets.FakePlayerWaypoint wp : path) {
                waypointSerializer.write(buffer, wp);
            }
        }
        buffer.writeDouble(value.pathSpeed());
        buffer.writeBoolean(value.pathLoop());
        buffer.writeBoolean(value.pathPingPong());
    }

    @Override
    public MoudPackets.FakePlayerDescriptor read(ByteBuffer buffer) {
        long id = buffer.readLong();
        String label = buffer.readString();
        String skin = buffer.readString();
        Vector3 pos = readVec(buffer);
        Quaternion rot = readQuat(buffer);
        double width = buffer.readDouble();
        double height = buffer.readDouble();
        boolean physics = buffer.readBoolean();
        boolean sneaking = buffer.readBoolean();
        boolean sprinting = buffer.readBoolean();
        boolean swinging = buffer.readBoolean();
        boolean usingItem = buffer.readBoolean();
        int pathCount = buffer.readInt();
        List<MoudPackets.FakePlayerWaypoint> path = new ArrayList<>(Math.max(0, pathCount));
        for (int i = 0; i < pathCount; i++) {
            path.add(waypointSerializer.read(buffer));
        }
        double pathSpeed = buffer.readDouble();
        boolean pathLoop = buffer.readBoolean();
        boolean pathPingPong = buffer.readBoolean();
        return new MoudPackets.FakePlayerDescriptor(id, label, skin, pos, rot, width, height,
                physics, sneaking, sprinting, swinging, usingItem, path, pathSpeed, pathLoop, pathPingPong);
    }

    private void writeVec(ByteBuffer buffer, Vector3 v) {
        buffer.writeDouble(v.x);
        buffer.writeDouble(v.y);
        buffer.writeDouble(v.z);
    }

    private Vector3 readVec(ByteBuffer buffer) {
        return new Vector3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    private void writeQuat(ByteBuffer buffer, Quaternion q) {
        buffer.writeFloat((float) q.x);
        buffer.writeFloat((float) q.y);
        buffer.writeFloat((float) q.z);
        buffer.writeFloat((float) q.w);
    }

    private Quaternion readQuat(ByteBuffer buffer) {
        return new Quaternion(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }
}
