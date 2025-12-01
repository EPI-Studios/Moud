package com.moud.network.serializer;

import com.moud.api.particle.ParticleDescriptor;
import com.moud.api.particle.ParticleEmitterConfig;
import com.moud.api.particle.Vector3f;
import com.moud.network.buffer.ByteBuffer;

public final class ParticleEmitterConfigSerializer implements PacketSerializer.TypeSerializer<ParticleEmitterConfig> {
    private final ParticleDescriptorSerializer descriptorSerializer = new ParticleDescriptorSerializer();

    @Override
    public void write(ByteBuffer buffer, ParticleEmitterConfig value) {
        buffer.writeString(value.id());
        descriptorSerializer.write(buffer, value.descriptor());
        buffer.writeFloat(value.ratePerSecond());
        buffer.writeBoolean(value.enabled());
        buffer.writeInt(value.maxParticles());
        writeVec(buffer, value.positionJitter());
        writeVec(buffer, value.velocityJitter());
        buffer.writeFloat(value.lifetimeJitter());
        buffer.writeLong(value.seed());
        writeTexturePool(buffer, value.texturePool());
    }

    @Override
    public ParticleEmitterConfig read(ByteBuffer buffer) {
        String id = buffer.readString();
        ParticleDescriptor descriptor = descriptorSerializer.read(buffer);
        float rate = buffer.readFloat();
        boolean enabled = buffer.readBoolean();
        int maxParticles = buffer.readInt();
        Vector3f positionJitter = readVec(buffer);
        Vector3f velocityJitter = readVec(buffer);
        float lifetimeJitter = buffer.readFloat();
        long seed = buffer.readLong();
        java.util.List<String> texturePool = readTexturePool(buffer);
        return new ParticleEmitterConfig(
                id,
                descriptor,
                rate,
                enabled,
                maxParticles,
                positionJitter,
                velocityJitter,
                lifetimeJitter,
                seed,
                texturePool
        );
    }

    private void writeVec(ByteBuffer buffer, Vector3f v) {
        buffer.writeFloat(v.x());
        buffer.writeFloat(v.y());
        buffer.writeFloat(v.z());
    }

    private Vector3f readVec(ByteBuffer buffer) {
        return new Vector3f(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    private void writeTexturePool(ByteBuffer buffer, java.util.List<String> textures) {
        buffer.writeInt(textures.size());
        for (String tex : textures) {
            buffer.writeString(tex);
        }
    }

    private java.util.List<String> readTexturePool(ByteBuffer buffer) {
        int count = buffer.readInt();
        java.util.List<String> list = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(buffer.readString());
        }
        return list;
    }
}
