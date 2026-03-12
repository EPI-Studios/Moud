package com.moud.client.fabric.model;

import java.util.List;

public record BoneNode(
        String name,
        String uuid,
        float pivotX,
        float pivotY,
        float pivotZ,
        List<CubeGeometry> cubes,
        List<BoneNode> children
) {
    public BoneNode {
        cubes = List.copyOf(cubes);
        children = List.copyOf(children);
    }
}
