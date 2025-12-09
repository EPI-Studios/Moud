package com.moud.server.plugin.impl;

import com.moud.api.math.Vector3;
import com.moud.plugin.api.services.ZoneService;
import com.moud.server.MoudEngine;
import com.moud.server.zone.ZoneManager;

public final class ZoneServiceImpl implements ZoneService {
    private final ZoneManager zoneManager;

    public ZoneServiceImpl() {
        this.zoneManager = MoudEngine.getInstance().getZoneManager();
    }

    @Override
    public void create(String id, Vector3 corner1, Vector3 corner2) {
        zoneManager.createZone(id, corner1, corner2, null, null);
    }

    @Override
    public void remove(String id) {
        zoneManager.removeZone(id);
    }
}
