package com.moud.core.mesh.obj;

import java.util.List;

public record ObjGroup(String materialName, List<int[]> faces) {
    public ObjGroup {
        faces = faces == null ? List.of() : List.copyOf(faces);
    }
}
