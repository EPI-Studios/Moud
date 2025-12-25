package com.moud.client.primitives;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.List;

public class CollisionTask {
    final long id;
    final MoudPackets.PrimitiveType type;
    final Vector3 pos;
    final Quaternion rot;
    final Vector3 scale;
    final List<Vector3> vertices;
    final List<Integer> indices;

    public CollisionTask(ClientPrimitive p) {
        this.id = p.getId();
        this.type = p.getType();
        this.pos = p.getInterpolatedPosition(0);
        this.rot = p.getInterpolatedRotation(0);
        this.scale = p.getInterpolatedScale(0);
        this.vertices = p.getVertices() == null ? List.of() : new ArrayList<>(p.getVertices());
        this.indices = p.getIndices() == null ? List.of() : new ArrayList<>(p.getIndices());
    }

    public PrimitiveCollisionManager.PrimitiveCollision calculate() {
        List<PrimitiveGeometry.Triangle> triangles = PrimitiveGeometry.generateMesh(type, pos, rot, scale, vertices, indices);

        Box bounds = PrimitiveGeometry.computeBounds(triangles);
        if (bounds == null) return null;

        List<Box> boxes = PrimitiveVoxelizer.voxelize(triangles, bounds);

        if (boxes.isEmpty()) {
            return new PrimitiveCollisionManager.PrimitiveCollision(id, bounds, VoxelShapes.cuboid(bounds), List.of());
        }

        List<VoxelShape> shapes = new ArrayList<>(boxes.size());
        for (Box b : boxes) {
            shapes.add(VoxelShapes.cuboid(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ));
        }

        return new PrimitiveCollisionManager.PrimitiveCollision(id, bounds, null, shapes);
    }
}