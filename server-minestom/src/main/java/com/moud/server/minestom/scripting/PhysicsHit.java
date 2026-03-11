package com.moud.server.minestom.scripting;

import com.moud.core.physics.RaycastResult;
import org.graalvm.polyglot.HostAccess;

public final class PhysicsHit {
    private final double x;
    private final double y;
    private final double z;
    private final double nx;
    private final double ny;
    private final double nz;
    private final double distance;
    private final long bodyId;

    PhysicsHit(RaycastResult r) {
        this.x = r.hitX();
        this.y = r.hitY();
        this.z = r.hitZ();
        this.nx = r.normalX();
        this.ny = r.normalY();
        this.nz = r.normalZ();
        this.distance = r.distance();
        this.bodyId = r.body() != null ? r.body().id() : -1L;
    }

    @HostAccess.Export public double x() { return x; }
    @HostAccess.Export public double y() { return y; }
    @HostAccess.Export public double z() { return z; }
    @HostAccess.Export public double nx() { return nx; }
    @HostAccess.Export public double ny() { return ny; }
    @HostAccess.Export public double nz() { return nz; }
    @HostAccess.Export public double distance() { return distance; }
    @HostAccess.Export public long bodyId() { return bodyId; }
}
