package com.moud.api.physics.player;

public record PlayerState(
        double x,
        double y,
        double z,
        float velX,
        float velY,
        float velZ,
        boolean onGround,
        boolean collidingHorizontally
) {
    public static PlayerState at(double x, double y, double z) {
        return new PlayerState(x, y, z, 0f, 0f, 0f, false, false);
    }
}

