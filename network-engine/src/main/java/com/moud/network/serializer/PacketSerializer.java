package com.moud.network.serializer;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.network.buffer.ByteBuffer;
import com.moud.network.metadata.PacketMetadata;

import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.util.*;

public class PacketSerializer {
    private final Map<Class<?>, TypeSerializer<?>> serializers = new HashMap<>();

    public PacketSerializer() {
        registerDefaults();
    }

    private void registerDefaults() {
        register(String.class, new StringSerializer());
        register(int.class, new IntSerializer());
        register(Integer.class, new IntSerializer());
        register(float.class, new FloatSerializer());
        register(Float.class, new FloatSerializer());
        register(double.class, new DoubleSerializer());
        register(Double.class, new DoubleSerializer());
        register(boolean.class, new BooleanSerializer());
        register(Boolean.class, new BooleanSerializer());
        register(long.class, new LongSerializer());
        register(Long.class, new LongSerializer());
        register(UUID.class, new UUIDSerializer());
        register(Vector3.class, new Vector3Serializer());
        register(byte[].class, new ByteArraySerializer());
        register(Quaternion.class, new QuaternionSerializer());
        register(byte[].class, new ByteArraySerializer());
        register(MoudPackets.CursorUpdateData.class, new CursorUpdateDataSerializer());
        register(MoudPackets.SceneObjectSnapshot.class, new SceneObjectSnapshotSerializer());
        register(MoudPackets.EditorAssetDefinition.class, new EditorAssetDefinitionSerializer());
        register(MoudPackets.CollisionBoxData.class, new CollisionBoxDataSerializer());
        register(MoudPackets.ProjectFileEntry.class, new ProjectFileEntrySerializer());
        register(MoudPackets.FakePlayerDescriptor.class, new FakePlayerDescriptorSerializer());
        register(MoudPackets.FakePlayerWaypoint.class, new FakePlayerWaypointSerializer());
        register(com.moud.api.animation.AnimationClip.class, new AnimationClipSerializer());
        register(MoudPackets.AnimationFileInfo.class, new AnimationFileInfoSerializer());
        register(com.moud.api.particle.ScalarKeyframe.class, new ScalarKeyframeSerializer());
        register(com.moud.api.particle.ColorKeyframe.class, new ColorKeyframeSerializer());
        register(com.moud.api.particle.UVRegion.class, new UVRegionSerializer());
        register(com.moud.api.particle.FrameAnimation.class, new FrameAnimationSerializer());
        register(com.moud.api.particle.LightSettings.class, new LightSettingsSerializer());
        register(com.moud.api.particle.ParticleDescriptor.class, new ParticleDescriptorSerializer());
        register(com.moud.api.particle.ParticleEmitterConfig.class, new ParticleEmitterConfigSerializer());
    }

    public <T> void register(Class<T> type, TypeSerializer<T> serializer) {
        serializers.put(type, serializer);
    }

    public byte[] serialize(Object packet, PacketMetadata metadata, ByteBuffer buffer) {
        for (PacketMetadata.FieldMetadata field : metadata.fields()) {
            Object value = field.getValue(packet);

            if (field.isOptional()) {
                buffer.writeBoolean(value != null);
                if (value == null) {
                    continue;
                }
            } else {
                if (value == null) {
                    throw new IllegalArgumentException("Non-optional field " + field.getField().getName() + " is null");
                }
            }

            writeValue(buffer, value, field.getType(), field.getGenericType());
        }
        return buffer.toByteArray();
    }

    public <T> T deserialize(byte[] data, Class<T> packetClass, PacketMetadata metadata, ByteBuffer buffer) {
        Object[] args = new Object[metadata.fields().size()];

        for (int i = 0; i < metadata.fields().size(); i++) {
            PacketMetadata.FieldMetadata field = metadata.fields().get(i);

            if (field.isOptional()) {
                boolean isPresent = buffer.readBoolean();
                if (isPresent) {
                    args[i] = readValue(buffer, field);
                } else {
                    args[i] = null;
                }
            } else {
                args[i] = readValue(buffer, field);
            }
        }

        return createInstance(packetClass, args);
    }

