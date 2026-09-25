package com.meekdev.moud.script.host.data;

import com.meekdev.moud.core.scene.Json;
import com.meekdev.moud.script.api.StoreRef;
import com.meekdev.moud.script.host.Args;
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
    private static final String ORDERED = "ordered:";
    private static final int PAGE = 100;
    private static final double LARGEST = 1.0e300;

    private StoreLibrary() {}

    public static void install(Host host) {
        StoreRef store = host.store();
        if (store == null) {
            host.global("orderedStore", "(name: string) -> OrderedStore", new Builtin("orderedStore", a -> {
                throw new HostError("orderedStore runs on the server");
            }));
            host.global("store", "(name: string) -> Store", new Builtin("store", a -> {
                throw new HostError("store runs on the server");
            }));
            return;
        }
        host.api().declare(new Members("Store")
                .method("get", "(key: string | number) -> any", a -> null)
                .method("set", "(key: string | number, value: any) -> ()", a -> null)
                .method("remove", "(key: string | number) -> ()", a -> null)
                .method("update", "(key: string | number, change: (old: any) -> any) -> any", a -> null)
                .method("keys", "(prefix: string?, limit: number?) -> { string }", a -> null)
                .method("session", "(key: string | number) -> StoreSession", a -> null).decl());
        host.api().declare(new Members("StoreSession")
                .declare("data", "{ [string]: any }")
                .method("save", "() -> ()", a -> null)
                .method("release", "() -> ()", a -> null).decl());
        host.api().declare(new Members("OrderedStore")
                .method("get", "(key: string | number) -> number?", a -> null)
                .method("set", "(key: string | number, value: number) -> ()", a -> null)
                .method("increment", "(key: string | number, delta: number?) -> number", a -> null)
                .method("remove", "(key: string | number) -> ()", a -> null)
                .method("getSorted", "(ascending: boolean, limit: number?, min: number?, max: number?) -> { { key: string, value: number } }", a -> null).decl());
        host.global("orderedStore", "(name: string) -> OrderedStore", new Builtin("orderedStore", a -> {
            String name = a.string(0);
            if (name.isEmpty()) throw a.error("orderedStore wants a name");
            return ordered(host, store, ORDERED + name);
        }));
        host.global("store", "(name: string) -> Store", new Builtin("store", a -> {
            String name = a.string(0);
            if (name.isEmpty()) throw a.error("store wants a name");
            if (name.startsWith(ORDERED)) throw a.error("a store name cannot start with '%s', that is where ordered stores keep their numbers", ORDERED);
            return store(host, store, name);
        }));
    }

    private static Members store(Host host, StoreRef store, String name) {
        return new Members("Store")
                .method("get", "(key: string | number) -> any", a -> decode(store.get(name, key(host, a))))
                .method("set", "(key: string | number, value: any) -> ()", a -> {
                    store.set(name, key(host, a), a.get(2) == null ? null : encode(a.get(2)));
                    return null;
                })
                .method("remove", "(key: string | number) -> ()", a -> {
                    store.set(name, key(host, a), null);
                    return null;
                })
                .method("update", "(key: string | number, change: (old: any) -> any) -> any", a -> {
                    Callable change = a.callable(2);
                    return decode(store.update(name, key(host, a), old -> {
                        Object[] out = change.call(decode(old));
                        Object next = out == null || out.length == 0 ? null : out[0];
                        return next == null ? null : encode(next);
                    }));
                })
                .method("keys", "(prefix: string?, limit: number?) -> { string }",
                        a -> new ArrayList<Object>(store.keys(name, a.string(1, ""), a.integer(2, 1000))))
                .method("session", "(key: string | number) -> StoreSession", a -> session(host, store, name, key(host, a)));
    }

    private static String key(Host host, Args a) {
        Object key = a.get(1);
        if (key instanceof String || key instanceof Number) return host.text(key);
        throw a.error("keys are text or numbers, not %s", Host.typeOf(key));
    }

    private static Members ordered(Host host, StoreRef store, String name) {
        return new Members("OrderedStore")
                .method("get", "(key: string | number) -> number?", a -> number(store.get(name, key(host, a))))
                .method("set", "(key: string | number, value: number) -> ()", a -> {
                    store.set(name, key(host, a), encode(finite(a.number(2))));
                    return null;
                })
                .method("increment", "(key: string | number, delta: number?) -> number", a -> {
                    double delta = finite(a.number(2, 1));
                    String next = store.update(name, key(host, a), old -> {
                        Double was = number(old);
                        return encode((was == null ? 0 : was) + delta);
                    });
                    return number(next);
                })
                .method("remove", "(key: string | number) -> ()", a -> {
                    store.set(name, key(host, a), null);
                    return null;
                })
                .method("getSorted", "(ascending: boolean, limit: number?, min: number?, max: number?) -> { { key: string, value: number } }", a -> {
                    List<Object> rows = new ArrayList<>();
                    int limit = Math.clamp(a.integer(2, PAGE), 0, 1000);
                    for (StoreRef.Ranked ranked : store.sorted(name, Boolean.TRUE.equals(a.get(1)), limit, a.number(3, -LARGEST), a.number(4, LARGEST))) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("key", ranked.key());
                        row.put("value", ranked.value());
                        rows.add(row);
                    }
                    return rows;
                });
    }

    private static Double number(String json) {
        Object value = decode(json);
        return value instanceof Number n ? n.doubleValue() : null;
    }

    private static double finite(double value) {
        if (!Double.isFinite(value)) throw new HostError("an ordered store only keeps finite numbers");
        return value;
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
