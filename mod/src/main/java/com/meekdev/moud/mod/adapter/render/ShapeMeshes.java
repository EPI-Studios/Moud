package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.instanced.MeshData;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.part.PartShape;
import com.meekdev.moud.core.part.Shapes;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ShapeMeshes {

    private static final Map<PartShape, MeshData> BY_SHAPE = new EnumMap<>(PartShape.class);

    private ShapeMeshes() {}

    public static MeshData of(PartShape shape) {
        MeshData baked = BY_SHAPE.get(shape);
        if (baked != null) return baked;
        MeshData made = shape == PartShape.BLOCK ? MeshData.unitCube() : bake(shape);
        BY_SHAPE.put(shape, made);
        return made;
    }

    public static Vector3 scale(Part part) {
        return Shapes.drawScale(part.shape, part.size);
    }

    private static MeshData bake(PartShape shape) {
        List<Vector3[]> faces = Shapes.triangles(shape, Vector3.ONE);
        float[] points = new float[faces.size() * 9];
        int at = 0;
        for (Vector3[] face : faces) {
            for (Vector3 corner : face) {
                points[at++] = (float) corner.x();
                points[at++] = (float) corner.y();
                points[at++] = (float) corner.z();
            }
        }
        return MeshData.of(points);
    }
}
