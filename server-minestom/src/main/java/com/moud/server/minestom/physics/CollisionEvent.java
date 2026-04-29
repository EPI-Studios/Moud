package com.moud.server.minestom.physics;

import org.graalvm.polyglot.HostAccess;

public final class CollisionEvent {
    private final long nodeIdA;
    private final long nodeIdB;
    private final float contactX;
    private final float contactY;
    private final float contactZ;
    private final float normalX;
    private final float normalY;
    private final float normalZ;
    private final float relVelX;
    private final float relVelY;
    private final float relVelZ;

    public CollisionEvent(long nodeIdA, long nodeIdB, float contactX, float contactY, float contactZ) {
        this(nodeIdA, nodeIdB, contactX, contactY, contactZ, 0f, 0f, 0f, 0f, 0f, 0f);
    }

    public CollisionEvent(long nodeIdA, long nodeIdB,
                          float contactX, float contactY, float contactZ,
                          float normalX, float normalY, float normalZ,
                          float relVelX, float relVelY, float relVelZ) {
        this.nodeIdA = nodeIdA;
        this.nodeIdB = nodeIdB;
        this.contactX = contactX;
        this.contactY = contactY;
        this.contactZ = contactZ;
        this.normalX = normalX;
        this.normalY = normalY;
        this.normalZ = normalZ;
        this.relVelX = relVelX;
        this.relVelY = relVelY;
        this.relVelZ = relVelZ;
    }

    @HostAccess.Export public long nodeIdA() { return nodeIdA; }
    @HostAccess.Export public long nodeIdB() { return nodeIdB; }
    @HostAccess.Export public float contactX() { return contactX; }
    @HostAccess.Export public float contactY() { return contactY; }
    @HostAccess.Export public float contactZ() { return contactZ; }
    @HostAccess.Export public float normalX() { return normalX; }
    @HostAccess.Export public float normalY() { return normalY; }
    @HostAccess.Export public float normalZ() { return normalZ; }
    @HostAccess.Export public float relVelX() { return relVelX; }
    @HostAccess.Export public float relVelY() { return relVelY; }
    @HostAccess.Export public float relVelZ() { return relVelZ; }
}
