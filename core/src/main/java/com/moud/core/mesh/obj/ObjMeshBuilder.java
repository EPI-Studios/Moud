package com.moud.core.mesh.obj;

import com.moud.core.mesh.ArrayMesh;
import com.moud.core.mesh.MeshPrimitive;
import com.moud.core.mesh.build.MeshBuilder;
import com.moud.core.mesh.build.SurfaceTool;

import java.util.Map;

public final class ObjMeshBuilder {

    private ObjMeshBuilder() {}

    public static ArrayMesh build(ObjModel model, Map<String, MtlMaterial> materials) {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        MeshBuilder mesh = new MeshBuilder();
        float[] positions = model.positions();
        float[] uvs = model.uvs();
        float[] normals = model.normals();

        for (ObjGroup group : model.groups()) {
            if (group.faces().isEmpty()) continue;

            MtlMaterial mtl = materials == null ? null : materials.get(group.materialName());
            String materialId = resolveMaterialId(mtl);

            SurfaceTool tool = beginChunk(mtl, materialId);
            boolean chunkMissingNormal = false;

            for (int[] face : group.faces()) {
                int[] vIdx = new int[3];
                for (int corner = 0; corner < 3; corner++) {
                    int p = face[corner * 3];
                    int u = face[corner * 3 + 1];
                    int n = face[corner * 3 + 2];
                    float px = positions[p * 3];
                    float py = positions[p * 3 + 1];
                    float pz = positions[p * 3 + 2];
                    if (u >= 0 && uvs.length >= (u + 1) * 2) {
                        tool.setUv(uvs[u * 2], uvs[u * 2 + 1]);
                    } else {
                        tool.setUv(0f, 0f);
                    }
                    if (n >= 0 && normals.length >= (n + 1) * 3) {
                        tool.setNormal(normals[n * 3], normals[n * 3 + 1], normals[n * 3 + 2]);
                    } else {
                        tool.setNormal(0f, 1f, 0f);
                        chunkMissingNormal = true;
                    }
                    vIdx[corner] = tool.addVertex(px, py, pz);
                }
                tool.addTriangle(vIdx[0], vIdx[1], vIdx[2]);
            }
            if (chunkMissingNormal) tool.generateFlatNormals();
            mesh.addSurface(tool.end());
        }
        return mesh.build();
    }

    private static SurfaceTool beginChunk(MtlMaterial mtl, String materialId) {
        SurfaceTool tool = SurfaceTool.begin(MeshPrimitive.TRIANGLES);
        if (mtl != null) tool.setColor(mtl.colorR(), mtl.colorG(), mtl.colorB(), mtl.alpha());
        tool.setMaterial(materialId);
        return tool;
    }

    private static String resolveMaterialId(MtlMaterial mtl) {
        if (mtl == null) return "";
        String tex = mtl.diffuseTexture();
        if (tex == null || tex.isBlank()) return "";
        String filename = tex;
        int slash = filename.lastIndexOf('/');
        if (slash >= 0) filename = filename.substring(slash + 1);
        int back = filename.lastIndexOf('\\');
        if (back >= 0) filename = filename.substring(back + 1);
        return "res://textures/" + filename;
    }
}
