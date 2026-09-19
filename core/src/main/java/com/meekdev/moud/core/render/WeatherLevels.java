package com.meekdev.moud.core.render;

public record WeatherLevels(double rain, double snow, double storm, double fog, double overcast) {

    public static final WeatherLevels CLEAR = new WeatherLevels(0, 0, 0, 0, 0);

    public static WeatherLevels of(WeatherKind kind, double intensity) {
        double i = Double.isFinite(intensity) ? Math.clamp(intensity, 0, 1) : 0;
        return switch (kind) {
            case CLEAR -> CLEAR;
            case RAIN -> new WeatherLevels(i, 0, 0, 0.15 * i, i);
            case SNOW -> new WeatherLevels(0, i, 0, 0.25 * i, 0.8 * i);
            case STORM -> new WeatherLevels(i, 0, i, 0.3 * i, i);
            case FOG -> new WeatherLevels(0, 0, 0, i, 0.3 * i);
        };
    }

    public WeatherLevels lerp(WeatherLevels to, double t) {
        return new WeatherLevels(rain + (to.rain - rain) * t, snow + (to.snow - snow) * t,
                storm + (to.storm - storm) * t, fog + (to.fog - fog) * t, overcast + (to.overcast - overcast) * t);
    }

    public double wet() {
        return Math.max(rain, snow);
    }

    public boolean calm() {
        return rain <= 0 && snow <= 0 && storm <= 0 && fog <= 0 && overcast <= 0;
    }
}
