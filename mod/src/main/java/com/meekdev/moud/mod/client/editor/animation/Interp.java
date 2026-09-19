package com.meekdev.moud.mod.client.editor.animation;

import java.util.Locale;

public enum Interp {
    LINEAR("linear", "Linear"),
    SMOOTH("catmullrom", "Smooth"),
    BEZIER("bezier", "Bezier"),
    STEP("step", "Step");

    private final String key;
    private final String label;

    Interp(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public static Interp named(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        for (Interp interp : values()) {
            if (interp.key.equals(lower)) return interp;
        }
        if (lower.equals("smooth")) return SMOOTH;
        throw new IllegalArgumentException("interpolation must be linear, catmullrom, bezier or step, got " + key);
    }
}
