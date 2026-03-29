package com.moud.server.minestom.scripting;

import org.graalvm.polyglot.HostAccess;

public final class PlayerInfo {
    private final double x;
    private final double y;
    private final double z;
    private final double ry;
    private final String name;
    private final String uuid;

    PlayerInfo(String uuid, String name, float[] pos) {
        this.uuid = uuid;
        this.name = name != null ? name : uuid;
        this.x  = pos != null && pos.length > 0 ? pos[0] : 0.0;
        this.y  = pos != null && pos.length > 1 ? pos[1] : 0.0;
        this.z  = pos != null && pos.length > 2 ? pos[2] : 0.0;
        this.ry = pos != null && pos.length > 3 ? pos[3] : 0.0;
    }

    @HostAccess.Export public double x()    { return x; }
    @HostAccess.Export public double y()    { return y; }
    @HostAccess.Export public double z()    { return z; }
    @HostAccess.Export public double ry()   { return ry; }
    @HostAccess.Export public String name() { return name; }
    @HostAccess.Export public String uuid() { return uuid; }
}
