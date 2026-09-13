package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// what a channel takes, and the refusal of anything else
//
// a channel that takes whatever arrives leaves the handler as the only thing between a client and
// the game. the declaration is mandatory, so the wrong shape never reaches a handler
//
// declared as text on the channel, because a list of kinds is not one of the property types §9.1
// allows and a channel's schema has to replicate like everything else about it: a client validates
// before it sends, so a place gets its error at the call rather than a message that vanishes
//
// checked on both sides on purpose. the send side check is for the place -- the error names the
// argument and the line. the receive side check on the server is the one that matters, because a
// client that has been tampered with does not run the first one
public final class Schema {

    // a kind, spelled the way a place would say it
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
                // one number type, because lua has one
                case NUMBER -> value instanceof Double || value instanceof Integer;
                case STRING -> value instanceof String;
                case VEC3 -> value instanceof Vec3;
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

    // one declared argument: its kind, and whether it may be left out
    public record Takes(Kind kind, boolean optional) {}

    // parsed once per spelling rather than per delivery, because the spelling is a property and a
    // property does not change twenty times a second
    private static final Map<String, List<Takes>> PARSED = new ConcurrentHashMap<>();

    private Schema() {}

    // "string, number" is two required. a trailing ? is one that may be left out, and once one may
    // be, everything after it may be too -- a hole in the middle is a typo, not a signature
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
                    throw new IllegalArgumentException("\"" + word + "\" is not a kind a channel can"
                            + " take. the kinds are bool, number, string, vec3, quat, cframe, color,"
                            + " udim2, instance, list and table, and a trailing ? means it may be left out");
                }
                if (seenOptional && !optional) {
                    throw new IllegalArgumentException("\"" + text + "\" has a required argument"
                            + " after one that may be left out, which no caller can satisfy");
                }
                seenOptional |= optional;
                takes.add(new Takes(kind, optional));
            }
            return List.copyOf(takes);
        });
    }

    // the arguments as a place wrote them or as they arrived: instances, not ids. so this runs before
    // the pack on the way out and after the unpack on the way in, and knows nothing about the wire
    public static void check(Remote remote, List<Object> args) {
        List<Takes> takes = parse(remote.accepts);
        if (args.size() > takes.size()) {
            throw new IllegalArgumentException(said(remote) + " takes " + shape(takes)
                    + " and was given " + args.size());
        }
        for (int n = 0; n < takes.size(); n++) {
            Takes want = takes.get(n);
            Object value = n < args.size() ? args.get(n) : null;
            if (value == null) {
                if (want.optional()) continue;
                throw new IllegalArgumentException(said(remote) + " wants "
                        + want.kind().name().toLowerCase() + " as argument " + (n + 1)
                        + " and was given nothing");
            }
            if (!want.kind().holds(value)) {
                throw new IllegalArgumentException(said(remote) + " wants "
                        + want.kind().name().toLowerCase() + " as argument " + (n + 1)
                        + " and was given " + value.getClass().getSimpleName().toLowerCase());
            }
        }
    }

    private static String said(Remote remote) {
        return remote.name();
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
