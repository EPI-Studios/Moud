package com.moud.physics.api;

public record ContactEvent(
        BodyHandle a,
        BodyHandle b,
        Vec3 point,
        Vec3 normal,
        float impulse
) {}
