package com.meekdev.moud.mod.features;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Features {

    // a switch is a name and a bit, and the bit is handed out in the order switches are declared
    //
    // the engine's own go first and keep the bits their ordinals had, so nothing that reads one
    // changes. an addon's follow, which is the whole reason this is a list and not an enum
    public record Switch(String key, int bit) {}

    private final Map<String, Switch> byKey = new HashMap<>();
    private final List<Switch> declared = new ArrayList<>();

    // the mixins read this from the render thread, the server thread and the chunk workers, so the
    // switches have to publish, which a set behind a plain field does not
    private volatile long on;

    public Features() {
        for (Feature f : Feature.values()) declare(f.key());
    }

    // what an addon calls to get a switch of its own. declaring one twice is the same switch, so
    // two sides of the same addon may both ask without coordinating
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
