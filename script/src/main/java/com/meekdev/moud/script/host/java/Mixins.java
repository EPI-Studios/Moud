package com.meekdev.moud.script.host.java;

import com.meekdev.moud.script.host.Args;
import com.meekdev.moud.script.host.Builtin;
import com.meekdev.moud.script.host.Callable;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.Invocable;
import com.meekdev.moud.script.host.Members;
import com.meekdev.moud.script.host.Results;
import com.meekdev.moud.script.mixin.Dispatch;
import com.meekdev.moud.script.mixin.Injections;
import com.meekdev.moud.script.mixin.Rewrite;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.bytebuddy.jar.asm.Type;

public final class Mixins {

    static final class Place {
        final Host host;
        final Thread owner;
        final Map<String, MixinState> states = new ConcurrentHashMap<>();
        final List<MixinHook> hooks = new CopyOnWriteArrayList<>();
        final List<MixinGroup> groups = new CopyOnWriteArrayList<>();
        final List<MixinHook> finished = new CopyOnWriteArrayList<>();
        String file;

        Place(Host host, Thread owner) {
            this.host = host;
            this.owner = owner;
        }

        void settle() {
            for (MixinHook hook : hooks) hook.tick = 0;
            if (finished.isEmpty()) return;
            List<MixinHook> done = new ArrayList<>(finished);
            finished.clear();
            done.forEach(MixinHook::remove);
        }
    }

    interface Body {
        Object run(MixinHook hook, Dispatch.Call call) throws Throwable;
    }

    private static final Map<Host, Place> PLACES = new ConcurrentHashMap<>();

    private Mixins() {}

    static void install(Host host) {
        Place place = new Place(host, Thread.currentThread());
        PLACES.put(host, place);
        host.onStep(dt -> place.settle());
        host.onClose(() -> {
            PLACES.remove(host);
            new ArrayList<>(place.hooks).forEach(MixinHook::remove);
            place.groups.clear();
        });
        host.api().declare(new Members("Mixin")
                .declare("label", "string").declare("methods", "number").declare("calls", "number").declare("errors", "number")
                .declare("enabled", "boolean").declareMethod("remove", "() -> ()").decl());
        host.api().declare(new Members("MixinGroup")
                .declare("name", "string").declare("enabled", "boolean").declare("hooks", "{ Mixin }").declareMethod("remove", "() -> ()").decl());
        host.api().declare(new Members("MixinState").declareMethod("ref", "(key: string) -> MixinStateRef").decl());
        host.api().declare(new Members("MixinStateRef").declare("key", "string").declare("value", "any").decl());
        String options = "{ when: ((...any) -> boolean)?, once: boolean?, limit: number?, priority: number?, raw: boolean?, ordinal: number? }?";
        host.api().declare(new Members("JavaMethod")
                .declare("name", "string")
                .declareMethod("before", "(handler: (self: any, ...any) -> any, options: " + options + ") -> Mixin")
                .declareMethod("after", "(handler: (self: any, result: any, ...any) -> any, options: " + options + ") -> Mixin")
                .declareMethod("replace", "(handler: (original: (...any) -> any, self: any, ...any) -> any, options: " + options + ") -> Mixin")
                .declareMethod("args", "(handler: (...any) -> ...any, options: " + options + ") -> Mixin")
                .declareMethod("redirect", "(call: JavaMethod, handler: (original: (...any) -> any, target: any, ...any) -> any, options: " + options + ") -> Mixin")
                .declareMethod("constant", "(value: number | string, replacement: any, options: " + options + ") -> Mixin")
                .declareMethod("variable", "(local: string | number, replacement: any, options: " + options + ") -> Mixin")
                .declareMethod("overload", "(...string | number) -> JavaMethod")
                .declareMethod("__call", "(...any) -> any")
                .decl());
        host.api().declare(new Members("JavaField")
                .declare("name", "string")
                .declare("type", "string")
                .declareMethod("changed", "(handler: (self: any, old: any, new: any) -> any, options: " + options + ") -> Mixin")
                .decl());
        Members mixin = new Members("MixinService")
                .function("state", "(name: string | { [string]: any }, defaults: { [string]: any }?) -> MixinState", a -> state(place, a))
                .function("hooks", "() -> { Mixin }", a -> new ArrayList<Object>(place.hooks))
                .declareMethod("__call", "(name: string) -> ({ [any]: any }) -> MixinGroup");
        host.global("mixin", "MixinService", new Invocable() {
            @Override
            public Object invoke(Args a) {
                String name = a.string(0);
                return new Builtin("mixin " + name, group -> MixinGroup.of(place, name, group.get(0)));
            }

            @Override
            public String typeName() {
                return "MixinService";
            }

            @Override
            public Object get(String key) {
                return mixin.get(key);
            }
        });
        host.declare(mixin);
    }

