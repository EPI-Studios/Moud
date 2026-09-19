package com.meekdev.moud.mod.adapter.render.effect;

import com.meekdev.moud.core.effect.PrecipitationField;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.render.WeatherLevels;
import com.meekdev.moud.mod.adapter.render.WeatherView;
import java.util.Random;

final class Precipitation {

    private static final double RAIN_RATE = 2600;
    private static final double SNOW_RATE = 320;
    private static final double RAIN_RADIUS = 18;
    private static final double SNOW_RADIUS = 16;
    private static final double STREAK = 0.035;
    private static final double STREAK_WIDTH = 0.012;
    private static final double FLAKE = 0.05;
    private static final double MIN_SIDE_SQ = 1.0e-10;
    private static final Color RAIN = new Color(0.72f, 0.78f, 0.9f);
    private static final Color SNOW = Color.WHITE;

    private static final PrecipitationField DROPS = new PrecipitationField(new Random());
    private static final PrecipitationField FLAKES = new PrecipitationField(new Random());

    private Precipitation() {}

    static void step(double seconds) {
        if (WeatherView.weather() == null) {
            DROPS.clear();
            FLAKES.clear();
            return;
        }
        WeatherLevels levels = WeatherView.levels();
        Vector3 eye = WeatherView.eye();
        Vector3 wind = WeatherView.wind();
        double stormy = 1 + 0.5 * levels.storm();
        fall(DROPS, levels.rain() * RAIN_RATE * stormy, RAIN_RADIUS, PrecipitationField.Fall.RAIN, eye, wind, seconds);
        fall(FLAKES, levels.snow() * SNOW_RATE, SNOW_RADIUS, PrecipitationField.Fall.SNOW, eye, wind, seconds);
    }

    private static void fall(PrecipitationField field, double rate, double radius, PrecipitationField.Fall fall,
                             Vector3 eye, Vector3 wind, double seconds) {
        field.step(seconds, eye, radius, fall, WeatherView.roofs());
        int due = field.due(rate, seconds);
        if (due > 0) field.spawn(eye, radius, fall, wind, WeatherView.roofs(), due);
    }

    static void draw(QuadBatch batch, Effects.View view) {
        if (DROPS.count() == 0 && FLAKES.count() == 0) return;
        Vector3 eye = view.eye();
        int light = Effects.light(lightProbe(eye));
        if (DROPS.count() > 0) {
            batch.use(QuadBatch.Layer.WORLD, EffectTextures.white());
            batch.light(light, 1, 0);
            batch.tint(RAIN, 0.35, 1);
            for (int i = 0; i < DROPS.count(); i++) streak(batch, view, DROPS.position(i), DROPS.velocity(i));
        }
        if (FLAKES.count() > 0) {
            batch.use(QuadBatch.Layer.WORLD, EffectTextures.of(""));
            batch.light(light, 1, 0);
            batch.tint(SNOW, 0.9, 1);
            Vector3 right = view.right().mul(FLAKE);
            Vector3 up = view.up().mul(FLAKE);
            for (int i = 0; i < FLAKES.count(); i++) {
                Vector3 at = FLAKES.position(i);
                batch.corner(at.sub(right).sub(up), 0, 1);
                batch.corner(at.add(right).sub(up), 1, 1);
                batch.corner(at.add(right).add(up), 1, 0);
                batch.corner(at.sub(right).add(up), 0, 0);
            }
        }
    }

    private static Vector3 lightProbe(Vector3 eye) {
        if (!WeatherView.sheltered()) return eye;
        double roof = WeatherView.roofs().top(eye.x(), eye.z());
        return new Vector3(eye.x(), roof + 1, eye.z());
    }

    private static void streak(QuadBatch batch, Effects.View view, Vector3 at, Vector3 velocity) {
        Vector3 along = velocity.mul(STREAK * 0.5);
        Vector3 side = velocity.cross(view.eye().sub(at));
        if (side.lengthSq() < MIN_SIDE_SQ) return;
        side = side.normalize().mul(STREAK_WIDTH);
        batch.corner(at.sub(side).sub(along), 0, 1);
        batch.corner(at.add(side).sub(along), 1, 1);
        batch.corner(at.add(side).add(along), 1, 0);
        batch.corner(at.sub(side).add(along), 0, 0);
    }
}
