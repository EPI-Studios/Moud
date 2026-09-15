package com.meekdev.moud.core.remote;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Schema {

    public enum Kind {
        BOOL, NUMBER, STRING, VEC3, QUAT, CFRAME, COLOR, UDIM2, INSTANCE, LIST, TABLE;

        static Kind of(String word) {
            return switch (word) {
                case "bool", "boolean" -> BOOL;
                case "number", "num" -> NUMBER;
                case "string", "text" -> STRING;
                case "vec3", "vector3" -> VEC3;
                case "quat", "quaternion" -> QUAT;
                case "cframe" -> CFRAME;
                case "color", "colour" -> COLOR;
                case "udim2" -> UDIM2;
                case "instance" -> INSTANCE;
                case "list", "array" -> LIST;
                case "table", "map" -> TABLE;
                default -> null;
            };
        }

        boolean holds(Object value) {
            return switch (this) {
                case BOOL -> value instanceof Boolean;
                case NUMBER -> value instanceof Double || value instanceof Integer;
                case STRING -> value instanceof String;
                case VEC3 -> value instanceof Vector3;
                case QUAT -> value instanceof Quat;
                case CFRAME -> value instanceof CFrame;
                case COLOR -> value instanceof Color;
                case UDIM2 -> value instanceof UDim2;
                case INSTANCE -> value instanceof Instance;
                case LIST -> value instanceof List<?>;
                case TABLE -> value instanceof Map<?, ?>;
            };
        }
    }

    public record Takes(Kind kind, boolean optional) {}

    private static final Map<String, List<Takes>> PARSED = new ConcurrentHashMap<>();

    private Schema() {}

    public static List<Takes> parse(String accepts) {
        return PARSED.computeIfAbsent(accepts, text -> {
            List<Takes> takes = new ArrayList<>();
            boolean seenOptional = false;
            for (String part : text.split(",")) {
                String word = part.trim();
                if (word.isEmpty()) continue;
                boolean optional = word.endsWith("?");
                if (optional) word = word.substring(0, word.length() - 1).trim();
                Kind kind = Kind.of(word.toLowerCase());
                if (kind == null) {
                    throw new IllegalArgumentException("unknown argument kind \"" + word + "\", expected bool, number, string, vec3, quat, cframe, color, udim2, instance, list or table");
                }
                if (seenOptional && !optional) {
                    throw new IllegalArgumentException("\"" + text + "\": required argument after an optional one");
                }
                seenOptional |= optional;
                takes.add(new Takes(kind, optional));
            }
            return List.copyOf(takes);
        });
    }

    public static void check(Remote remote, List<Object> args) {
        List<Takes> takes = parse(remote.accepts);
        if (args.size() > takes.size()) {
            String hint = takes.isEmpty() ? ", list what it takes in its accepts, like \"string\" or \"string, number?\"" : "";
            throw new IllegalArgumentException(remote.name() + " expects (" + shape(takes) + "), got " + args.size() + " arguments" + hint);
        }
        for (int n = 0; n < takes.size(); n++) {
            Takes want = takes.get(n);
            Object value = n < args.size() ? args.get(n) : null;
            if (value == null) {
                if (want.optional()) continue;
                throw new IllegalArgumentException(remote.name() + ": argument " + (n + 1) + " must be "
                        + want.kind().name().toLowerCase() + ", got nil");
            }
            if (!want.kind().holds(value)) {
                throw new IllegalArgumentException(remote.name() + ": argument " + (n + 1) + " must be "
                        + want.kind().name().toLowerCase() + ", got " + value.getClass().getSimpleName().toLowerCase());
            }
        }
    }

    private static String shape(List<Takes> takes) {
        if (takes.isEmpty()) return "no arguments";
        StringBuilder out = new StringBuilder();
        for (int n = 0; n < takes.size(); n++) {
            if (n > 0) out.append(", ");
            out.append(takes.get(n).kind().name().toLowerCase());
            if (takes.get(n).optional()) out.append('?');
        }
        return out.toString();
    }
}
