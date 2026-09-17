package com.meekdev.moud.script.luau;

import com.meekdev.moud.core.clazz.CallbackDef;
import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.Enums;
import com.meekdev.moud.core.clazz.EventDef;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.clazz.PropertyType;
import com.meekdev.moud.script.host.Api;
import com.meekdev.moud.script.host.Literals;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class LuauTypes {

    private static final String INDENT = "    ";
    private static final String ROOT = "Instance";
    private static final String CALLBACK = "((...any) -> ...any)?";
    private static final String DEFAULT_SIGNAL = "InstanceSignal";
    private static final String ANY_SIGNAL = "AnySignal";
    private static final String PLACED = LuauResource.text("types/placed.d.luau");
    private static final Properties GUI_SIGNALS = LuauResource.properties("types/gui-signals.properties");
    private static final Properties CLASS_SIGNALS = LuauResource.properties("types/class-signals.properties");
    private static final Properties REMOTE_SIGNALS = LuauResource.properties("types/remote-signals.properties");

    private LuauTypes() {}

    public static String declare(Api api, ClassRegistry classes) {
        StringBuilder out = new StringBuilder(65536);
        Set<String> instanceClasses = new HashSet<>();
        for (ClassDef<?> def : classes.all()) instanceClasses.add(def.name());

        for (Map.Entry<String, String> alias : api.aliases().entrySet()) {
            line(out, "type ", alias.getKey(), " = ", alias.getValue());
            out.append('\n');
        }
        for (Api.Decl decl : api.classes()) {
            if (instanceClasses.contains(decl.name()) || decl.name().equals(ROOT)) continue;
            line(out, "declare extern type ", decl.name(), " with");
            out.append(builtInMembers(decl.name()));
            members(out, decl);
            end(out);
        }

        line(out, "declare extern type ", ROOT, " with");
        out.append(builtInMembers(ROOT));
        Api.Decl shared = api.decl(ROOT);
        if (shared != null) members(out, shared);
        end(out);

        for (ClassDef<?> def : classes.all()) classDeclaration(out, api, def);

        for (Map.Entry<String, String> extension : api.extensions().entrySet()) {
            Api.Decl decl = api.decl(extension.getValue());
            if (decl == null) continue;
            line(out, "declare ", extension.getKey(), ": typeof(", extension.getKey(), ") & {");
            for (Api.Member member : decl.members()) {
                line(out, INDENT, member.name(), ": ", member.type(), ",");
            }
            line(out, "}");
        }

        line(out, "declare script: Instance");
        for (Map.Entry<String, String> global : api.globals().entrySet()) {
            if (global.getKey().equals("require") || global.getKey().equals("script")) continue;
            String type = global.getValue();
            if (type.startsWith("(")) {
                Signature signature = Signature.parse(type);
                line(out, "declare function ", global.getKey(), "(", signature.params(false), "): ", signature.returns());
            } else {
                line(out, "declare ", global.getKey(), ": ", type);
            }
        }
        return out.toString();
    }

    private static void members(StringBuilder out, Api.Decl decl) {
        for (Api.Member member : decl.members()) {
            switch (member.kind()) {
                case FIELD -> line(out, INDENT, member.name(), ": ", member.type());
                case METHOD -> {
                    Signature signature = Signature.parse(member.type());
                    line(out, INDENT, "function ", member.name(), "(", signature.params(true), "): ", signature.returns());
                }
                case FUNCTION -> line(out, INDENT, member.name(), ": ", member.type());
            }
        }
    }

    private static void classDeclaration(StringBuilder out, Api api, ClassDef<?> def) {
        String parent = def.parent() == null ? ROOT : def.parent().name();
        line(out, "declare extern type ", def.name(), " extends ", parent, " with");
        PropertyDef frame = def.property("cframe");
        if (frame != null && frame.index() >= inherited(def)) out.append(PLACED);
        for (CallbackDef callback : def.callbacks()) {
            if (def.parent() != null && def.parent().callback(callback.name()) != null) continue;
            line(out, INDENT, callback.name(), ": ", CALLBACK);
        }
        for (EventDef event : def.events()) {
            line(out, INDENT, event.name(), ": ", signal(def, event));
        }
        PropertyDef[] properties = def.properties();
        for (int i = inherited(def); i < properties.length; i++) {
            line(out, INDENT, properties[i].name(), ": ", luau(properties[i]));
        }
        Api.Decl methods = api.decl(def.name());
        if (methods != null) members(out, methods);
        end(out);
    }

    private static String signal(ClassDef<?> def, EventDef event) {
        String gui = GUI_SIGNALS.getProperty(event.name());
        if (gui != null && def.isA(Classes.GUI_OBJECT)) return gui;
        String shape = CLASS_SIGNALS.getProperty(def.name());
        if (shape != null) return shape;
        if (remote(def)) return REMOTE_SIGNALS.getProperty(event.name(), ANY_SIGNAL);
        return payload(event);
    }

    private static String builtInMembers(String type) {
        return LuauResource.textOrEmpty("types/members/" + type + ".d.luau");
    }

    private static void line(StringBuilder out, String... parts) {
        for (String part : parts) out.append(part);
        out.append('\n');
    }

    private static void end(StringBuilder out) {
        out.append("end\n\n");
    }

    private static boolean remote(ClassDef<?> def) {
        for (ClassDef<?> at = def; at != null; at = at.parent()) {
            if (at == Classes.REMOTE) return true;
        }
        return false;
    }

    private static int inherited(ClassDef<?> def) {
        return def.parent() == null ? 0 : def.parent().properties().length;
    }

    private static String luau(PropertyDef property) {
        if (property.type() != PropertyType.ENUM) {
            return switch (property.type()) {
                case BOOL -> "boolean";
                case INT, NUM -> "number";
                case STRING, ASSET, ENUM -> "string";
                case VEC3 -> "Vector3";
                case QUAT -> "Quat";
                case CFRAME -> "CFrame";
                case COLOR -> "Color";
                case UDIM2 -> "UDim2";
                case REF -> "Instance?";
            };
        }
        return Literals.union(Enums.names(property.defaultValue().getClass()));
    }

    record Signature(String rawParams, String returns) {

        static Signature parse(String type) {
            int depth = 0;
            for (int i = 0; i < type.length(); i++) {
                char c = type.charAt(i);
                if (c == '(' || c == '{' || c == '[') depth++;
                if (c == ')' || c == '}' || c == ']') depth--;
                if (depth == 0 && c == ')') {
                    String params = type.substring(1, i).trim();
                    String rest = type.substring(i + 1).trim();
                    String returns = rest.startsWith("->") ? rest.substring(2).trim() : "()";
                    return new Signature(params, returns);
                }
            }
            return new Signature("...any", "...any");
        }

        String params(boolean self) {
            int last = 0;
            int depth = 0;
            for (int i = 0; i < rawParams.length(); i++) {
                char c = rawParams.charAt(i);
                if (c == '(' || c == '{' || c == '[') depth++;
                if (c == ')' || c == '}' || c == ']') depth--;
                if (depth == 0 && c == ',') last = i + 1;
            }
            String tail = rawParams.substring(last).trim();
            String params = rawParams;
            if (tail.startsWith("...") && !tail.startsWith("...:")) {
                params = rawParams.substring(0, last) + (last == 0 ? "" : " ") + "...: " + tail.substring(3).trim();
            }
            if (!self) return params;
            return params.isEmpty() ? "self" : "self, " + params;
        }
    }

    private static String payload(EventDef event) {
        if (event.field().getGenericType() instanceof ParameterizedType generic && generic.getActualTypeArguments().length == 1) {
            Type argument = generic.getActualTypeArguments()[0];
            if (argument == Double.class) return "NumberSignal";
            if (argument == Boolean.class) return "BoolSignal";
            if (argument == String.class) return "StringSignal";
        }
        return DEFAULT_SIGNAL;
    }
}
