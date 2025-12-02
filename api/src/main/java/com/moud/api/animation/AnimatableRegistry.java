package com.moud.api.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AnimatableRegistry {
    private static final Map<String, List<AnimatableProperty>> REGISTRY = new ConcurrentHashMap<>();
    private static volatile boolean defaultsRegistered;

    private AnimatableRegistry() {
    }

    public static void register(String objectType, List<AnimatableProperty> properties) {
        REGISTRY.put(objectType.toLowerCase(), List.copyOf(properties));
    }

    public static List<AnimatableProperty> getProperties(String objectType) {
        if (objectType == null) {
            return Collections.emptyList();
        }
        return REGISTRY.getOrDefault(objectType.toLowerCase(), Collections.emptyList());
    }

    public static List<AnimatableProperty> getAllProperties(String objectType) {
        List<AnimatableProperty> out = new ArrayList<>(getProperties("transform"));
        out.addAll(getProperties(objectType));
        return out;
    }

    public static synchronized void registerDefaults() {
        if (defaultsRegistered) {
            return;
        }
        defaultsRegistered = true;
        List<AnimatableProperty> transform = List.of(
                new AnimatableProperty("position.x", "Position X", "Transform", -1024f, 1024f, 0f, PropertyTrack.PropertyType.FLOAT),
                new AnimatableProperty("position.y", "Position Y", "Transform", -1024f, 1024f, 0f, PropertyTrack.PropertyType.FLOAT),
                new AnimatableProperty("position.z", "Position Z", "Transform", -1024f, 1024f, 0f, PropertyTrack.PropertyType.FLOAT),
                new AnimatableProperty("rotation.x", "Rotation X", "Transform", -360f, 360f, 0f, PropertyTrack.PropertyType.ANGLE),
                new AnimatableProperty("rotation.y", "Rotation Y", "Transform", -360f, 360f, 0f, PropertyTrack.PropertyType.ANGLE),
                new AnimatableProperty("rotation.z", "Rotation Z", "Transform", -360f, 360f, 0f, PropertyTrack.PropertyType.ANGLE),
                new AnimatableProperty("scale.x", "Scale X", "Transform", 0f, 64f, 1f, PropertyTrack.PropertyType.FLOAT),
                new AnimatableProperty("scale.y", "Scale Y", "Transform", 0f, 64f, 1f, PropertyTrack.PropertyType.FLOAT),
                new AnimatableProperty("scale.z", "Scale Z", "Transform", 0f, 64f, 1f, PropertyTrack.PropertyType.FLOAT)
        );
        register("transform", transform);

        register("light", List.of(
                new AnimatableProperty("intensity", "Intensity", "Light", 0f, 10f, 1f, PropertyTrack.PropertyType.FLOAT),
                new AnimatableProperty("color.r", "Color R", "Light", 0f, 1f, 1f, PropertyTrack.PropertyType.COLOR),
                new AnimatableProperty("color.g", "Color G", "Light", 0f, 1f, 1f, PropertyTrack.PropertyType.COLOR),
                new AnimatableProperty("color.b", "Color B", "Light", 0f, 1f, 1f, PropertyTrack.PropertyType.COLOR),
                new AnimatableProperty("range", "Range", "Light", 0f, 128f, 8f, PropertyTrack.PropertyType.FLOAT)
        ));

        register("display", List.of(
                new AnimatableProperty("opacity", "Opacity", "Display", 0f, 1f, 1f, PropertyTrack.PropertyType.FLOAT)
        ));

        register("camera", List.of(
                new AnimatableProperty("fov", "Field of View", "Camera", 10f, 170f, 70f, PropertyTrack.PropertyType.FLOAT),
                new AnimatableProperty("near", "Near", "Camera", 0.01f, 10f, 0.1f, PropertyTrack.PropertyType.FLOAT),
                new AnimatableProperty("far", "Far", "Camera", 1f, 2048f, 128f, PropertyTrack.PropertyType.FLOAT)
        ));

        register("particle_emitter", List.of(
                new AnimatableProperty("enabled", "Enabled", "Emitter", 0f, 1f, 1f, PropertyTrack.PropertyType.FLOAT),
                new AnimatableProperty("rate", "Rate (pps)", "Emitter", 0f, 100_000f, 10f, PropertyTrack.PropertyType.FLOAT),
                new AnimatableProperty("maxParticles", "Max Particles", "Emitter", 0f, 100_000f, 1024f, PropertyTrack.PropertyType.FLOAT),
                new AnimatableProperty("lifetime", "Lifetime", "Emitter", 0.01f, 120f, 1f, PropertyTrack.PropertyType.FLOAT),
                new AnimatableProperty("gravityMultiplier", "Gravity Multiplier", "Emitter", -10f, 10f, 1f, PropertyTrack.PropertyType.FLOAT),
                new AnimatableProperty("drag", "Drag", "Emitter", 0f, 10f, 0f, PropertyTrack.PropertyType.FLOAT),
                new AnimatableProperty("impostorSlices", "Impostor Slices", "Emitter", 1f, 8f, 1f, PropertyTrack.PropertyType.FLOAT),
                new AnimatableProperty("collideWithPlayers", "Collide With Players", "Emitter", 0f, 1f, 0f, PropertyTrack.PropertyType.FLOAT)
        ));

        List<AnimatableProperty> limbs = new ArrayList<>(transform);
        register("fakeplayer:head", limbs);
        register("fakeplayer:left_arm", limbs);
        register("fakeplayer:right_arm", limbs);
        register("fakeplayer:left_leg", limbs);
        register("fakeplayer:right_leg", limbs);
        register("fakeplayer:torso", limbs);
    }
}
