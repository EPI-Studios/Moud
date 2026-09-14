package com.meekdev.moud.addon.java;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.script.host.Builtin;
import com.meekdev.moud.script.host.Callable;
import com.meekdev.moud.script.host.Connection;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostSignal;
import com.meekdev.moud.script.host.Results;
import com.meekdev.moud.script.host.Suspend;
import com.meekdev.moud.script.host.Tasks;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

public abstract class PlaceScript {

    private Host host;
    private Instance script;

    final void attach(Host host, Instance script) {
        this.host = host;
        this.script = script;
    }

    public abstract void run();

    protected final Host host() {
        return host;
    }

    protected final Instance world() {
        return host.world();
    }

    protected final Instance script() {
        return script;
    }

    protected final Object global(String name) {
        Object value = host.globals().get(name);
        if (value == null && !host.globals().containsKey(name)) throw new HostError("there is no global '%s'", name);
        return value;
    }

    protected final Object get(Object target, String key) {
        return host.index(target, key);
    }

    @SuppressWarnings("unchecked")
    protected final <T> T get(Object target, String key, Class<T> type) {
        Object value = get(target, key);
        if (!type.isInstance(value)) throw new HostError("%s is a %s, not a %s", key, Host.typeOf(value), type.getSimpleName());
        return (T) value;
    }

    protected final void set(Object target, String key, Object value) {
        host.assign(target, key, value);
    }

    protected final Object call(Object target, String method, Object... args) {
        return first(callAll(target, method, args));
    }

    protected final Object[] callAll(Object target, String method, Object... args) {
        if (!(get(target, method) instanceof Builtin fn)) throw new HostError("%s is not a method", method);
        Object[] withSelf = new Object[args.length + 1];
        withSelf[0] = target;
        System.arraycopy(args, 0, withSelf, 1, args.length);
        return flatten(host.invoke(fn, withSelf));
    }

    protected final Object use(String library, String function, Object... args) {
        if (!(get(global(library), function) instanceof Builtin fn)) throw new HostError("%s.%s is not a function", library, function);
        return first(flatten(host.invoke(fn, args)));
    }

    @SuppressWarnings("unchecked")
    protected final <T extends Instance> T add(Instance parent, String className, Map<String, Object> properties) {
        return (T) call(parent, "add", className, properties);
    }

    protected final Object tween(Instance target, Map<String, Object> goals, Map<String, Object> info) {
        return call(target, "tween", goals, info);
    }

    protected final void print(Object... values) {
        StringBuilder line = new StringBuilder();
        for (int n = 0; n < values.length; n++) {
            if (n > 0) line.append('\t');
            line.append(host.text(values[n]));
        }
        host.print(line.toString());
    }

    protected final Connection onStep(DoubleConsumer handler) {
        return host.stepped().connect(args -> {
            handler.accept(((Number) args[0]).doubleValue());
            return new Object[0];
        });
    }

    protected final Connection onRenderStep(DoubleConsumer handler) {
        return host.renderStepped().connect(args -> {
            handler.accept(((Number) args[0]).doubleValue());
            return new Object[0];
        });
    }

    protected final Connection connect(Object signal, Consumer<Object[]> handler) {
        if (!(signal instanceof HostSignal hostSignal)) throw new HostError("that is not a signal");
        return hostSignal.connect(args -> {
            handler.accept(args);
            return new Object[0];
        });
    }

    protected final void delay(double seconds, Runnable action) {
        Tasks.delay(host, seconds, callable(action));
    }

    protected final void every(double seconds, Runnable action) {
        use("task", "every", seconds, callable(action));
    }

    protected static Callable callable(Runnable action) {
        return args -> {
            action.run();
            return new Object[0];
        };
    }

    protected static Map<String, Object> props(Object... keysAndValues) {
        if (keysAndValues.length % 2 != 0) throw new IllegalArgumentException("props takes keys and values in pairs");
        Map<String, Object> out = new LinkedHashMap<>();
        for (int n = 0; n < keysAndValues.length; n += 2) out.put((String) keysAndValues[n], keysAndValues[n + 1]);
        return out;
    }

    protected static Vector3 vec3(double x, double y, double z) {
        return new Vector3(x, y, z);
    }

    protected static Color color(double r, double g, double b) {
        return new Color((float) r, (float) g, (float) b, 1);
    }

    protected static Quat euler(double x, double y, double z) {
        return Quat.euler(x, y, z);
    }

    protected static CFrame at(Vector3 position) {
        return CFrame.at(position);
    }

    private static Object[] flatten(Object result) {
        return switch (result) {
            case null -> new Object[0];
            case Results many -> many.values();
            case Suspend suspend -> {
                suspend.cancel().run();
                throw new HostError("java places cannot wait, use delay or connect to a signal instead");
            }
            default -> new Object[] {result};
        };
    }

    private static Object first(Object[] values) {
        return values.length == 0 ? null : values[0];
    }
}
