package com.moud.core.mesh.sdf;

@FunctionalInterface
public interface ScalarField {
    float sample(float x, float y, float z);
}
