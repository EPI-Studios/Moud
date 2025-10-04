package com.moud.network.serializer;

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
    }

    public <T> void register(Class<T> type, TypeSerializer<T> serializer) {
        serializers.put(type, serializer);
    }

    public byte[] serialize(Object packet, PacketMetadata metadata, ByteBuffer buffer) {
        for (PacketMetadata.FieldMetadata field : metadata.fields()) {
            Object value = field.getValue(packet);

            if (value == null && !field.isOptional()) {
                throw new IllegalArgumentException("Non-optional field " + field.getField().getName() + " is null");
            }

            if (value != null) {
                writeValue(buffer, value, field.getType());
            }
        }

        return buffer.toByteArray();
    }

    public <T> T deserialize(byte[] data, Class<T> packetClass, PacketMetadata metadata, ByteBuffer buffer) {
        Object[] args = new Object[metadata.fields().size()];

        for (int i = 0; i < metadata.fields().size(); i++) {
            PacketMetadata.FieldMetadata field = metadata.fields().get(i);
            args[i] = readValue(buffer, field);
        }

        return createInstance(packetClass, args);
    }

    @SuppressWarnings("unchecked")
    private void writeValue(ByteBuffer buffer, Object value, Class<?> type) {
        if (type.isEnum()) {
            buffer.writeInt(((Enum<?>) value).ordinal());
            return;
        }

        if (type == Map.class || type.isAssignableFrom(Map.class)) {
            MapSerializerUtil.writeStringObjectMap(buffer, (Map<String, Object>) value);
            return;
        }

        if (type == List.class) {
            writeList(buffer, (List<?>) value);
            return;
        }

        TypeSerializer<Object> serializer = (TypeSerializer<Object>) serializers.get(type);

        if (serializer != null) {
            serializer.write(buffer, value);
        } else {
            throw new UnsupportedOperationException("No serializer for type: " + type);
        }
    }
    private void writeList(ByteBuffer buffer, List<?> list) {
        buffer.writeInt(list.size());
        for (Object item : list) {
            if (item instanceof MoudPackets.CursorUpdateData(
                    UUID playerId, Vector3 position, Vector3 normal, boolean hit
            )) {
                writeValue(buffer, playerId, UUID.class);
                writeValue(buffer, position, Vector3.class);
                writeValue(buffer, normal, Vector3.class);
                writeValue(buffer, hit, boolean.class);
            } else if (item instanceof UUID) {
                writeValue(buffer, item, UUID.class);
            } else if (item instanceof Map) {
                writeValue(buffer, item, Map.class);
            } else {
                throw new UnsupportedOperationException("Cannot serialize unknown type in list: " + item.getClass());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Object readValue(ByteBuffer buffer, PacketMetadata.FieldMetadata field) {
        Class<?> type = field.getType();

        if (type.isEnum()) {
            int ordinal = buffer.readInt();
            Enum<?>[] enumConstants = (Enum<?>[]) type.getEnumConstants();
            if (ordinal >= 0 && ordinal < enumConstants.length) {
                return enumConstants[ordinal];
            } else {
                throw new IndexOutOfBoundsException("Invalid ordinal " + ordinal + " for enum " + type.getSimpleName());
            }
        }

        if (type == Map.class || type.isAssignableFrom(Map.class)) {
            return MapSerializerUtil.readStringObjectMap(buffer);
        }

        if (type == List.class) {
            return readList(buffer, field);
        }

        TypeSerializer<Object> serializer = (TypeSerializer<Object>) serializers.get(type);

        if (serializer != null) {
            return serializer.read(buffer);
        } else {
            throw new UnsupportedOperationException("No serializer for type: " + type);
        }
    }

    private List<?> readList(ByteBuffer buffer, PacketMetadata.FieldMetadata field) {
        int size = buffer.readInt();
        List<Object> list = new ArrayList<>(size);

        Class<?> listElementType = getListElementType(field);

        for (int i = 0; i < size; i++) {
            if (listElementType == MoudPackets.CursorUpdateData.class) {
                UUID playerId = (UUID) readValue(buffer, UUID.class);
                Vector3 position = (Vector3) readValue(buffer, Vector3.class);
                Vector3 normal = (Vector3) readValue(buffer, Vector3.class);
                boolean hit = (boolean) readValue(buffer, boolean.class);
                list.add(new MoudPackets.CursorUpdateData(playerId, position, normal, hit));
            } else if (listElementType == UUID.class) {
                list.add(readValue(buffer, UUID.class));
            } else if (listElementType == Map.class) {
                list.add(MapSerializerUtil.readStringObjectMap(buffer));
            } else {
                throw new UnsupportedOperationException("Cannot deserialize unknown type in list: " + listElementType);
            }
        }
        return list;
    }

    private Object readValue(ByteBuffer buffer, Class<?> type) {
        if (type.isEnum()) {
            int ordinal = buffer.readInt();
            Enum<?>[] enumConstants = (Enum<?>[]) type.getEnumConstants();
            if (ordinal >= 0 && ordinal < enumConstants.length) {
                return enumConstants[ordinal];
            } else {
                throw new IndexOutOfBoundsException("Invalid ordinal " + ordinal + " for enum " + type.getSimpleName());
            }
        }
        TypeSerializer<?> serializer = serializers.get(type);
        if (serializer != null) {
            return serializer.read(buffer);
        }
        throw new UnsupportedOperationException("No serializer for type: " + type);
    }

    private Class<?> getListElementType(PacketMetadata.FieldMetadata field) {
        java.lang.reflect.Type genericType = field.getField().getGenericType();
        if (genericType instanceof ParameterizedType) {
            java.lang.reflect.Type[] typeArguments = ((ParameterizedType) genericType).getActualTypeArguments();
            if (typeArguments.length > 0) {
                java.lang.reflect.Type listType = typeArguments[0];
                if (listType instanceof Class<?>) {
                    return (Class<?>) listType;
                } else if (listType instanceof ParameterizedType) {
                    return (Class<?>) ((ParameterizedType) listType).getRawType();
                }
            }
        }
        throw new UnsupportedOperationException("Cannot deserialize raw or complex List. Use a generic List<T> with simple types or List<Map<...>>.");
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

    private static class ByteArraySerializer implements TypeSerializer<byte[]> {
        public void write(ByteBuffer buffer, byte[] value) {
            buffer.writeByteArray(value);
        }

        public byte[] read(ByteBuffer buffer) {
            return buffer.readByteArray();
        }
    }
}