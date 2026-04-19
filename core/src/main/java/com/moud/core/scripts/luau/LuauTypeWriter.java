package com.moud.core.scripts.luau;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

public final class LuauTypeWriter {

    private static final Set<Class<?>> NUMBERS = Set.of(
            byte.class, Byte.class, short.class, Short.class,
            int.class, Integer.class, long.class, Long.class,
            float.class, Float.class, double.class, Double.class
    );

    private LuauTypeWriter() {}

    public static String writeDeclarations(Collection<Class<?>> classes) {
        var sb = new StringBuilder();
        classes.stream()
                .sorted(Comparator.comparing(LuauTypeWriter::luauName))
                .forEach(c -> { appendClass(sb, c); sb.append('\n'); });
        return sb.toString();
    }

    public static String luauName(Class<?> cls) {
        var ann = cls.getAnnotation(LuauExport.class);
        return (ann != null && !ann.name().isBlank()) ? ann.name() : cls.getSimpleName();
    }

    private static void appendClass(StringBuilder sb, Class<?> cls) {
        var classAnn = cls.getAnnotation(LuauExport.class);
        if (classAnn == null) return;

        appendDocBlock(sb, classAnn.doc(), "");
        sb.append("declare class ").append(luauName(cls)).append('\n');

        Arrays.stream(cls.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()) && f.isAnnotationPresent(LuauExport.class))
                .sorted(Comparator.comparing(Field::getName))
                .forEach(f -> {
                    var fa = f.getAnnotation(LuauExport.class);
                    appendDocBlock(sb, fa.doc(), "    ");
                    String name = fa.name().isBlank() ? f.getName() : fa.name();
                    sb.append("    ").append(name).append(": ").append(luauType(f.getGenericType())).append('\n');
                });

        Arrays.stream(cls.getDeclaredMethods())
                .filter(m -> !Modifier.isStatic(m.getModifiers()) && m.isAnnotationPresent(LuauExport.class))
                .sorted(Comparator.comparing(Method::getName))
                .forEach(m -> appendMethod(sb, cls, m, m.getAnnotation(LuauExport.class)));

        sb.append("end\n");
    }

    private static void appendMethod(StringBuilder sb, Class<?> owner, Method m, LuauExport ma) {
        var doc = parseDoc(ma.doc());

        if (!doc.body.isEmpty()) appendDocBlock(sb, String.join("\n", doc.body), "    ");
        doc.params.forEach((k, v) -> sb.append("    --- @param ").append(k).append(" -- ").append(v).append('\n'));
        if (doc.returns != null) sb.append("    --- @return ").append(doc.returns).append('\n');

        String name = ma.name().isBlank() ? m.getName() : ma.name();
        sb.append("    function ").append(name).append("(self: ").append(luauName(owner));

        var types = m.getGenericParameterTypes();
        var params = m.getParameters();
        for (int i = 0; i < types.length; i++) {
            String pn = params[i].getName();
            pn = (pn == null || pn.isBlank() || pn.startsWith("arg")) ? "p" + i : pn;
            sb.append(", ").append(pn).append(": ").append(luauType(types[i]));
        }
        sb.append("): ").append(luauType(m.getGenericReturnType())).append('\n');
    }

    public static void appendDocBlock(StringBuilder sb, String doc, String indent) {
        if (doc != null && !doc.isBlank()) {
            doc.lines().forEach(line -> sb.append(indent).append("--- ").append(line).append('\n'));
        }
    }

    private static final class ParsedDoc {
        final List<String> body = new ArrayList<>();
        final Map<String, String> params = new LinkedHashMap<>();
        String returns;
    }

    private static ParsedDoc parseDoc(String raw) {
        var pd = new ParsedDoc();
        if (raw == null || raw.isBlank()) return pd;

        for (String line : raw.split("\n")) {
            String t = line.strip();
            if (t.startsWith("@param")) {
                String rest = t.substring(6).strip();
                int sp = rest.indexOf(' ');
                if (sp > 0) pd.params.put(rest.substring(0, sp), rest.substring(sp + 1).strip());
                else if (!rest.isEmpty()) pd.params.put(rest, "");
            } else if (t.startsWith("@return")) {
                pd.returns = t.substring(7).strip();
            } else {
                pd.body.add(line);
            }
        }

        while (!pd.body.isEmpty() && pd.body.getFirst().isBlank()) pd.body.removeFirst();
        while (!pd.body.isEmpty() && pd.body.getLast().isBlank()) pd.body.removeLast();

        return pd;
    }

    public static String luauType(Type t) {
        if (t instanceof Class<?> c) return luauClassType(c);
        if (t instanceof ParameterizedType pt) {
            Class<?> raw = (Class<?>) pt.getRawType();
            Type[] args = pt.getActualTypeArguments();

            if (Collection.class.isAssignableFrom(raw)) return "{" + luauType(args[0]) + "}";
            if (Map.class.isAssignableFrom(raw)) return "{[" + luauType(args[0]) + "]: " + luauType(args[1]) + "}";
            if ("Optional".equals(raw.getSimpleName())) return luauType(args[0]) + "?";

            return luauClassType(raw);
        }
        return "any";
    }

    private static String luauClassType(Class<?> c) {
        if (c.isArray()) return "{" + luauClassType(c.getComponentType()) + "}";
        if (c == void.class || c == Void.class) return "()";
        if (c == boolean.class || c == Boolean.class) return "boolean";
        if (c == String.class || c == CharSequence.class || c == char.class || c == Character.class) return "string";
        if (NUMBERS.contains(c)) return "number";
        if (c.isAnnotationPresent(LuauExport.class)) return luauName(c);

        if (Runnable.class.isAssignableFrom(c) || Consumer.class.isAssignableFrom(c) || BiConsumer.class.isAssignableFrom(c)) {
            return "(...any) -> ()";
        }
        if (Supplier.class.isAssignableFrom(c)) return "() -> any";

        if (Function.class.isAssignableFrom(c) || BiFunction.class.isAssignableFrom(c) ||
                Arrays.stream(c.getInterfaces()).anyMatch(i -> i.getName().startsWith("java.util.function."))) {
            return "(...any) -> any";
        }

        return "any";
    }
}