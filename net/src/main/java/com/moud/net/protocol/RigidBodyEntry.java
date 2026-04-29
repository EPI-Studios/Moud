package com.moud.net.protocol;

public record RigidBodyEntry(
        long nodeId,
        float px, float py, float pz,
        float qx, float qy, float qz, float qw,
        float vx, float vy, float vz,
        float ax, float ay, float az,
        boolean sleeping
) {
}
