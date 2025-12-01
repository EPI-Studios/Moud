package com.moud.network.serializer;

import com.moud.api.math.Vector3;
import com.moud.api.particle.ColorKeyframe;
import com.moud.api.particle.FrameAnimation;
import com.moud.api.particle.LightSettings;
import com.moud.api.particle.ParticleDescriptor;
import com.moud.api.particle.ScalarKeyframe;
import com.moud.api.particle.UVRegion;
import com.moud.api.particle.Vector3f;
import com.moud.network.buffer.ByteBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ParticleDescriptorSerializer implements PacketSerializer.TypeSerializer<ParticleDescriptor> {
    private final ScalarKeyframeSerializer scalarSerializer = new ScalarKeyframeSerializer();
    private final ColorKeyframeSerializer colorSerializer = new ColorKeyframeSerializer();
    private final UVRegionSerializer uvSerializer = new UVRegionSerializer();
    private final FrameAnimationSerializer frameSerializer = new FrameAnimationSerializer();
    private final LightSettingsSerializer lightSerializer = new LightSettingsSerializer();

    @Override
    public void write(ByteBuffer buffer, ParticleDescriptor value) {
        buffer.writeString(value.texture());
        buffer.writeInt(value.renderType().ordinal());
        buffer.writeInt(value.billboarding().ordinal());
        buffer.writeInt(value.collisionMode().ordinal());
        writeVec(buffer, value.position());
        writeVec(buffer, value.velocity());
        writeVec(buffer, value.acceleration());
        buffer.writeFloat(value.drag());
        buffer.writeFloat(value.gravityMultiplier());
        buffer.writeFloat(value.lifetimeSeconds());

        writeScalarList(buffer, value.sizeOverLife());
        writeScalarList(buffer, value.rotationOverLife());
        writeColorList(buffer, value.colorOverLife());
        writeScalarList(buffer, value.alphaOverLife());

        writeOptional(buffer, value.uvRegion(), v -> uvSerializer.write(buffer, v));
        writeOptional(buffer, value.frameAnimation(), v -> frameSerializer.write(buffer, v));

        List<String> behaviors = value.behaviors();
        buffer.writeInt(behaviors.size());
        for (String behavior : behaviors) {
            buffer.writeString(behavior);
        }

        Map<String, Object> payload = value.behaviorPayload();
        buffer.writeBoolean(payload != null && !payload.isEmpty());
        if (payload != null && !payload.isEmpty()) {
            MapSerializerUtil.writeStringObjectMap(buffer, payload);
        }

        lightSerializer.write(buffer, value.light());
        buffer.writeInt(value.sortHint().ordinal());
    }

    @Override
    public ParticleDescriptor read(ByteBuffer buffer) {
        String texture = buffer.readString();
        int render = buffer.readInt();
        int billboard = buffer.readInt();
        int collision = buffer.readInt();
        Vector3f pos = readVec(buffer);
        Vector3f vel = readVec(buffer);
        Vector3f accel = readVec(buffer);
        float drag = buffer.readFloat();
        float gravity = buffer.readFloat();
        float lifetime = buffer.readFloat();

        List<ScalarKeyframe> size = readScalarList(buffer);
        List<ScalarKeyframe> rotation = readScalarList(buffer);
        List<ColorKeyframe> color = readColorList(buffer);
        List<ScalarKeyframe> alpha = readScalarList(buffer);

        UVRegion uv = readOptional(buffer, b -> uvSerializer.read(b));
        FrameAnimation frameAnim = readOptional(buffer, b -> frameSerializer.read(b));

        int behaviorCount = buffer.readInt();
        List<String> behaviors = new ArrayList<>(behaviorCount);
        for (int i = 0; i < behaviorCount; i++) {
            behaviors.add(buffer.readString());
        }

        Map<String, Object> payload = null;
        boolean hasPayload = buffer.readBoolean();
        if (hasPayload) {
            payload = MapSerializerUtil.readStringObjectMap(buffer);
        }

        LightSettings light = lightSerializer.read(buffer);
        int sortOrdinal = buffer.readInt();

        return new ParticleDescriptor(
                texture,
                com.moud.api.particle.RenderType.values()[render],
                com.moud.api.particle.Billboarding.values()[billboard],
                com.moud.api.particle.CollisionMode.values()[collision],
                pos,
                vel,
                accel,
                drag,
                gravity,
                lifetime,
                size,
                rotation,
                color,
                alpha,
                uv,
                frameAnim,
                behaviors,
                payload,
                light,
                com.moud.api.particle.SortHint.values()[sortOrdinal]
        );
    }

    private void writeScalarList(ByteBuffer buffer, List<ScalarKeyframe> list) {
        buffer.writeInt(list.size());
        for (ScalarKeyframe kf : list) {
            scalarSerializer.write(buffer, kf);
        }
    }

    private List<ScalarKeyframe> readScalarList(ByteBuffer buffer) {
        int count = buffer.readInt();
        List<ScalarKeyframe> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(scalarSerializer.read(buffer));
        }
        return list;
    }

    private void writeColorList(ByteBuffer buffer, List<ColorKeyframe> list) {
        buffer.writeInt(list.size());
        for (ColorKeyframe kf : list) {
            colorSerializer.write(buffer, kf);
        }
    }

    private List<ColorKeyframe> readColorList(ByteBuffer buffer) {
        int count = buffer.readInt();
        List<ColorKeyframe> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(colorSerializer.read(buffer));
        }
        return list;
    }

    private void writeVec(ByteBuffer buffer, Vector3f v) {
        buffer.writeFloat(v.x());
        buffer.writeFloat(v.y());
        buffer.writeFloat(v.z());
    }

    private Vector3f readVec(ByteBuffer buffer) {
        return new Vector3f(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    private <T> void writeOptional(ByteBuffer buffer, T value, Writer<T> writer) {
        buffer.writeBoolean(value != null);
        if (value != null) {
            writer.write(value);
        }
    }

    private <T> T readOptional(ByteBuffer buffer, java.util.function.Function<ByteBuffer, T> reader) {
        boolean present = buffer.readBoolean();
        if (!present) {
            return null;
        }
        return reader.apply(buffer);
    }

    @FunctionalInterface
    private interface Writer<T> {
        void write(T value);
    }
}
