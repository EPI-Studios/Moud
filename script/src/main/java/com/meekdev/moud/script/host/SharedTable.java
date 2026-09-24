package com.meekdev.moud.script.host;

import com.meekdev.moud.core.instance.Instance;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SharedTable implements HostObject {

    private static final double LARGEST_KEY = 4294967295.0;

    static final Builtin NEXT = new Builtin("SharedTable.next", a -> {
        Snapshot snapshot = (Snapshot) a.get(0);
        int position = a.has(1) ? snapshot.after(a.get(1)) : 0;
        if (position >= snapshot.keys.size()) return null;
        return Results.of(snapshot.keys.get(position), snapshot.values.get(position));
    }, true);

    private final Map<Object, Object> entries = new LinkedHashMap<>();
    private final boolean frozen;

    public SharedTable() {
        this(false);
    }

    private SharedTable(boolean frozen) {
        this.frozen = frozen;
    }

    static SharedTable from(Object data) {
        SharedTable table = new SharedTable();
        switch (data) {
            case null -> { }
            case List<?> list -> {
                for (int index = 0; index < list.size(); index++) {
                    Object value = value(list.get(index));
                    if (value != null) table.entries.put((double) (index + 1), value);
                }
            }
            case Map<?, ?> map -> {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object value = value(entry.getValue());
                    if (value != null) table.entries.put(key(entry.getKey()), value);
                }
            }
            case SharedTable other -> table.entries.putAll(other.entriesCopy());
            default -> throw new HostError("SharedTable expects a table, got %s", Host.typeOf(data));
        }
        return table;
    }

    public synchronized Object at(Object key) {
        return entries.get(key(key));
    }

    public void put(Object key, Object value) {
        Object tableKey = key(key);
        Object tableValue = value(value);
        synchronized (this) {
            writable();
            if (tableValue == null) entries.remove(tableKey);
            else entries.put(tableKey, tableValue);
        }
    }

    synchronized int size() {
        return entries.size();
    }

    synchronized void clear() {
        writable();
        entries.clear();
    }

    boolean frozen() {
        return frozen;
    }

    SharedTable copy(boolean deep, boolean freeze) {
        return copy(deep, freeze, new IdentityHashMap<>());
    }

    private SharedTable copy(boolean deep, boolean freeze, Map<SharedTable, SharedTable> done) {
        SharedTable copied = new SharedTable(freeze);
        done.put(this, copied);
        for (Map.Entry<Object, Object> entry : entriesCopy().entrySet()) {
            Object value = entry.getValue();
            if (deep && value instanceof SharedTable inner) {
                SharedTable seen = done.get(inner);
                value = seen != null ? seen : inner.copy(true, freeze, done);
            }
            copied.entries.put(entry.getKey(), value);
        }
        return copied;
    }

    private synchronized Map<Object, Object> entriesCopy() {
        return new LinkedHashMap<>(entries);
    }

    synchronized double increment(Object key, double delta) {
        writable();
        Object tableKey = key(key);
        if (!(entries.get(tableKey) instanceof Double old)) {
            throw new HostError("SharedTable.increment needs a number at %s", Values.text(tableKey));
        }
        entries.put(tableKey, old + delta);
        return old;
    }

    void update(Object key, Callable fn) {
        Object tableKey = key(key);
        while (true) {
            Object old;
            synchronized (this) {
                writable();
                old = entries.get(tableKey);
            }
            Object[] returned = fn.call(old);
            Object next = value(returned == null || returned.length == 0 ? null : returned[0]);
            synchronized (this) {
                writable();
                if (entries.get(tableKey) != old) continue;
                if (next == null) entries.remove(tableKey);
                else entries.put(tableKey, next);
                return;
            }
        }
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(new ArrayList<>(entries.keySet()), new ArrayList<>(entries.values()));
    }

    private void writable() {
        if (frozen) throw new HostError("this SharedTable is frozen");
    }

    static Object key(Object key) {
        return switch (key) {
            case String text -> text;
            case Number number -> numberKey(number);
            default -> throw new HostError("SharedTable keys are strings or numbers, got %s", Host.typeOf(key));
        };
    }

    private static double numberKey(Number number) {
        double whole = number.doubleValue();
        if (whole < 0 || whole > LARGEST_KEY || whole != Math.floor(whole)) {
            throw new HostError("SharedTable number keys are whole numbers from 0, got %s", Values.text(number));
        }
        return whole;
    }

    static Object value(Object value) {
        return switch (value) {
            case null -> null;
            case Boolean b -> b;
            case Number n -> n.doubleValue();
            case String s -> s;
            case Instance i -> i;
            case SharedTable t -> t;
            case List<?> l -> from(l);
            case Map<?, ?> m -> from(m);
            default -> {
                if (Values.isValue(value)) yield value;
                throw new HostError("a SharedTable cannot hold a %s", Host.typeOf(value));
            }
        };
    }

    @Override
    public String typeName() {
        return "SharedTable";
    }

    @Override
    public Object get(String key) {
        return at(key);
    }

    @Override
    public void set(String key, Object value) {
        put(key, value);
    }

    static final class Snapshot implements HostObject {
        private final List<Object> keys;
        private final List<Object> values;
        private Map<Object, Integer> positions;

        Snapshot(List<Object> keys, List<Object> values) {
            this.keys = keys;
            this.values = values;
        }

        int after(Object key) {
            if (positions == null) {
                positions = new HashMap<>();
                for (int position = 0; position < keys.size(); position++) positions.put(keys.get(position), position);
            }
            Object lookup = key instanceof Number number ? number.doubleValue() : key;
            Integer position = positions.get(lookup);
            return position == null ? keys.size() : position + 1;
        }

        @Override
        public String typeName() {
            return "SharedTableIterator";
        }

        @Override
        public Object get(String key) {
            throw new HostError("SharedTableIterator has no member '%s'", key);
        }
    }
}
