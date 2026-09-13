package com.meekdev.moud.core.query;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.clazz.Enums;
import com.meekdev.moud.core.clazz.PropertyDef;
import java.util.ArrayList;
import java.util.List;
import com.meekdev.moud.core.instance.Instance;

public final class Selector {

    private record Test(String property, String op, String value) {}

    private record Compound(ClassDef<?> type, String name, List<String> tags, List<Test> tests) {}

    private final List<Compound> parts;
    private final List<Boolean> direct;

    private Selector(List<Compound> parts, List<Boolean> direct) {
        this.parts = parts;
        this.direct = direct;
    }

    public static Selector parse(String text, ClassRegistry classes) {
        List<Compound> parts = new ArrayList<>();
        List<Boolean> direct = new ArrayList<>();
        String[] tokens = text.trim().replace(">", " > ").split("\\s+");
        boolean nextDirect = false;
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            if (token.equals(">")) {
                if (parts.isEmpty()) throw new IllegalArgumentException("a query cannot start with >");
                nextDirect = true;
                continue;
            }
            parts.add(compound(token, classes));
            direct.add(nextDirect);
            nextDirect = false;
        }
        if (parts.isEmpty()) throw new IllegalArgumentException("an empty query finds nothing");
        return new Selector(parts, direct);
    }

    private static Compound compound(String token, ClassRegistry classes) {
        int i = 0;
        StringBuilder type = new StringBuilder();
        while (i < token.length() && "#.[".indexOf(token.charAt(i)) < 0) type.append(token.charAt(i++));
        ClassDef<?> def = null;
        if (!type.isEmpty() && !type.toString().equals("*")) {
            def = classes.find(type.toString());
            if (def == null) throw new IllegalArgumentException("there is no class called " + type);
        }
        String name = null;
        List<String> tags = new ArrayList<>();
        List<Test> tests = new ArrayList<>();
        while (i < token.length()) {
            char c = token.charAt(i);
            if (c == '#' || c == '.') {
                int start = ++i;
                while (i < token.length() && "#.[".indexOf(token.charAt(i)) < 0) i++;
                String word = token.substring(start, i);
                if (c == '#') {
                    name = word;
                } else {
                    tags.add(word);
                }
            } else if (c == '[') {
                int end = token.indexOf(']', i);
                if (end < 0) throw new IllegalArgumentException("a [ in a query is never closed");
                tests.add(test(token.substring(i + 1, end)));
                i = end + 1;
            } else {
                throw new IllegalArgumentException("a query cannot have " + c + " there");
            }
        }
        return new Compound(def, name, tags, tests);
    }

    private static Test test(String inside) {
        for (String op : List.of("!=", ">=", "<=", "=", ">", "<")) {
            int at = inside.indexOf(op);
            if (at > 0) {
                String value = inside.substring(at + op.length()).trim();
                if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                return new Test(inside.substring(0, at).trim(), op, value);
            }
        }
        return new Test(inside.trim(), "exists", "");
    }

    public List<Instance> all(Instance root) {
        List<Instance> out = new ArrayList<>();
        for (Instance child : root.children()) collect(child, root, out);
        return out;
    }

    private void collect(Instance at, Instance root, List<Instance> out) {
        if (matches(at, parts.size() - 1, root)) out.add(at);
        for (Instance child : at.children()) collect(child, root, out);
    }

    private boolean matches(Instance instance, int index, Instance root) {
        if (!one(instance, parts.get(index))) return false;
        if (index == 0) return true;
        Instance up = instance.parent();
        if (direct.get(index)) return up != null && up != root && matches(up, index - 1, root);
        for (; up != null && up != root; up = up.parent()) {
            if (matches(up, index - 1, root)) return true;
        }
        return false;
    }

    private static boolean one(Instance instance, Compound compound) {
        if (compound.type() != null && !instance.def().isA(compound.type())) return false;
        if (compound.name() != null && !instance.name().equals(compound.name())) return false;
        for (String tag : compound.tags()) {
            if (!instance.hasTag(tag)) return false;
        }
        for (Test test : compound.tests()) {
            if (!matchesTest(instance, test)) return false;
        }
        return true;
    }

    private static boolean matchesTest(Instance instance, Test test) {
        PropertyDef property = instance.def().property(test.property());
        if (property == null) return false;
        if (test.op().equals("exists")) return true;
        Object value = property.type().isBool() ? property.getBool(instance)
                : property.isNumeric() ? property.getNum(instance) : property.getObj(instance);
        if (value instanceof Double number) {
            double wanted;
            try {
                wanted = Double.parseDouble(test.value());
            } catch (NumberFormatException ignored) {
                return false;
            }
            return switch (test.op()) {
                case "=" -> number == wanted;
                case "!=" -> number != wanted;
                case ">" -> number > wanted;
                case "<" -> number < wanted;
                case ">=" -> number >= wanted;
                case "<=" -> number <= wanted;
                default -> false;
            };
        }
        String text = value instanceof Enum<?> e ? Enums.name(e) : value instanceof Instance i ? i.name() : String.valueOf(value);
        return switch (test.op()) {
            case "=" -> text.equals(test.value());
            case "!=" -> !text.equals(test.value());
            default -> false;
        };
    }
}
