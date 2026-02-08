package com.moud.network.serializer;

import com.moud.network.MoudPackets;
import com.moud.network.buffer.ByteBuffer;
import com.moud.network.serializer.PacketSerializer.TypeSerializer;

public final class AnimationFileInfoSerializer implements TypeSerializer<MoudPackets.AnimationFileInfo> {
    @Override
    public void write(ByteBuffer buffer, MoudPackets.AnimationFileInfo value) {
        buffer.writeString(value.path());
        buffer.writeString(value.name());
        buffer.writeFloat(value.duration());
        buffer.writeInt(value.trackCount());
    }

    @Override
    public MoudPackets.AnimationFileInfo read(ByteBuffer buffer) {
        String path = buffer.readString();
        String name = buffer.readString();
        float duration = buffer.readFloat();
        int trackCount = buffer.readInt();
        return new MoudPackets.AnimationFileInfo(path, name, duration, trackCount);
    }
}
