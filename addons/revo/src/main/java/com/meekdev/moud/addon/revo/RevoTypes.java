package com.meekdev.moud.addon.revo;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.script.host.Api;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class RevoTypes {

    private static final String HEADER = Resources.text("header.rv");
    private static final Map<String, String> TYPES = Resources.table("types.properties");

    private RevoTypes() {}

    static String declare(Api api, ClassRegistry classes) {
        StringBuilder out = new StringBuilder(32768);
        out.append(HEADER);
        for (ClassDef<?> def : classes.all()) {
            List<String> fields = new ArrayList<>(List.of("name: string", "className: string"));
            for (ClassDef<?> at = def; at != null; at = at.parent()) {
                for (PropertyDef property : at.properties()) {
                    if (property.index() < (at.parent() == null ? 0 : at.parent().properties().length)) continue;
                    fields.add(property.name() + ": any");
                }
                Api.Decl methods = api.decl(at.name());
                if (methods != null) collect(methods, fields);
            }
            Api.Decl shared = api.decl("Instance");
            if (shared != null) collect(shared, fields);
            type(out, def.name(), fields);
        }
        for (Api.Decl decl : api.classes()) {
            if (classes.find(decl.name()) != null || decl.name().equals("Instance")) continue;
            List<String> fields = new ArrayList<>();
            collect(decl, fields);
            type(out, decl.name(), fields);
        }
        for (Map.Entry<String, String> global : api.globals().entrySet()) {
            String type = global.getValue();
            out.append("pub declare ").append(global.getKey()).append(" = ")
                    .append(type.startsWith("(") ? function(type) : simple(type)).append('\n');
        }
        return out.toString();
    }

    private static void collect(Api.Decl decl, List<String> fields) {
        for (Api.Member member : decl.members()) {
            String field = member.name() + ": " + (member.kind() == Api.Kind.FIELD ? simple(member.type()) : "function");
            if (!fields.contains(field)) fields.add(field);
        }
    }

    private static void type(StringBuilder out, String name, List<String> fields) {
        out.append("pub type ").append(name).append(" = {\n");
        for (String field : fields) out.append("  ").append(field).append(",\n");
        out.append("}\n\n");
    }

    private static String simple(String type) {
        String bare = type.endsWith("?") ? type.substring(0, type.length() - 1) : type;
        String mapped = TYPES.get(bare);
        if (mapped != null) return mapped;
        if (bare.startsWith("{")) return "table";
        if (bare.startsWith("(")) return "function";
        return "any";
    }

    private static String function(String signature) {
        int close = signature.indexOf(')');
        String params = signature.substring(1, Math.max(1, close)).trim();
        List<String> typed = new ArrayList<>();
        if (!params.isEmpty() && !params.startsWith("...")) {
            int depth = 0;
            int start = 0;
            for (int i = 0; i <= params.length(); i++) {
                char c = i < params.length() ? params.charAt(i) : ',';
                if (c == '(' || c == '{') depth++;
                if (c == ')' || c == '}') depth--;
                if (c == ',' && depth == 0) {
                    String param = params.substring(start, i).trim();
                    int colon = param.indexOf(':');
                    if (colon > 0) {
                        String type = param.substring(colon + 1).trim();
                        String name = param.substring(0, colon).trim();
                        typed.add((type.endsWith("?") ? "?" : "") + name + ": " + simple(type));
                    }
                    start = i + 1;
                }
            }
        }
        return "fn(" + String.join(", ", typed) + ") -> any";
    }
}
