package com.moud.client.fabric.render.scene.subrender.particle;

public final class Particle {
    public double x, y, z;
    public float vx, vy, vz;
    public float age;
    public float lifetime;
    public float sizeStart, sizeEnd;
    public float r0, g0, b0, a0;
    public float r1, g1, b1, a1;
    public float rotation;
    public float angularVelocity;
    public int frameOffset;
    public float seedNoise;
    public boolean alive = true;
    public boolean grounded;
    public boolean canSubEmit = true;

    public void reset() {
        x = 0; y = 0; z = 0;
        vx = 0; vy = 0; vz = 0;
        age = 0; lifetime = 0;
        sizeStart = 0; sizeEnd = 0;
        r0 = 0; g0 = 0; b0 = 0; a0 = 0;
        r1 = 0; g1 = 0; b1 = 0; a1 = 0;
        rotation = 0; angularVelocity = 0;
        frameOffset = 0;
        seedNoise = 0;
        alive = true;
        grounded = false;
        canSubEmit = true;
    }
}