    @SuppressWarnings("unchecked")
    private void writeValue(ByteBuffer buffer, Object value, Class<?> rawType, java.lang.reflect.Type genericType) {
        if (rawType.isEnum()) {
            buffer.writeInt(((Enum<?>) value).ordinal());
            return;
        }

        if (Map.class.isAssignableFrom(rawType)) {
            MapSerializerUtil.writeStringObjectMap(buffer, (Map<String, Object>) value);
            return;
        }

        if (List.class.isAssignableFrom(rawType)) {
            writeList(buffer, (List<?>) value, genericType);
            return;
        }

        TypeSerializer<Object> serializer = (TypeSerializer<Object>) serializers.get(rawType);

        if (serializer != null) {
            serializer.write(buffer, value);
        } else {
            throw new UnsupportedOperationException("No serializer for type: " + rawType);
        }
    }

    private void writeList(ByteBuffer buffer, List<?> list, java.lang.reflect.Type listType) {
        if (!(listType instanceof ParameterizedType parameterizedType)) {
            throw new UnsupportedOperationException("Cannot serialize raw List without generic type information");
        }

        java.lang.reflect.Type elementType = parameterizedType.getActualTypeArguments()[0];
        Class<?> elementClass = extractRawClass(elementType);

        buffer.writeInt(list.size());
        for (Object item : list) {
            writeValue(buffer, item, elementClass, elementType);
        }
    }

    @SuppressWarnings("unchecked")
    private Object readValue(ByteBuffer buffer, PacketMetadata.FieldMetadata field) {
        return readValue(buffer, field.getType(), field.getGenericType());
    }

    @SuppressWarnings("unchecked")
    private Object readValue(ByteBuffer buffer, Class<?> rawType, java.lang.reflect.Type genericType) {
        if (rawType.isEnum()) {
            int ordinal = buffer.readInt();
            Enum<?>[] enumConstants = (Enum<?>[]) rawType.getEnumConstants();
            if (ordinal >= 0 && ordinal < enumConstants.length) {
                return enumConstants[ordinal];
            } else {
                throw new IndexOutOfBoundsException("Invalid ordinal " + ordinal + " for enum " + rawType.getSimpleName());
            }
        }

        if (Map.class.isAssignableFrom(rawType)) {
            return MapSerializerUtil.readStringObjectMap(buffer);
        }

        if (List.class.isAssignableFrom(rawType)) {
            return readList(buffer, genericType);
        }

        TypeSerializer<Object> serializer = (TypeSerializer<Object>) serializers.get(rawType);
        if (serializer != null) {
            return serializer.read(buffer);
        }
        throw new UnsupportedOperationException("No serializer for type: " + rawType);
    }

