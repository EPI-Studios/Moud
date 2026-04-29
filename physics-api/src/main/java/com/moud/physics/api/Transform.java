package com.moud.physics.api;

public record Transform(Vec3 pos, Quat rot) {
    public static final Transform IDENTITY = new Transform(Vec3.ZERO, Quat.IDENTITY);
}
