package com.meekdev.moud.script.host.java;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.script.host.Args;
import com.meekdev.moud.script.host.Builtin;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostObject;
import com.meekdev.moud.script.host.Members;
import com.meekdev.moud.script.host.Values;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class JavaLibrary {

    static final Object MISMATCH = new Object();

    private static final Map<Class<?>, Map<String, List<Method>>> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Field>> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Builtin> CALLERS = new ConcurrentHashMap<>();

    public record JavaObject(Object value) implements HostObject {

        @Override
        public String typeName() {
            return "JavaObject";
        }

        @Override
        public Object get(String key) {
            return member(value.getClass(), key, false, value);
        }

        @Override
        public void set(String key, Object assigned) {
            write(value.getClass(), key, false, value, assigned);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof JavaObject o && Objects.equals(value, o.value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    public record JavaClass(Class<?> type) implements HostObject {

        @Override
        public String typeName() {
            return "JavaClass";
        }

        @Override
        public Object get(String key) {
            if (key.equals("new")) return caller("new");
            if (!methods(type, key, true).isEmpty() || !methods(type, key, false).isEmpty()) return new JavaMethod(type, key, List.of());
            return member(type, key, true, null);
        }

        @Override
        public void set(String key, Object assigned) {
            write(type, key, true, null, assigned);
        }

        @Override
        public String toString() {
            return "class " + type.getName();
        }
    }

    private JavaLibrary() {}

    public static void install(Host host) {
        Members java = new Members("Java")
                .function("use", "(name: string) -> any", a -> new JavaClass(load(a.string(0))))
                .function("class", "(name: string) -> any", a -> new JavaClass(load(a.string(0))))
                .function("typeof", "(value: any) -> string?", a -> a.get(0) instanceof JavaObject o ? o.value().getClass().getName() : null)
                .function("instanceOf", "(value: any, className: string) -> boolean",
                        a -> a.get(0) instanceof JavaObject o && load(a.string(1)).isInstance(o.value()));
        host.global("java", "Java", java);
        host.declare(java);
        Mixins.install(host);
    }

    public static Class<?> load(String name) {
        try {
            return Class.forName(name, true, JavaLibrary.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            throw new HostError("there is no java class '%s'", name);
        }
    }

    public static Object wrap(Object value) {
        return switch (value) {
            case null -> null;
            case Boolean b -> b;
            case Number n when !(n instanceof BigInteger || n instanceof BigDecimal) -> n.doubleValue();
            case Character c -> String.valueOf(c);
            case String s -> s;
            case Class<?> c -> new JavaClass(c);
            case Instance i -> i;
            default -> Values.isValue(value) ? value : new JavaObject(value);
        };
    }

    public static Object convert(Object value, Class<?> want) {
        Class<?> type = box(want);
        if (value != null && !want.isPrimitive() && !(value instanceof JavaObject o && want.isInstance(o.value()))) {
            Object converted = Conversions.toJava(value, want);
            if (converted != MISMATCH) return converted;
        }
        return switch (value) {
            case null -> want.isPrimitive() ? MISMATCH : null;
            case Boolean b -> type == Boolean.class || type == Object.class ? b : MISMATCH;
            case Number number -> {
                double d = number.doubleValue();
                if (type == Double.class || type == Object.class || type == Number.class) yield d;
                if (type == Float.class) yield (float) d;
                if (d != Math.rint(d)) yield MISMATCH;
                if (type == Integer.class) yield (int) d;
                if (type == Long.class) yield (long) d;
                if (type == Short.class) yield (short) d;
                if (type == Byte.class) yield (byte) d;
                if (type == Character.class) yield (char) d;
                yield MISMATCH;
            }
            case String text -> {
                if (type == String.class || type == Object.class || type == CharSequence.class) yield text;
                if (type == Character.class && text.length() == 1) yield text.charAt(0);
                if (type.isEnum()) {
                    for (Object constant : type.getEnumConstants()) {
                        if (((Enum<?>) constant).name().equalsIgnoreCase(text)) yield constant;
                    }
                }
                yield MISMATCH;
            }
            case JavaObject o -> type.isInstance(o.value()) ? o.value() : MISMATCH;
            case JavaClass c -> type == Class.class || type == Object.class ? c.type() : MISMATCH;
            default -> type.isInstance(value) ? value : MISMATCH;
        };
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == boolean.class) return Boolean.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static Object member(Class<?> type, String key, boolean statics, Object self) {
        if (!methods(type, key, statics).isEmpty()) return caller(key);
        Field field = field(type, key, statics);
        if (field == null) throw new HostError("%s has no %s method or field '%s'", type.getName(), statics ? "static" : "", key);
        try {
            return wrap(field.get(self));
        } catch (IllegalAccessException e) {
            throw new HostError("%s.%s can not be read: %s", type.getName(), key, e.getMessage());
        }
    }

    private static void write(Class<?> type, String key, boolean statics, Object self, Object assigned) {
        Field field = field(type, key, statics);
        if (field == null) throw new HostError("%s has no field '%s'", type.getName(), key);
        Object value = convert(assigned, field.getType());
        if (value == MISMATCH) throw new HostError("%s.%s is a %s", type.getName(), key, field.getType().getSimpleName());
        try {
            field.set(self, value);
        } catch (IllegalAccessException e) {
            throw new HostError("%s.%s can not be written: %s", type.getName(), key, e.getMessage());
        }
    }

    private static Builtin caller(String name) {
        return CALLERS.computeIfAbsent(name, key -> new Builtin("java:" + key, a -> call(a, key)));
    }

    private static Object call(Args a, String name) {
        boolean statics = a.get(0) instanceof JavaClass;
        Object self = a.get(0) instanceof JavaObject o ? o.value() : null;
        if (!statics && self == null) throw new HostError("call %s with a colon: thing:%s(...)", name, name);
        Class<?> type = statics ? ((JavaClass) a.get(0)).type() : self.getClass();
        List<? extends Executable> candidates = name.equals("new") && statics
                ? List.of(type.getDeclaredConstructors())
                : methods(type, name, statics);
        return invoke(type, name, self, a.from(1), candidates);
    }

    static Object invoke(Class<?> type, String name, Object self, Object[] given, List<? extends Executable> candidates) {
        for (Executable candidate : candidates) {
            Object[] args = arguments(candidate, given);
            if (args == null) continue;
            try {
                candidate.setAccessible(true);
                Object result = candidate instanceof Constructor<?> constructor
                        ? constructor.newInstance(args)
                        : ((Method) candidate).invoke(self, args);
                return wrap(result);
            } catch (InvocationTargetException e) {
                throw new HostError("%s.%s threw %s", type.getSimpleName(), name, e.getCause());
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new HostError("%s.%s could not be called: %s", type.getSimpleName(), name, e);
            }
        }
        Set<String> shapes = new LinkedHashSet<>();
        for (Executable candidate : candidates) shapes.add(candidate.toGenericString());
        throw new HostError("no overload of %s.%s matches %d arguments, candidates:\n  %s", type.getName(), name, given.length,
                String.join("\n  ", shapes));
    }

    private static Object[] arguments(Executable executable, Object[] given) {
        Class<?>[] parameters = executable.getParameterTypes();
        if (executable.isVarArgs() && given.length >= parameters.length - 1) {
            Object[] out = new Object[parameters.length];
            for (int n = 0; n < parameters.length - 1; n++) {
                out[n] = convert(given[n], parameters[n]);
                if (out[n] == MISMATCH) return null;
            }
            Class<?> element = parameters[parameters.length - 1].getComponentType();
            int rest = given.length - (parameters.length - 1);
            Object array = Array.newInstance(element, rest);
            for (int n = 0; n < rest; n++) {
                Object one = convert(given[parameters.length - 1 + n], element);
                if (one == MISMATCH) return null;
                Array.set(array, n, one);
            }
            out[parameters.length - 1] = array;
            return out;
        }
        if (parameters.length != given.length) return null;
        Object[] out = new Object[given.length];
        for (int n = 0; n < given.length; n++) {
            out[n] = convert(given[n], parameters[n]);
            if (out[n] == MISMATCH) return null;
        }
        return out;
    }

    static List<Method> methods(Class<?> type, String name, boolean statics) {
        return METHOD_CACHE.computeIfAbsent(type, t -> new ConcurrentHashMap<>()).computeIfAbsent(name + (statics ? "#static" : ""), n -> {
            List<Method> out = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Class<?> c = type; c != null; c = c.getSuperclass()) collect(c.getDeclaredMethods(), name, statics, out, seen);
            for (Method m : type.getMethods()) collect(new Method[] {m}, name, statics, out, seen);
            return out;
        });
    }

    private static void collect(Method[] found, String name, boolean statics, List<Method> out, Set<String> seen) {
        for (Method m : found) {
            if (!m.getName().equals(name) || Modifier.isStatic(m.getModifiers()) != statics || m.isBridge()) continue;
            if (!seen.add(Arrays.toString(m.getParameterTypes()))) continue;
            try {
                m.setAccessible(true);
            } catch (RuntimeException ignored) {
                if (!Modifier.isPublic(m.getModifiers())) continue;
            }
            out.add(m);
        }
    }

    private static Field field(Class<?> type, String name, boolean statics) {
        Map<String, Field> known = FIELD_CACHE.computeIfAbsent(type, t -> new ConcurrentHashMap<>());
        Field cached = known.get(name);
        if (cached != null) return cached;
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                if (Modifier.isStatic(f.getModifiers()) != statics) continue;
                f.setAccessible(true);
                known.put(name, f);
                return f;
            } catch (NoSuchFieldException | RuntimeException ignored) {
            }
        }
        return null;
    }
}
