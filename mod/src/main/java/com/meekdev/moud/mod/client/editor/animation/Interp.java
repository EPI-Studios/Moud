package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.character.ClipCurve;

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

    public ClipCurve.Interp runtime() {
        return switch (this) {
            case LINEAR -> ClipCurve.Interp.LINEAR;
            case SMOOTH -> ClipCurve.Interp.CATMULLROM;
            case BEZIER -> ClipCurve.Interp.BEZIER;
            case STEP -> ClipCurve.Interp.STEP;
        };
    }
}
