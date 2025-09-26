package com.moud.network.serializer;

import com.moud.api.math.Vector3;
import com.moud.network.buffer.ByteBuffer;

import java.util.HashMap;
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
        int size = buffer.readInt();
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String key = buffer.readString();
            Object value = readObject(buffer);
            map.put(key, value);
        }
        return map;
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
        } else if (obj instanceof Map) {
            buffer.writeInt(1); // fallback to string
            buffer.writeString(obj.toString());
        } else {
            buffer.writeInt(1); // fallback to string
            buffer.writeString(obj.toString());
        }
    }

    private static Object readObject(ByteBuffer buffer) {
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
            default -> null;
        };
    }
}