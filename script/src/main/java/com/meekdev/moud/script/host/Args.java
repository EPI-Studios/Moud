package com.meekdev.moud.script.host;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vector3;
import java.util.List;
import java.util.Map;

public final class Args {

    private final Host host;
    private final String name;
    private final Object[] values;

    public Args(Host host, String name, Object[] values) {
        this.host = host;
        this.name = name;
        this.values = values;
    }

    public Host host() {
        return host;
    }

    public String name() {
        return name;
    }

    public int count() {
        return values.length;
    }

    public Object get(int at) {
        return at < values.length ? values[at] : null;
    }

    public boolean has(int at) {
        return get(at) != null;
    }

    public Object[] from(int at) {
        if (at >= values.length) return new Object[0];
        Object[] rest = new Object[values.length - at];
        System.arraycopy(values, at, rest, 0, rest.length);
        return rest;
    }

    public HostError error(String format, Object... args) {
        return new HostError(name + ": " + String.format(format, args));
    }

    private HostError expected(int at, String what) {
        Object value = get(at);
        return error("argument %d expects %s, got %s", at + 1, what, Host.typeOf(value));
    }

    public double number(int at) {
        if (get(at) instanceof Number n) return n.doubleValue();
        throw expected(at, "a number");
    }

    public double number(int at, double fallback) {
        return has(at) ? number(at) : fallback;
    }

    public int integer(int at) {
        return (int) number(at);
    }

    public int integer(int at, int fallback) {
        return has(at) ? integer(at) : fallback;
    }

    public String string(int at) {
        if (get(at) instanceof String s) return s;
        throw expected(at, "a string");
    }

    public String string(int at, String fallback) {
        return has(at) ? string(at) : fallback;
    }

    public boolean bool(int at) {
        if (get(at) instanceof Boolean b) return b;
        throw expected(at, "a boolean");
    }

    public boolean bool(int at, boolean fallback) {
        return has(at) ? bool(at) : fallback;
    }

    public boolean truthy(int at) {
        Object value = get(at);
        return value != null && !Boolean.FALSE.equals(value);
    }

    public Vector3 vector(int at) {
        if (get(at) instanceof Vector3 v) return v;
        throw expected(at, "a vec3");
    }

    public Vector3 vector(int at, Vector3 fallback) {
        return has(at) ? vector(at) : fallback;
    }

    public CFrame cframe(int at) {
        if (get(at) instanceof CFrame c) return c;
        throw expected(at, "a cframe");
    }

    public Quat quat(int at) {
        if (get(at) instanceof Quat q) return q;
        throw expected(at, "a quat");
    }

    public Color color(int at) {
        if (get(at) instanceof Color c) return c;
        throw expected(at, "a color");
    }

    public Color color(int at, Color fallback) {
        return has(at) ? color(at) : fallback;
    }

    public UDim2 udim2(int at) {
        if (get(at) instanceof UDim2 u) return u;
        throw expected(at, "a udim2");
    }

    public Instance instance(int at) {
        if (get(at) instanceof Instance i) {
            if (!i.isAlive()) throw error("argument %d is a destroyed %s", at + 1, i.def().name());
            return i;
        }
        throw expected(at, "an instance");
    }

    public Instance instance(int at, Instance fallback) {
        return has(at) ? instance(at) : fallback;
    }

    public Instance self() {
        if (get(0) instanceof Instance i) {
            if (!i.isAlive()) throw new HostError("%s has been destroyed", i.name());
            return i;
        }
        throw error("expects to be called with ':' on an instance");
    }

    public <T> T self(Class<T> type) {
        Object value = get(0);
        if (type.isInstance(value)) return type.cast(value);
        throw error("expects to be called with ':' on a %s", type.getSimpleName());
    }

    public Callable callable(int at) {
        if (get(at) instanceof Callable c) return c;
        throw expected(at, "a function");
    }

    public Callable callable(int at, Callable fallback) {
        return has(at) ? callable(at) : fallback;
    }

    @SuppressWarnings("unchecked")
    public List<Object> list(int at) {
        return switch (get(at)) {
            case List<?> l -> (List<Object>) l;
            case Map<?, ?> m when m.isEmpty() -> List.of();
            default -> throw expected(at, "a list");
        };
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> map(int at) {
        return switch (get(at)) {
            case Map<?, ?> m -> (Map<String, Object>) m;
            case List<?> l when l.isEmpty() -> Map.of();
            default -> throw expected(at, "a table");
        };
    }

    public Map<String, Object> map(int at, Map<String, Object> fallback) {
        return has(at) ? map(at) : fallback;
    }
}
