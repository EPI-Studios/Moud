package com.moud.client.fabric.render.scene.subrender.particle;

import com.moud.client.fabric.render.scene.util.NodePropertyUtils;
import com.moud.net.protocol.SceneSnapshot;

public final class EmitterConfig {
    public boolean emitting;
    public boolean oneShot;
    public int burstCount;
    public float prewarm;

    public float rate;
    public int maxParticles;
    public float lifetime;
    public float lifetimeVariance;
    public boolean localSpace;
    public long seed;

    public String shapeType;
    public float shapeX, shapeY, shapeZ;
    public boolean surfaceEmit;
    public float jitterX, jitterY, jitterZ;

    public float velocityX, velocityY, velocityZ;
    public float velocityRandom;
    public float spreadDeg;
    public float gravity;
    public float damping;
    public float windX, windY, windZ;
    public float turbStrength, turbScale, turbSpeed;

    public boolean collision;
    public float bounce;
    public float friction;

    public float sizeStart, sizeEnd;
    public ParticleCurve sizeCurve;
    public ParticleCurve alphaCurve;
    public boolean useAlphaCurve;

    public float cr0, cg0, cb0, ca0;
    public float cr1, cg1, cb1, ca1;

    public float rotationStart, rotationVariance;
    public float angularVelocity, angularVariance;

    public String billboardMode;
    public float stretchScale;

    public String texture;
    public int hframes, vframes, frameCount;
    public float frameFps;
    public int frameStart;
    public boolean frameRandomStart;

    public boolean unlit;
    public boolean additive;

    public int subEmitCount;
    public float subEmitLifetime;
    public float subEmitSpeed;
    public float subEmitSize;
    public boolean subEmitInheritVelocity;

    public boolean softParticles;
    public float softFadeDistance;
    public boolean distortion;
    public float distortionStrength;
    public float inheritVelocity;

    public float lodDistance;
    public float lodRateScale;
    public float lodSizeScale;
    public float cullRadius;

    public boolean visible;

