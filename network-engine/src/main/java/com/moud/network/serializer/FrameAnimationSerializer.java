package com.moud.network.serializer;

import com.moud.api.particle.FrameAnimation;
import com.moud.network.buffer.ByteBuffer;

public final class FrameAnimationSerializer implements PacketSerializer.TypeSerializer<FrameAnimation> {
    @Override
    public void write(ByteBuffer buffer, FrameAnimation value) {
        buffer.writeInt(value.frames());
        buffer.writeFloat(value.fps());
        buffer.writeBoolean(value.loop());
        buffer.writeBoolean(value.pingPong());
        buffer.writeInt(value.startFrame());
    }

    @Override
    public FrameAnimation read(ByteBuffer buffer) {
        int frames = buffer.readInt();
        float fps = buffer.readFloat();
        boolean loop = buffer.readBoolean();
        boolean pingPong = buffer.readBoolean();
        int startFrame = buffer.readInt();
        return new FrameAnimation(frames, fps, loop, pingPong, startFrame);
    }
}
