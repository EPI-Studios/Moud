package com.meekdev.moud.mod.features;

import java.util.HashMap;
import java.util.Map;

public final class Features {

    private static final Map<String, Feature> BY_KEY = new HashMap<>();

    static {
        if (Feature.values().length > Long.SIZE) {
            throw new IllegalStateException("more features than fit the switch word");
        }
        for (Feature f : Feature.values()) BY_KEY.put(Feature.normalise(f.key()), f);
    }

    // the mixins read this from the render thread, the server thread and the chunk workers, so the
    // switches have to publish, which a set behind a plain field does not
    private volatile long on;

    public boolean isOn(Feature f) {
        return (on & (1L << f.ordinal())) != 0L;
    }

    public void set(Feature f, boolean enabled) {
        long bit = 1L << f.ordinal();
        on = enabled ? on | bit : on & ~bit;
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