    public static List<MixinHook> live() {
        List<MixinHook> out = new ArrayList<>();
        for (Place place : PLACES.values()) out.addAll(place.hooks);
        return out;
    }

    public static void loading(Host host, String file, Runnable body) {
        Place place = PLACES.get(host);
        if (place == null) {
            body.run();
            return;
        }
        String before = place.file;
        place.file = file;
        try {
            body.run();
        } finally {
            place.file = before;
        }
    }

    public static void unload(Host host, String file) {
        Place place = PLACES.get(host);
        if (place == null) return;
        for (MixinHook hook : place.hooks) if (file.equals(hook.file)) hook.remove();
        place.groups.removeIf(group -> file.equals(group.file));
    }

    public static Map<String, MixinState> states(Host host) {
        Place place = PLACES.get(host);
        return place == null ? Map.of() : place.states;
    }

    private static Object state(Place place, Args a) {
        if (a.get(0) instanceof String name) {
            MixinState state = place.states.computeIfAbsent(name, MixinState::new);
            state.fill(a.map(1, Map.of()));
            return state;
        }
        MixinState state = new MixinState("");
        state.fill(a.map(0, Map.of()));
        return state;
    }

    private static Place place(Args a) {
        Place place = PLACES.get(a.host());
        if (place == null) throw new HostError("mixins are not available here");
        return place;
    }

    static Object method(Args a, Dispatch.Kind kind) {
        JavaMethod target = a.self(JavaMethod.class);
        Callable handler = a.callable(1);
        MixinHook.Options options = MixinHook.Options.parse(a, 2);
        Place place = place(a);
        MixinHook hook = new MixinHook(place, target.type().getSimpleName() + "." + target.name() + ":" + kind.name().toLowerCase(), kind, options,
                handler.retain());
        Body body = switch (kind) {
            case ARGS -> (self, call) -> {
                Object[] out = self.call(scriptArgs(self, call, false, false));
                for (int n = 0; n < Math.min(out.length, call.args.length); n++) {
                    if (out[n] != null) call.args[n] = java(out[n], call.parameters[n]);
                }
                return Dispatch.PROCEED;
            };
            case BEFORE -> (self, call) -> {
                Object[] out = self.call(scriptArgs(self, call, true, false));
                if (out.length == 0 || out[0] == null) return Dispatch.PROCEED;
                return java(out[0], call.returns);
            };
            case AFTER -> (self, call) -> {
                Object[] out = self.call(scriptArgs(self, call, true, true));
                if (out.length == 0 || out[0] == null) return Dispatch.PROCEED;
                return java(out[0], call.returns);
            };
            default -> (self, call) -> {
                Object[] given = scriptArgs(self, call, true, false);
                Object[] out = self.call(prepend(original(self, call), given));
                return out.length == 0 || out[0] == null ? null : java(out[0], call.returns);
            };
        };
        place(hook, target.declared(), method -> {
            MixinHook.Piece piece = hook.piece(kind, body);
            Injections.hook(method, piece);
            return () -> Injections.unhook(method, piece);
        });
        return hook;
    }

