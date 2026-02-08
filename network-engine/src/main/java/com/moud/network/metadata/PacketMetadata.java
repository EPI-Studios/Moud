package com.moud.network.metadata;

import java.lang.reflect.Field;
import java.util.List;

public record PacketMetadata(String packetId, Class<?> packetClass, Direction direction, List<FieldMetadata> fields) {

    public enum Direction {
        CLIENT_TO_SERVER,
        SERVER_TO_CLIENT,
        BIDIRECTIONAL
    }

    public static class FieldMetadata {
        private final Field field;
        private final int order;
        private final boolean optional;
        private final int maxLength;
        private final Class<?> type;
        private final java.lang.reflect.Type genericType;

        public FieldMetadata(Field field, int order, boolean optional, int maxLength) {
            this.field = field;
            this.order = order;
            this.optional = optional;
            this.maxLength = maxLength;
            this.type = field.getType();
            this.genericType = field.getGenericType();
            field.setAccessible(true);
        }

        public Field getField() {
            return field;
        }

        public int getOrder() {
            return order;
        }

        public boolean isOptional() {
            return optional;
        }

        public int getMaxLength() {
            return maxLength;
        }

        public Class<?> getType() {
            return type;
        }

        public java.lang.reflect.Type getGenericType() {
            return genericType;
        }

        public Object getValue(Object instance) {
            try {
                return field.get(instance);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to access field " + field.getName(), e);
            }
        }
    }
}
