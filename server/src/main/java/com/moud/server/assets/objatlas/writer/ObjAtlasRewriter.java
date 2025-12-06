package com.moud.server.assets.objatlas.writer;

import com.moud.api.math.Vector2;
import com.moud.api.math.Vector3;
import com.moud.server.assets.objatlas.obj.ObjFace;
import com.moud.server.assets.objatlas.obj.ObjMaterial;
import com.moud.server.assets.objatlas.obj.ObjModel;
import com.moud.server.assets.objatlas.obj.ObjVertexRef;
import com.moud.server.assets.objatlas.texture.TextureAtlas;
import com.moud.server.assets.objatlas.texture.TextureRegion;

import java.util.*;

/**
 * Rewrites UV coordinates of an ObjModel so that all diffuse textures (map_Kd)
 * are replaced by a single atlas texture.
 *
 * Steps:
 *  - For each material, find its original map_Kd (texture name).
 *  - For each face using that material, remap its UVs into the corresponding atlas region.
 *  - Materials are copied but map_Kd is replaced by the atlas texture filename.
 */
public final class ObjAtlasRewriter {

    public ObjModel rewriteModelForAtlas(ObjModel original, TextureAtlas atlas) {
        List<Vector3> positions = original.positions();
        List<Vector3> normals = original.normals();
        List<Vector2> originalTexCoords = original.texCoords();

        // 1) New texCoords list starts as a copy of the original UVs
        List<Vector2> newTexCoords = new ArrayList<>(originalTexCoords);

        // 2) Cache: (materialName, oldVtIndex) -> newVtIndex
        Map<String, Integer> vtRemapCache = new HashMap<>();

        // 3) Remember original map_Kd per material name
        Map<String, String> materialToOriginalTexture = new HashMap<>();
        for (Map.Entry<String, ObjMaterial> entry : original.materials().entrySet()) {
            String matName = entry.getKey();
            ObjMaterial mat = entry.getValue();
            materialToOriginalTexture.put(matName, mat.mapDiffuse());
        }

        // 4) Rewrite faces
        List<ObjFace> newFaces = new ArrayList<>(original.faces().size());

        for (ObjFace face : original.faces()) {
            String matName = face.materialName();
            String originalTexName = matName != null ? materialToOriginalTexture.get(matName) : null;
            TextureRegion region = null;

            if (originalTexName != null) {
                region = atlas.regionsByTextureName().get(originalTexName);
            }

            List<ObjVertexRef> newVerts = new ArrayList<>(face.vertices().size());

            for (ObjVertexRef vRef : face.vertices()) {
                int posIndex = vRef.positionIndex();
                int normIndex = vRef.normalIndex();
                int texIndex = vRef.texCoordIndex();

                int newTexIndex = texIndex;

                if (texIndex >= 0 && region != null) {
                    // we need to remap this UV
                    String key = matName + "#" + texIndex;
                    Integer cachedIndex = vtRemapCache.get(key);
                    if (cachedIndex == null) {
                        Vector2 uv = originalTexCoords.get(texIndex);

                        float u = uv.x;
                        float v = uv.y;

                        float newU = region.uOffset() + u * region.uScale();
                        float newV = region.vOffset() + v * region.vScale();

                        newTexCoords.add(new Vector2(newU, newV));
                        cachedIndex = newTexCoords.size() - 1;
                        vtRemapCache.put(key, cachedIndex);
                    }
                    newTexIndex = cachedIndex;
                }

                newVerts.add(new ObjVertexRef(posIndex, newTexIndex, normIndex));
            }

            newFaces.add(new ObjFace(
                    List.copyOf(newVerts),
                    face.materialName(),
                    face.groupName(),
                    face.smoothingGroup()
            ));
        }

        // 5) Rewrite materials: same as original but with map_Kd = atlas.atlasFileName()
        Map<String, ObjMaterial> newMaterials = new LinkedHashMap<>();
        for (Map.Entry<String, ObjMaterial> entry : original.materials().entrySet()) {
            String name = entry.getKey();
            ObjMaterial mat = entry.getValue();
            String originalTexName = mat.mapDiffuse();

            String newMapKd = mat.mapDiffuse();
            if (originalTexName != null && atlas.regionsByTextureName().containsKey(originalTexName)) {
                newMapKd = atlas.atlasFileName();
            }

            ObjMaterial newMat = new ObjMaterial(
                    mat.name(),
                    mat.ambient(),
                    mat.diffuse(),
                    mat.specular(),
                    mat.emissive(),
                    mat.shininess(),
                    mat.opticalDensity(),
                    mat.dissolve(),
                    mat.illuminationModel(),
                    newMapKd,
                    mat.mapAmbient(),
                    mat.mapSpecular(),
                    mat.mapBump()
            );
            newMaterials.put(name, newMat);
        }

        return new ObjModel(
                original.name(),
                List.copyOf(positions),
                List.copyOf(normals),
                List.copyOf(newTexCoords),
                List.copyOf(newFaces),
                Map.copyOf(newMaterials)
        );
    }
}