    static Object redirect(Args a) {
        JavaMethod target = a.self(JavaMethod.class);
        if (!(a.get(1) instanceof JavaMethod callee)) throw a.error("argument 2 expects the method whose call to redirect, like Other.method");
        Callable handler = a.callable(2);
        MixinHook.Options options = MixinHook.Options.parse(a, 3);
        MixinHook hook = new MixinHook(place(a), target.type().getSimpleName() + "." + target.name() + ":redirect " + callee.type().getSimpleName() + "." + callee.name(),
                Dispatch.Kind.REDIRECT, options, handler.retain());
        Body body = (self, call) -> {
            Object[] given = scriptArgs(self, call, true, false);
            Object[] out = self.call(prepend(original(self, call), given));
            return out.length == 0 || out[0] == null ? null : java(out[0], call.returns);
        };
        List<Method> calls = callee.all();
        place(hook, target.declared(), method -> {
            List<Runnable> undo = new ArrayList<>();
            RuntimeException first = null;
            for (Method called : calls) {
                Rewrite rewrite = new Rewrite.Redirect(Type.getInternalName(called.getDeclaringClass()), called.getName(),
                        Type.getMethodDescriptor(called), Modifier.isStatic(called.getModifiers()), options.ordinal());
                MixinHook.Piece piece = hook.piece(Dispatch.Kind.REDIRECT, body);
                try {
                    String id = Injections.rewrite(method, rewrite, called, piece);
                    undo.add(() -> Injections.unrewrite(id, piece));
                } catch (RuntimeException e) {
                    hook.pieces.remove(piece);
                    if (first == null) first = e;
                }
            }
            if (undo.isEmpty()) throw first;
            return () -> undo.forEach(Runnable::run);
        });
        return hook;
    }

    static Object constant(Args a) {
        JavaMethod target = a.self(JavaMethod.class);
        Object value = a.get(1);
        if (!(value instanceof Number) && !(value instanceof String)) throw a.error("argument 1 expects the number or text to replace");
        Object replacement = a.get(2);
        MixinHook.Options options = MixinHook.Options.parse(a, 3);
        MixinHook hook = new MixinHook(place(a), target.type().getSimpleName() + "." + target.name() + ":constant " + value, Dispatch.Kind.CONSTANT, options,
                replacement instanceof Callable c ? c.retain() : null);
        Body body = replacer(replacement);
        Rewrite rewrite = new Rewrite.Constant(value, options.ordinal());
        place(hook, target.declared(), method -> {
            MixinHook.Piece piece = hook.piece(Dispatch.Kind.CONSTANT, body);
            String id = Injections.rewrite(method, rewrite, null, piece);
            return () -> Injections.unrewrite(id, piece);
        });
        return hook;
    }

    static Object variable(Args a) {
        JavaMethod target = a.self(JavaMethod.class);
        Object local = a.get(1);
        Object replacement = a.get(2);
        MixinHook.Options options = MixinHook.Options.parse(a, 3);
        MixinHook hook = new MixinHook(place(a), target.type().getSimpleName() + "." + target.name() + ":variable " + a.host().text(local), Dispatch.Kind.VARIABLE,
                options, replacement instanceof Callable c ? c.retain() : null);
        Body body = replacer(replacement);
        place(hook, target.declared(), method -> {
            Rewrite rewrite = variable(method, local, options.ordinal());
            MixinHook.Piece piece = hook.piece(Dispatch.Kind.VARIABLE, body);
            String id = Injections.rewrite(method, rewrite, null, piece);
            return () -> Injections.unrewrite(id, piece);
        });
        return hook;
    }

    static Object field(Args a) {
        Field field = a.self(JavaField.class).field();
        Callable handler = a.callable(1);
        MixinHook.Options options = MixinHook.Options.parse(a, 2);
        MixinHook hook = new MixinHook(place(a), field.getDeclaringClass().getSimpleName() + ".fields." + field.getName() + ":changed",
                Dispatch.Kind.FIELD, options, handler.retain());
        Body body = (self, call) -> {
            Object[] out = self.call(new Object[] {JavaLibrary.wrap(call.self), self.script(call.args[0]), self.script(call.value)});
            return out.length == 0 || out[0] == null ? Dispatch.PROCEED : java(out[0], field.getType());
        };
        MixinHook.Piece piece = hook.piece(Dispatch.Kind.FIELD, body);
        String id;
        try {
            id = Injections.watch(field, piece);
        } catch (RuntimeException e) {
            hook.release();
            if (e instanceof HostError) throw e;
            throw new HostError("could not watch %s.%s: %s", field.getDeclaringClass().getSimpleName(), field.getName(), e.getMessage());
        }
        hook.methods = 1;
        hook.undo = () -> Injections.unwatch(id, piece);
        hook.place.hooks.add(hook);
        return hook;
    }

