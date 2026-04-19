package com.moud.client.fabric.model;

import java.util.List;

public record BoneNode(
        String name,
        String uuid,
        float pivotX,
        float pivotY,
        float pivotZ,
        float posX,
        float posY,
        float posZ,
        float rotX,
        float rotY,
        float rotZ,
        List<CubeGeometry> cubes,
        List<MeshGeometry> meshes,
        List<BoneNode> children
) {
    public BoneNode {
        cubes = List.copyOf(cubes);
        meshes = List.copyOf(meshes);
        children = List.copyOf(children);
    }
}
