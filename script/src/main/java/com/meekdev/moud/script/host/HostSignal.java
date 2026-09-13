package com.meekdev.moud.script.host;

import java.util.ArrayList;
import java.util.List;

public final class HostSignal implements HostObject {

    private record Handler(Callable fn, Object owner) {}

    private static final Builtin CONNECT = new Builtin("Signal:connect", args ->
            args.self(HostSignal.class).connect(args.callable(1)));

    private static final Builtin ONCE = new Builtin("Signal:once", args -> {
        HostSignal signal = args.self(HostSignal.class);
        Callable fn = args.callable(1).retain();
        Connection[] connection = new Connection[1];
        connection[0] = signal.connect(values -> {
            connection[0].disconnect();
            return signal.host.call(fn, signal.where, values);
        });
        return connection[0];
    });

    private static final Builtin EVERY = new Builtin("Signal:every", args -> {
        HostSignal signal = args.self(HostSignal.class);
        int n = Math.max(1, args.integer(1));
        Callable fn = args.callable(2).retain();
        int[] count = {0};
        return signal.connect(values -> {
            if (++count[0] < n) return null;
            count[0] = 0;
            return signal.host.call(fn, signal.where, values);
        });
    });

    private static final Builtin WAIT = new Builtin("Signal:wait", args -> {
        HostSignal signal = args.self(HostSignal.class);
        double timeout = args.number(1, -1);
        Object[][] fired = {null};
        Connection connection = signal.connect(values -> {
            fired[0] = values;
            return null;
        });
        double[] waited = {0};
        return new Suspend(dt -> {
            if (fired[0] != null) {
                connection.disconnect();
                return fired[0];
            }
            waited[0] += dt;
            if (timeout >= 0 && waited[0] >= timeout) {
                connection.disconnect();
                return new Object[0];
            }
            return null;
        }, connection::disconnect);
    });

    private final Host host;
    private final String type;
    private final String where;
    private List<Handler> handlers = List.of();

    public HostSignal(Host host, String type, String where) {
        this.host = host;
        this.type = type;
        this.where = where;
    }

    public Connection connect(Callable fn) {
        Callable kept = fn.retain();
        Object owner = host.ownership().current();
        Handler handler = new Handler(kept, owner);
        List<Handler> next = new ArrayList<>(handlers);
        next.add(handler);
        handlers = next;
        Connection connection = new Connection(() -> {
            if (!handlers.contains(handler)) return;
            List<Handler> rest = new ArrayList<>(handlers);
            rest.remove(handler);
            handlers = rest;
            kept.release();
        });
        host.ownership().onRelease(owner, connection::disconnect);
        return connection;
    }

    public void fire(Object... args) {
        for (Handler handler : handlers) {
            Object before = host.ownership().enter(handler.owner());
            try {
                host.call(handler.fn(), "signal", args);
            } finally {
                host.ownership().leave(before);
            }
        }
    }

    public int count() {
        return handlers.size();
    }

    public void clear() {
        for (Handler handler : handlers) handler.fn().release();
        handlers = List.of();
    }

    @Override
    public String typeName() {
        return type;
    }

    @Override
    public Object get(String key) {
        return switch (key) {
            case "connect" -> CONNECT;
            case "once" -> ONCE;
            case "every" -> EVERY;
            case "wait" -> WAIT;
            default -> throw new HostError("%s has no member '%s'", type, key);
        };
    }

    public static Api.Decl decl(String type, String handler) {
        return new Api.Decl(type, null, List.of(
                new Api.Member("connect", Api.Kind.METHOD, "(handler: " + handler + ") -> Connection"),
                new Api.Member("once", Api.Kind.METHOD, "(handler: " + handler + ") -> Connection"),
                new Api.Member("every", Api.Kind.METHOD, "(n: number, handler: " + handler + ") -> Connection"),
                new Api.Member("wait", Api.Kind.METHOD, "(timeout: number?) -> ...any")));
    }
}
