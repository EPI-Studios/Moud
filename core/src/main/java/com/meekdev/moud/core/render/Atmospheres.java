package com.meekdev.moud.core.render;

import com.meekdev.moud.core.math.Color;

public final class Atmospheres {

    public record Fog(double start, double end, double skyEnd, Color color) {}

    public record Air(double density, double offset, Color color, Color decay, double glare, double haze) {

        public static Air of(Atmosphere atmosphere) {
            return new Air(atmosphere.density, atmosphere.offset, atmosphere.color, atmosphere.decay,
                    atmosphere.glare, atmosphere.haze);
        }

        public static Air open(Color color) {
            return new Air(0, 0.25, color, color, 0, 0);
        }

        public Air tinted(Color tint) {
            return new Air(density, offset, color.times(tint), decay.times(tint), glare, haze);
        }

        public Air toward(Color other, double amount) {
            float t = (float) Math.clamp(amount, 0, 1);
            return new Air(density, offset, color.lerp(other, t), decay.lerp(other, t), glare, haze);
        }
    }

    private static final double CLOSEST = 8;

    private Atmospheres() {}

    public static Fog fog(Atmosphere atmosphere, double viewDistance, double facingSun) {
        return fog(Air.of(atmosphere), viewDistance, facingSun);
    }

    public static Fog fog(Air air, double viewDistance, double facingSun) {
        double reach = Math.max(CLOSEST, viewDistance);
        double far = Math.clamp(reach * Math.pow(1 - Math.clamp(air.density(), 0, 1), 2), CLOSEST, reach);
        double near = far * Math.clamp(0.25 + Math.clamp(air.offset(), -1, 1) * 0.75, 0, 0.95);
        double haze = Math.clamp(air.haze() / 10, 0, 1);
        double skyEnd = far + (reach * 4 - far) * (1 - haze);
        return new Fog(near, far, skyEnd, color(air, facingSun));
    }

    public static Color color(Atmosphere atmosphere, double facingSun) {
        return color(Air.of(atmosphere), facingSun);
    }

    public static Color color(Air air, double facingSun) {
        float haze = (float) Math.clamp(air.haze() / 10, 0, 1);
        Color away = air.color().lerp(air.decay(), 1 - haze);
        float glare = (float) (Math.clamp(air.glare() / 10, 0, 1) * Math.clamp(facingSun, 0, 1));
        return away.lerp(brighten(away), glare);
    }

    private static Color brighten(Color color) {
        float peak = Math.max(color.r(), Math.max(color.g(), color.b()));
        if (peak <= 0.001f) return Color.WHITE;
        float gain = 1 / peak;
        return new Color(color.r() * gain, color.g() * gain, color.b() * gain, color.a());
    }
}