    public static EmitterConfig parse(SceneSnapshot.NodeSnapshot node) {
        EmitterConfig c = new EmitterConfig();
        c.visible = NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "visible"), true);
        c.emitting = NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "emitting"), true);
        c.oneShot = NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "one_shot"), false);
        c.burstCount = Math.max(0, (int) NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "burst_count"), 0f));
        c.prewarm = Math.max(0f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "prewarm"), 0f));

        c.rate = Math.max(0f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "rate"), 20f));
        c.maxParticles = Math.max(0, (int) NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "max_particles"), 200f));
        c.lifetime = Math.max(0.01f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "lifetime"), 1.5f));
        c.lifetimeVariance = Math.max(0f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "lifetime_variance"), 0.3f));
        c.localSpace = NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "local_space"), false);
        c.seed = (long) NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "seed"), 0f);

        String shape = NodePropertyUtils.stringProp(node, "shape_type");
        c.shapeType = shape == null ? "point" : shape.trim().toLowerCase();
        c.shapeX = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "shape_size_x"), NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "shape_size"), 0f));
        c.shapeY = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "shape_size_y"), c.shapeX);
        c.shapeZ = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "shape_size_z"), c.shapeX);
        c.surfaceEmit = NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "surface_emit"), false);
        c.jitterX = Math.max(0f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "jitter_x"), 0f));
        c.jitterY = Math.max(0f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "jitter_y"), 0f));
        c.jitterZ = Math.max(0f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "jitter_z"), 0f));

        c.velocityX = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "velocity_x"), 0f);
        c.velocityY = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "velocity_y"), 1f);
        c.velocityZ = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "velocity_z"), 0f);
        c.velocityRandom = Math.max(0f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "velocity_random"), 0.5f));
        c.spreadDeg = Math.max(0f, Math.min(180f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "spread"), 30f)));
        c.gravity = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "gravity"), -2f);
        c.damping = Math.max(0f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "damping"), 0f));
        c.windX = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "wind_x"), 0f);
        c.windY = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "wind_y"), 0f);
        c.windZ = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "wind_z"), 0f);
        c.turbStrength = Math.max(0f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "turbulence_strength"), 0f));
        c.turbScale = Math.max(0.01f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "turbulence_scale"), 1f));
        c.turbSpeed = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "turbulence_speed"), 1f);

        c.collision = NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "collision"), false);
        c.bounce = NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "bounce"), 0.3f));
        c.friction = NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "collision_friction"), 0.5f));

        c.sizeStart = Math.max(0f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "size_start"), 0.3f));
        c.sizeEnd = Math.max(0f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "size_end"), 0f));
        c.sizeCurve = ParticleCurve.parse(NodePropertyUtils.stringProp(node, "size_curve"), 1f);
        String alphaSpec = NodePropertyUtils.stringProp(node, "alpha_curve");
        c.useAlphaCurve = alphaSpec != null && !alphaSpec.isBlank();
        c.alphaCurve = ParticleCurve.parse(alphaSpec, 1f);

        c.cr0 = NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "color_start_r"), 1f));
        c.cg0 = NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "color_start_g"), 1f));
        c.cb0 = NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "color_start_b"), 1f));
        c.ca0 = NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "color_start_a"), 1f));
        c.cr1 = NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "color_end_r"), 1f));
        c.cg1 = NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "color_end_g"), 1f));
        c.cb1 = NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "color_end_b"), 1f));
        c.ca1 = NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "color_end_a"), 0f));

        c.rotationStart = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "rotation_start"), 0f);
        c.rotationVariance = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "rotation_variance"), 0f);
        c.angularVelocity = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "angular_velocity"), 0f);
        c.angularVariance = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "angular_variance"), 0f);

        String billboard = NodePropertyUtils.stringProp(node, "billboard_mode");
        c.billboardMode = billboard == null ? "camera" : billboard.trim().toLowerCase();
        c.stretchScale = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "stretch_scale"), 1f);

        c.texture = NodePropertyUtils.stringProp(node, "texture");
        c.hframes = Math.max(1, (int) NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "hframes"), 1f));
        c.vframes = Math.max(1, (int) NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "vframes"), 1f));
        int totalFrames = c.hframes * c.vframes;
        c.frameCount = (int) NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "frame_count"), 0f);
        if (c.frameCount <= 0 || c.frameCount > totalFrames) c.frameCount = totalFrames;
        c.frameFps = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "frame_fps"), 0f);
        c.frameStart = Math.max(0, (int) NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "frame_start"), 0f));
        c.frameRandomStart = NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "frame_random_start"), false);

        c.unlit = NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "unlit"), false);
        c.additive = NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "additive"), false);

        c.subEmitCount = Math.max(0, (int) NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "sub_emit_count"), 0f));
        c.subEmitLifetime = Math.max(0.01f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "sub_emit_lifetime"), 0.5f));
        c.subEmitSpeed = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "sub_emit_speed"), 1f);
        c.subEmitSize = Math.max(0f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "sub_emit_size"), 0.1f));
        c.subEmitInheritVelocity = NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "sub_emit_inherit_velocity"), true);

        c.softParticles = NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "soft_particles"), false);
        c.softFadeDistance = Math.max(0f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "soft_fade_distance"), 0.5f));
        c.distortion = NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "distortion"), false);
        c.distortionStrength = Math.max(0f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "distortion_strength"), 0.3f));
        c.inheritVelocity = NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "inherit_velocity"), 0f));

        c.lodDistance = Math.max(0f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "lod_distance"), 32f));
        c.lodRateScale = Math.max(0f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "lod_rate_scale"), 0.5f));
        c.lodSizeScale = Math.max(0.1f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "lod_size_scale"), 1.5f));
        c.cullRadius = Math.max(0f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "cull_radius"), 8f));

        return c;
    }

    public boolean hasSpread() {
        return spreadDeg > 0.001f;
    }
}
