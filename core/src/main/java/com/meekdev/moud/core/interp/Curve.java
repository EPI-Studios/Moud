package com.meekdev.moud.core.interp;

// a curve maps alpha to alpha, so "does not interpolate" and "interpolates in one jump" are the
// same statement and nothing has to branch on whether a value is animatable
public enum Curve {

    STEP {
        @Override public double at(double a) { return a < 1.0 ? 0.0 : 1.0; }
    },
    LINEAR {
        @Override public double at(double a) { return a; }
    },
    SMOOTH {
        @Override public double at(double a) { return a * a * (3.0 - 2.0 * a); }
    },
    QUAD_IN {
        @Override public double at(double a) { return a * a; }
    },
    QUAD_OUT {
        @Override public double at(double a) { return a * (2.0 - a); }
    },
    CUBIC_IN {
        @Override public double at(double a) { return a * a * a; }
    },
    CUBIC_OUT {
        @Override public double at(double a) { double b = a - 1.0; return b * b * b + 1.0; }
    },
    SINE_IN_OUT {
        @Override public double at(double a) { return 0.5 - 0.5 * Math.cos(Math.PI * a); }
    },
    EXPO_OUT {
        @Override public double at(double a) { return a >= 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * a); }
    },
    BACK_OUT {
        @Override public double at(double a) {
            double b = a - 1.0;
            return 1.0 + b * b * ((BACK + 1.0) * b + BACK);
        }
    },
    BOUNCE_OUT {
        @Override public double at(double a) {
            if (a < 1.0 / 2.75) return 7.5625 * a * a;
            if (a < 2.0 / 2.75) { double b = a - 1.5 / 2.75; return 7.5625 * b * b + 0.75; }
            if (a < 2.5 / 2.75) { double b = a - 2.25 / 2.75; return 7.5625 * b * b + 0.9375; }
            double b = a - 2.625 / 2.75;
            return 7.5625 * b * b + 0.984375;
        }
    };

    private static final double BACK = 1.70158;

    public abstract double at(double alpha);

    public double clamped(double alpha) {
        return at(alpha <= 0.0 ? 0.0 : Math.min(alpha, 1.0));
    }
}
