package com.meekdev.moud.script.host;

import java.util.ArrayList;
import java.util.List;

public final class HostSignal implements HostObject {

    private record Handler(Callable fn, Object owner, Connection connection) {}

    private static final Builtin CONNECT = new Builtin("Signal:connect", args ->
            args.self(HostSignal.class).connect(args.callable(1)));

    private static final Builtin CONNECT_PARALLEL = new Builtin("Signal:connectParallel", args -> {
        HostSignal signal = args.self(HostSignal.class);
        return signal.host.parallel().connect(signal, args.callable(1));
    });

    private static final Builtin ONCE = new Builtin("Signal:once", args -> {
        HostSignal signal = args.self(HostSignal.class);
        Callable fn = args.callable(1).retain();
        boolean[] fired = {false};
        Connection[] connection = new Connection[1];
        connection[0] = signal.connect(new Callable() {
            @Override
            public Object[] call(Object... values) {
                if (fired[0]) return null;
                fired[0] = true;
                connection[0].disconnect();
                try {
                    return signal.host.call(fn, signal.where, values);
                } finally {
                    fn.release();
                }
            }

            @Override
            public void release() {
                if (!fired[0]) fn.release();
            }
        });
        return connection[0];
    });

    private static final Builtin EVERY = new Builtin("Signal:every", args -> {
        HostSignal signal = args.self(HostSignal.class);
        int n = Math.max(1, args.integer(1));
        Callable fn = args.callable(2).retain();
        int[] count = {0};
        return signal.connect(new Callable() {
            @Override
            public Object[] call(Object... values) {
                if (++count[0] < n) return null;
                count[0] = 0;
                return signal.host.call(fn, signal.where, values);
            }

            @Override
            public void release() {
                fn.release();
            }
        });
    });

    private static final Builtin WAIT = new Builtin("Signal:wait", args -> {
        HostSignal signal = args.self(HostSignal.class);
        double timeout = args.number(1, -1);
        Object[][] fired = {null};
        Connection connection = signal.connect(values -> {
            if (fired[0] == null) fired[0] = values;
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

    private static final Builtin FIRE = new Builtin("Signal:fire", args -> {
        HostSignal signal = args.self(HostSignal.class);
        if (!signal.firable) throw new HostError("%s is fired by the engine, not by scripts", signal.type);
        signal.fire(args.from(1));
        return null;
    });

    private static final Builtin DISCONNECT_ALL = new Builtin("Signal:disconnectAll", args -> {
        args.self(HostSignal.class).clear();
        return null;
    });

    private final Host host;
    private final String type;
    private boolean firable;
    private final String where;
    private List<Handler> handlers = List.of();

    public HostSignal(Host host, String type, String where) {
        this.host = host;
        this.type = type;
        this.where = where;
    }

    public static HostSignal scripted(Host host, String name) {
        HostSignal signal = new HostSignal(host, "Signal", name);
        signal.firable = true;
        host.onClose(signal::clear);
        return signal;
    }

    public Connection connect(Callable fn) {
        Callable kept = fn.retain();
        Object owner = host.ownership().current();
        Handler[] handler = new Handler[1];
        Runnable[] forget = new Runnable[1];
        Connection connection = new Connection(() -> {
            if (!handlers.contains(handler[0])) return;
            List<Handler> rest = new ArrayList<>(handlers);
            rest.remove(handler[0]);
            handlers = rest;
            kept.release();
            forget[0].run();
        });
        handler[0] = new Handler(kept, owner, connection);
        List<Handler> next = new ArrayList<>(handlers);
        next.add(handler[0]);
        handlers = next;
        forget[0] = host.ownership().onRelease(owner, connection::disconnect);
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
        for (Handler handler : handlers) handler.connection().disconnect();
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
            case "connectParallel" -> CONNECT_PARALLEL;
            case "once" -> ONCE;
            case "every" -> EVERY;
            case "wait" -> WAIT;
            case "fire" -> FIRE;
            case "disconnectAll" -> DISCONNECT_ALL;
            case "count" -> (double) handlers.size();
            default -> throw new HostError("%s has no member '%s'", type, key);
        };
    }

    public static Api.Decl decl(String type, String handler) {
        return new Api.Decl(type, null, List.of(
                new Api.Member("connect", Api.Kind.METHOD, "(handler: " + handler + ") -> Connection"),
                new Api.Member("connectParallel", Api.Kind.METHOD, "(handler: " + handler + ") -> Connection"),
                new Api.Member("once", Api.Kind.METHOD, "(handler: " + handler + ") -> Connection"),
                new Api.Member("every", Api.Kind.METHOD, "(n: number, handler: " + handler + ") -> Connection"),
                new Api.Member("wait", Api.Kind.METHOD, "(timeout: number?) -> ...any"),
                new Api.Member("count", Api.Kind.FIELD, "number")));
    }
}
