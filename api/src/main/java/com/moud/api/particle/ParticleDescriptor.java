package com.moud.api.particle;

import java.util.List;
import java.util.Map;
import java.util.Objects;


public record ParticleDescriptor(
        String texture,
        RenderType renderType,
        Billboarding billboarding,
        CollisionMode collisionMode,
        Vector3f position,
        Vector3f velocity,
        Vector3f acceleration,
        float drag,
        float gravityMultiplier,
        float lifetimeSeconds,
        List<ScalarKeyframe> sizeOverLife,
        List<ScalarKeyframe> rotationOverLife,
        List<ColorKeyframe> colorOverLife,
        List<ScalarKeyframe> alphaOverLife,
        UVRegion uvRegion,
        FrameAnimation frameAnimation,
        List<String> behaviors,
        Map<String, Object> behaviorPayload,
        LightSettings light,
        SortHint sortHint
) {
    public ParticleDescriptor {
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(position, "position");

        renderType = renderType == null ? RenderType.TRANSLUCENT : renderType;
        billboarding = billboarding == null ? Billboarding.CAMERA_FACING : billboarding;
        collisionMode = collisionMode == null ? CollisionMode.NONE : collisionMode;

        velocity = velocity == null ? new Vector3f(0f, 0f, 0f) : velocity;
        acceleration = acceleration == null ? new Vector3f(0f, 0f, 0f) : acceleration;

        sizeOverLife = sizeOverLife == null ? List.of() : List.copyOf(sizeOverLife);
        rotationOverLife = rotationOverLife == null ? List.of() : List.copyOf(rotationOverLife);
        colorOverLife = colorOverLife == null ? List.of() : List.copyOf(colorOverLife);
        alphaOverLife = alphaOverLife == null ? List.of() : List.copyOf(alphaOverLife);

        behaviors = behaviors == null ? List.of() : List.copyOf(behaviors);
        behaviorPayload = behaviorPayload == null ? Map.of() : Map.copyOf(behaviorPayload);
        light = light == null ? new LightSettings(0, 0, false) : light;
        sortHint = sortHint == null ? SortHint.NONE : sortHint;

        drag = Float.isNaN(drag) ? 0f : drag;
        gravityMultiplier = Float.isNaN(gravityMultiplier) ? 1f : gravityMultiplier;
        lifetimeSeconds = Math.max(0f, lifetimeSeconds);
    }
}
