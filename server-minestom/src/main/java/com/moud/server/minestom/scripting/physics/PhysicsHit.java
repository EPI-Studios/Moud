package com.moud.server.minestom.scripting.physics;

import com.moud.core.physics.RaycastResult;
import com.moud.server.minestom.physics.JoltPhysicsWorld;
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

    public PhysicsHit(RaycastResult r) {
        this(r, null);
    }

    public PhysicsHit(RaycastResult r, JoltPhysicsWorld world) {
        this.x = r.hitX();
        this.y = r.hitY();
        this.z = r.hitZ();
        this.nx = r.normalX();
        this.ny = r.normalY();
        this.nz = r.normalZ();
        this.distance = r.distance();
        int bId = r.body() != null ? r.body().id() : -1;
        this.bodyId = bId;
        long resolvedNode = -1L;
        if (world != null && bId >= 0) {
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
