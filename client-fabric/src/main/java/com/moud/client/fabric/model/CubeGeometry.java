package com.moud.client.fabric.model;

public record CubeGeometry(
        float fromX, float fromY, float fromZ,
        float toX,   float toY,   float toZ,
        float originX, float originY, float originZ,
        float rotX, float rotY, float rotZ,
        float inflate,
        FaceUV north, FaceUV south, FaceUV east, FaceUV west, FaceUV up, FaceUV down
) {
    public record FaceUV(float u1, float v1, float u2, float v2, int rotation, int textureIndex) {}
}
