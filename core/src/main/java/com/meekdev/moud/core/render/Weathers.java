package com.meekdev.moud.core.render;

import com.meekdev.moud.core.math.Color;
import java.util.ArrayList;
import java.util.List;

public final class Weathers {

    public record Ambience(String sound, double volume, double pitch) {}

    public static final String RAIN_SOUND = "minecraft:weather.rain";
    public static final String WIND_SOUND = "minecraft:item.elytra.flying";
    public static final String THUNDER_SOUND = "minecraft:entity.lightning_bolt.thunder";
    public static final String IMPACT_SOUND = "minecraft:entity.lightning_bolt.impact";

    private static final Color OVERCAST = new Color(0.6f, 0.62f, 0.66f);
    private static final double THICKEST = 0.9;
    private static final double INDOORS = 0.35;

    private Weathers() {}

    public static Atmospheres.Air air(Atmospheres.Air base, WeatherLevels levels) {
        if (levels.calm()) return base;
        double thick = Math.clamp(levels.fog() * 0.8 + levels.rain() * 0.2 + levels.snow() * 0.3 + levels.storm() * 0.1, 0, THICKEST);
        double density = 1 - (1 - Math.clamp(base.density(), 0, 1)) * (1 - thick);
        double haze = base.haze() + (10 - base.haze()) * Math.clamp(levels.fog() * 0.9 + levels.overcast() * 0.3, 0, 1);
        Atmospheres.Air grey = new Atmospheres.Air(density, base.offset(), base.color(), base.decay(), base.glare() * (1 - levels.overcast()), haze)
                .toward(OVERCAST.times(base.color()).lerp(OVERCAST, 0.5f), levels.overcast() * 0.5);
        float dark = (float) (1 - 0.4 * Math.clamp(levels.storm(), 0, 1));
        return grey.tinted(new Color(dark, dark, dark));
    }

    public static boolean fogs(WeatherLevels levels) {
        return levels.fog() > 0 || levels.rain() > 0 || levels.snow() > 0;
    }

    public static double cover(double cover, WeatherLevels levels) {
        return Math.max(cover, Math.clamp(levels.overcast(), 0, 1));
    }

    public static double cloudShade(WeatherLevels levels) {
        return 1 - 0.45 * Math.clamp(levels.storm(), 0, 1) - 0.2 * Math.clamp(levels.overcast(), 0, 1);
    }

    public static WeatherLevels falling(WeatherLevels levels, double rain, double thunder) {
        double wet = Math.max(levels.rain(), Math.clamp(rain, 0, 1));
        double storm = wet > 0 ? Math.max(levels.storm(), Math.clamp(thunder, 0, 1)) : levels.storm();
        return new WeatherLevels(wet, levels.snow(), storm, levels.fog(), levels.overcast());
    }

    public static double muffled(double volume, boolean sheltered) {
        return sheltered ? volume * INDOORS : volume;
    }

    public static List<Ambience> ambience(WeatherLevels levels, boolean sheltered) {
        List<Ambience> out = new ArrayList<>(2);
        double rain = Math.clamp(levels.rain(), 0, 1) * (0.55 + 0.35 * Math.clamp(levels.storm(), 0, 1));
        out.add(new Ambience(RAIN_SOUND, muffled(rain, sheltered), 1));
        double wind = Math.clamp(levels.snow() * 0.12 + levels.fog() * 0.06 + levels.storm() * 0.08, 0, 0.25);
        out.add(new Ambience(WIND_SOUND, muffled(wind, sheltered), 0.5));
        return out;
    }
}
