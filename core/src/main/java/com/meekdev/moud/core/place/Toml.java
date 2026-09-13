package com.meekdev.moud.core.place;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// the part of toml a place file uses: tables, dotted keys, strings, numbers, booleans and arrays.
// values come back as String, Long, Double, Boolean, List and Map
public final class Toml {

    private final String text;
    private int at;
    private int line = 1;

    private Toml(String text) {
        this.text = text;
    }

    public static Map<String, Object> parse(String text) {
        return new Toml(text).document();
    }

    private Map<String, Object> document() {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> table = root;
        while (true) {
            skipBlank();
            if (at >= text.length()) return root;
            char c = text.charAt(at);
            if (c == '[') {
                at++;
                if (peek() == '[') throw error("arrays of tables are not supported");
                List<String> path = keyPath(']');
                expect(']');
                table = descend(root, path);
                endLine();
                continue;
            }
            List<String> path = keyPath('=');
            expect('=');
            skipSpaces();
            Object value = value();
            Map<String, Object> into = descend(table, path.subList(0, path.size() - 1));
            String key = path.getLast();
            if (into.containsKey(key)) throw error("'" + key + "' is set twice");
            into.put(key, value);
            endLine();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> descend(Map<String, Object> from, List<String> path) {
        Map<String, Object> table = from;
        for (String part : path) {
            Object next = table.computeIfAbsent(part, k -> new LinkedHashMap<String, Object>());
            if (!(next instanceof Map)) throw error("'" + part + "' is a value, not a table");
            table = (Map<String, Object>) next;
        }
        return table;
    }

    private List<String> keyPath(char end) {
        List<String> path = new ArrayList<>();
        while (true) {
            skipSpaces();
            char c = peek();
            String part;
            if (c == '"') {
                part = basicString();
            } else {
                int start = at;
                while (at < text.length() && isBare(text.charAt(at))) at++;
                if (start == at) throw error("expected a key");
                part = text.substring(start, at);
            }
            path.add(part);
            skipSpaces();
            if (peek() == '.') {
                at++;
                continue;
            }
            if (peek() != end) throw error("expected '" + end + "' after a key");
            return path;
        }
    }

    private Object value() {
        char c = peek();
        if (c == '"') return basicString();
        if (c == '\'') return literalString();
        if (c == '[') return array();
        if (c == '{') return inlineTable();
        if (text.startsWith("true", at)) {
            at += 4;
            return Boolean.TRUE;
        }
        if (text.startsWith("false", at)) {
            at += 5;
            return Boolean.FALSE;
        }
        return number();
    }

    private List<Object> array() {
        expect('[');
        List<Object> values = new ArrayList<>();
        while (true) {
            skipBlank();
            if (peek() == ']') {
                at++;
                return values;
            }
            values.add(value());
            skipBlank();
            if (peek() == ',') {
                at++;
                continue;
            }
            skipBlank();
            expect(']');
            return values;
        }
    }

    private Map<String, Object> inlineTable() {
        expect('{');
        Map<String, Object> table = new LinkedHashMap<>();
        skipSpaces();
        if (peek() == '}') {
            at++;
            return table;
        }
        while (true) {
            List<String> path = keyPath('=');
            expect('=');
            skipSpaces();
            descend(table, path.subList(0, path.size() - 1)).put(path.getLast(), value());
            skipSpaces();
            if (peek() == ',') {
                at++;
                continue;
            }
            expect('}');
            return table;
        }
    }

    private Object number() {
        int start = at;
        while (at < text.length() && "+-0123456789._eE".indexOf(text.charAt(at)) >= 0) at++;
        String raw = text.substring(start, at).replace("_", "");
        if (raw.isEmpty()) throw error("expected a value");
        try {
            if (raw.contains(".") || raw.contains("e") || raw.contains("E")) return Double.parseDouble(raw);
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw error("'" + raw + "' is not a number");
        }
    }

    private String basicString() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            if (at >= text.length() || text.charAt(at) == '\n') throw error("a string is not closed");
            char c = text.charAt(at++);
            if (c == '"') return out.toString();
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char e = text.charAt(at++);
            switch (e) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case 'u' -> {
                    out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    at += 4;
                }
                default -> throw error("\\" + e + " is not an escape");
            }
        }
    }

    private String literalString() {
        expect('\'');
        int end = text.indexOf('\'', at);
        if (end < 0 || text.substring(at, end).contains("\n")) throw error("a string is not closed");
        String value = text.substring(at, end);
        at = end + 1;
        return value;
    }

    private void endLine() {
        skipSpaces();
        if (at < text.length() && text.charAt(at) == '#') {
            while (at < text.length() && text.charAt(at) != '\n') at++;
        }
        if (at < text.length() && text.charAt(at) == '\r') at++;
        if (at < text.length() && text.charAt(at) != '\n') throw error("unexpected text after a value");
    }

    private void skipSpaces() {
        while (at < text.length() && (text.charAt(at) == ' ' || text.charAt(at) == '\t')) at++;
    }

    private void skipBlank() {
        while (at < text.length()) {
            char c = text.charAt(at);
            if (c == '#') {
                while (at < text.length() && text.charAt(at) != '\n') at++;
            } else if (c == '\n') {
                line++;
                at++;
            } else if (Character.isWhitespace(c)) {
                at++;
            } else {
                return;
            }
        }
    }

    private char peek() {
        return at < text.length() ? text.charAt(at) : '\0';
    }

    private void expect(char c) {
        if (peek() != c) throw error("expected '" + c + "'");
        at++;
    }

    private static boolean isBare(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
    }

    private IllegalArgumentException error(String what) {
        return new IllegalArgumentException("place.toml line " + line + ": " + what);
    }
}
