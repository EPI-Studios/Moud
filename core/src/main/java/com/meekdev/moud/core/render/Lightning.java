package com.meekdev.moud.core.render;

public final class Lightning {

    public static final double FLASH_SECONDS = 0.7;
    public static final double SOUND_SPEED = 343;
    public static final double LONGEST_DELAY = 5;
    public static final double SECONDS_BETWEEN = 9;

    private static final double[][] FLICKERS = {{0, 0.08, 1}, {0.14, 0.2, 0.55}, {0.3, 0.4, 0.8}};

    private Lightning() {}

    public static double flash(double since) {
        if (!(since >= 0) || since >= FLASH_SECONDS) return 0;
        double light = 0;
        for (double[] flicker : FLICKERS) {
            double start = flicker[0];
            double end = flicker[1];
            if (since < start) continue;
            double level = since <= end ? flicker[2] : flicker[2] * Math.exp(-(since - end) * 30);
            light = Math.max(light, level);
        }
        return Math.clamp(light * (1 - since / FLASH_SECONDS * 0.3), 0, 1);
    }

    public static double chance(double storm, double seconds) {
        double level = Math.clamp(storm, 0, 1);
        if (level <= 0 || seconds <= 0) return 0;
        return 1 - Math.exp(-level * seconds / SECONDS_BETWEEN);
    }

    public static boolean due(double storm, double seconds, double roll) {
        return roll < chance(storm, seconds);
    }

    public static double thunderDelay(double distance) {
        return Math.clamp(distance / SOUND_SPEED, 0, LONGEST_DELAY);
    }

    public static double thunderVolume(double distance) {
        return Math.clamp(1.2 - distance / 400, 0.25, 1);
    }
}
