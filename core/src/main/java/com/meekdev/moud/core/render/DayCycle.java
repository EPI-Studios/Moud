package com.meekdev.moud.core.render;

import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.scene.Json;
import java.util.List;
import java.util.Map;

public final class DayCycle {

    public record Tint(Color ambient, Color outdoor, Color fog, Color sky) {

        public static final Tint NEUTRAL = new Tint(Color.BLACK, Color.WHITE, Color.WHITE, Color.WHITE);
    }

    record Curve(double[] keys, Color[] colors) {

        Color at(double x) {
            if (x <= keys[0]) return colors[0];
            int last = keys.length - 1;
            if (x >= keys[last]) return colors[last];
            int i = 0;
            while (x > keys[i + 1]) i++;
            double t = (x - keys[i]) / (keys[i + 1] - keys[i]);
            return colors[i].lerp(colors[i + 1], (float) (t * t * (3 - 2 * t)));
        }
    }

    private static final Map<?, ?> TABLES = (Map<?, ?>) Json.resource(DayCycle.class, "day-cycle.json");

    static final double[] ELEVATIONS = elevations();

    private static final Curve AMBIENT = curve("ambient");

    private static final Curve DUSK_OUTDOOR = curve("duskOutdoor");
    private static final Curve DAWN_OUTDOOR = curve("dawnOutdoor");

    private static final Curve DUSK_FOG = curve("duskFog");
    private static final Curve DAWN_FOG = curve("dawnFog");

    private static final Curve DUSK_SKY = curve("duskSky");
    private static final Curve DAWN_SKY = curve("dawnSky");

    private DayCycle() {}

    public static Tint at(double clockTime, double latitude) {
        double elevation = Daylight.sunDirection(clockTime, latitude).y();
        boolean morning = Daylight.hours(clockTime) < 12;
        return new Tint(AMBIENT.at(elevation),
                (morning ? DAWN_OUTDOOR : DUSK_OUTDOOR).at(elevation),
                (morning ? DAWN_FOG : DUSK_FOG).at(elevation),
                (morning ? DAWN_SKY : DUSK_SKY).at(elevation));
    }

    public static Tint of(Lighting lighting) {
        return lighting.dayCycle ? at(lighting.clockTime, lighting.geographicLatitude) : Tint.NEUTRAL;
    }

    private static double[] elevations() {
        List<?> keys = (List<?>) TABLES.get("elevations");
        double[] elevations = new double[keys.size()];
        for (int i = 0; i < elevations.length; i++) elevations[i] = number(keys.get(i));
        return elevations;
    }

    private static Curve curve(String name) {
        List<?> rows = (List<?>) TABLES.get(name);
        Color[] colors = new Color[rows.size()];
        for (int i = 0; i < colors.length; i++) {
            List<?> rgb = (List<?>) rows.get(i);
            colors[i] = new Color((float) number(rgb.get(0)), (float) number(rgb.get(1)), (float) number(rgb.get(2)));
        }
        return new Curve(ELEVATIONS, colors);
    }

    private static double number(Object value) {
        return ((Number) value).doubleValue();
    }
}
