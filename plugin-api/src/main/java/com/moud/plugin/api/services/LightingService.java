package com.moud.plugin.api.services;

import com.moud.plugin.api.services.lighting.LightHandle;
import com.moud.plugin.api.services.lighting.PointLightDefinition;

import java.util.Collection;
import java.util.Optional;

public interface LightingService {
    LightHandle create(PointLightDefinition definition);
    Optional<LightHandle> get(long id);
    Collection<LightHandle> all();
}
