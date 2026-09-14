package com.meekdev.moud.addon.java;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.script.host.Api;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class JavaTypes {

    static final String CLASS = "moud.Api";

    private static final Template API = Template.load("Api.java.template");
    private static final Template RECORD = Template.load("record.template");
    private static final Template METHODS = Template.load("methods.template");
    private static final Template METHOD = Template.load("method.template");
    private static final Map<String, String> TYPES = Template.table("types.properties");
    private static final Map<String, String> CONVERTERS = Template.table("converters.properties");
    private static final Set<String> RESERVED = Template.words("reserved.txt");
    private static final String FUNCTION = "Function<Object[], Object>";

    private JavaTypes() {}

    static String source(Api api, ClassRegistry classes) {
        Set<String> records = new LinkedHashSet<>();
        for (Api.Decl decl : api.classes()) {
            String name = decl.name();
            if (classes.find(name) == null && !name.equals("Instance") && !TYPES.containsKey(name)) records.add(name);
        }
        return API.fill("globals", globals(api, records), "records", records(api, records), "methods", methods(api, classes, records));
    }

    private static String globals(Api api, Set<String> records) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> global : api.globals().entrySet()) {
            String key = global.getKey();
            String type = global.getValue();
            if (key.startsWith("__")) continue;
            String quoted = "\"" + key + "\"";
            if (type.startsWith("(")) {
                out.append(Template.indent(method("static ", javaName(key), type, records, "callGlobal", List.of(quoted), false), 4));
            } else {
                out.append(Template.indent(getter("static ", javaName(key), mapped(type, records), "bridge.global(" + quoted + ")", records), 4));
            }
        }
        return out.toString();
    }

    private static String records(Api api, Set<String> records) {
        StringBuilder out = new StringBuilder();
        for (Api.Decl decl : api.classes()) {
            if (!records.contains(decl.name())) continue;
            out.append(Template.indent(RECORD.fill("name", decl.name(), "members", members(decl, records)), 4));
        }
        return out.toString();
    }

    private static String methods(Api api, ClassRegistry classes, Set<String> records) {
        List<Api.Decl> decls = new ArrayList<>();
        Api.Decl shared = api.decl("Instance");
        if (shared != null) decls.add(shared);
        Set<String> seen = new HashSet<>();
        for (ClassDef<?> def : classes.all()) {
            Api.Decl decl = api.decl(def.name());
            if (decl != null && seen.add(def.name())) decls.add(decl);
        }
        StringBuilder out = new StringBuilder();
        for (Api.Decl decl : decls) {
            StringBuilder body = new StringBuilder();
            for (Api.Member member : decl.members()) {
                if (member.kind() != Api.Kind.METHOD) continue;
                String params = member.type().substring(1);
                String signature = "(self: Instance" + (params.startsWith(")") ? "" : ", ") + params;
                String target = "self, \"" + member.name() + "\"";
                body.append(Template.indent(method("static ", javaName(member.name()), signature, records, "call", List.of(target), true), 4));
            }
            if (body.isEmpty()) continue;
            out.append(Template.indent(METHODS.fill("name", decl.name(), "methods", body.toString()), 8));
        }
        return out.toString();
    }

    private static String members(Api.Decl decl, Set<String> records) {
        StringBuilder out = new StringBuilder();
        Set<String> names = new HashSet<>();
        for (Api.Member member : decl.members()) {
            String name = javaName(member.name());
            if (!names.add(name)) continue;
            String target = "self, \"" + member.name() + "\"";
            String type = member.type();
            switch (member.kind()) {
                case FIELD -> out.append(Template.indent(getter("", name, mapped(type, records), "bridge.get(" + target + ")", records), 4));
                case METHOD -> out.append(Template.indent(method("", name, type, records, "call", List.of(target), false), 4));
                case FUNCTION -> {
                    if (type.startsWith("(")) out.append(Template.indent(method("", name, type, records, "callField", List.of(target), false), 4));
                }
            }
        }
        return out.toString();
    }

    private static String getter(String modifiers, String name, String type, String read, Set<String> records) {
        return METHOD.fill("modifiers", modifiers, "returns", type, "name", name, "params", "", "body", "return " + convert(type, read, records));
    }

    private static String method(String modifiers, String name, String signature, Set<String> records, String call,
                                 List<String> target, boolean firstIsSelf) {
        int close = matching(signature);
        String params = signature.substring(1, close).trim();
        String rest = signature.substring(close + 1).trim();
        String returns = rest.startsWith("->") ? rest.substring(2).trim() : "()";
        List<String> declared = new ArrayList<>();
        List<String> passed = new ArrayList<>(target);
        boolean varargs = false;
        int index = 0;
        for (String param : params.isEmpty() ? List.<String>of() : split(params, ',')) {
            index++;
            if (param.startsWith("...")) {
                declared.add("Object... rest");
                passed.add("rest");
                varargs = true;
                break;
            }
            int colon = colon(param);
            String pname = colon < 0 ? "arg" + index : javaName(param.substring(0, colon).trim());
            String ptype = colon < 0 ? param : param.substring(colon + 1).trim();
            declared.add(parameter(ptype, records) + " " + pname);
            if (firstIsSelf && index == 1) continue;
            passed.add(records.contains(bare(ptype)) ? "(" + pname + " == null ? null : " + pname + ".self)" : pname);
        }
        String invocation = "bridge." + call + (varargs ? "Rest" : "") + "(" + String.join(", ", passed) + ")";
        boolean tuple = returns.startsWith("...") || returns.startsWith("(") && !isFunction(returns);
        String result = tuple ? "Object[]" : mapped(returns, records);
        String body;
        if (result.equals("()")) {
            body = invocation;
        } else {
            body = "return " + convert(result, tuple ? invocation : "bridge.first(" + invocation + ")", records);
        }
        return METHOD.fill("modifiers", modifiers, "returns", result.equals("()") ? "void" : result, "name", name,
                "params", String.join(", ", declared), "body", body);
    }

    private static String mapped(String luau, Set<String> records) {
        String type = luau.trim();
        if (type.isEmpty() || type.equals("()")) return "()";
        if (type.endsWith("?")) {
            String boxed = TYPES.get(type);
            return boxed != null ? boxed : mapped(type.substring(0, type.length() - 1), records);
        }
        if (split(type, '|').size() > 1) return "Object";
        if (type.startsWith("\"")) return "String";
        if (type.startsWith("{ [string]")) return "Map<String, Object>";
        if (type.startsWith("{")) return "Object";
        if (type.startsWith("(")) return isFunction(type) ? "Object" : "Object[]";
        if (TYPES.containsKey(type)) return TYPES.get(type);
        if (records.contains(type)) return type;
        return type.equals("Instance") || !Character.isUpperCase(type.charAt(0)) ? "Object" : "Instance";
    }

    private static String parameter(String luau, Set<String> records) {
        if (isFunction(luau)) return FUNCTION;
        String mapped = mapped(luau, records);
        return mapped.equals("()") ? "Object" : mapped;
    }

    private static String convert(String type, String value, Set<String> records) {
        String converter = CONVERTERS.get(records.contains(type) ? "record" : type);
        if (converter == null) converter = CONVERTERS.get("cast");
        return converter.replace("{{value}}", value).replace("{{type}}", type);
    }

    private static String bare(String type) {
        String trimmed = type.trim();
        return trimmed.endsWith("?") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static String javaName(String name) {
        String clean = name.replaceAll("[^A-Za-z0-9_]", "_");
        return RESERVED.contains(clean) ? clean + "_" : clean;
    }

    private static boolean isFunction(String type) {
        String trimmed = type.trim();
        if (!trimmed.startsWith("(")) return false;
        return trimmed.substring(matching(trimmed) + 1).trim().startsWith("->");
    }

    private static int matching(String text) {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(' || c == '{' || c == '[') depth++;
            if (c == ')' || c == '}' || c == ']') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return text.length() - 1;
    }

    private static int colon(String text) {
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
            boolean arrow = c == '-' && i + 1 < text.length() && text.charAt(i + 1) == '>';
            if (separator == '|' && arrow && depth == 0) return List.of(text);
            if (c == separator && depth == 0) {
                parts.add(text.substring(start, i).trim());
                start = i + 1;
            }
        }
        parts.add(text.substring(start).trim());
        return parts;
    }
}
