package com.moud.server.minestom.scripting.player;

import org.graalvm.polyglot.HostAccess;

import java.util.Map;

public final class PlayerInfo {
    private final double x;
    private final double y;
    private final double z;
    private final double ry;
    private final String name;
    private final String uuid;
    private final Map<String, String> clientState;

    public PlayerInfo(String uuid, String name, float[] pos, Map<String, String> clientState) {
        this.uuid = uuid;
        this.name = name != null ? name : uuid;
        this.x  = pos != null && pos.length > 0 ? pos[0] : 0.0;
        this.y  = pos != null && pos.length > 1 ? pos[1] : 0.0;
        this.z  = pos != null && pos.length > 2 ? pos[2] : 0.0;
        this.ry = pos != null && pos.length > 3 ? pos[3] : 0.0;
        this.clientState = clientState == null ? Map.of() : clientState;
    }

    @HostAccess.Export public double x()    { return x; }
    @HostAccess.Export public double y()    { return y; }
    @HostAccess.Export public double z()    { return z; }
    @HostAccess.Export public double ry()   { return ry; }
    @HostAccess.Export public String name() { return name; }
    @HostAccess.Export public String uuid() { return uuid; }
    @HostAccess.Export public String state(String key) { return key == null ? "" : clientState.getOrDefault(key, ""); }
}
