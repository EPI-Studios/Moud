package com.meekdev.moud.script.host.data;

import com.meekdev.moud.core.scene.Json;
import com.meekdev.moud.script.api.StoreRef;
import com.meekdev.moud.script.host.Builtin;
import com.meekdev.moud.script.host.Callable;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.Members;
import com.meekdev.moud.script.host.ScriptValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StoreLibrary {

    private static final double LEASE = 60;
    private static final double RENEW = 20;
    private static final int DEEPEST = 32;

    private StoreLibrary() {}

    public static void install(Host host) {
        StoreRef store = host.store();
        if (store == null) return;
        host.api().declare(new Members("Store")
                .method("get", "(key: any) -> any", a -> null)
                .method("set", "(key: any, value: any) -> ()", a -> null)
                .method("remove", "(key: any) -> ()", a -> null)
                .method("update", "(key: any, change: (old: any) -> any) -> any", a -> null)
                .method("keys", "(prefix: string?, limit: number?) -> { string }", a -> null)
                .method("session", "(key: any) -> StoreSession", a -> null).decl());
        host.api().declare(new Members("StoreSession")
                .declare("data", "{ [string]: any }")
                .method("save", "() -> ()", a -> null)
                .method("release", "() -> ()", a -> null).decl());
        host.global("store", "(name: string) -> Store", new Builtin("store", a -> {
            String name = a.string(0);
            if (name.isEmpty()) throw a.error("store wants a name");
            return store(host, store, name);
        }));
    }

    private static Members store(Host host, StoreRef store, String name) {
        return new Members("Store")
                .method("get", "(key: any) -> any", a -> decode(store.get(name, host.text(a.get(1)))))
                .method("set", "(key: any, value: any) -> ()", a -> {
                    store.set(name, host.text(a.get(1)), a.get(2) == null ? null : encode(a.get(2)));
                    return null;
                })
                .method("remove", "(key: any) -> ()", a -> {
                    store.set(name, host.text(a.get(1)), null);
                    return null;
                })
                .method("update", "(key: any, change: (old: any) -> any) -> any", a -> {
                    Callable change = a.callable(2);
                    return decode(store.update(name, host.text(a.get(1)), old -> {
                        Object[] out = change.call(decode(old));
                        Object next = out == null || out.length == 0 ? null : out[0];
                        return next == null ? null : encode(next);
                    }));
                })
                .method("keys", "(prefix: string?, limit: number?) -> { string }",
                        a -> new ArrayList<Object>(store.keys(name, a.string(1, ""), a.integer(2, 1000))))
                .method("session", "(key: any) -> StoreSession", a -> session(host, store, name, host.text(a.get(1))));
    }

    private static Members session(Host host, StoreRef store, String name, String key) {
        if (!store.lease(name, key, store.owner(), LEASE)) {
            throw new HostError("another server holds %s/%s, which is the save it is still writing", name, key);
        }
        Object loaded = decode(store.get(name, key));
        @SuppressWarnings("unchecked")
        Map<String, Object> initial = loaded instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        ScriptValue data = host.engine().table(initial);
        boolean[] released = {false};
        double[] waited = {0};
        host.onStep(dt -> {
            if (released[0]) return;
            waited[0] += dt;
            if (waited[0] < RENEW) return;
            waited[0] = 0;
            store.lease(name, key, store.owner(), LEASE);
        });
        Runnable save = () -> {
            if (released[0]) throw new HostError("this session was released");
            store.set(name, key, encode(host.engine().read(data)));
        };
        Members session = new Members("StoreSession");
        session.value("data", "{ [string]: any }", data)
                .method("save", "() -> ()", a -> {
                    save.run();
                    return null;
                })
                .method("release", "() -> ()", a -> {
                    if (released[0]) return null;
                    save.run();
                    released[0] = true;
                    store.release(name, key, store.owner());
                    data.release();
                    return null;
                });
        return session;
    }

    private static Object decode(String json) {
        return json == null ? null : Json.parse(json);
    }

    public static String encode(Object value) {
        return Json.write(check(value, 0, "the value"));
    }

    private static Object check(Object value, int depth, String where) {
        if (depth > DEEPEST) throw new HostError("%s nests deeper than %d", where, DEEPEST);
        return switch (value) {
            case null -> null;
            case Boolean b -> b;
            case Number n -> {
                double d = n.doubleValue();
                if (Double.isNaN(d) || Double.isInfinite(d)) throw new HostError("%s: cannot save this number", where);
                yield d;
            }
            case String s -> s;
            case List<?> list -> {
                List<Object> out = new ArrayList<>(list.size());
                for (int n = 0; n < list.size(); n++) out.add(check(list.get(n), depth + 1, where + "[" + (n + 1) + "]"));
                yield out;
            }
            case Map<?, ?> map -> {
                LinkedHashMap<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) throw new HostError("%s has a key that is not text", where);
                    out.put(key, check(entry.getValue(), depth + 1, where + "." + key));
                }
                yield out;
            }
            default -> throw new HostError("%s: cannot save a %s", where, Host.typeOf(value));
        };
    }
}
