package com.moud.client.fabric.scripting.api;

public final class ClientRayHit {
    private final double x, y, z, nx, ny, nz, distance;

    public ClientRayHit(double x, double y, double z,
                        double nx, double ny, double nz,
                        double distance) {
        this.x = x; this.y = y; this.z = z;
        this.nx = nx; this.ny = ny; this.nz = nz;
        this.distance = distance;
    }

    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public double nx() { return nx; }
    public double ny() { return ny; }
    public double nz() { return nz; }
    public double distance() { return distance; }
}
