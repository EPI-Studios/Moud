package com.moud.server.proxy;

import com.moud.server.ts.TsExpose;
import com.moud.api.math.Vector3;
import com.moud.server.zone.ZoneManager;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

@TsExpose
public class ZoneAPIProxy {
    private final ZoneManager zoneManager;
    public ZoneAPIProxy(ZoneManager zoneManager) {
        this.zoneManager = zoneManager;
    }

    @HostAccess.Export
    public void create(String id, Vector3 corner1, Vector3 corner2, Value options) {
        Value onEnter = null;
        Value onLeave = null;

        if (options != null && options.hasMembers()) {
            if (options.hasMember("onEnter")) onEnter = options.getMember("onEnter");
            if (options.hasMember("onLeave")) onLeave = options.getMember("onLeave");
        }

        zoneManager.createZone(id, corner1, corner2, onEnter, onLeave);
    }

    @HostAccess.Export
    public void remove(String id) {
        zoneManager.removeZone(id);
    }

    @HostAccess.Export
    public void setCallbacks(String id, Value options) {
        Value onEnter = null;
        Value onLeave = null;
        if (options != null && options.hasMembers()) {
            if (options.hasMember("onEnter")) onEnter = options.getMember("onEnter");
            if (options.hasMember("onLeave")) onLeave = options.getMember("onLeave");
        }
        zoneManager.setZoneCallbacks(id, onEnter, onLeave);
    }
}
