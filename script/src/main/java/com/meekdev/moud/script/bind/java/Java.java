package com.meekdev.moud.script.bind.java;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;
import com.meekdev.moud.script.bind.Proxies;
import com.meekdev.moud.script.bind.Values;

public final class Java {

    static final int OBJECT = 20;
    static final int CLASS = 21;

    private static final String METHODS = "__moud_java_methods";

    private record JavaClass(Class<?> type) {}

    private static final Map<Class<?>, Map<String, List<Method>>> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    private Java() {}

    public static void install(LuaState state) {
        state.newTable();
        state.pushFunction(LuaFunc.wrap(s -> index(s, false), "java.__index"));
        state.rawSetField(-2, "__index");
        state.pushFunction(LuaFunc.wrap(s -> assign(s, false), "java.__newindex"));
        state.rawSetField(-2, "__newindex");
        state.pushFunction(LuaFunc.wrap(Java::text, "java.__tostring"));
        state.rawSetField(-2, "__tostring");
        state.pushFunction(LuaFunc.wrap(s -> {
            s.pushBoolean(Objects.equals(s.toUserDataTagged(1, OBJECT), s.toUserDataTagged(2, OBJECT)));
            return 1;
        }, "java.__eq"));
        state.rawSetField(-2, "__eq");
        state.setUserDataMetaTable(OBJECT);

        state.newTable();
        state.pushFunction(LuaFunc.wrap(s -> index(s, true), "javaclass.__index"));
        state.rawSetField(-2, "__index");
        state.pushFunction(LuaFunc.wrap(s -> assign(s, true), "javaclass.__newindex"));
        state.rawSetField(-2, "__newindex");
        state.pushFunction(LuaFunc.wrap(Java::text, "javaclass.__tostring"));
        state.rawSetField(-2, "__tostring");
        state.setUserDataMetaTable(CLASS);

        state.newTable();
        state.rawSetField(LuaState.REGISTRY_INDEX, METHODS);

        state.newTable();
        state.pushFunction(LuaFunc.wrap(s -> {
            state.newUserDataTaggedWithMetatable(new JavaClass(load(s, s.checkString(1))), CLASS);
            return 1;
        }, "java.class"));
        state.rawSetField(-2, "class");
        state.pushFunction(LuaFunc.wrap(s -> {
            Object value = s.toUserDataTagged(1, OBJECT);
            if (value == null) {
                s.pushNil();
            } else {
                s.pushString(value.getClass().getName());
            }
            return 1;
        }, "java.typeof"));
        state.rawSetField(-2, "typeof");
        state.pushFunction(LuaFunc.wrap(s -> {
            Object value = s.toUserDataTagged(1, OBJECT);
            s.pushBoolean(value != null && load(s, s.checkString(2)).isInstance(value));
            return 1;
        }, "java.instanceOf"));
        state.rawSetField(-2, "instanceOf");
        state.setGlobal("java");
    }

