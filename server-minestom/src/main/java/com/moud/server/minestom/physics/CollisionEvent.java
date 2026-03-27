package com.moud.server.minestom.physics;

import org.graalvm.polyglot.HostAccess;

public final class CollisionEvent {
    private final long nodeIdA;
    private final long nodeIdB;
    private final float contactX;
    private final float contactY;
    private final float contactZ;

    public CollisionEvent(long nodeIdA, long nodeIdB, float contactX, float contactY, float contactZ) {
        this.nodeIdA = nodeIdA;
        this.nodeIdB = nodeIdB;
        this.contactX = contactX;
        this.contactY = contactY;
        this.contactZ = contactZ;
    }

    @HostAccess.Export public long nodeIdA() { return nodeIdA; }
    @HostAccess.Export public long nodeIdB() { return nodeIdB; }
    @HostAccess.Export public float contactX() { return contactX; }
    @HostAccess.Export public float contactY() { return contactY; }
    @HostAccess.Export public float contactZ() { return contactZ; }
}
