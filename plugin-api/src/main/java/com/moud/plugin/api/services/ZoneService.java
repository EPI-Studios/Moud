package com.moud.plugin.api.services;

import com.moud.api.math.Vector3;

public interface ZoneService {
    void create(String id, Vector3 corner1, Vector3 corner2);
    void remove(String id);
}
