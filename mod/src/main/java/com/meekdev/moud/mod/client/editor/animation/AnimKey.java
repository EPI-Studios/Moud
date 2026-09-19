package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.math.Vector3;
import org.jspecify.annotations.Nullable;

public record AnimKey(double time, Vector3 value, Interp interp, @Nullable Handle in, @Nullable Handle out) {

    public record Handle(double dt, Vector3 dv) {}

    public AnimKey(double time, Vector3 value, Interp interp) {
        this(time, value, interp, null, null);
    }

    public AnimKey at(double moved) {
        return new AnimKey(moved, value, interp, in, out);
    }

    public AnimKey with(Vector3 changed) {
        return new AnimKey(time, changed, interp, in, out);
    }

    public AnimKey with(Interp changed) {
        return new AnimKey(time, value, changed, in, out);
    }

    public AnimKey handles(@Nullable Handle before, @Nullable Handle after) {
        return new AnimKey(time, value, interp, before, after);
    }
}
