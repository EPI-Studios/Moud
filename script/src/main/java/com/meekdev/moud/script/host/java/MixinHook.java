package com.meekdev.moud.script.host.java;

import com.meekdev.moud.script.host.Args;
import com.meekdev.moud.script.host.Builtin;
import com.meekdev.moud.script.host.Callable;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostObject;
import com.meekdev.moud.script.mixin.Dispatch;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MixinHook implements HostObject {

    record Options(Callable when, MixinState.Ref whenRef, boolean once, int limit, int priority, boolean raw, int ordinal) {

        static Options parse(Args a, int at) {
            Map<String, Object> map = a.map(at, Map.of());
            Object when = map.get("when");
            if (when != null && !(when instanceof Callable) && !(when instanceof MixinState.Ref)) {
                throw a.error("when expects a function or a state ref");
            }
            return new Options(when instanceof Callable c ? c.retain() : null, when instanceof MixinState.Ref r ? r : null,
                    Boolean.TRUE.equals(map.get("once")), number(map.get("limit")), number(map.get("priority")),
                    Boolean.TRUE.equals(map.get("raw")), number(map.get("ordinal")));
        }

        private static int number(Object value) {
            return value instanceof Number n ? n.intValue() : 0;
        }
    }

    final class Piece extends Dispatch.Hook {
        private final Mixins.Body body;

        Piece(Dispatch.Kind kind, Mixins.Body body) {
            super(kind, options.priority(), place.owner);
            this.body = body;
        }

        MixinHook owner() {
            return MixinHook.this;
        }

        @Override
        protected boolean ready() {
            return enabled && !removed && !(options.once() && used) && (options.limit() <= 0 || tick < options.limit())
                    && (group == null || group.enabled);
        }

        @Override
        protected Object run(Dispatch.Call call) throws Throwable {
            if (!allowed(call)) return Dispatch.PROCEED;
            tick++;
            if (options.once()) {
                used = true;
                place.finished.add(MixinHook.this);
            }
            return body.run(MixinHook.this, call);
        }

        @Override
        protected void failed(Throwable error) {
            place.host.error(label, error instanceof RuntimeException e ? e : new HostError("%s", String.valueOf(error)));
        }
    }

    final Mixins.Place place;
    final String label;
    final String file;
    final Dispatch.Kind kind;
    final Options options;
    final List<Piece> pieces = new CopyOnWriteArrayList<>();
    private final Callable handler;
    volatile boolean enabled = true;
    volatile boolean removed;
    boolean used;
    int tick;
    int methods;
    MixinGroup group;
    Runnable undo = () -> {};

    MixinHook(Mixins.Place place, String label, Dispatch.Kind kind, Options options, Callable handler) {
        this.place = place;
        this.label = label;
        this.file = place.file;
        this.kind = kind;
        this.options = options;
        this.handler = handler;
    }

    Piece piece(Dispatch.Kind kind, Mixins.Body body) {
        Piece piece = new Piece(kind, body);
        pieces.add(piece);
        return piece;
    }

    private boolean allowed(Dispatch.Call call) {
        if (group != null && !group.allowed()) return false;
        if (options.whenRef() != null) {
            Object value = options.whenRef().state().value(options.whenRef().key());
            if (value == null || Boolean.FALSE.equals(value)) return false;
        }
        if (options.when() == null) return true;
        Object[] out = options.when().call(new Object[] {JavaLibrary.wrap(call.self)});
        return out != null && out.length > 0 && out[0] != null && !Boolean.FALSE.equals(out[0]);
    }

    Object[] call(Object[] args) {
        Object[] out = handler.call(args);
        return out == null ? new Object[0] : out;
    }

    Object script(Object value) {
        return options.raw() ? JavaLibrary.wrap(value) : Conversions.toScript(value);
    }

    public String label() {
        return label;
    }

    public String file() {
        return file;
    }

    public boolean client() {
        return place.host.client();
    }

    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean on) {
        enabled = on;
    }

    public long calls() {
        long total = 0;
        for (Piece piece : pieces) total += piece.calls();
        return total;
    }

    public long nanos() {
        long total = 0;
        for (Piece piece : pieces) total += piece.nanos();
        return total;
    }

    public long errors() {
        long total = 0;
        for (Piece piece : pieces) total += piece.errors();
        return total;
    }

    public synchronized void remove() {
        if (removed) return;
        removed = true;
        place.hooks.remove(this);
        try {
            undo.run();
        } finally {
            release();
        }
    }

    void release() {
        if (handler != null) handler.release();
        if (options.when() != null) options.when().release();
    }

    @Override
    public String typeName() {
        return "Mixin";
    }

    @Override
    public Object get(String key) {
        return switch (key) {
            case "label" -> label;
            case "methods" -> (double) methods;
            case "calls" -> (double) calls();
            case "errors" -> (double) errors();
            case "enabled" -> enabled;
            case "remove" -> REMOVE;
            default -> throw new HostError("Mixin has no member '%s'", key);
        };
    }

    @Override
    public void set(String key, Object value) {
        if (!key.equals("enabled")) throw new HostError("Mixin.%s cannot be assigned", key);
        enabled = value != null && !Boolean.FALSE.equals(value);
    }

    private static final Builtin REMOVE = new Builtin("Mixin:remove", a -> {
        a.self(MixinHook.class).remove();
        return null;
    });
}
