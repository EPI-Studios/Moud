package com.moud.network.metadata;

import java.lang.reflect.Field;
import java.util.List;

public class PacketMetadata {
    private final String packetId;
    private final Class<?> packetClass;
    private final Direction direction;
    private final List<FieldMetadata> fields;

    public PacketMetadata(String packetId, Class<?> packetClass, Direction direction, List<FieldMetadata> fields) {
        this.packetId = packetId;
        this.packetClass = packetClass;
        this.direction = direction;
        this.fields = fields;
    }

    public String getPacketId() {
        return packetId;
    }

    public Class<?> getPacketClass() {
        return packetClass;
    }

    public Direction getDirection() {
        return direction;
    }

    public List<FieldMetadata> getFields() {
        return fields;
    }

    public static class FieldMetadata {
        private final Field field;
        private final int order;
        private final boolean optional;
        private final int maxLength;
        private final Class<?> type;

        public FieldMetadata(Field field, int order, boolean optional, int maxLength) {
            this.field = field;
            this.order = order;
            this.optional = optional;
            this.maxLength = maxLength;
            this.type = field.getType();
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

        public Object getValue(Object instance) {
            try {
                return field.get(instance);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to access field " + field.getName(), e);
            }
        }
    }

    public enum Direction {
        CLIENT_TO_SERVER,
        SERVER_TO_CLIENT,
        BIDIRECTIONAL
    }
}