    private List<?> readList(ByteBuffer buffer, java.lang.reflect.Type listType) {
        if (!(listType instanceof ParameterizedType parameterizedType)) {
            throw new UnsupportedOperationException("Cannot deserialize raw List without generic type information");
        }

        java.lang.reflect.Type elementType = parameterizedType.getActualTypeArguments()[0];
        Class<?> elementClass = extractRawClass(elementType);

        int size = buffer.readInt();
        List<Object> list = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            list.add(readValue(buffer, elementClass, elementType));
        }
        return list;
    }

    private Class<?> extractRawClass(java.lang.reflect.Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            java.lang.reflect.Type raw = parameterizedType.getRawType();
            if (raw instanceof Class<?>) {
                return (Class<?>) raw;
            }
        }
        throw new UnsupportedOperationException("Unsupported type: " + type);
    }
    @SuppressWarnings("unchecked")
    private <T> T createInstance(Class<T> clazz, Object[] args) {
        try {
            Constructor<?>[] constructors = clazz.getConstructors();
            for (Constructor<?> constructor : constructors) {
                if (constructor.getParameterCount() == args.length) {
                    return (T) constructor.newInstance(args);
                }
            }
            throw new RuntimeException("No suitable constructor found for " + clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of " + clazz, e);
        }
    }

    public interface TypeSerializer<T> {
        void write(ByteBuffer buffer, T value);

        T read(ByteBuffer buffer);
    }

    private static class StringSerializer implements TypeSerializer<String> {
        public void write(ByteBuffer buffer, String value) {
            buffer.writeString(value);
        }

        public String read(ByteBuffer buffer) {
            return buffer.readString();
        }
    }

    private static class IntSerializer implements TypeSerializer<Integer> {
        public void write(ByteBuffer buffer, Integer value) {
            buffer.writeInt(value);
        }

        public Integer read(ByteBuffer buffer) {
            return buffer.readInt();
        }
    }

    private static class FloatSerializer implements TypeSerializer<Float> {
        public void write(ByteBuffer buffer, Float value) {
            buffer.writeFloat(value);
        }

        public Float read(ByteBuffer buffer) {
            return buffer.readFloat();
        }
    }

    private static class DoubleSerializer implements TypeSerializer<Double> {
        public void write(ByteBuffer buffer, Double value) {
            buffer.writeDouble(value);
        }

        public Double read(ByteBuffer buffer) {
            return buffer.readDouble();
        }
    }

    private static class BooleanSerializer implements TypeSerializer<Boolean> {
        public void write(ByteBuffer buffer, Boolean value) {
            buffer.writeBoolean(value);
        }

        public Boolean read(ByteBuffer buffer) {
            return buffer.readBoolean();
        }
    }

    private static class LongSerializer implements TypeSerializer<Long> {
        public void write(ByteBuffer buffer, Long value) {
            buffer.writeLong(value);
        }

        public Long read(ByteBuffer buffer) {
            return buffer.readLong();
        }
    }

    private static class UUIDSerializer implements TypeSerializer<UUID> {
        public void write(ByteBuffer buffer, UUID value) {
            buffer.writeUuid(value);
        }

        public UUID read(ByteBuffer buffer) {
            return buffer.readUuid();
        }
    }

    private static class Vector3Serializer implements TypeSerializer<Vector3> {
        public void write(ByteBuffer buffer, Vector3 value) {
            buffer.writeFloat((float) value.x);
            buffer.writeFloat((float) value.y);
            buffer.writeFloat((float) value.z);
        }

        public Vector3 read(ByteBuffer buffer) {
            return new Vector3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
        }
    }

    private static class QuaternionSerializer implements TypeSerializer<Quaternion> {
        public void write(ByteBuffer buffer, Quaternion value) {
            buffer.writeFloat(value.x);
            buffer.writeFloat(value.y);
            buffer.writeFloat(value.z);
            buffer.writeFloat(value.w);
        }

        public Quaternion read(ByteBuffer buffer) {
            return new Quaternion(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
        }
    }

    private static class ByteArraySerializer implements TypeSerializer<byte[]> {
        public void write(ByteBuffer buffer, byte[] value) {
            buffer.writeByteArray(value);
        }

        public byte[] read(ByteBuffer buffer) {
            return buffer.readByteArray();
        }
    }

    private class CursorUpdateDataSerializer implements TypeSerializer<MoudPackets.CursorUpdateData> {
        @Override
        public void write(ByteBuffer buffer, MoudPackets.CursorUpdateData value) {
            writeValue(buffer, value.playerId(), UUID.class, UUID.class);
            writeValue(buffer, value.position(), Vector3.class, Vector3.class);
            writeValue(buffer, value.normal(), Vector3.class, Vector3.class);
            writeValue(buffer, value.hit(), boolean.class, boolean.class);
        }

        @Override
        public MoudPackets.CursorUpdateData read(ByteBuffer buffer) {
            UUID playerId = (UUID) readValue(buffer, UUID.class, UUID.class);
            Vector3 position = (Vector3) readValue(buffer, Vector3.class, Vector3.class);
            Vector3 normal = (Vector3) readValue(buffer, Vector3.class, Vector3.class);
            boolean hit = (boolean) readValue(buffer, boolean.class, boolean.class);
            return new MoudPackets.CursorUpdateData(playerId, position, normal, hit);
        }
    }

    private static class SceneObjectSnapshotSerializer implements TypeSerializer<MoudPackets.SceneObjectSnapshot> {
        @Override
        public void write(ByteBuffer buffer, MoudPackets.SceneObjectSnapshot value) {
            buffer.writeString(value.objectId());
            buffer.writeString(value.objectType());
            MapSerializerUtil.writeStringObjectMap(buffer, value.properties());
        }

        @Override
        public MoudPackets.SceneObjectSnapshot read(ByteBuffer buffer) {
            String objectId = buffer.readString();
            String objectType = buffer.readString();
            Map<String, Object> properties = MapSerializerUtil.readStringObjectMap(buffer);
            return new MoudPackets.SceneObjectSnapshot(objectId, objectType, properties);
        }
    }

    private static class EditorAssetDefinitionSerializer implements TypeSerializer<MoudPackets.EditorAssetDefinition> {
        @Override
        public void write(ByteBuffer buffer, MoudPackets.EditorAssetDefinition value) {
            buffer.writeString(value.id());
            buffer.writeString(value.label());
            buffer.writeString(value.objectType());
            MapSerializerUtil.writeStringObjectMap(buffer, value.defaultProperties());
        }

        @Override
        public MoudPackets.EditorAssetDefinition read(ByteBuffer buffer) {
            String id = buffer.readString();
            String label = buffer.readString();
            String type = buffer.readString();
            Map<String, Object> defaults = MapSerializerUtil.readStringObjectMap(buffer);
            return new MoudPackets.EditorAssetDefinition(id, label, type, defaults);
        }
    }

    private static class ProjectFileEntrySerializer implements TypeSerializer<MoudPackets.ProjectFileEntry> {
        @Override
        public void write(ByteBuffer buffer, MoudPackets.ProjectFileEntry value) {
            buffer.writeString(value.path());
            buffer.writeInt(value.kind().ordinal());
        }

        @Override
        public MoudPackets.ProjectFileEntry read(ByteBuffer buffer) {
            String path = buffer.readString();
            int kindOrdinal = buffer.readInt();
            MoudPackets.ProjectEntryKind kind = MoudPackets.ProjectEntryKind.values()[Math.max(0, Math.min(kindOrdinal, MoudPackets.ProjectEntryKind.values().length - 1))];
            return new MoudPackets.ProjectFileEntry(path, kind);
        }
    }

    private class CollisionBoxDataSerializer implements TypeSerializer<MoudPackets.CollisionBoxData> {
        @Override
        public void write(ByteBuffer buffer, MoudPackets.CollisionBoxData value) {
            writeValue(buffer, value.center(), Vector3.class, Vector3.class);
            writeValue(buffer, value.halfExtents(), Vector3.class, Vector3.class);
            writeValue(buffer, value.rotation(), Quaternion.class, Quaternion.class);
        }

        @Override
        public MoudPackets.CollisionBoxData read(ByteBuffer buffer) {
            Vector3 center = (Vector3) readValue(buffer, Vector3.class, Vector3.class);
            Vector3 halfExtents = (Vector3) readValue(buffer, Vector3.class, Vector3.class);
            Quaternion rotation = (Quaternion) readValue(buffer, Quaternion.class, Quaternion.class);
            return new MoudPackets.CollisionBoxData(center, halfExtents, rotation);
        }
    }
}
