package com.meekdev.moud.core.render;

public final class WeatherMix {

    private WeatherLevels current = WeatherLevels.CLEAR;
    private WeatherLevels from = WeatherLevels.CLEAR;
    private WeatherLevels target = WeatherLevels.CLEAR;
    private double elapsed;
    private double span;
    private boolean started;

    public WeatherLevels step(Weather weather, double seconds) {
        return step(weather.kind, weather.intensity, weather.transition, seconds);
    }

    public WeatherLevels step(WeatherKind kind, double intensity, double transition, double seconds) {
        WeatherLevels goal = WeatherLevels.of(kind, intensity);
        if (!started) {
            started = true;
            current = from = target = goal;
            return current;
        }
        if (!goal.equals(target)) {
            from = current;
            target = goal;
            elapsed = 0;
            span = Double.isFinite(transition) ? Math.max(0, transition) : 0;
        }
        elapsed += Math.max(0, seconds);
        double t = span <= 0 ? 1 : Math.clamp(elapsed / span, 0, 1);
        current = from.lerp(target, t * t * (3 - 2 * t));
        return current;
    }

    public WeatherLevels levels() {
        return current;
    }

    public boolean settled() {
        return current.equals(target);
    }

    public void reset() {
        current = from = target = WeatherLevels.CLEAR;
        elapsed = 0;
        span = 0;
        started = false;
    }
}
