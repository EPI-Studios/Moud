package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.character.ClipCurve;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.tween.Easing;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class Curves {

    private static final double THIRD = 1.0 / 3.0;

    private Curves() {}

    public static @Nullable ClipCurve curve(List<AnimKey> keys) {
        if (keys.isEmpty()) return null;
        List<ClipCurve.Key> converted = new ArrayList<>(keys.size());
        for (AnimKey key : keys) {
            converted.add(new ClipCurve.Key(key.time(), array(key.value()), key.interp().runtime(), Easing.LINEAR, Easing.Direction.IN_OUT,
                    handle(key.in()), handle(key.out())));
        }
        return new ClipCurve(converted);
    }

    private static ClipCurve.@Nullable Handle handle(AnimKey.@Nullable Handle handle) {
        return handle == null ? null : new ClipCurve.Handle(handle.dt(), array(handle.dv()));
    }

    private static double[] array(Vector3 value) {
        return new double[] {value.x(), value.y(), value.z()};
    }

    public static Vector3 sample(List<AnimKey> keys, double time, Vector3 rest) {
        return sample(curve(keys), time, rest);
    }

    public static Vector3 sample(@Nullable ClipCurve curve, double time, Vector3 rest) {
        if (curve == null) return rest;
        double[] v = curve.sample(time);
        return new Vector3(v[0], v[1], v[2]);
    }

    public static double component(Vector3 value, int axis) {
        return switch (axis) {
            case 0 -> value.x();
            case 1 -> value.y();
            default -> value.z();
        };
    }

    public static Vector3 withComponent(Vector3 value, int axis, double changed) {
        return switch (axis) {
            case 0 -> new Vector3(changed, value.y(), value.z());
            case 1 -> new Vector3(value.x(), changed, value.z());
            default -> new Vector3(value.x(), value.y(), changed);
        };
    }

    public static AnimKey.Handle outHandle(AnimKey from, AnimKey to) {
        if (from.out() != null) return from.out();
        return new AnimKey.Handle((to.time() - from.time()) * THIRD, Vector3.ZERO);
    }

    public static AnimKey.Handle inHandle(AnimKey from, AnimKey to) {
        if (to.in() != null) return to.in();
        return new AnimKey.Handle(-(to.time() - from.time()) * THIRD, Vector3.ZERO);
    }
}
