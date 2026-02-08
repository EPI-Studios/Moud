package com.moud.network.serializer;

import com.moud.api.math.Vector3;
import com.moud.network.buffer.ByteBuffer;
import com.moud.network.limits.NetworkLimits;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapSerializerUtil {

    public static void writeStringObjectMap(ByteBuffer buffer, Map<String, Object> map) {
        buffer.writeInt(map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            buffer.writeString(entry.getKey());
            writeObject(buffer, entry.getValue());
        }
    }

    public static Map<String, Object> readStringObjectMap(ByteBuffer buffer) {
        return readStringObjectMap(buffer, 0);
    }

    private static void writeObject(ByteBuffer buffer, Object obj) {
        if (obj == null) {
            buffer.writeInt(0); // type: null
        } else if (obj instanceof String) {
            buffer.writeInt(1); // type: string
            buffer.writeString((String) obj);
        } else if (obj instanceof Integer) {
            buffer.writeInt(2); // type: int
            buffer.writeInt((Integer) obj);
        } else if (obj instanceof Double) {
            buffer.writeInt(3); // type: double
            buffer.writeDouble((Double) obj);
        } else if (obj instanceof Boolean) {
            buffer.writeInt(4); // type: boolean
            buffer.writeBoolean((Boolean) obj);
        } else if (obj instanceof Long) {
            buffer.writeInt(5); // type: long
            buffer.writeLong((Long) obj);
        } else if (obj instanceof Float) {
            buffer.writeInt(6); // type: float
            buffer.writeFloat((Float) obj);
        } else if (obj instanceof Vector3) {
            buffer.writeInt(7); // type: Vector3
            Vector3 vec = (Vector3) obj;
            buffer.writeFloat((float) vec.x);
            buffer.writeFloat((float) vec.y);
            buffer.writeFloat((float) vec.z);
        } else if (obj instanceof Map<?, ?> nested) {
            buffer.writeInt(8); // type: Map
            buffer.writeInt(nested.size());
            for (Map.Entry<?, ?> entry : nested.entrySet()) {
                buffer.writeString(String.valueOf(entry.getKey()));
                writeObject(buffer, entry.getValue());
            }
        } else if (obj instanceof List<?> list) {
            buffer.writeInt(9); // type: List
            buffer.writeInt(list.size());
            for (Object element : list) {
                writeObject(buffer, element);
            }
        } else {
            buffer.writeInt(1); // fallback to string
            buffer.writeString(obj.toString());
        }
    }

    private static Map<String, Object> readStringObjectMap(ByteBuffer buffer, int depth) {
        if (depth > NetworkLimits.MAX_NESTING_DEPTH) {
            throw new IllegalArgumentException("Map payload nesting depth exceeds limit " + NetworkLimits.MAX_NESTING_DEPTH);
        }

        int size = buffer.readInt();
        if (size < 0 || size > NetworkLimits.MAX_MAP_ENTRIES) {
            throw new IllegalArgumentException("Map size " + size + " exceeds limit " + NetworkLimits.MAX_MAP_ENTRIES);
        }

        Map<String, Object> map = new HashMap<>(Math.min(size, 16));
        for (int i = 0; i < size; i++) {
            String key = buffer.readString();
            Object value = readObject(buffer, depth + 1);
            map.put(key, value);
        }
        return map;
    }

    private static Object readObject(ByteBuffer buffer, int depth) {
        if (depth > NetworkLimits.MAX_NESTING_DEPTH) {
            throw new IllegalArgumentException("Payload nesting depth exceeds limit " + NetworkLimits.MAX_NESTING_DEPTH);
        }

        int type = buffer.readInt();
        return switch (type) {
            case 0 -> null;
            case 1 -> buffer.readString();
            case 2 -> buffer.readInt();
            case 3 -> buffer.readDouble();
            case 4 -> buffer.readBoolean();
            case 5 -> buffer.readLong();
            case 6 -> buffer.readFloat();
            case 7 -> new Vector3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
            case 8 -> {
                int size = buffer.readInt();
                if (size < 0 || size > NetworkLimits.MAX_MAP_ENTRIES) {
                    throw new IllegalArgumentException("Map size " + size + " exceeds limit " + NetworkLimits.MAX_MAP_ENTRIES);
                }

                Map<String, Object> nested = new HashMap<>(Math.min(size, 16));
                for (int i = 0; i < size; i++) {
                    String key = buffer.readString();
                    nested.put(key, readObject(buffer, depth + 1));
                }
                yield nested;
            }
            case 9 -> {
                int size = buffer.readInt();
                if (size < 0 || size > NetworkLimits.MAX_COLLECTION_ELEMENTS) {
                    throw new IllegalArgumentException(
                            "List size " + size + " exceeds limit " + NetworkLimits.MAX_COLLECTION_ELEMENTS
                    );
                }
                List<Object> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(readObject(buffer, depth + 1));
                }
                yield list;
            }
            default -> null;
        };
    }
}
