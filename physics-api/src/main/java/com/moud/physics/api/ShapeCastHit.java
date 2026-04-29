package com.moud.physics.api;

public record ShapeCastHit(BodyHandle body, Vec3 point, Vec3 normal, float toi) {}
