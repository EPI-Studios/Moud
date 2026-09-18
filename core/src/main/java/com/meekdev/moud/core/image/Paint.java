package com.meekdev.moud.core.image;

import com.meekdev.moud.core.math.Color;

public record Paint(float r, float g, float b, float a, Blend blend) {

    public static Paint of(Color color, double transparency, Blend blend) {
        float alpha = (float) (color.a() * (1 - Math.clamp(transparency, 0, 1)));
        return new Paint(clamp(color.r()), clamp(color.g()), clamp(color.b()), clamp(alpha), blend);
    }

    public static Paint over(Color color) {
        return of(color, 0, Blend.OVER);
    }

    private static float clamp(float v) {
        return Math.clamp(v, 0f, 1f);
    }
}
