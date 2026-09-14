package com.meekdev.moud.script.host.java;

import com.meekdev.moud.script.host.Builtin;
import com.meekdev.moud.script.host.Callable;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MixinGroup implements HostObject {

    private static final Builtin REMOVE = new Builtin("MixinGroup:remove", a -> {
        a.self(MixinGroup.class).remove();
        return null;
    });

    final String name;
    final String file;
    private final Mixins.Place place;
    private final List<MixinHook> hooks = new ArrayList<>();
    private Callable when;
    private MixinState.Ref whenRef;
    volatile boolean enabled = true;

    private MixinGroup(Mixins.Place place, String name) {
        this.place = place;
        this.name = name;
        this.file = place.file;
    }

    static MixinGroup of(Mixins.Place place, String name, Object contents) {
        MixinGroup group = new MixinGroup(place, name);
        List<Object> values = new ArrayList<>();
        switch (contents) {
            case Map<?, ?> map -> {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    switch (String.valueOf(entry.getKey())) {
                        case "when" -> {
                            if (entry.getValue() instanceof Callable c) group.when = c.retain();
                            else if (entry.getValue() instanceof MixinState.Ref r) group.whenRef = r;
                            else throw new HostError("mixin %s: when expects a function or a state ref", name);
                        }
                        case "enabled" -> group.enabled = !Boolean.FALSE.equals(entry.getValue());
                        default -> values.add(entry.getValue());
                    }
                }
            }
            case List<?> list -> values.addAll(list);
            case null -> {}
            default -> throw new HostError("mixin %s expects a table of hooks", name);
        }
        for (Object value : values) {
            if (!(value instanceof MixinHook hook)) throw new HostError("mixin %s holds a %s, only hooks go in a mixin", name, place.host.text(value));
            hook.group = group;
            group.hooks.add(hook);
        }
        place.groups.removeIf(old -> old.name.equals(name) && Objects.equals(old.file, group.file));
        place.groups.add(group);
        return group;
    }

    boolean allowed() {
        if (whenRef != null) {
            Object value = whenRef.state().value(whenRef.key());
            if (value == null || Boolean.FALSE.equals(value)) return false;
        }
        if (when == null) return true;
        Object[] out = when.call();
        return out != null && out.length > 0 && out[0] != null && !Boolean.FALSE.equals(out[0]);
    }

    public String name() {
        return name;
    }

    public void remove() {
        hooks.forEach(MixinHook::remove);
        place.groups.remove(this);
        if (when != null) when.release();
    }

    @Override
    public String typeName() {
        return "MixinGroup";
    }

    @Override
    public Object get(String key) {
        return switch (key) {
            case "name" -> name;
            case "enabled" -> enabled;
            case "hooks" -> new ArrayList<Object>(hooks);
            case "remove" -> REMOVE;
            default -> throw new HostError("MixinGroup has no member '%s'", key);
        };
    }

    @Override
    public void set(String key, Object value) {
        if (!key.equals("enabled")) throw new HostError("MixinGroup.%s cannot be assigned", key);
        enabled = value != null && !Boolean.FALSE.equals(value);
    }
}
