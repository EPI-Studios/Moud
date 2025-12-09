package com.moud.server.editor.runtime;

import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.MoudEngine;
import com.moud.server.zone.ZoneManager;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public final class ZoneRuntimeAdapter implements SceneRuntimeAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZoneRuntimeAdapter.class);
    private String zoneId;

    @Override
    public void create(MoudPackets.SceneObjectSnapshot snapshot) {
        this.zoneId = snapshot.objectId();
        Map<String, Object> props = snapshot.properties();
        Vector3 c1 = readVector(props.get("corner1"), new Vector3(0, 0, 0));
        Vector3 c2 = readVector(props.get("corner2"), new Vector3(1, 1, 1));
        MoudEngine.getInstance().getZoneManager().createZone(zoneId, c1, c2, (Value) null, (Value) null);
        LOGGER.info("Created scene zone '{}' between {} and {}", zoneId, c1, c2);
    }

    @Override
    public void update(MoudPackets.SceneObjectSnapshot snapshot) {
        // recreate
        remove();
        create(snapshot);
    }

    @Override
    public void remove() {
        if (zoneId != null) {
            MoudEngine.getInstance().getZoneManager().removeZone(zoneId);
            LOGGER.info("Removed scene zone '{}'", zoneId);
        }
    }

    private Vector3 readVector(Object raw, Vector3 fallback) {
        if (raw instanceof Map<?, ?> map) {
            double x = toDouble(map.get("x"), fallback.x);
            double y = toDouble(map.get("y"), fallback.y);
            double z = toDouble(map.get("z"), fallback.z);
            return new Vector3(x, y, z);
        }
        return fallback;
    }

    private double toDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value != null ? Double.parseDouble(value.toString()) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
}
