package com.meekdev.moud.core.tween;

public enum Easing {
    LINEAR, SINE, QUAD, CUBIC, QUART, QUINT, EXPO, CIRC, BACK, ELASTIC, BOUNCE;

    public enum Direction { IN, OUT, IN_OUT }

    public double apply(double t, Direction direction) {
        t = Math.clamp(t, 0, 1);
        return switch (direction) {
            case IN -> in(t);
            case OUT -> 1 - in(1 - t);
            case IN_OUT -> t < 0.5 ? in(t * 2) / 2 : 1 - in((1 - t) * 2) / 2;
        };
    }

    private double in(double t) {
        return switch (this) {
            case LINEAR -> t;
            case SINE -> 1 - Math.cos(t * Math.PI / 2);
            case QUAD -> t * t;
            case CUBIC -> t * t * t;
            case QUART -> t * t * t * t;
            case QUINT -> t * t * t * t * t;
            case EXPO -> t == 0 ? 0 : Math.pow(2, 10 * (t - 1));
            case CIRC -> 1 - Math.sqrt(1 - t * t);
            case BACK -> t * t * (2.70158 * t - 1.70158);
            case ELASTIC -> t == 0 || t == 1 ? t : -Math.pow(2, 10 * (t - 1)) * Math.sin((t - 1.075) * (2 * Math.PI) / 0.3);
            case BOUNCE -> 1 - bounce(1 - t);
        };
    }

    private static double bounce(double t) {
        if (t < 1 / 2.75) return 7.5625 * t * t;
        if (t < 2 / 2.75) return 7.5625 * (t -= 1.5 / 2.75) * t + 0.75;
        if (t < 2.5 / 2.75) return 7.5625 * (t -= 2.25 / 2.75) * t + 0.9375;
        return 7.5625 * (t -= 2.625 / 2.75) * t + 0.984375;
    }
}
