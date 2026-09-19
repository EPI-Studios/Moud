package com.meekdev.moud.script.host;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class Members implements HostObject {

    private record Field(Supplier<Object> getter, Consumer<Object> setter) {}

    private record Bound(Function<Object, Object> getter) {}

    private final String type;
    private final Map<String, Object> entries = new LinkedHashMap<>();
    private final List<Api.Member> declared = new ArrayList<>();

    public Members(String type) {
        this.type = type;
    }

    @Override
    public String typeName() {
        return type;
    }

    public Members method(String name, String signature, HostFunction body) {
        entries.put(name, new Builtin(type + ":" + name, body));
        declared.add(new Api.Member(name, Api.Kind.METHOD, signature));
        return this;
    }

    public Members function(String name, String signature, HostFunction body) {
        entries.put(name, new Builtin(type + "." + name, body));
        declared.add(new Api.Member(name, Api.Kind.FUNCTION, signature));
        return this;
    }

    public Members field(String name, String fieldType, Supplier<Object> getter) {
        return field(name, fieldType, getter, null);
    }

    public Members field(String name, String fieldType, Supplier<Object> getter, Consumer<Object> setter) {
        entries.put(name, new Field(getter, setter));
        declared.add(new Api.Member(name, Api.Kind.FIELD, fieldType, setter == null));
        return this;
    }

    public Members bound(String name, String fieldType, Function<Object, Object> getter) {
        entries.put(name, new Bound(getter));
        declared.add(new Api.Member(name, Api.Kind.FIELD, fieldType, true));
        return this;
    }

    public Members declareMethod(String name, String signature) {
        declared.add(new Api.Member(name, Api.Kind.METHOD, signature));
        return this;
    }

    public Members declare(String name, String fieldType) {
        declared.add(new Api.Member(name, Api.Kind.FIELD, fieldType));
        return this;
    }

    public Members value(String name, String fieldType, Object value) {
        return field(name, fieldType, () -> value);
    }

    public Set<String> names() {
        return entries.keySet();
    }

    public boolean has(String name) {
        return entries.containsKey(name);
    }

    @Override
    public Object get(String key) {
        Object entry = entries.get(key);
        if (entry == null) throw new HostError("%s has no member '%s'", type, key);
        if (entry instanceof Bound) throw new HostError("%s.%s is read on an instance", type, key);
        return entry instanceof Field field ? field.getter().get() : entry;
    }

    public Object get(Object self, String key) {
        return entries.get(key) instanceof Bound bound ? bound.getter().apply(self) : get(key);
    }

    @Override
    public void set(String key, Object value) {
        if (!(entries.get(key) instanceof Field field) || field.setter() == null) {
            throw new HostError("%s.%s cannot be assigned", type, key);
        }
        field.setter().accept(value);
    }

    public Api.Decl decl() {
        return new Api.Decl(type, null, List.copyOf(declared));
    }
}
