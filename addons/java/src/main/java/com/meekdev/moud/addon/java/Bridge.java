package com.meekdev.moud.addon.java;

import com.meekdev.moud.script.host.Args;
import com.meekdev.moud.script.host.Builtin;
import com.meekdev.moud.script.host.Callable;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.Invocable;
import com.meekdev.moud.script.host.Results;
import com.meekdev.moud.script.host.Suspend;
import com.meekdev.moud.script.host.ThreadFiber;
import java.util.Arrays;
import java.util.function.Function;

public final class Bridge {

    private final Host host;

    Bridge(Host host) {
        this.host = host;
    }

    public Object global(String name) {
        return host.globals().get(name);
    }

    public Object get(Object target, String key) {
        return host.index(target, key);
    }

    public Object call(Object target, String method, Object... args) {
        return invoke(get(target, method), prepend(target, args), method);
    }

    public Object callRest(Object target, String method, Object... args) {
        return call(target, method, flattenRest(args));
    }

    public Object callField(Object target, String field, Object... args) {
        return invoke(get(target, field), args, field);
    }

    public Object callFieldRest(Object target, String field, Object... args) {
        return callField(target, field, flattenRest(args));
    }

    public Object callGlobal(String name, Object... args) {
        return invoke(global(name), args, name);
    }

    public Object callGlobalRest(String name, Object... args) {
        return callGlobal(name, flattenRest(args));
    }

    private Object invoke(Object fn, Object[] args, String name) {
        Object[] converted = new Object[args.length];
        for (int n = 0; n < args.length; n++) converted[n] = callable(args[n]);
        Object result = switch (fn) {
            case Builtin builtin -> host.invoke(builtin, converted);
            case Invocable invocable -> invocable.invoke(new Args(host, name, converted));
            case Callable callable -> new Results(callable.call(converted));
            default -> throw new HostError("%s is not a function", name);
        };
        return result instanceof Suspend suspend ? new Results(ThreadFiber.await(suspend)) : result;
    }

    @SuppressWarnings("unchecked")
    private static Object callable(Object value) {
        if (value instanceof Function<?, ?> function) {
            Function<Object[], Object> body = (Function<Object[], Object>) function;
            return (Callable) args -> {
                Object out = body.apply(args);
                return out == null ? new Object[0] : new Object[] {out};
            };
        }
        return value;
    }

    public Object first(Object result) {
        Object[] all = all(result);
        return all.length == 0 ? null : all[0];
    }

    public Object[] all(Object result) {
        return switch (result) {
            case null -> new Object[0];
            case Results many -> many.values();
            case Object[] array -> array;
            default -> new Object[] {result};
        };
    }

    private static Object[] prepend(Object first, Object[] rest) {
        Object[] out = new Object[rest.length + 1];
        out[0] = first;
        System.arraycopy(rest, 0, out, 1, rest.length);
        return out;
    }

    private static Object[] flattenRest(Object[] args) {
        if (args.length == 0 || !(args[args.length - 1] instanceof Object[] rest)) return args;
        Object[] out = Arrays.copyOf(args, args.length - 1 + rest.length);
        System.arraycopy(rest, 0, out, args.length - 1, rest.length);
        return out;
    }

    public static double number(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        throw new HostError("expected a number, got %s", Host.typeOf(value));
    }

    public static Double optionalNumber(Object value) {
        return value == null ? null : number(value);
    }

    public static boolean truthy(Object value) {
        return value != null && !Boolean.FALSE.equals(value);
    }

    public static <T> T wrap(Object value, Function<Object, T> wrapper) {
        return value == null ? null : wrapper.apply(value);
    }
}
