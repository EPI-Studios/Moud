package com.moud.network.serializer;

import com.moud.api.particle.ColorKeyframe;
import com.moud.api.particle.Ease;
import com.moud.network.buffer.ByteBuffer;

public final class ColorKeyframeSerializer implements PacketSerializer.TypeSerializer<ColorKeyframe> {
    @Override
    public void write(ByteBuffer buffer, ColorKeyframe value) {
        buffer.writeFloat(value.t());
        buffer.writeFloat(value.r());
        buffer.writeFloat(value.g());
        buffer.writeFloat(value.b());
        buffer.writeFloat(value.a());
        Ease ease = value.ease();
        buffer.writeInt(ease != null ? ease.ordinal() : -1);
    }

    @Override
    public ColorKeyframe read(ByteBuffer buffer) {
        float t = buffer.readFloat();
        float r = buffer.readFloat();
        float g = buffer.readFloat();
        float b = buffer.readFloat();
        float a = buffer.readFloat();
        int easeOrdinal = buffer.readInt();
        Ease ease = easeOrdinal >= 0 && easeOrdinal < Ease.values().length ? Ease.values()[easeOrdinal] : null;
        return new ColorKeyframe(t, r, g, b, a, ease);
    }
}
