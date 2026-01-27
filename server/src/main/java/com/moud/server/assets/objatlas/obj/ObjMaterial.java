package com.moud.server.assets.objatlas.obj;

import com.moud.server.assets.objatlas.texture.RgbColor;

/**
 * Material definition from an MTL file.
 */
public record ObjMaterial(
        String name,
        RgbColor ambient,          // Ka
        RgbColor diffuse,          // Kd
        RgbColor specular,         // Ks
        RgbColor emissive,         // Ke
        float shininess,           // Ns
        float opticalDensity,      // Ni
        float dissolve,            // d (opacity)
        int illuminationModel,     // illum
        String mapDiffuse,         // map_Kd
        String mapAmbient,         // map_Ka
        String mapSpecular,        // map_Ks
        String mapBump             // map_Bump / bump
) {
}
