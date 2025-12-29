package com.moud.network.serializer;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.api.physics.player.PlayerPhysicsConfig;
import com.moud.network.MoudPackets;
import com.moud.network.buffer.ByteBuffer;
import com.moud.network.limits.NetworkLimits;
import com.moud.network.metadata.PacketMetadata;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
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
        register(float[].class, new FloatArraySerializer());
        register(Quaternion.class, new QuaternionSerializer());
        register(byte[].class, new ByteArraySerializer());
        register(MoudPackets.CursorUpdateData.class, new CursorUpdateDataSerializer());
        register(MoudPackets.SceneObjectSnapshot.class, new SceneObjectSnapshotSerializer());
        register(MoudPackets.EditorAssetDefinition.class, new EditorAssetDefinitionSerializer());
        register(MoudPackets.CollisionBoxData.class, new CollisionBoxDataSerializer());
        register(MoudPackets.ProjectFileEntry.class, new ProjectFileEntrySerializer());
        register(com.moud.api.animation.AnimationClip.class, new AnimationClipSerializer());
        register(MoudPackets.AnimationFileInfo.class, new AnimationFileInfoSerializer());
        register(com.moud.api.particle.ScalarKeyframe.class, new ScalarKeyframeSerializer());
        register(com.moud.api.particle.ColorKeyframe.class, new ColorKeyframeSerializer());
        register(com.moud.api.particle.UVRegion.class, new UVRegionSerializer());
        register(com.moud.api.particle.FrameAnimation.class, new FrameAnimationSerializer());
        register(com.moud.api.particle.LightSettings.class, new LightSettingsSerializer());
        register(com.moud.api.particle.ParticleDescriptor.class, new ParticleDescriptorSerializer());
        register(com.moud.api.particle.ParticleEmitterConfig.class, new ParticleEmitterConfigSerializer());
        register(MoudPackets.UIElementDefinition.class, new UIElementDefinitionSerializer());
        register(MoudPackets.PrimitiveMaterial.class, new PrimitiveMaterialSerializer());
        register(MoudPackets.PrimitivePhysics.class, new PrimitivePhysicsSerializer());
        register(MoudPackets.PrimitiveBatchEntry.class, new PrimitiveBatchEntrySerializer());
        register(MoudPackets.PrimitiveTransformEntry.class, new PrimitiveTransformEntrySerializer());
        register(MoudPackets.IKJointData.class, new IKJointDataSerializer());
        register(PlayerPhysicsConfig.class, new PlayerPhysicsConfigSerializer());
        register(MoudPackets.ZoneDefinition.class, new ZoneDefinitionSerializer());
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
            return;
        }

        if (rawType.isRecord()) {
            writeRecord(buffer, value, rawType);
            return;
        }

        throw new UnsupportedOperationException("No serializer for type: " + rawType);
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

        if (rawType.isRecord()) {
            return readRecord(buffer, rawType);
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
        if (size < 0 || size > NetworkLimits.MAX_COLLECTION_ELEMENTS) {
            throw new IllegalArgumentException(
                    "List size " + size + " exceeds limit " + NetworkLimits.MAX_COLLECTION_ELEMENTS
            );
        }
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

    private void writeRecord(ByteBuffer buffer, Object value, Class<?> recordType) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot serialize null record: " + recordType.getSimpleName());
        }
        RecordComponent[] components = recordType.getRecordComponents();
        for (RecordComponent component : components) {
            Object componentValue;
            try {
                componentValue = component.getAccessor().invoke(value);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Failed to read record component " + component.getName(), e);
            }

            if (componentValue == null) {
                throw new IllegalArgumentException(
                        "Non-optional record component " + component.getName() + " is null for " + recordType.getSimpleName()
                );
            }

            Class<?> componentClass = extractRawClass(component.getGenericType());
            writeValue(buffer, componentValue, componentClass, component.getGenericType());
        }
    }

    private Object readRecord(ByteBuffer buffer, Class<?> recordType) {
        RecordComponent[] components = recordType.getRecordComponents();
        Object[] args = new Object[components.length];
        Class<?>[] paramTypes = new Class<?>[components.length];

        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            java.lang.reflect.Type componentGenericType = component.getGenericType();
            Class<?> componentClass = extractRawClass(componentGenericType);
            args[i] = readValue(buffer, componentClass, componentGenericType);
            paramTypes[i] = component.getType();
        }

        try {
            Constructor<?> ctor = recordType.getDeclaredConstructor(paramTypes);
            return ctor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            return createInstance(recordType, args);
        }
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

    private static class PlayerPhysicsConfigSerializer implements TypeSerializer<PlayerPhysicsConfig> {
        @Override
        public void write(ByteBuffer buffer, PlayerPhysicsConfig value) {
            buffer.writeFloat(value.speed());
            buffer.writeFloat(value.accel());
            buffer.writeFloat(value.friction());
            buffer.writeFloat(value.airResistance());
            buffer.writeFloat(value.gravity());
            buffer.writeFloat(value.jumpForce());
            buffer.writeFloat(value.stepHeight());
            buffer.writeFloat(value.width());
            buffer.writeFloat(value.height());
            buffer.writeFloat(value.sprintMultiplier());
            buffer.writeFloat(value.sneakMultiplier());
        }

        @Override
        public PlayerPhysicsConfig read(ByteBuffer buffer) {
            return new PlayerPhysicsConfig(
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat()
            );
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

    private static class FloatArraySerializer implements TypeSerializer<float[]> {
        @Override
        public void write(ByteBuffer buffer, float[] value) {
            buffer.writeInt(value.length);
            for (float v : value) {
                buffer.writeFloat(v);
            }
        }

        @Override
        public float[] read(ByteBuffer buffer) {
            int size = buffer.readInt();
            if (size < 0 || size > NetworkLimits.MAX_COLLECTION_ELEMENTS) {
                throw new IllegalArgumentException(
                        "Array size " + size + " exceeds limit " + NetworkLimits.MAX_COLLECTION_ELEMENTS
                );
            }
            float[] out = new float[size];
            for (int i = 0; i < size; i++) {
                out[i] = buffer.readFloat();
            }
            return out;
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

    private class PrimitiveMaterialSerializer implements TypeSerializer<MoudPackets.PrimitiveMaterial> {
        @Override
        public void write(ByteBuffer buffer, MoudPackets.PrimitiveMaterial value) {
            buffer.writeFloat(value.r());
            buffer.writeFloat(value.g());
            buffer.writeFloat(value.b());
            buffer.writeFloat(value.a());
            boolean hasTexture = value.texture() != null;
            buffer.writeBoolean(hasTexture);
            if (hasTexture) {
                buffer.writeString(value.texture());
            }
            buffer.writeBoolean(value.unlit());
            buffer.writeBoolean(value.doubleSided());
            buffer.writeBoolean(value.renderThroughBlocks());
        }

        @Override
        public MoudPackets.PrimitiveMaterial read(ByteBuffer buffer) {
            float r = buffer.readFloat();
            float g = buffer.readFloat();
            float b = buffer.readFloat();
            float a = buffer.readFloat();
            boolean hasTex = buffer.readBoolean();
            String texture = hasTex ? buffer.readString() : null;
            boolean unlit = buffer.readBoolean();
            boolean doubleSided = buffer.readBoolean();
            boolean renderThrough = buffer.readBoolean();
            return new MoudPackets.PrimitiveMaterial(r, g, b, a, texture, unlit, doubleSided, renderThrough);
        }
    }

    private class PrimitiveBatchEntrySerializer implements TypeSerializer<MoudPackets.PrimitiveBatchEntry> {
        @Override
        public void write(ByteBuffer buffer, MoudPackets.PrimitiveBatchEntry value) {
            writeValue(buffer, value.primitiveId(), long.class, long.class);
            writeValue(buffer, value.type(), MoudPackets.PrimitiveType.class, MoudPackets.PrimitiveType.class);
            writeValue(buffer, value.position(), Vector3.class, Vector3.class);
            writeValue(buffer, value.rotation(), Quaternion.class, Quaternion.class);
            writeValue(buffer, value.scale(), Vector3.class, Vector3.class);
            writeValue(buffer, value.material(), MoudPackets.PrimitiveMaterial.class, MoudPackets.PrimitiveMaterial.class);
            boolean hasVerts = value.vertices() != null;
            buffer.writeBoolean(hasVerts);
            if (hasVerts) {
                writeValue(buffer, value.vertices(), List.class, MoudPackets.PrimitiveBatchEntry.class.getRecordComponents()[6].getGenericType());
            }
            boolean hasGroup = value.groupId() != null;
            buffer.writeBoolean(hasGroup);
            if (hasGroup) {
                buffer.writeString(value.groupId());
            }
            boolean hasIndices = value.indices() != null;
            buffer.writeBoolean(hasIndices);
            if (hasIndices) {
                writeValue(buffer, value.indices(), List.class, MoudPackets.PrimitiveBatchEntry.class.getRecordComponents()[8].getGenericType());
            }
            boolean hasPhysics = value.physics() != null;
            buffer.writeBoolean(hasPhysics);
            if (hasPhysics) {
                writeValue(buffer, value.physics(), MoudPackets.PrimitivePhysics.class, MoudPackets.PrimitivePhysics.class);
            }
        }

        @Override
        public MoudPackets.PrimitiveBatchEntry read(ByteBuffer buffer) {
            long id = (long) readValue(buffer, long.class, long.class);
            MoudPackets.PrimitiveType type = (MoudPackets.PrimitiveType) readValue(buffer, MoudPackets.PrimitiveType.class, MoudPackets.PrimitiveType.class);
            Vector3 pos = (Vector3) readValue(buffer, Vector3.class, Vector3.class);
            Quaternion rot = (Quaternion) readValue(buffer, Quaternion.class, Quaternion.class);
            Vector3 scale = (Vector3) readValue(buffer, Vector3.class, Vector3.class);
            MoudPackets.PrimitiveMaterial mat = (MoudPackets.PrimitiveMaterial) readValue(buffer, MoudPackets.PrimitiveMaterial.class, MoudPackets.PrimitiveMaterial.class);
            boolean hasVerts = buffer.readBoolean();
            @SuppressWarnings("unchecked")
            List<Vector3> verts = hasVerts ? (List<Vector3>) readValue(buffer, List.class, MoudPackets.PrimitiveBatchEntry.class.getRecordComponents()[6].getGenericType()) : null;
            boolean hasGroup = buffer.readBoolean();
            String groupId = hasGroup ? buffer.readString() : null;
            boolean hasIndices = buffer.readBoolean();
            @SuppressWarnings("unchecked")
            List<Integer> indices = hasIndices ? (List<Integer>) readValue(buffer, List.class, MoudPackets.PrimitiveBatchEntry.class.getRecordComponents()[8].getGenericType()) : null;
            boolean hasPhysics = buffer.readBoolean();
            MoudPackets.PrimitivePhysics physics = hasPhysics ? (MoudPackets.PrimitivePhysics) readValue(buffer, MoudPackets.PrimitivePhysics.class, MoudPackets.PrimitivePhysics.class) : null;
            return new MoudPackets.PrimitiveBatchEntry(id, type, pos, rot, scale, mat, verts, groupId, indices, physics);
        }
    }

    private class PrimitiveTransformEntrySerializer implements TypeSerializer<MoudPackets.PrimitiveTransformEntry> {
        @Override
        public void write(ByteBuffer buffer, MoudPackets.PrimitiveTransformEntry value) {
            writeValue(buffer, value.primitiveId(), long.class, long.class);
            writeValue(buffer, value.position(), Vector3.class, Vector3.class);
            writeValue(buffer, value.rotation(), Quaternion.class, Quaternion.class);
            writeValue(buffer, value.scale(), Vector3.class, Vector3.class);
        }

        @Override
        public MoudPackets.PrimitiveTransformEntry read(ByteBuffer buffer) {
            long id = (long) readValue(buffer, long.class, long.class);
            Vector3 pos = (Vector3) readValue(buffer, Vector3.class, Vector3.class);
            Quaternion rot = (Quaternion) readValue(buffer, Quaternion.class, Quaternion.class);
            Vector3 scale = (Vector3) readValue(buffer, Vector3.class, Vector3.class);
            return new MoudPackets.PrimitiveTransformEntry(id, pos, rot, scale);
        }
    }

    private class IKJointDataSerializer implements TypeSerializer<MoudPackets.IKJointData> {
        @Override
        public void write(ByteBuffer buffer, MoudPackets.IKJointData value) {
            writeValue(buffer, value.position(), Vector3.class, Vector3.class);
            writeValue(buffer, value.rotation(), Quaternion.class, Quaternion.class);
        }

        @Override
        public MoudPackets.IKJointData read(ByteBuffer buffer) {
            Vector3 pos = (Vector3) readValue(buffer, Vector3.class, Vector3.class);
            Quaternion rot = (Quaternion) readValue(buffer, Quaternion.class, Quaternion.class);
            return new MoudPackets.IKJointData(pos, rot);
        }
    }

    private class PrimitivePhysicsSerializer implements TypeSerializer<MoudPackets.PrimitivePhysics> {
        @Override
        public void write(ByteBuffer buffer, MoudPackets.PrimitivePhysics value) {
            writeValue(buffer, value.hasCollision(), boolean.class, boolean.class);
            writeValue(buffer, value.isDynamic(), boolean.class, boolean.class);
            writeValue(buffer, value.mass(), float.class, float.class);
        }

        @Override
        public MoudPackets.PrimitivePhysics read(ByteBuffer buffer) {
            boolean hasCollision = (boolean) readValue(buffer, boolean.class, boolean.class);
            boolean isDynamic = (boolean) readValue(buffer, boolean.class, boolean.class);
            float mass = (float) readValue(buffer, float.class, float.class);
            return new MoudPackets.PrimitivePhysics(hasCollision, isDynamic, mass);
        }
    }
}
