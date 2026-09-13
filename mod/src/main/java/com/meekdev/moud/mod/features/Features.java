package com.meekdev.moud.mod.features;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Features {

    public record Switch(String key, int bit) {}

    private final Map<String, Switch> byKey = new HashMap<>();
    private final List<Switch> declared = new ArrayList<>();

    private volatile long on;

    public Features() {
        for (Feature f : Feature.values()) declare(f.key());
    }

    public Switch declare(String key) {
        String name = Feature.normalise(key);
        Switch existing = byKey.get(name);
        if (existing != null) return existing;
        if (declared.size() == Long.SIZE) {
            throw new IllegalStateException("no room for switch '" + key + "': "
                    + Long.SIZE + " is all the switch word holds");
        }
        Switch made = new Switch(key, declared.size());
        byKey.put(name, made);
        declared.add(made);
        return made;
    }

    public boolean isOn(Feature f) {
        return (on & (1L << f.ordinal())) != 0L;
    }

    public boolean isOn(Switch s) {
        return (on & (1L << s.bit())) != 0L;
    }

    public void set(Feature f, boolean enabled) {
        flip(f.ordinal(), enabled);
    }

    public void set(Switch s, boolean enabled) {
        flip(s.bit(), enabled);
    }

    public void set(String key, boolean enabled) {
        flip(lookup(key).bit(), enabled);
    }

    private void flip(int bit, boolean enabled) {
        long mask = 1L << bit;
        on = enabled ? on | mask : on & ~mask;
    }

    public Switch lookup(String key) {
        Switch found = byKey.get(Feature.normalise(key));
        if (found != null) return found;
        throw new IllegalArgumentException("unknown feature '" + key + "', valid names are "
                + String.join(", ", declared.stream().map(Switch::key).sorted().toList()));
    }

    public List<Switch> all() {
        return List.copyOf(declared);
    }
}
