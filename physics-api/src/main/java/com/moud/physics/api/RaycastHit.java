package com.moud.physics.api;

public record RaycastHit(BodyHandle body, Vec3 point, Vec3 normal, float distance) {}
