package com.moud.network.serializer;

import com.moud.api.particle.LightSettings;
import com.moud.network.buffer.ByteBuffer;

public final class LightSettingsSerializer implements PacketSerializer.TypeSerializer<LightSettings> {
    @Override
    public void write(ByteBuffer buffer, LightSettings value) {
        buffer.writeInt(value.block());
        buffer.writeInt(value.sky());
        buffer.writeBoolean(value.emissive());
    }

    @Override
    public LightSettings read(ByteBuffer buffer) {
        int block = buffer.readInt();
        int sky = buffer.readInt();
        boolean emissive = buffer.readBoolean();
        return new LightSettings(block, sky, emissive);
    }
}
