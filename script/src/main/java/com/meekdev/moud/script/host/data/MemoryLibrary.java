package com.meekdev.moud.script.host.data;

import com.meekdev.moud.core.memory.MemoryQueue;
import com.meekdev.moud.core.memory.MemorySorted;
import com.meekdev.moud.core.memory.MemoryStores;
import com.meekdev.moud.core.scene.Json;
import com.meekdev.moud.script.api.MemoryRef;
import com.meekdev.moud.script.host.Args;
import com.meekdev.moud.script.host.Callable;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.Members;
import com.meekdev.moud.script.host.Results;
import com.meekdev.moud.script.host.Suspend;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MemoryLibrary {

    private static final String SERVER = "memory stores are used from a server Script";
    private static final int LONGEST_KEY = 128;
    private static final int LARGEST_VALUE = 32 * 1024;
    private static final int MOST_KEYS = 1000;
    private static final int MOST_RANGE = 200;
    private static final int MOST_READ = 100;
    private static final double INVISIBLE = 30;
    private static final String SORT_KEY = "(number | string)?";
    private static final String BOUND = "{ key: string?, sortKey: " + SORT_KEY + " }?";

    private MemoryLibrary() {}

    public static void install(Host host) {
        host.api().declare(hash(host, "").decl());
        host.api().declare(sorted(host, "").decl());
        host.api().declare(queue(host, "", INVISIBLE).decl());
        Members memory = new Members("MemoryStore")
                .method("hashMap", "(name: string) -> MemoryHashMap", a -> {
                    stores(host);
                    return hash(host, name(a.string(1)));
                })
                .method("sortedMap", "(name: string) -> MemorySortedMap", a -> {
                    stores(host);
                    return sorted(host, name(a.string(1)));
                })
                .method("queue", "(name: string, invisibilityTimeout: number?) -> MemoryQueue", a -> {
                    stores(host);
                    String name = name(a.string(1));
                    double invisible = a.number(2, INVISIBLE);
                    if (!(invisible > 0 && invisible <= MemoryStores.LONGEST)) {
                        throw new HostError("a queue hides what it hands out for more than 0 and up to %d seconds", (long) MemoryStores.LONGEST);
                    }
                    return queue(host, name, invisible);
                });
        host.global("memory", "MemoryStore", memory);
        host.declare(memory);
    }

    private static Members hash(Host host, String name) {
        return new Members("MemoryHashMap")
                .method("get", "(key: string) -> any?", a -> decode(stores(host).hash(name).get(key(a, 1))))
                .method("set", "(key: string, value: any, expiration: number) -> boolean", a -> {
                    String key = key(a, 1);
                    String value = encode(a.get(2));
                    stores(host).hash(name).set(key, value, expiration(a, 3));
                    return true;
                })
                .method("update", "(key: string, transform: (old: any?) -> any?, expiration: number) -> any?", a -> {
                    String key = key(a, 1);
                    Callable transform = a.callable(2);
                    double expiration = expiration(a, 3);
                    return decode(stores(host).hash(name).update(key, old -> {
                        Object next = first(transform.call(decode(old)), 0);
                        return next == null ? null : encode(next);
                    }, expiration));
                })
                .method("remove", "(key: string) -> ()", a -> {
                    stores(host).hash(name).remove(key(a, 1));
                    return null;
                })
                .method("list", "(pageSize: number?) -> { string }", a -> {
                    int size = a.integer(1, MOST_KEYS);
                    if (size < 1 || size > MOST_KEYS) throw new HostError("list takes a page of 1 to %d keys", MOST_KEYS);
                    return new ArrayList<Object>(stores(host).hash(name).keys(size));
                });
    }

    private static Members sorted(Host host, String name) {
        return new Members("MemorySortedMap")
                .method("get", "(key: string) -> (any?, " + SORT_KEY + ")", a -> {
                    MemorySorted.Item item = stores(host).sorted(name).get(key(a, 1));
                    return item == null ? null : Results.of(decode(item.value()), item.sortKey());
                })
                .method("set", "(key: string, value: any, expiration: number, sortKey: " + SORT_KEY + ") -> boolean", a -> {
                    String key = key(a, 1);
                    String value = encode(a.get(2));
                    double expiration = expiration(a, 3);
                    return stores(host).sorted(name).set(key, value, sortKey(a.get(4)), expiration);
                })
                .method("update", "(key: string, transform: (value: any?, sortKey: " + SORT_KEY + ") -> (any?, " + SORT_KEY + "), expiration: number) -> (any?, " + SORT_KEY + ")", a -> {
                    String key = key(a, 1);
                    Callable transform = a.callable(2);
                    double expiration = expiration(a, 3);
                    MemorySorted.Item item = stores(host).sorted(name).update(key, old -> {
                        Object[] out = old == null ? transform.call(null, null) : transform.call(decode(old.value()), old.sortKey());
                        Object next = first(out, 0);
                        return next == null ? null : new MemorySorted.Item(key, encode(next), sortKey(first(out, 1)));
                    }, expiration);
                    return item == null ? null : Results.of(decode(item.value()), item.sortKey());
                })
                .method("remove", "(key: string) -> ()", a -> {
                    stores(host).sorted(name).remove(key(a, 1));
                    return null;
                })
                .method("getRange", "(direction: \"ascending\" | \"descending\", count: number, exclusiveLowerBound: " + BOUND + ", exclusiveUpperBound: " + BOUND
                        + ") -> { { key: string, value: any, sortKey: " + SORT_KEY + " } }", a -> {
                    boolean ascending = switch (a.string(1)) {
                        case "ascending" -> true;
                        case "descending" -> false;
                        default -> throw new HostError("getRange goes \"ascending\" or \"descending\", not \"%s\"", a.string(1));
                    };
                    int count = a.integer(2);
                    if (count < 1 || count > MOST_RANGE) throw new HostError("getRange reads 1 to %d items, not %d", MOST_RANGE, count);
                    List<Object> rows = new ArrayList<>();
                    for (MemorySorted.Item item : stores(host).sorted(name).range(ascending, count, bound(a, 3), bound(a, 4))) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("key", item.key());
                        row.put("value", decode(item.value()));
                        if (item.sortKey() != null) row.put("sortKey", item.sortKey());
                        rows.add(row);
                    }
                    return rows;
                })
                .method("getSize", "() -> number", a -> (double) stores(host).sorted(name).size());
    }

    private static Members queue(Host host, String name, double invisible) {
        return new Members("MemoryQueue")
                .method("addAsync", "(value: any, expiration: number, priority: number?) -> ()", a -> {
                    String value = encode(a.get(1));
                    double expiration = expiration(a, 2);
                    double priority = a.number(3, 0);
                    if (!Double.isFinite(priority)) throw new HostError("a queue priority is a finite number");
                    stores(host).queue(name).add(value, expiration, priority);
                    return null;
                })
                .method("readAsync", "(count: number, allOrNothing: boolean?, waitTimeout: number?) -> ({ any }, string?)", a -> {
                    int count = a.integer(1);
                    if (count < 1 || count > MOST_READ) throw new HostError("readAsync reads 1 to %d items, not %d", MOST_READ, count);
                    boolean all = a.bool(2, false);
                    double timeout = a.number(3, -1);
                    if (timeout < 0 && timeout != -1) throw new HostError("waitTimeout is a number of seconds, or -1 to wait until items come");
                    Object[] now = read(host, name, count, all, invisible);
                    if (now != null || timeout == 0) return now == null ? Results.of(new ArrayList<>(), null) : Results.of(now);
                    double[] waited = {0};
                    return new Suspend(dt -> {
                        Object[] got = read(host, name, count, all, invisible);
                        if (got != null) return got;
                        waited[0] += dt;
                        if (timeout >= 0 && waited[0] >= timeout) return new Object[] {new ArrayList<>(), null};
                        return null;
                    });
                })
                .method("removeAsync", "(id: string) -> ()", a -> {
                    stores(host).queue(name).remove(a.string(1));
                    return null;
                })
                .method("getSize", "(excludeInvisible: boolean?) -> number", a -> (double) stores(host).queue(name).size(a.bool(1, false)));
    }

    private static Object[] read(Host host, String name, int count, boolean all, double invisible) {
        MemoryQueue.Read read = stores(host).queue(name).read(count, all, invisible);
        if (read == null) return null;
        List<Object> values = new ArrayList<>(read.values().size());
        for (String value : read.values()) values.add(decode(value));
        return new Object[] {values, read.id()};
    }

    private static MemoryStores stores(Host host) {
        MemoryRef ref = host.memory();
        MemoryStores stores = host.client() || ref == null ? null : ref.stores();
        if (stores == null) throw new HostError(SERVER);
        return stores;
    }

    private static String name(String name) {
        if (name.isEmpty() || name.length() > LONGEST_KEY) throw new HostError("a memory store needs a name of 1 to %d letters", LONGEST_KEY);
        return name;
    }

    private static String key(Args a, int at) {
        String key = a.string(at);
        if (key.isEmpty() || key.length() > LONGEST_KEY) throw new HostError("a memory key is 1 to %d letters long", LONGEST_KEY);
        return key;
    }

    private static double expiration(Args a, int at) {
        if (!(a.get(at) instanceof Number n)) throw new HostError("every memory write needs an expiration in seconds");
        double seconds = n.doubleValue();
        if (!(seconds > 0 && seconds <= MemoryStores.LONGEST)) {
            throw new HostError("an expiration is more than 0 and up to %d seconds (45 days), not %s", (long) MemoryStores.LONGEST, a.host().text(seconds));
        }
        return seconds;
    }

    private static Object sortKey(Object value) {
        return switch (value) {
            case null -> null;
            case Number n -> {
                if (!Double.isFinite(n.doubleValue())) throw new HostError("a sortKey number must be finite");
                yield n.doubleValue();
            }
            case String s -> {
                if (s.length() > LONGEST_KEY) throw new HostError("a sortKey string is up to %d letters long", LONGEST_KEY);
                yield s;
            }
            default -> throw new HostError("a sortKey is a number or a string, not a %s", Host.typeOf(value));
        };
    }

    private static MemorySorted.Bound bound(Args a, int at) {
        Object given = a.get(at);
        if (given == null) return null;
        if (!(given instanceof Map<?, ?> map)) throw new HostError("a range bound is a table like { key = ..., sortKey = ... }");
        Object key = map.get("key");
        if (key != null && !(key instanceof String)) throw new HostError("a range bound's key is a string");
        Object sortKey = sortKey(map.get("sortKey"));
        if (key == null && sortKey == null) throw new HostError("a range bound needs a key, a sortKey or both");
        return new MemorySorted.Bound((String) key, sortKey);
    }

    private static Object first(Object[] out, int at) {
        return out == null || out.length <= at ? null : out[at];
    }

    private static String encode(Object value) {
        if (value == null) throw new HostError("a memory store cannot keep nil, remove the key instead");
        String json = StoreLibrary.encode(value);
        if (json.getBytes(StandardCharsets.UTF_8).length > LARGEST_VALUE) throw new HostError("a memory value is up to 32 KB once encoded");
        return json;
    }

    private static Object decode(String json) {
        return json == null ? null : Json.parse(json);
    }
}
