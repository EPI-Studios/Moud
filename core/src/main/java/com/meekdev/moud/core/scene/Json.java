package com.meekdev.moud.core.scene;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// json for scene files: maps keep their order, numbers read back as Double, and writing indents so a
// scene diffs line by line
public final class Json {

    private final String text;
    private int at;

    private Json(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        Json json = new Json(text);
        json.skip();
        Object value = json.value();
        json.skip();
        if (json.at != text.length()) throw json.error("unexpected text after the end");
        return value;
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value, 0);
        return out.append('\n').toString();
    }

    private static void write(StringBuilder out, Object value, int depth) {
        switch (value) {
            case null -> out.append("null");
            case Boolean b -> out.append(b);
            case Number n -> number(out, n.doubleValue());
            case String s -> string(out, s);
            case List<?> list -> {
                // a short list of numbers stays on one line, which is what a vector looks like
                if (list.stream().allMatch(v -> v instanceof Number) && list.size() <= 4) {
                    out.append('[');
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) out.append(", ");
                        number(out, ((Number) list.get(i)).doubleValue());
                    }
                    out.append(']');
                    return;
                }
                if (list.isEmpty()) {
                    out.append("[]");
                    return;
                }
                out.append("[\n");
                for (int i = 0; i < list.size(); i++) {
                    indent(out, depth + 1);
                    write(out, list.get(i), depth + 1);
                    out.append(i + 1 < list.size() ? ",\n" : "\n");
                }
                indent(out, depth);
                out.append(']');
            }
            case Map<?, ?> map -> {
                if (map.isEmpty()) {
                    out.append("{}");
                    return;
                }
                out.append("{\n");
                int i = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    indent(out, depth + 1);
                    string(out, String.valueOf(entry.getKey()));
                    out.append(": ");
                    write(out, entry.getValue(), depth + 1);
                    out.append(++i < map.size() ? ",\n" : "\n");
                }
                indent(out, depth);
                out.append('}');
            }
            default -> throw new IllegalArgumentException(value.getClass().getSimpleName() + " is not json");
        }
    }

    private static void number(StringBuilder out, double n) {
        if (n == Math.rint(n) && Math.abs(n) < 1e15) out.append((long) n);
        else out.append(n);
    }

    private static void string(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                case '\r' -> out.append("\\r");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }

    private static void indent(StringBuilder out, int depth) {
        out.append("  ".repeat(depth));
    }

    private Object value() {
        char c = peek();
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        at++;
        Map<String, Object> map = new LinkedHashMap<>();
        skip();
        if (peek() == '}') {
            at++;
            return map;
        }
        while (true) {
            skip();
            if (peek() != '"') throw error("expected a key in quotes");
            String key = string();
            skip();
            expect(':');
            skip();
            map.put(key, value());
            skip();
            if (peek() == ',') {
                at++;
                continue;
            }
            expect('}');
            return map;
        }
    }

    private List<Object> array() {
        at++;
        List<Object> list = new ArrayList<>();
        skip();
        if (peek() == ']') {
            at++;
            return list;
        }
        while (true) {
            skip();
            list.add(value());
            skip();
            if (peek() == ',') {
                at++;
                continue;
            }
            expect(']');
            return list;
        }
    }

    private String string() {
        at++;
        StringBuilder out = new StringBuilder();
        while (true) {
            if (at >= text.length()) throw error("a string is not closed");
            char c = text.charAt(at++);
            if (c == '"') return out.toString();
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char e = text.charAt(at++);
            switch (e) {
                case '"', '\\', '/' -> out.append(e);
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    at += 4;
                }
                default -> throw error("\\" + e + " is not an escape");
            }
        }
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, at)) throw error("expected a value");
        at += word.length();
        return value;
    }

    private Double number() {
        int start = at;
        while (at < text.length() && "+-0123456789.eE".indexOf(text.charAt(at)) >= 0) at++;
        if (start == at) throw error("expected a value");
        try {
            return Double.parseDouble(text.substring(start, at));
        } catch (NumberFormatException e) {
            throw error("'" + text.substring(start, at) + "' is not a number");
        }
    }

    private void skip() {
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++;
    }

    private char peek() {
        return at < text.length() ? text.charAt(at) : '\0';
    }

    private void expect(char c) {
        if (peek() != c) throw error("expected '" + c + "'");
        at++;
    }

    private IllegalArgumentException error(String what) {
        int line = 1;
        for (int i = 0; i < Math.min(at, text.length()); i++) if (text.charAt(i) == '\n') line++;
        return new IllegalArgumentException("line " + line + ": " + what);
    }
}
