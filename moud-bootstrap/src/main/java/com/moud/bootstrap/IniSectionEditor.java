package com.moud.bootstrap;

import java.util.ArrayList;
import java.util.List;

final class IniSectionEditor {

    private final ArrayList<String> lines;

    IniSectionEditor(List<String> sourceLines) {
        this.lines = new ArrayList<>(sourceLines == null ? List.of() : sourceLines);
    }

    String value(String section, String key) {
        Range range = findSection(section);
        if (range == null) {
            return "";
        }
        for (int i = range.start(); i < range.end(); i++) {
            ParsedKeyValue parsed = parseKeyValue(lines.get(i));
            if (parsed != null && parsed.key().equals(key)) {
                return parsed.value();
            }
        }
        return "";
    }

    boolean upsert(String section, String key, String value) {
        Range range = findSection(section);
        if (range == null) {
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
                lines.add("");
            }
            lines.add("[" + section + "]");
            lines.add(key + "=" + value);
            return true;
        }

        for (int i = range.start(); i < range.end(); i++) {
            ParsedKeyValue parsed = parseKeyValue(lines.get(i));
            if (parsed != null && parsed.key().equals(key)) {
                String next = key + "=" + value;
                if (next.equals(lines.get(i))) {
                    return false;
                }
                lines.set(i, next);
                return true;
            }
        }

        lines.add(range.end(), key + "=" + value);
        return true;
    }

    List<String> lines() {
        return List.copyOf(lines);
    }

    private Range findSection(String section) {
        String header = "[" + section + "]";
        for (int i = 0; i < lines.size(); i++) {
            if (header.equals(lines.get(i).trim())) {
                int end = lines.size();
                for (int j = i + 1; j < lines.size(); j++) {
                    String trimmed = lines.get(j).trim();
                    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                        end = j;
                        break;
                    }
                }
                return new Range(i + 1, end);
            }
        }
        return null;
    }

    private static ParsedKeyValue parseKeyValue(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";") || trimmed.startsWith("[")) {
            return null;
        }
        int idx = line.indexOf('=');
        if (idx < 0) {
            return null;
        }
        String key = line.substring(0, idx).trim();
        if (key.isEmpty()) {
            return null;
        }
        String value = line.substring(idx + 1).trim();
        return new ParsedKeyValue(key, value);
    }

    private record Range(int start, int end) {
    }

    private record ParsedKeyValue(String key, String value) {
    }
}
