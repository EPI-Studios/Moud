package com.meekdev.moud.addon.revo;

import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.script.host.Api;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RevoTypes {

    private static final String HEADER = Resources.text("header.rv");
    private static final Map<String, String> TYPES = Resources.table("types.properties");
    private static final Set<String> BUILTIN = Resources.words("builtins.txt");

    private RevoTypes() {}

    static String prelude(Api api, ClassRegistry classes) {
        StringBuilder out = new StringBuilder();
        for (String line : declare(api, classes).split("\n")) {
            String text = line.strip();
            if (text.isEmpty() || text.startsWith("#")) continue;
            if (text.startsWith("pub declare ")) {
                String rest = text.substring("pub declare ".length());
                int equals = rest.indexOf(" = ");
                String name = rest.substring(0, equals);
                out.append("const ").append(name).append(": ").append(rest.substring(equals + 3)).append(" = ").append(name).append(' ');
            } else {
                out.append(text.startsWith("pub ") ? text.substring(4) : text).append(' ');
            }
        }
        return out.toString();
    }

    static String declare(Api api, ClassRegistry classes) {
        Set<String> records = new HashSet<>();
        for (Api.Decl decl : api.classes()) {
            if (classes.find(decl.name()) == null && !decl.name().equals("Instance")) records.add(decl.name());
        }
        StringBuilder out = new StringBuilder(32768);
        out.append(HEADER);
        for (Api.Decl decl : api.classes()) {
            if (!records.contains(decl.name())) continue;
            out.append("pub type ").append(decl.name()).append(" = {\n");
            for (Api.Member member : decl.members()) {
                String type = switch (member.kind()) {
                    case FIELD -> type(member.type(), records);
                    case METHOD -> function(member.type(), records, true);
                    case FUNCTION -> member.type().startsWith("(") ? function(member.type(), records, false) : type(member.type(), records);
                };
                out.append("  ").append(member.name()).append(": ").append(type).append(",\n");
            }
            out.append("}\n\n");
        }
        for (Map.Entry<String, String> global : api.globals().entrySet()) {
            if (BUILTIN.contains(global.getKey())) continue;
            String type = global.getValue();
            out.append("pub declare ").append(global.getKey()).append(" = ")
                    .append(type.startsWith("(") ? function(type, records, false) : type(type, records)).append('\n');
        }
        return out.toString();
    }

    static String type(String luau, Set<String> records) {
        String type = luau.trim();
        if (type.isEmpty() || type.equals("()")) return "any";
        List<String> union = split(type, '|');
        if (union.size() > 1) {
            List<String> parts = new ArrayList<>();
            for (String part : union) {
                String mapped = type(part, records);
                if (mapped.equals("any")) return "any";
                if (!parts.contains(mapped)) parts.add(mapped);
            }
            return String.join(" | ", parts);
        }
        if (type.endsWith("?")) {
            String inner = type(type.substring(0, type.length() - 1), records);
            return inner.equals("any") ? "any" : inner + "?";
        }
        if (type.startsWith("\"")) return "string";
        if (type.startsWith("{")) return "table";
        if (type.startsWith("(")) return isFunctionType(type) ? "function" : "table";
        String mapped = TYPES.get(type);
        if (mapped != null) return mapped;
        return records.contains(type) ? type : "any";
    }

    private static String function(String signature, Set<String> records, boolean self) {
        int close = matching(signature, 0);
        String params = signature.substring(1, close).trim();
        String rest = signature.substring(close + 1).trim();
        String returns = rest.startsWith("->") ? rest.substring(2).trim() : "()";
        List<String> typed = new ArrayList<>();
        if (self) typed.add("self: any");
        int index = 0;
        for (String param : params.isEmpty() ? List.<String>of() : split(params, ',')) {
            String text = param.trim();
            index++;
            if (text.startsWith("...")) {
                typed.add("?rest: any...");
                break;
            }
            int colon = topLevelColon(text);
            String name = colon < 0 ? "arg" + index : text.substring(0, colon).trim();
            String type = colon < 0 ? text : text.substring(colon + 1).trim();
            boolean optional = type.endsWith("?") && split(type, '|').size() == 1;
            String mapped = type(optional ? type.substring(0, type.length() - 1) : type, records);
            typed.add((optional ? "?" : "") + name + ": " + mapped);
        }
        String result = returns.startsWith("...") ? "any" : type(returns, records);
        return "fn(" + String.join(", ", typed) + ") -> " + result;
    }

    private static boolean isFunctionType(String type) {
        int close = matching(type, 0);
        return type.substring(close + 1).trim().startsWith("->");
    }

    private static int matching(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(' || c == '{' || c == '[') depth++;
            if (c == ')' || c == '}' || c == ']') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return text.length() - 1;
    }

    private static int topLevelColon(String text) {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(' || c == '{' || c == '[') depth++;
            if (c == ')' || c == '}' || c == ']') depth--;
            if (c == ':' && depth == 0) return i;
        }
        return -1;
    }

    private static List<String> split(String text, char separator) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(' || c == '{' || c == '[') depth++;
            if (c == ')' || c == '}' || c == ']') depth--;
            if (c == '-' && i + 1 < text.length() && text.charAt(i + 1) == '>') {
                if (depth == 0 && separator == '|') return List.of(text);
            }
            if (c == separator && depth == 0) {
                parts.add(text.substring(start, i).trim());
                start = i + 1;
            }
        }
        parts.add(text.substring(start).trim());
        return parts;
    }
}
