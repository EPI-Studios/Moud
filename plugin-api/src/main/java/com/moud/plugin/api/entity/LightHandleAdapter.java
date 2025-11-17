package com.moud.plugin.api.entity;

import com.moud.api.math.Vector3;
import com.moud.plugin.api.services.lighting.LightHandle;
import com.moud.plugin.api.services.lighting.PointLightDefinition;

public final class LightHandleAdapter implements Light {
    private final LightHandle handle;
    private PointLightDefinition definition;

    public LightHandleAdapter(LightHandle handle) {
        this.handle = handle;
        this.definition = handle.definition();
    }

    @Override
    public long id() {
        return handle.id();
    }

    @Override
    public Light moveTo(Vector3 position) {
        definition = new PointLightDefinition(
                definition.type(),
                position,
                definition.direction(),
                definition.red(),
                definition.green(),
                definition.blue(),
                definition.brightness(),
                definition.radius()
        );
        return this;
    }

    @Override
    public Light color(float r, float g, float b) {
        definition = new PointLightDefinition(
                definition.type(),
                definition.position(),
                definition.direction(),
                r, g, b,
                definition.brightness(),
                definition.radius()
        );
        return this;
    }

    @Override
    public Light radius(float radius) {
        definition = new PointLightDefinition(
                definition.type(),
                definition.position(),
                definition.direction(),
                definition.red(),
                definition.green(),
                definition.blue(),
                definition.brightness(),
                radius
        );
        return this;
    }

    @Override
    public Light brightness(float brightness) {
        definition = new PointLightDefinition(
                definition.type(),
                definition.position(),
                definition.direction(),
                definition.red(),
                definition.green(),
                definition.blue(),
                brightness,
                definition.radius()
        );
        return this;
    }

    @Override
    public void update() {
        handle.update(definition);
    }

    @Override
    public void remove() {
        handle.remove();
    }
}
