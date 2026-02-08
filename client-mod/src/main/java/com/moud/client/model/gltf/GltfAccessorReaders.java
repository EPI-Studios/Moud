package com.moud.client.model.gltf;

import de.javagl.jgltf.model.AccessorData;
import de.javagl.jgltf.model.AccessorModel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class GltfAccessorReaders {
    private GltfAccessorReaders() {}

    // glTF component type constants
    private static final int GLTF_COMPONENT_BYTE = 5120;
    private static final int GLTF_COMPONENT_UNSIGNED_BYTE = 5121;
    private static final int GLTF_COMPONENT_SHORT = 5122;
    private static final int GLTF_COMPONENT_UNSIGNED_SHORT = 5123;
    private static final int GLTF_COMPONENT_UNSIGNED_INT = 5125;
    private static final int GLTF_COMPONENT_FLOAT = 5126;

    static float[] readFloatArray(AccessorModel accessor) {
        AccessorData data = accessor.getAccessorData();
        ByteBuffer buffer = data.createByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
        int totalComponents = data.getTotalNumComponents();
        float[] out = new float[totalComponents];

        Class<?> componentType = data.getComponentType();
        boolean normalized = accessor.isNormalized();
        int gltfComponentType = accessor.getComponentType();

        if (componentType == Float.TYPE || componentType == Float.class || gltfComponentType == GLTF_COMPONENT_FLOAT) {
            for (int i = 0; i < totalComponents; i++) {
                out[i] = buffer.getFloat();
            }
            return out;
        }
        if (componentType == Byte.TYPE || componentType == Byte.class) {
            boolean unsigned = gltfComponentType == GLTF_COMPONENT_UNSIGNED_BYTE;
            for (int i = 0; i < totalComponents; i++) {
                int value = unsigned ? Byte.toUnsignedInt(buffer.get()) : buffer.get();
                if (normalized) {
                    out[i] = unsigned ? (value / 255.0f) : Math.max(-1.0f, value / 127.0f);
                } else {
                    out[i] = (float) value;
                }
            }
            return out;
        }
        if (componentType == Short.TYPE || componentType == Short.class) {
            boolean unsigned = gltfComponentType == GLTF_COMPONENT_UNSIGNED_SHORT;
            for (int i = 0; i < totalComponents; i++) {
                int value = unsigned ? Short.toUnsignedInt(buffer.getShort()) : buffer.getShort();
                if (normalized) {
                    out[i] = unsigned ? (value / 65535.0f) : Math.max(-1.0f, value / 32767.0f);
                } else {
                    out[i] = (float) value;
                }
            }
            return out;
        }
        if (componentType == Integer.TYPE || componentType == Integer.class) {
            for (int i = 0; i < totalComponents; i++) {
                out[i] = gltfComponentType == GLTF_COMPONENT_UNSIGNED_INT
                        ? (float) Integer.toUnsignedLong(buffer.getInt())
                        : (float) buffer.getInt();
            }
            return out;
        }
        throw new IllegalArgumentException("Unsupported float accessor component type: " + componentType);
    }

    static int[] readUnsignedIntArray(AccessorModel accessor) {
        AccessorData data = accessor.getAccessorData();
        ByteBuffer buffer = data.createByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
        int totalComponents = data.getTotalNumComponents();
        int[] out = new int[totalComponents];

        Class<?> componentType = data.getComponentType();
        if (componentType == Byte.TYPE || componentType == Byte.class) {
            for (int i = 0; i < totalComponents; i++) {
                out[i] = Byte.toUnsignedInt(buffer.get());
            }
            return out;
        }
        if (componentType == Short.TYPE || componentType == Short.class) {
            for (int i = 0; i < totalComponents; i++) {
                out[i] = Short.toUnsignedInt(buffer.getShort());
            }
            return out;
        }
        if (componentType == Integer.TYPE || componentType == Integer.class) {
            for (int i = 0; i < totalComponents; i++) {
                out[i] = buffer.getInt();
            }
            return out;
        }
        throw new IllegalArgumentException("Unsupported int accessor component type: " + componentType);
    }
}
