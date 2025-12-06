package com.moud.server.assets.objatlas.texture;

import java.util.Map;

/**
 * Represents a built texture atlas.
 * - atlasFileName : filename to put in map_Kd (e.g. "atlas_diffuse.png")
 * - regionsByTextureName : key = original map_Kd string from MTL
 */
public record TextureAtlas(
        String atlasFileName,
        int width,
        int height,
        Map<String, TextureRegion> regionsByTextureName
) {
}