    public static Class<?> load(LuaState state, String name) {
        try {
            return Class.forName(name, true, Java.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            throw state.error("there is no java class '%s'", name);
        }
    }

    public static void push(LuaState state, Object value) {
        switch (value) {
            case null -> state.pushNil();
            case Boolean b -> state.pushBoolean(b);
            case Number n when !(n instanceof BigInteger || n instanceof BigDecimal) ->
                    state.pushNumber(n.doubleValue());
            case Character c -> state.pushString(String.valueOf(c));
            case String s -> state.pushString(s);
            case Class<?> c -> state.newUserDataTaggedWithMetatable(new JavaClass(c), CLASS);
            default -> state.newUserDataTaggedWithMetatable(value, OBJECT);
        }
    }

    static final Object MISMATCH = new Object();

    public static Object convert(LuaState state, int at, Class<?> want) {
        Class<?> type = box(want);
        switch (state.type(at)) {
            case NIL, NONE -> {
                return want.isPrimitive() ? MISMATCH : null;
            }
            case BOOLEAN -> {
                return type == Boolean.class || type == Object.class ? state.toBoolean(at) : MISMATCH;
            }
            case NUMBER -> {
                double d = state.toNumber(at);
                if (type == Double.class || type == Object.class || type == Number.class) return d;
                if (type == Float.class) return (float) d;
                boolean whole = d == Math.rint(d);
                if (!whole) return MISMATCH;
                if (type == Integer.class) return (int) d;
                if (type == Long.class) return (long) d;
                if (type == Short.class) return (short) d;
                if (type == Byte.class) return (byte) d;
                if (type == Character.class) return (char) d;
                return MISMATCH;
            }
            case STRING -> {
                String text = state.toString(at);
                if (type == String.class || type == Object.class || type == CharSequence.class) return text;
                if (type == Character.class && text.length() == 1) return text.charAt(0);
                if (type.isEnum()) {
                    for (Object constant : type.getEnumConstants()) {
                        if (((Enum<?>) constant).name().equalsIgnoreCase(text)) return constant;
                    }
                }
                return MISMATCH;
            }
            case USERDATA -> {
                Object object = state.toUserDataTagged(at, OBJECT);
                if (object != null) return type.isInstance(object) ? object : MISMATCH;
                Object cls = state.toUserDataTagged(at, CLASS);
                if (cls instanceof JavaClass c) return type == Class.class || type == Object.class ? c.type() : MISMATCH;
                Object instance = state.toUserDataTagged(at, Proxies.TAG);
                if (instance != null) return type.isInstance(instance) ? instance : MISMATCH;
                Object other = Values.value(state, at);
                return other != null && type.isInstance(other) ? other : MISMATCH;
            }
            default -> {
                return MISMATCH;
            }
        }
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

    private static int text(LuaState state) {
        Object object = state.toUserDataTagged(1, OBJECT);
        if (object == null && state.toUserDataTagged(1, CLASS) instanceof JavaClass c) {
            state.pushString("class " + c.type().getName());
        } else {
            state.pushString(String.valueOf(object));
        }
        return 1;
    }

    private static int index(LuaState state, boolean statics) {
        Class<?> type = typeAt(state, statics);
        String key = state.checkString(2);
        if (statics && key.equals("new")) {
            pushMethod(state, "new");
            return 1;
        }
        if (!methods(type, key, statics).isEmpty()) {
            pushMethod(state, key);
            return 1;
        }
        Field field = field(type, key, statics);
        if (field == null) {
            throw state.error("%s has no %s method or field '%s'", type.getName(), statics ? "static" : "", key);
        }
        try {
            push(state, field.get(statics ? null : state.toUserDataTagged(1, OBJECT)));
        } catch (IllegalAccessException e) {
            throw state.error("%s.%s can not be read: %s", type.getName(), key, e.getMessage());
        }
        return 1;
    }

    private static int assign(LuaState state, boolean statics) {
        Class<?> type = typeAt(state, statics);
        String key = state.checkString(2);
        Field field = field(type, key, statics);
        if (field == null) throw state.error("%s has no field '%s'", type.getName(), key);
        Object value = convert(state, 3, field.getType());
        if (value == MISMATCH) throw state.error("%s.%s is a %s", type.getName(), key, field.getType().getSimpleName());
        try {
            field.set(statics ? null : state.toUserDataTagged(1, OBJECT), value);
        } catch (IllegalAccessException e) {
            throw state.error("%s.%s can not be written: %s", type.getName(), key, e.getMessage());
        }
        return 0;
    }

    private static Class<?> typeAt(LuaState state, boolean statics) {
        if (statics) {
            if (!(state.toUserDataTagged(1, CLASS) instanceof JavaClass c)) throw state.error("not a java class");
            return c.type();
        }
        Object object = state.toUserDataTagged(1, OBJECT);
        if (object == null) throw state.error("not a java object");
        return object.getClass();
    }

    private static void pushMethod(LuaState state, String name) {
        state.rawGetField(LuaState.REGISTRY_INDEX, METHODS);
        if (state.rawGetField(-1, name) == LuaType.FUNCTION) {
            state.remove(-2);
            return;
        }
        state.pop(1);
        state.pushFunction(LuaFunc.wrap(s -> call(s, name), "java:" + name));
        state.pushValue(-1);
        state.rawSetField(-3, name);
        state.remove(-2);
    }

    private static int call(LuaState state, String name) {
        boolean statics = state.toUserDataTagged(1, CLASS) instanceof JavaClass;
        Object self = statics ? null : state.toUserDataTagged(1, OBJECT);
        if (!statics && self == null) {
            throw state.error("call %s with a colon: thing:%s(...)", name, name);
        }
        Class<?> type = statics ? ((JavaClass) state.toUserDataTagged(1, CLASS)).type() : self.getClass();
        int given = state.top() - 1;
        List<? extends Executable> candidates = name.equals("new") && statics
                ? List.of(type.getDeclaredConstructors())
                : methods(type, name, statics);
        for (Executable candidate : candidates) {
            Object[] args = arguments(state, candidate, given);
            if (args == null) continue;
            try {
                candidate.setAccessible(true);
                Object result = candidate instanceof Constructor<?> constructor
                        ? constructor.newInstance(args)
                        : ((Method) candidate).invoke(self, args);
                push(state, result);
                return 1;
            } catch (InvocationTargetException thrown) {
                throw state.error("%s.%s threw %s", type.getSimpleName(), name, thrown.getCause());
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw state.error("%s.%s could not be called: %s", type.getSimpleName(), name, e);
            }
        }
        Set<String> shapes = new LinkedHashSet<>();
        for (Executable candidate : candidates) shapes.add(candidate.toGenericString());
        throw state.error("no overload of %s.%s matches %d arguments, candidates:\n  %s", type.getName(), name, given,
                String.join("\n  ", shapes));
    }

    private static Object[] arguments(LuaState state, Executable executable, int given) {
        Class<?>[] parameters = executable.getParameterTypes();
        if (executable.isVarArgs() && given >= parameters.length - 1) {
            Object[] out = new Object[parameters.length];
            for (int n = 0; n < parameters.length - 1; n++) {
                out[n] = convert(state, n + 2, parameters[n]);
                if (out[n] == MISMATCH) return null;
            }
            Class<?> element = parameters[parameters.length - 1].getComponentType();
            int rest = given - (parameters.length - 1);
            Object array = Array.newInstance(element, rest);
            for (int n = 0; n < rest; n++) {
                Object one = convert(state, parameters.length + 1 + n, element);
                if (one == MISMATCH) return null;
                Array.set(array, n, one);
            }
            out[parameters.length - 1] = array;
            return out;
        }
        if (parameters.length != given) return null;
        Object[] out = new Object[given];
        for (int n = 0; n < given; n++) {
            out[n] = convert(state, n + 2, parameters[n]);
            if (out[n] == MISMATCH) return null;
        }
        return out;
    }

    static List<Method> methods(Class<?> type, String name, boolean statics) {
        return METHOD_CACHE.computeIfAbsent(type, t -> new ConcurrentHashMap<>()).computeIfAbsent(name, n -> {
            List<Method> out = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Class<?> c = type; c != null; c = c.getSuperclass()) collect(c.getDeclaredMethods(), n, statics, out, seen);
            for (Method m : type.getMethods()) collect(new Method[] {m}, n, statics, out, seen);
            return out;
        });
    }

    private static void collect(Method[] found, String name, boolean statics, List<Method> out, Set<String> seen) {
        for (Method m : found) {
            if (!m.getName().equals(name) || Modifier.isStatic(m.getModifiers()) != statics || m.isBridge()) continue;
            String shape = Arrays.toString(m.getParameterTypes());
            if (!seen.add(shape)) continue;
            try {
                m.setAccessible(true);
            } catch (RuntimeException closed) {
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
