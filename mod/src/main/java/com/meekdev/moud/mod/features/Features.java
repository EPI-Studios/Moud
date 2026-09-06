package com.meekdev.moud.mod.features;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public final class Features {

    private static final Map<String, Feature> BY_KEY = new HashMap<>();

    static {
        for (Feature f : Feature.values()) BY_KEY.put(Feature.normalise(f.key()), f);
    }

    private final EnumSet<Feature> on = EnumSet.noneOf(Feature.class);

    public boolean isOn(Feature f) {
        return on.contains(f);
    }

    public void set(Feature f, boolean enabled) {
        if (enabled) on.add(f); else on.remove(f);
    }

    public void set(String key, boolean enabled) {
        set(lookup(key), enabled);
    }

    public static Feature lookup(String key) {
        Feature f = BY_KEY.get(Feature.normalise(key));
        if (f != null) return f;
        throw new IllegalArgumentException("unknown feature '" + key + "', valid names are "
                + String.join(", ", BY_KEY.values().stream().map(Feature::key).sorted().toList()));
    }

}
