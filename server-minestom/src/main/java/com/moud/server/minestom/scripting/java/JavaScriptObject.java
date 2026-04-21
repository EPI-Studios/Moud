package com.moud.server.minestom.scripting.java;

import com.moud.server.minestom.scripting.ScriptInvocationException;
import com.moud.server.minestom.scripting.ScriptObject;
import com.moud.server.minestom.scripting.api.CoreScriptApi;
import com.moud.server.minestom.scripting.player.InputEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class JavaScriptObject implements ScriptObject {
    private final NodeScript instance;

    JavaScriptObject(NodeScript instance, CoreScriptApi api) {
        this.instance = instance;
        instance.bindCoreApi(api);
    }

    @Override
    public boolean hasMethod(String member) {
        if (member == null || member.isBlank()) {
            return false;
        }
        String target = resolveMethodName(member);
        if (target == null) {
            return false;
        }
        return findMethod(target, member) != null;
    }

    @Override
    public void invokeMethod(String member, Object... args) throws ScriptInvocationException {
        if (member == null || member.isBlank()) {
            return;
        }
        String target = resolveMethodName(member);
        if (target == null) {
            return;
        }
        Method method = findMethod(target, member);
        if (method == null) {
            return;
        }
        try {
            Object[] callArgs = buildArgs(method, member, args);
            method.setAccessible(true);
            method.invoke(instance, callArgs);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new ScriptInvocationException(cause.getMessage(), cause);
        } catch (Exception e) {
            throw new ScriptInvocationException(e.getMessage(), e);
        }
    }

    private Method findMethod(String target, String originalMember) {
        if (originalMember.startsWith("_on_")) {
            Method direct = lookup(target, Object.class);
            if (direct != null) {
                return direct;
            }
            return lookup("onSignal", String.class, Object.class);
        }
        if (originalMember.equals("_process") || originalMember.equals("_physics_process")
                || originalMember.equals("_physicsProcess")) {
            return lookup(target, double.class);
        }
        if (originalMember.equals("_input")) {
            return lookup(target, InputEvent.class);
        }
        return lookup(target);
    }

    private Method lookup(String name, Class<?>... paramTypes) {
        try {
            return instance.getClass().getMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private Object[] buildArgs(Method method, String originalMember, Object[] incoming) {
        Class<?>[] params = method.getParameterTypes();
        if (originalMember.startsWith("_on_")) {
            String signal = originalMember.substring(4);
            Object value = incoming != null && incoming.length > 0 ? incoming[0] : null;
            if (params.length == 2 && params[0] == String.class) {
                return new Object[]{signal, value};
            }
            if (params.length == 1) {
                return new Object[]{value};
            }
            return new Object[0];
        }
        Object[] tail = incoming == null || incoming.length <= 1 ? new Object[0]
                : java.util.Arrays.copyOfRange(incoming, 1, incoming.length);
        if (params.length == 0) {
            return new Object[0];
        }
        if (params.length == tail.length) {
            return tail;
        }
        Object[] out = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            out[i] = i < tail.length ? coerce(tail[i], params[i]) : defaultValue(params[i]);
        }
        return out;
    }

    private static Object coerce(Object value, Class<?> target) {
        if (value == null) {
            return defaultValue(target);
        }
        if (target.isInstance(value)) {
            return value;
        }
        if (value instanceof Number n) {
            if (target == double.class || target == Double.class) return n.doubleValue();
            if (target == float.class || target == Float.class) return n.floatValue();
            if (target == long.class || target == Long.class) return n.longValue();
            if (target == int.class || target == Integer.class) return n.intValue();
        }
        return value;
    }

    private static Object defaultValue(Class<?> target) {
        if (target == double.class) return 0.0;
        if (target == float.class) return 0.0f;
        if (target == long.class) return 0L;
        if (target == int.class) return 0;
        if (target == boolean.class) return false;
        return null;
    }

    private static String resolveMethodName(String member) {
        return switch (member) {
            case "_ready" -> "onReady";
            case "_enter_tree", "_enterTree" -> "onEnterTree";
            case "_exit_tree", "_exitTree" -> "onExitTree";
            case "_process" -> "onProcess";
            case "_physics_process", "_physicsProcess" -> "onPhysicsProcess";
            case "_input" -> "onInput";
            default -> {
                if (member.startsWith("_on_")) {
                    yield "on" + toCamelCase(member.substring(4));
                }
                yield null;
            }
        };
    }

    private static String toCamelCase(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean upper = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '_' || c == '-') {
                upper = true;
                continue;
            }
            if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }
}