    private static Rewrite variable(Method method, Object local, int ordinal) {
        List<Injections.Local> locals = Injections.locals(method);
        if (local instanceof String name) {
            for (Injections.Local found : locals) {
                if (found.name().equals(name)) return new Rewrite.Variable(found.slot(), found.descriptor(), ordinal);
            }
            List<String> names = locals.stream().map(Injections.Local::name).distinct().toList();
            throw new HostError("%s.%s has no local '%s'%s", method.getDeclaringClass().getSimpleName(), method.getName(), name,
                    names.isEmpty() ? ", and its class keeps no local names: use the slot number" : ", it has " + String.join(", ", names));
        }
        if (!(local instanceof Number number)) throw new HostError("a local is picked by its name or its slot number");
        int slot = number.intValue();
        for (Injections.Local found : locals) {
            if (found.slot() == slot) return new Rewrite.Variable(slot, found.descriptor(), ordinal);
        }
        return new Rewrite.Variable(slot, null, ordinal);
    }

    private static Body replacer(Object replacement) {
        return switch (replacement) {
            case Callable fn -> (self, call) -> {
                Object[] out = self.call(new Object[] {self.script(call.value)});
                return out.length == 0 || out[0] == null ? Dispatch.PROCEED : java(out[0], call.value.getClass());
            };
            case MixinState.Ref ref -> (self, call) -> {
                Object value = ref.state().value(ref.key());
                return value == null ? Dispatch.PROCEED : java(value, call.value.getClass());
            };
            case null -> throw new HostError("a replacement is needed: a value, a state ref or a function");
            default -> {
                Object[] fixed = new Object[1];
                yield (self, call) -> {
                    if (fixed[0] == null || fixed[0].getClass() != call.value.getClass()) fixed[0] = java(replacement, call.value.getClass());
                    return fixed[0];
                };
            }
        };
    }

    private interface Placement {
        Runnable apply(Method method);
    }

    private static void place(MixinHook hook, List<Method> methods, Placement placement) {
        List<Runnable> undo = new ArrayList<>();
        for (Method method : methods) {
            try {
                undo.add(placement.apply(method));
            } catch (RuntimeException e) {
                undo.forEach(Runnable::run);
                hook.release();
                if (e instanceof HostError) throw e;
                throw new HostError("could not hook %s.%s: %s", method.getDeclaringClass().getSimpleName(), method.getName(), e.getMessage());
            }
        }
        hook.methods = methods.size();
        hook.undo = () -> undo.forEach(Runnable::run);
        hook.place.hooks.add(hook);
    }

    private static Object[] scriptArgs(MixinHook hook, Dispatch.Call call, boolean self, boolean result) {
        int offset = (self ? 1 : 0) + (result ? 1 : 0);
        Object[] out = new Object[offset + call.args.length];
        if (self) out[0] = JavaLibrary.wrap(call.self);
        if (result) out[self ? 1 : 0] = hook.script(call.value);
        for (int n = 0; n < call.args.length; n++) out[offset + n] = hook.script(call.args[n]);
        return out;
    }

    private static Builtin original(MixinHook hook, Dispatch.Call call) {
        return new Builtin("original", a -> {
            Object self = switch (a.get(0)) {
                case null -> call.self;
                case JavaLibrary.JavaObject object -> object.value();
                case Object other -> java(other, call.self == null ? Object.class : call.self.getClass());
            };
            Object[] args = call.args.clone();
            for (int n = 0; n < args.length; n++) {
                if (a.count() > n + 1) args[n] = java(a.get(n + 1), call.parameters[n]);
            }
            Object result = call.proceed(self, args);
            return call.returns == void.class ? new Results(new Object[0]) : hook.script(result);
        });
    }

    static Object java(Object value, Class<?> type) {
        if (type == void.class || value == null) return null;
        Object converted = JavaLibrary.convert(value, type);
        if (converted == JavaLibrary.MISMATCH) throw new HostError("%s can not be a %s", Host.typeOf(value), type.getSimpleName());
        return converted;
    }

    private static Object[] prepend(Object first, Object[] rest) {
        Object[] out = new Object[rest.length + 1];
        out[0] = first;
        System.arraycopy(rest, 0, out, 1, rest.length);
        return out;
    }
}
