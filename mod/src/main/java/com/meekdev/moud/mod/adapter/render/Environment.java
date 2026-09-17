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
import com.meekdev.moud.core.render.Daylight;
import com.meekdev.moud.core.render.Environments;
import com.meekdev.moud.core.render.Lighting;
import com.meekdev.moud.core.render.Sky;
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

    private static @Nullable Lighting lighting;
    private static @Nullable Sky sky;
    private static @Nullable Atmosphere atmosphere;
    private static @Nullable Clouds clouds;
    private static @Nullable Light sun;
    private static @Nullable Runnable modelDefaults;
    private static Vector3 sunDirection = Vector3.UP;

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
            drop();
            return;
        }
        sunDirection = Daylight.sunDirection(lighting.clockTime, lighting.geographicLatitude);
        models(lighting);
        exposure(lighting);
        shadows(lighting);
    }

    public static float skyFactor(float factor) {
        return lighting == null ? factor : (float) (factor * lighting.brightness / NEUTRAL_BRIGHTNESS);
    }

    public static Vector3f skyLight(Vector3f color) {
        if (lighting == null) return color;
        Color tint = lighting.outdoorAmbient;
        return new Vector3f(color.x * tint.r() / NEUTRAL_AMBIENT, color.y * tint.g() / NEUTRAL_AMBIENT,
                color.z * tint.b() / NEUTRAL_AMBIENT);
    }

    public static Vector3f ambient(Vector3f color) {
        if (lighting == null) return color;
        Color floor = lighting.ambient;
        return new Vector3f(Math.max(color.x, floor.r()), Math.max(color.y, floor.g()), Math.max(color.z, floor.b()));
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

    public static int cloudColor(int color) {
        if (clouds == null) return color;
        Color tint = clouds.color;
        double thickness = Math.clamp(clouds.density, 0, 1) * (0.3 + 0.7 * Math.clamp(clouds.cover, 0, 1));
        return ARGB.multiplyAlpha(ARGB.scaleRGB(color, tint.r(), tint.g(), tint.b()), (float) thickness);
    }

    public static void fog(FogData data, Vector3fc look, double viewDistance) {
        if (atmosphere == null) return;
        double facing = look.x() * sunDirection.x() + look.y() * sunDirection.y() + look.z() * sunDirection.z();
        Atmospheres.Fog fog = Atmospheres.fog(atmosphere, viewDistance, facing);
        data.environmentalStart = (float) fog.start();
        data.environmentalEnd = (float) fog.end();
        data.skyEnd = (float) fog.skyEnd();
        Color color = fog.color();
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
        if (!from.globalShadows || !Shadows.isEnabled()) {
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
        ShadowSettings.defaults().softness((float) from.shadowSoftness);
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
        float x = model.sunX();
        float y = model.sunY();
        float z = model.sunZ();
        float intensity = model.sunIntensity();
        float ambient = model.ambientStrength();
        float environment = model.envIntensity();
        return () -> model.sunDirection(x, y, z).sunIntensity(intensity)
                .ambientStrength(ambient).envIntensity(environment);
    }
}
