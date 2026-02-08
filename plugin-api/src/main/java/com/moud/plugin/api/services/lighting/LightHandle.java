package com.moud.plugin.api.services.lighting;

public interface LightHandle extends AutoCloseable {
    long id();
    PointLightDefinition definition();
    void update(PointLightDefinition definition);
    void remove();

    @Override
    default void close() {
        remove();
    }
}
