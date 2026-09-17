package com.meekdev.moud.core.effect;

import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.ui.SurfaceFace;

public record ParticleLook(
        String texture,
        double rate,
        double lifetimeMin,
        double lifetimeMax,
        double speedMin,
        double speedMax,
        double spreadAngle,
        SurfaceFace direction,
        EmissionShape shape,
        Vector3 acceleration,
        double drag,
        double rotationMin,
        double rotationMax,
        double rotSpeedMin,
        double rotSpeedMax,
        double sizeStart,
        double sizeEnd,
        double transparencyStart,
        double transparencyEnd,
        Color colorStart,
        Color colorEnd,
        double brightness,
        double lightEmission,
        double lightInfluence,
        ParticleOrientation orientation,
        boolean locked,
        double timeScale) {

    public static final String FLAME = "minecraft:flame";
    public static final String SMOKE = "minecraft:big_smoke_0";
    public static final String GLINT = "minecraft:glint";

    private static final Color FLAME_CORE = new Color(1f, 0.93f, 0.6f);

    public static ParticleLook of(ParticleEmitter e) {
        return new ParticleLook(e.texture, e.rate, e.lifetimeMin, e.lifetimeMax, e.speedMin, e.speedMax,
                e.spreadAngle, e.emissionDirection, e.shapeStyle, e.acceleration, e.drag,
                e.rotationMin, e.rotationMax, e.rotSpeedMin, e.rotSpeedMax, e.sizeStart, e.sizeEnd,
                e.transparencyStart, e.transparencyEnd, e.colorStart, e.colorEnd, e.brightness,
                e.lightEmission, e.lightInfluence, e.orientation, e.lockedToPart, e.timeScale);
    }

    public static ParticleLook of(Fire fire) {
        double size = fire.size;
        double rise = fire.heat * 0.15;
        return new ParticleLook(FLAME, 12 * size, 0.6, 1.1, rise * 0.4, rise * 0.6, 12,
                SurfaceFace.TOP, EmissionShape.VOLUME, new Vector3(0, rise, 0), 1.5, -30, 30, -45, 45,
                size * 0.45, size * 0.1, 0.1, 1, FLAME_CORE.lerp(fire.color, 0.6f), fire.secondaryColor, 1,
                1, 0, ParticleOrientation.FACING_CAMERA, false, fire.timeScale);
    }

    public static ParticleLook of(Smoke smoke) {
        double size = smoke.size;
        double opacity = Math.clamp(smoke.opacity, 0, 1);
        return new ParticleLook(SMOKE, 6 + size * 2, 4, 6, smoke.riseVelocity * 0.8, smoke.riseVelocity * 1.2, 20,
                SurfaceFace.TOP, EmissionShape.VOLUME, Vector3.ZERO, 0.2, -180, 180, -20, 20,
                size * 0.6, size * 2.4, 1 - opacity, 1, smoke.color, smoke.color, 1,
                0, 1, ParticleOrientation.FACING_CAMERA, false, smoke.timeScale);
    }

    public static ParticleLook of(Sparkles sparkles) {
        return new ParticleLook(GLINT, 18, 0.8, 1.4, 1.5, 3, 180,
                SurfaceFace.TOP, EmissionShape.VOLUME, new Vector3(0, -1.5, 0), 1, 0, 360, -90, 90,
                0.35, 0.05, 0, 0.6, sparkles.sparkleColor, sparkles.sparkleColor, 1.5,
                1, 0, ParticleOrientation.FACING_CAMERA, false, sparkles.timeScale);
    }
}
