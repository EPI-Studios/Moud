package com.moud.client.zone;

import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientZoneManager {
    private static final Map<String, ZoneBounds> ZONES = new ConcurrentHashMap<>();

    private ClientZoneManager() {
    }

    public static void applySync(List<MoudPackets.ZoneDefinition> zones) {
        ZONES.clear();
        if (zones == null || zones.isEmpty()) {
            return;
        }
        for (MoudPackets.ZoneDefinition zone : zones) {
            upsert(zone);
        }
    }

    public static void upsert(MoudPackets.ZoneDefinition zone) {
        if (zone == null || zone.id() == null || zone.id().isBlank()) {
            return;
        }

        ZoneBounds bounds = ZoneBounds.from(zone.min(), zone.max());
        if (bounds == null) {
            return;
        }
        ZONES.put(zone.id(), bounds);
    }

    public static void remove(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return;
        }
        ZONES.remove(zoneId);
    }

    public static boolean isInZone(double x, double y, double z, String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return false;
        }
        ZoneBounds zone = ZONES.get(zoneId);
        if (zone == null) {
            return false;
        }
        return zone.contains(x, y, z);
    }

    public static List<ZoneSnapshot> snapshot() {
        if (ZONES.isEmpty()) {
            return List.of();
        }
        List<ZoneSnapshot> out = new ArrayList<>(ZONES.size());
        for (Map.Entry<String, ZoneBounds> entry : ZONES.entrySet()) {
            ZoneBounds bounds = entry.getValue();
            if (bounds == null) {
                continue;
            }
            out.add(new ZoneSnapshot(entry.getKey(), bounds));
        }
        return out;
    }

    public static void clear() {
        ZONES.clear();
    }

    public record ZoneSnapshot(String id, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        private ZoneSnapshot(String id, ZoneBounds bounds) {
            this(id, bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
        }
    }

    private record ZoneBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        static ZoneBounds from(Vector3 min, Vector3 max) {
            if (min == null || max == null) {
                return null;
            }
            float minX = Math.min(min.x, max.x);
            float minY = Math.min(min.y, max.y);
            float minZ = Math.min(min.z, max.z);
            float maxX = Math.max(min.x, max.x);
            float maxY = Math.max(min.y, max.y);
            float maxZ = Math.max(min.z, max.z);
            return new ZoneBounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        boolean contains(double x, double y, double z) {
            return x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }
    }
}
