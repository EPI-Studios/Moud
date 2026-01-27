package com.moud.server.assets.objatlas.texture;

public record TextureRegion(
        int x,
        int y,
        int width,
        int height,
        float uOffset,
        float vOffset,
        float uScale,
        float vScale
) {
}
