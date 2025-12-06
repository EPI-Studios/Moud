package com.moud.server.assets.objatlas.obj;

import com.moud.api.math.Vector2;
import com.moud.api.math.Vector3;

import java.util.List;
import java.util.Map;

/**
 * Data model for a parsed OBJ file.
 */
public record ObjModel(
        String name,
        List<Vector3> positions,
        List<Vector3> normals,
        List<Vector2> texCoords,
        List<ObjFace> faces,
        Map<String, ObjMaterial> materials
) {
}
