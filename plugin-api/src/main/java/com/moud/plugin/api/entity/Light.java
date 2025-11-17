package com.moud.plugin.api.entity;

import com.moud.api.math.Vector3;

public interface Light {
    long id();
    Light moveTo(Vector3 position);
    Light color(float r, float g, float b);
    Light radius(float radius);
    Light brightness(float brightness);
    void update();
    void remove();
}
