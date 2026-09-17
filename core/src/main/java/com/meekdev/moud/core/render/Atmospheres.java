package com.meekdev.moud.core.render;

import com.meekdev.moud.core.math.Color;

public final class Atmospheres {

    public record Fog(double start, double end, double skyEnd, Color color) {}

    private static final double CLOSEST = 8;

    private Atmospheres() {}

    public static Fog fog(Atmosphere atmosphere, double viewDistance, double facingSun) {
        double reach = Math.max(CLOSEST, viewDistance);
        double far = Math.clamp(reach * Math.pow(1 - Math.clamp(atmosphere.density, 0, 1), 2), CLOSEST, reach);
        double near = far * Math.clamp(0.25 + Math.clamp(atmosphere.offset, -1, 1) * 0.75, 0, 0.95);
        double haze = Math.clamp(atmosphere.haze / 10, 0, 1);
        double skyEnd = far + (reach * 4 - far) * (1 - haze);
        return new Fog(near, far, skyEnd, color(atmosphere, facingSun));
    }

    public static Color color(Atmosphere atmosphere, double facingSun) {
        float haze = (float) Math.clamp(atmosphere.haze / 10, 0, 1);
        Color away = atmosphere.color.lerp(atmosphere.decay, 1 - haze);
        float glare = (float) (Math.clamp(atmosphere.glare / 10, 0, 1) * Math.clamp(facingSun, 0, 1));
        return away.lerp(brighten(away), glare);
    }

    private static Color brighten(Color color) {
        float peak = Math.max(color.r(), Math.max(color.g(), color.b()));
        if (peak <= 0.001f) return Color.WHITE;
        float gain = 1 / peak;
        return new Color(color.r() * gain, color.g() * gain, color.b() * gain, color.a());
    }
}
