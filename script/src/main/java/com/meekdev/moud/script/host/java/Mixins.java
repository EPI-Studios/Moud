package com.meekdev.moud.script.host.java;

import com.meekdev.moud.script.host.Args;
import com.meekdev.moud.script.host.Callable;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostObject;
import com.meekdev.moud.script.host.Members;
import com.meekdev.moud.script.mixin.Dispatch;
import com.meekdev.moud.script.mixin.Injections;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class Mixins {

    private record Placed(Method method, Dispatch.Hook hook) {}

    private record MixinCall(Dispatch.Call call) implements HostObject {

        private static final Members METHODS = new Members("MixinCall")
                .declare("returning", "boolean")
                .declare("cancelled", "boolean")
                .method("cancel", "(result: any?) -> ()", a -> {
                    Dispatch.Call c = call(a);
                    if (c.returning) throw new HostError("cannot cancel at return, use setReturn");
                    c.cancel(a.has(1) ? value(a.get(1), c.returns) : null);
                    return null;
                })
                .method("setReturn", "(value: any) -> ()", a -> {
                    Dispatch.Call c = call(a);
                    if (c.returns == void.class) throw new HostError("this method returns nothing");
                    c.value(value(a.get(1), c.returns));
                    return null;
                })
                .method("getReturn", "() -> any", a -> JavaLibrary.wrap(call(a).value()))
                .method("getArg", "(index: number) -> any", a -> {
                    Dispatch.Call c = call(a);
                    return JavaLibrary.wrap(c.args[slot(a, c)]);
                })
                .method("setArg", "(index: number, value: any) -> ()", a -> {
                    Dispatch.Call c = call(a);
                    if (c.returning) throw new HostError("cannot change arguments at return");
                    int n = slot(a, c);
                    c.args[n] = value(a.get(2), c.parameters[n]);
                    return null;
                });

        private static Dispatch.Call call(Args a) {
            return a.self(MixinCall.class).call();
        }

        @Override
        public String typeName() {
            return "MixinCall";
        }

        @Override
        public Object get(String key) {
            return switch (key) {
                case "returning" -> call.returning;
                case "cancelled" -> call.cancelled();
                default -> METHODS.get(key);
            };
        }
    }

    private Mixins() {}

    static void install(Host host) {
        host.api().declare(MixinCall.METHODS.decl());
        host.api().declare(new Members("Mixin").declare("methods", "number").method("remove", "() -> ()", a -> null).decl());
        List<Runnable> removers = new ArrayList<>();
        host.onClose(() -> new ArrayList<>(removers).forEach(Runnable::run));
        Members mixin = new Members("MixinService")
                .function("inject", "(className: string, method: string, at: \"head\" | \"return\", handler: (self: any, info: MixinCall, ...any) -> (), "
                        + "options: { descriptor: string?, params: number? }?) -> Mixin", a -> inject(host, a, removers));
        host.global("mixin", "MixinService", mixin);
        host.declare(mixin);
    }

    private static Object inject(Host host, Args a, List<Runnable> removers) {
        String className = a.string(0);
        String name = a.string(1);
        boolean head = switch (a.string(2)) {
            case "head" -> true;
            case "return", "tail" -> false;
            default -> throw new HostError("invalid injection point '%s', expected head or return", a.string(2));
        };
        if (!(a.get(3) instanceof Callable handler)) throw new HostError("mixin.inject expects a function as the fourth argument");
        Map<String, Object> options = a.map(4, Map.of());
        String descriptor = options.get("descriptor") instanceof String d ? d : null;
        int params = options.get("params") instanceof Number n ? n.intValue() : -1;

        Class<?> type = JavaLibrary.load(className);
        List<Method> matched = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.isBridge() || method.isSynthetic()) continue;
            if (params >= 0 && method.getParameterCount() != params) continue;
            if (descriptor != null && !Injections.id(method).endsWith(descriptor)) continue;
            matched.add(method);
        }
        if (matched.isEmpty()) {
            List<String> near = new ArrayList<>();
            for (Class<?> c = type.getSuperclass(); c != null; c = c.getSuperclass()) {
                for (Method method : c.getDeclaredMethods()) {
                    if (method.getName().equals(name)) near.add(c.getName());
                }
            }
            throw new HostError(near.isEmpty()
                    ? String.format("%s declares no method '%s'", className, name)
                    : String.format("%s declares no method '%s', it inherits it: hook %s instead", className, name, String.join(" or ", near)));
        }

        Callable kept = handler.retain();
        Thread owner = Thread.currentThread();
        List<Placed> placed = new ArrayList<>();
        for (Method method : matched) {
            Dispatch.Hook hook = hook(host, kept, owner, method);
            try {
                Injections.add(method, hook, head);
            } catch (RuntimeException e) {
                for (Placed done : placed) Injections.remove(done.method(), done.hook());
                kept.release();
                throw new HostError("could not hook %s: %s", method, e.getMessage());
            }
            placed.add(new Placed(method, hook));
        }
        boolean[] removed = {false};
        Runnable remove = () -> {
            if (removed[0]) return;
            removed[0] = true;
            for (Placed one : placed) Injections.remove(one.method(), one.hook());
            kept.release();
        };
        removers.add(remove);
        return new Members("Mixin")
                .value("methods", "number", (double) placed.size())
                .method("remove", "() -> ()", x -> {
                    remove.run();
                    removers.remove(remove);
                    return null;
                });
    }

    private static Dispatch.Hook hook(Host host, Callable handler, Thread owner, Method method) {
        String where = "mixin " + method.getDeclaringClass().getSimpleName() + "." + method.getName();
        return new Dispatch.Hook() {
            @Override
            public Thread owner() {
                return owner;
            }

            @Override
            public void run(Dispatch.Call call) {
                Object[] args = new Object[2 + call.args.length];
                args[0] = JavaLibrary.wrap(call.self);
                args[1] = new MixinCall(call);
                for (int n = 0; n < call.args.length; n++) args[2 + n] = JavaLibrary.wrap(call.args[n]);
                host.call(handler, where, args);
            }
        };
    }

    private static int slot(Args a, Dispatch.Call call) {
        int n = a.integer(1) - 1;
        if (n < 0 || n >= call.args.length) {
            throw new HostError("argument index out of range (%d arguments, got %d)", call.args.length, n + 1);
        }
        return n;
    }

    private static Object value(Object value, Class<?> type) {
        Object converted = JavaLibrary.convert(value, type);
        if (converted == JavaLibrary.MISMATCH) throw new HostError("that can not be a %s", type.getSimpleName());
        return converted;
    }
}
