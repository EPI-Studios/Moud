package com.moud.server.physics.mesh;

import com.github.stephengold.joltjni.Triangle;
import com.github.stephengold.joltjni.Vec3;
import net.minestom.server.instance.block.BlockFace;

import java.util.List;

record Face(BlockFace blockFace, float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ,
            int blockX, int blockY, int blockZ) {

    Face(BlockFace blockFace, double minX, double minY, double minZ,
         double maxX, double maxY, double maxZ,
         int blockX, int blockY, int blockZ) {
        this(blockFace, (float) minX, (float) minY, (float) minZ,
                (float) maxX, (float) maxY, (float) maxZ,
                blockX, blockY, blockZ);
    }

    boolean isEdge() {
        return switch (blockFace) {
            case BOTTOM -> minY == 0.0f;
            case TOP -> maxY == 1.0f;
            case NORTH -> minZ == 0.0f;
            case SOUTH -> maxZ == 1.0f;
            case WEST -> minX == 0.0f;
            case EAST -> maxX == 1.0f;
        };
    }

    void addTris(List<Triangle> triangles) {
        Vec3 point1;
        Vec3 point2;
        Vec3 point3;
        Vec3 point4;

        switch (blockFace) {
            case TOP -> {
                point1 = new Vec3(minX + blockX, maxY + blockY, minZ + blockZ);
                point2 = new Vec3(maxX + blockX, maxY + blockY, minZ + blockZ);
                point3 = new Vec3(maxX + blockX, maxY + blockY, maxZ + blockZ);
                point4 = new Vec3(minX + blockX, maxY + blockY, maxZ + blockZ);
            }
            case BOTTOM -> {
                point1 = new Vec3(maxX + blockX, maxY + blockY, maxZ + blockZ);
                point2 = new Vec3(maxX + blockX, maxY + blockY, minZ + blockZ);
                point3 = new Vec3(minX + blockX, maxY + blockY, minZ + blockZ);
                point4 = new Vec3(minX + blockX, maxY + blockY, maxZ + blockZ);
            }
            case WEST -> {
                point1 = new Vec3(maxX + blockX, minY + blockY, minZ + blockZ);
                point2 = new Vec3(maxX + blockX, maxY + blockY, minZ + blockZ);
                point3 = new Vec3(maxX + blockX, maxY + blockY, maxZ + blockZ);
                point4 = new Vec3(maxX + blockX, minY + blockY, maxZ + blockZ);
            }
            case EAST -> {
                point1 = new Vec3(maxX + blockX, maxY + blockY, maxZ + blockZ);
                point2 = new Vec3(maxX + blockX, maxY + blockY, minZ + blockZ);
                point3 = new Vec3(maxX + blockX, minY + blockY, minZ + blockZ);
                point4 = new Vec3(maxX + blockX, minY + blockY, maxZ + blockZ);
            }
            case SOUTH -> {
                point1 = new Vec3(maxX + blockX, maxY + blockY, minZ + blockZ);
                point2 = new Vec3(maxX + blockX, minY + blockY, minZ + blockZ);
                point3 = new Vec3(minX + blockX, minY + blockY, minZ + blockZ);
                point4 = new Vec3(minX + blockX, maxY + blockY, minZ + blockZ);
            }
            case NORTH -> {
                point1 = new Vec3(minX + blockX, minY + blockY, minZ + blockZ);
                point2 = new Vec3(maxX + blockX, minY + blockY, minZ + blockZ);
                point3 = new Vec3(maxX + blockX, maxY + blockY, minZ + blockZ);
                point4 = new Vec3(minX + blockX, maxY + blockY, minZ + blockZ);
            }
            default -> throw new IllegalStateException("Unexpected value: " + blockFace);
        }

        triangles.add(new Triangle(point3, point2, point1));
        triangles.add(new Triangle(point1, point4, point3));
    }
}
