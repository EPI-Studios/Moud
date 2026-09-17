package com.meekdev.moud.core.render;

import com.meekdev.moud.core.math.Vector3;

public final class Daylight {

    public static final double TICKS_PER_DAY = 24000;

    private Daylight() {}

    public static double hours(double clockTime) {
        double wrapped = clockTime % 24;
        return wrapped < 0 ? wrapped + 24 : wrapped;
    }

    public static double ticks(double clockTime) {
        return hours(clockTime - 6) / 24 * TICKS_PER_DAY;
    }

    public static double clockTime(double ticks) {
        double day = ticks % TICKS_PER_DAY;
        if (day < 0) day += TICKS_PER_DAY;
        return hours(day / TICKS_PER_DAY * 24 + 6);
    }

    public static double minutesAfterMidnight(double clockTime) {
        return hours(clockTime) * 60;
    }

    public static double fromMinutes(double minutes) {
        return hours(minutes / 60);
    }

    public static Vector3 sunDirection(double clockTime, double latitude) {
        double hourAngle = (hours(clockTime) - 12) / 24 * 2 * Math.PI;
        double tilt = Math.toRadians(Math.clamp(latitude, -90, 90));
        double east = -Math.sin(hourAngle);
        double up = Math.cos(tilt) * Math.cos(hourAngle);
        double south = Math.sin(tilt) * Math.cos(hourAngle);
        return new Vector3(east, up, south).normalize();
    }

    public static Vector3 moonDirection(double clockTime, double latitude) {
        return sunDirection(clockTime, latitude).neg();
    }
}
