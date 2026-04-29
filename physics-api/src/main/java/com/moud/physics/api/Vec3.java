package com.moud.physics.api;

public record Vec3(float x, float y, float z) {
    public static final Vec3 ZERO = new Vec3(0f, 0f, 0f);
    public static final Vec3 Y    = new Vec3(0f, 1f, 0f);
}
