package com.moud.client.fabric.model;

public record MeshGeometry(
        float x0, float y0, float z0,
        float x1, float y1, float z1,
        float x2, float y2, float z2,
        float x3, float y3, float z3,
        float u0, float v0,
        float u1, float v1,
        float u2, float v2,
        float u3, float v3,
        int textureIndex
) {}
