package com.moud.network.serializer;

import com.moud.api.particle.UVRegion;
import com.moud.network.buffer.ByteBuffer;

public final class UVRegionSerializer implements PacketSerializer.TypeSerializer<UVRegion> {
    @Override
    public void write(ByteBuffer buffer, UVRegion value) {
        buffer.writeFloat(value.u0());
        buffer.writeFloat(value.v0());
        buffer.writeFloat(value.u1());
        buffer.writeFloat(value.v1());
    }

    @Override
    public UVRegion read(ByteBuffer buffer) {
        return new UVRegion(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }
}
