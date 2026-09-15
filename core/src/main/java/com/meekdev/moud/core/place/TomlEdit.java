package com.meekdev.moud.core.place;

import java.util.ArrayList;
import java.util.List;

public final class TomlEdit {

    private TomlEdit() {}

    public static String set(String text, String table, String key, Object value) {
        return change(text, table, key, key + " = " + literal(value));
    }

    public static String remove(String text, String table, String key) {
        return change(text, table, key, null);
    }

    private static String change(String text, String table, String key, String replacement) {
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        int start = 0;
        int end = lines.size();
        if (!table.isEmpty()) {
            int header = -1;
            for (int n = 0; n < lines.size(); n++) {
                if (isHeader(lines.get(n), table)) {
                    header = n;
                    break;
                }
            }
            if (header < 0) {
                if (replacement == null) return text;
                while (!lines.isEmpty() && lines.getLast().isBlank()) lines.removeLast();
                if (!lines.isEmpty()) lines.add("");
                lines.add("[" + table + "]");
                lines.add(replacement);
                lines.add("");
                return String.join("\n", lines);
            }
            start = header + 1;
        }
        for (int n = start; n < lines.size(); n++) {
            if (lines.get(n).strip().startsWith("[")) {
                end = n;
                break;
            }
        }
        for (int n = start; n < end; n++) {
            if (!keyOf(lines.get(n)).equals(key)) continue;
            if (replacement == null) lines.remove(n);
            else lines.set(n, replacement);
            return String.join("\n", lines);
        }
        if (replacement == null) return text;
        int at = end;
        while (at > start && lines.get(at - 1).isBlank()) at--;
        lines.add(at, replacement);
        return String.join("\n", lines);
    }

    private static boolean isHeader(String line, String table) {
        String stripped = line.strip();
        int comment = stripped.indexOf('#');
        if (comment >= 0) stripped = stripped.substring(0, comment).strip();
        return stripped.equals("[" + table + "]");
    }

    private static String keyOf(String line) {
        String stripped = line.strip();
        if (stripped.startsWith("#") || stripped.startsWith("[")) return "";
        int equals = stripped.indexOf('=');
        if (equals < 0) return "";
        String key = stripped.substring(0, equals).strip();
        if (key.length() >= 2 && key.startsWith("\"") && key.endsWith("\"")) key = key.substring(1, key.length() - 1);
        return key;
    }

    static String literal(Object value) {
        return switch (value) {
            case String text -> "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            case Boolean flag -> flag.toString();
            case Number number -> Long.toString(number.longValue());
            default -> throw new IllegalArgumentException("place.toml can not hold " + value);
        };
    }
}
