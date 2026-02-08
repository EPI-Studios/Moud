package com.moud.server.proxy;

import com.moud.server.ts.TsExpose;
import com.moud.api.math.Vector3;
import com.moud.server.scripting.PolyglotValueUtil;
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
        if (corner1 == null || corner2 == null) {
            return;
        }
        Value onEnter = null;
        Value onLeave = null;

        if (options != null && options.hasMembers()) {
            if (options.hasMember("onEnter")) onEnter = options.getMember("onEnter");
            if (options.hasMember("onLeave")) onLeave = options.getMember("onLeave");
        }

        zoneManager.createZone(id, corner1, corner2, onEnter, onLeave);
    }

    @HostAccess.Export
    public void create(String id, Vector3 corner1, Vector3 corner2) {
        create(id, corner1, corner2, null);
    }

    @HostAccess.Export
    public void create(String id, Value corner1, Value corner2, Value options) {
        Vector3 c1 = readVector3(corner1);
        Vector3 c2 = readVector3(corner2);
        if (c1 == null || c2 == null) {
            return;
        }
        create(id, c1, c2, options);
    }

    @HostAccess.Export
    public void create(String id, Value corner1, Value corner2) {
        create(id, corner1, corner2, null);
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

    private static Vector3 readVector3(Value value) {
        if (value == null || value.isNull() || !value.hasMembers()) {
            return null;
        }
        double x = PolyglotValueUtil.readDouble(value, "x", Double.NaN);
        double y = PolyglotValueUtil.readDouble(value, "y", Double.NaN);
        double z = PolyglotValueUtil.readDouble(value, "z", Double.NaN);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return null;
        }
        return new Vector3(x, y, z);
    }
}
