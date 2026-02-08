package com.moud.network.serializer;

import com.moud.api.particle.Ease;
import com.moud.api.particle.ScalarKeyframe;
import com.moud.network.buffer.ByteBuffer;

public final class ScalarKeyframeSerializer implements PacketSerializer.TypeSerializer<ScalarKeyframe> {
    @Override
    public void write(ByteBuffer buffer, ScalarKeyframe value) {
        buffer.writeFloat(value.t());
        buffer.writeFloat(value.value());
        Ease ease = value.ease();
        buffer.writeInt(ease != null ? ease.ordinal() : -1);
    }

    @Override
    public ScalarKeyframe read(ByteBuffer buffer) {
        float t = buffer.readFloat();
        float v = buffer.readFloat();
        int easeOrdinal = buffer.readInt();
        Ease ease = easeOrdinal >= 0 && easeOrdinal < Ease.values().length ? Ease.values()[easeOrdinal] : null;
        return new ScalarKeyframe(t, v, ease);
    }
}
