package com.moud.core.mesh.obj;

import java.util.List;

public record ObjModel(
        float[] positions,
        float[] uvs,
        float[] normals,
        List<ObjGroup> groups,
        String mtlLibName
) {
    public ObjModel {
        positions = positions == null ? new float[0] : positions;
        uvs = uvs == null ? new float[0] : uvs;
        normals = normals == null ? new float[0] : normals;
        groups = groups == null ? List.of() : List.copyOf(groups);
    }
}
