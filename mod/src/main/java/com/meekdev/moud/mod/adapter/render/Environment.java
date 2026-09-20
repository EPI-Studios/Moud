package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.grade.ColorGrade;
import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.Lights;
import com.meekdev.amnetic.client.model.ModelLighting;
import com.meekdev.amnetic.client.shadow.ShadowSettings;
import com.meekdev.amnetic.client.shadow.Shadows;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.render.Atmosphere;
import com.meekdev.moud.core.render.Atmospheres;
import com.meekdev.moud.core.render.Clouds;
import com.meekdev.moud.core.render.DayCycle;
import com.meekdev.moud.core.render.Daylight;
import com.meekdev.moud.core.render.Environments;
import com.meekdev.moud.core.render.Lighting;
import com.meekdev.moud.core.render.Sky;
import com.meekdev.moud.core.render.Weathers;
import com.meekdev.moud.mod.client.ClientScene;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.util.ARGB;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public final class Environment {

    private static final double NEUTRAL_BRIGHTNESS = 2;
    private static final double BODY_DISTANCE = 100;
    private static final double EVERY_STAR = 3000;
    private static final float NEUTRAL_AMBIENT = 0.5f;
    private static final double FLASH_LIGHT = 0.6;
    private static final double FLASH_FOG = 0.5;

    private static @Nullable Lighting lighting;
    private static @Nullable Sky sky;
    private static @Nullable Atmosphere atmosphere;
    private static @Nullable Clouds clouds;
    private static @Nullable Light sun;
    private static @Nullable Runnable modelDefaults;
    private static Vector3 sunDirection = Vector3.UP;
    private static DayCycle.Tint tint = DayCycle.Tint.NEUTRAL;

    private Environment() {}

    public static @Nullable Lighting lighting() {
        return lighting;
    }

    public static @Nullable Sky sky() {
        return sky;
    }

    public static @Nullable Atmosphere atmosphere() {
        return atmosphere;
    }

    public static @Nullable Clouds clouds() {
        return clouds;
    }

    public static Vector3 sunDirection() {
        return sunDirection;
    }

    public static void frame() {
        InstanceTree tree = ClientScene.tree();
        lighting = Environments.lighting(tree);
        sky = Environments.sky(tree);
        atmosphere = Environments.atmosphere(tree);
        clouds = Environments.clouds(tree);
        if (lighting == null) {
            sunDirection = Vector3.UP;
            tint = DayCycle.Tint.NEUTRAL;
            drop();
            return;
        }
        sunDirection = Daylight.sunDirection(lighting.clockTime, lighting.geographicLatitude);
        tint = DayCycle.of(lighting);
        models(lighting);
        exposure(lighting);
        shadows(lighting);
    }

    public static float skyFactor(float factor) {
        if (lighting == null) return factor;
        return (float) (factor * lighting.brightness / NEUTRAL_BRIGHTNESS + WeatherView.flash() * FLASH_LIGHT);
    }

    public static Vector3f skyLight(Vector3f color) {
        if (lighting == null) return color;
        Color outdoor = lighting.outdoorAmbient.times(tint.outdoor());
        return new Vector3f(color.x * outdoor.r() / NEUTRAL_AMBIENT, color.y * outdoor.g() / NEUTRAL_AMBIENT,
                color.z * outdoor.b() / NEUTRAL_AMBIENT);
    }

    public static Vector3f ambient(Vector3f color) {
        if (lighting == null) return color;
        Color set = lighting.ambient;
        Color cycle = tint.ambient();
        return new Vector3f(Math.max(color.x, Math.max(set.r(), cycle.r())), Math.max(color.y, Math.max(set.g(), cycle.g())),
                Math.max(color.z, Math.max(set.b(), cycle.b())));
    }

    public static int skyColor(int color) {
        if (lighting == null || !lighting.dayCycle) return color;
        Color sky = tint.sky();
        return ARGB.scaleRGB(color, sky.r(), sky.g(), sky.b());
    }

    public static float sunScale(float quad) {
        return bodyScale(sky == null ? 0 : sky.sunAngularSize, quad);
    }

    public static float moonScale(float quad) {
        return bodyScale(sky == null ? 0 : sky.moonAngularSize, quad);
    }

    public static int starIndices(int indices) {
        if (sky == null) return indices;
        int quads = indices / 6;
        return (int) Math.round(quads * Math.clamp(sky.starCount / EVERY_STAR, 0, 1)) * 6;
    }

    public static double cloudCover() {
        if (clouds == null) return 0;
        return Weathers.cover(clouds.cover, WeatherView.levels());
    }

    public static int cloudColor(int color) {
        if (clouds == null && WeatherView.weather() == null) return color;
        float shade = (float) Weathers.cloudShade(WeatherView.levels());
        if (clouds == null) return ARGB.scaleRGB(color, shade, shade, shade);
        Color tint = clouds.color;
        double thickness = Math.clamp(clouds.density, 0, 1) * (0.3 + 0.7 * Math.clamp(cloudCover(), 0, 1));
        return ARGB.multiplyAlpha(ARGB.scaleRGB(color, tint.r() * shade, tint.g() * shade, tint.b() * shade), (float) thickness);
    }

    public static boolean fogged() {
        return atmosphere != null || WeatherView.weather() != null && Weathers.fogs(WeatherView.levels());
    }

    public static void fog(FogData data, Vector3fc look, double viewDistance) {
        if (!fogged()) return;
        double facing = look.x() * sunDirection.x() + look.y() * sunDirection.y() + look.z() * sunDirection.z();
        Atmospheres.Air air = atmosphere != null ? Atmospheres.Air.of(atmosphere).tinted(tint.fog())
                : Atmospheres.Air.open(new Color(data.color.x(), data.color.y(), data.color.z()));
        Atmospheres.Fog fog = Atmospheres.fog(Weathers.air(air, WeatherView.levels()), viewDistance, facing);
        data.environmentalStart = (float) fog.start();
        data.environmentalEnd = (float) fog.end();
        data.skyEnd = (float) fog.skyEnd();
        Color color = fog.color().lerp(Color.WHITE, (float) (WeatherView.flash() * FLASH_FOG));
        data.color.set(color.r(), color.g(), color.b(), 1);
    }

    private static float bodyScale(double angularSize, float quad) {
        if (sky == null || angularSize <= 0 || quad <= 0) return 1;
        return (float) (BODY_DISTANCE * Math.tan(Math.toRadians(angularSize) / 2) / quad);
    }

    private static void models(Lighting from) {
        if (modelDefaults == null) modelDefaults = capture();
        ModelLighting.INSTANCE
                .sunDirection((float) sunDirection.x(), (float) sunDirection.y(), (float) sunDirection.z())
                .sunIntensity((float) (from.brightness / NEUTRAL_BRIGHTNESS))
                .ambientStrength((float) from.environmentDiffuseScale)
                .envIntensity((float) from.environmentSpecularScale);
    }

    private static void exposure(Lighting from) {
        if (from.exposureCompensation == 0) return;
        ColorGrade.settings().enabled(true)
                .exposure((float) (ColorGrade.settings().exposure() * Math.pow(2, from.exposureCompensation)));
    }

    private static void shadows(Lighting from) {
        if (from.globalShadows) Shadows.enable();
        if (!from.globalShadows) {
            if (sun != null) {
                sun.remove();
                sun = null;
            }
            return;
        }
        if (sun == null) sun = Lights.directional(new Vector3f(0, -1, 0), 1, 1, 1, 1);
        sun.setDirection((float) -sunDirection.x(), (float) -sunDirection.y(), (float) -sunDirection.z())
                .setIntensity((float) (from.brightness / NEUTRAL_BRIGHTNESS))
                .setEnabled(sunDirection.y() > 0)
                .castsShadow(true);
        ShadowSettings.defaults()
                .softness((float) from.shadowSoftness)
                .bias((float) from.shadowBias)
                .normalBias((float) from.shadowNormalBias)
                .fadeStart((float) from.shadowFade)
                .sunBlockOccluderRadius((float) from.blockShadowDistance);
    }

    private static void drop() {
        if (sun != null) {
            sun.remove();
            sun = null;
        }
        if (modelDefaults == null) return;
        modelDefaults.run();
        modelDefaults = null;
    }

    private static Runnable capture() {
        ModelLighting model = ModelLighting.INSTANCE;
        boolean owned = model.sunSet();
        float x = model.sunX();
        float y = model.sunY();
        float z = model.sunZ();
        float intensity = model.sunIntensity();
        float ambient = model.ambientStrength();
        float environment = model.envIntensity();
        return () -> {
            model.sunDirection(x, y, z).sunIntensity(intensity)
                    .ambientStrength(ambient).envIntensity(environment);
            if (!owned) model.clearSun();
        };
    }
}
