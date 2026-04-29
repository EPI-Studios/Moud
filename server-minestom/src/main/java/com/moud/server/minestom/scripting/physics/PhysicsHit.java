package com.moud.server.minestom.scripting.physics;

import com.moud.physics.api.RaycastHit;
import com.moud.server.minestom.physics.rapier.RapierScenePhysicsWorld;
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
    private final long nodeId;

    public PhysicsHit(RaycastHit r) {
        this(r, null);
    }

    public PhysicsHit(RaycastHit r, RapierScenePhysicsWorld world) {
        this.x = r.point().x();
        this.y = r.point().y();
        this.z = r.point().z();
        this.nx = r.normal().x();
        this.ny = r.normal().y();
        this.nz = r.normal().z();
        this.distance = r.distance();
        long bId = r.body() != null ? r.body().id() : 0L;
        this.bodyId = bId;
        long resolvedNode = -1L;
        if (world != null && bId != 0L) {
            Long candidate = world.nodeIdForBody(bId);
            if (candidate != null) resolvedNode = candidate;
        }
        this.nodeId = resolvedNode;
    }

    @HostAccess.Export public double x() { return x; }
    @HostAccess.Export public double y() { return y; }
    @HostAccess.Export public double z() { return z; }
    @HostAccess.Export public double nx() { return nx; }
    @HostAccess.Export public double ny() { return ny; }
    @HostAccess.Export public double nz() { return nz; }
    @HostAccess.Export public double distance() { return distance; }
    @HostAccess.Export public long bodyId() { return bodyId; }
    @HostAccess.Export public long nodeId() { return nodeId; }
}
