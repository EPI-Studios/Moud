package com.moud.server.scripting;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ScriptMapper {
    private static final int MAX_DEPTH = 32;

    private ScriptMapper() {
    }

    public static Object toJavaObject(Value value) {
        return convertValue(value, 0);
    }

    public static Map<String, Object> toMap(Value value) {
        Object converted = toJavaObject(value);
        if (converted instanceof Map<?, ?> convertedMap) {
            Map<String, Object> result = new HashMap<>();
            convertedMap.forEach((k, v) -> {
                if (k != null && v != null) {
                    result.put(k.toString(), v);
                }
            });
            return result;
        }
        return Map.of();
    }

    public static List<Object> toList(Value value) {
        Object converted = toJavaObject(value);
        if (converted instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    private static Object convertValue(Value value, int depth) {
        if (value == null || value.isNull() || depth > MAX_DEPTH) {
            return null;
        }

        if (value.isHostObject()) {
            Object hostObj = null;
            try {
                hostObj = value.asHostObject();
            } catch (Exception ignored) {
            }

            if (hostObj instanceof Vector3 vec) {
                return Map.of("x", vec.x, "y", vec.y, "z", vec.z);
            }

            if (hostObj instanceof Quaternion quat) {
                return Map.of("x", quat.x, "y", quat.y, "z", quat.z, "w", quat.w);
            }

            if (hostObj instanceof Map<?, ?> map) {
                Map<String, Object> converted = new HashMap<>();
                map.forEach((k, v) -> {
                    if (k != null && v != null) {
                        converted.put(k.toString(), v instanceof Value val ? convertValue(val, depth + 1) : v);
                    }
                });
                return converted;
            }

            if (hostObj instanceof List<?> list) {
                List<Object> converted = new ArrayList<>();
                for (Object element : list) {
                    if (element instanceof Value val) {
                        Object convertedElement = convertValue(val, depth + 1);
                        if (convertedElement != null) {
                            converted.add(convertedElement);
                        }
                    } else if (element != null) {
                        converted.add(element);
                    }
                }
                return converted;
            }

            if (hostObj != null) {
                return hostObj;
            }
        }

        if (value.hasArrayElements()) {
            List<Object> list = new ArrayList<>();
            long size = value.getArraySize();
            for (int i = 0; i < size; i++) {
                Object converted = convertValue(value.getArrayElement(i), depth + 1);
                if (converted != null) {
                    list.add(converted);
                }
            }
            return list;
        }

        if (value.hasMembers()) {
            Map<String, Object> map = new HashMap<>();
            for (String key : value.getMemberKeys()) {
                Object member = convertValue(value.getMember(key), depth + 1);
                if (member != null) {
                    map.put(key, member);
                }
            }
            return map;
        }

        if (value.isNumber()) {
            if (value.fitsInInt()) {
                return value.asInt();
            }
            if (value.fitsInLong()) {
                return value.asLong();
            }
            return value.asDouble();
        }

        if (value.isBoolean()) {
            return value.asBoolean();
        }

        if (value.isString()) {
            return value.asString();
        }

        return value.toString();
    }
}

