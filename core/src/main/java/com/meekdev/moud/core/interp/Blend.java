package com.meekdev.moud.core.interp;

import com.meekdev.moud.core.clazz.PropertyType;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vec3;

// keyed on the property type, so every property that will ever exist interpolates without a
// per call site decision. types with no midpoint step, which is a curve rather than a refusal
public final class Blend {

    private Blend() {}

    public static boolean isContinuous(PropertyType type) {
        return switch (type) {
            case NUM, VEC3, QUAT, CFRAME, COLOR -> true;
            case BOOL, INT, STRING, ASSET, ENUM, REF -> false;
        };
    }

    public static double number(double from, double to, double alpha) {
        return from + (to - from) * alpha;
    }

    public static Object of(PropertyType type, Object from, Object to, double alpha) {
        if (!isContinuous(type)) return alpha < 1.0 ? from : to;
        return switch (type) {
            case VEC3 -> ((Vec3) from).lerp((Vec3) to, alpha);
            case QUAT -> ((Quat) from).slerp((Quat) to, alpha);
            case CFRAME -> ((CFrame) from).lerp((CFrame) to, alpha);
            case COLOR -> ((Color) from).lerp((Color) to, (float) alpha);
            default -> alpha < 1.0 ? from : to;
        };
    }
}
