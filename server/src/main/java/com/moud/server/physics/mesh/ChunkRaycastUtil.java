package com.moud.server.physics.mesh;

import com.moud.api.math.Vector3;
import com.moud.server.raycast.RaycastResult;
import com.moud.server.raycast.RaycastUtil;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ChunkRaycastUtil {
    private ChunkRaycastUtil() {
    }

    public static @Nullable Hit raycastDown(Instance instance, Vector3 origin, double maxDistance) {
        if (instance == null || origin == null) {
            return null;
        }
        double maxDist = maxDistance > 0 ? maxDistance : 256.0;
        int chunkX = (int) Math.floor(origin.x / 16.0);
        int chunkZ = (int) Math.floor(origin.z / 16.0);
        List<Face> faces = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk chunk = instance.getChunk(chunkX + dx, chunkZ + dz);
                if (chunk == null) continue;
                List<Face> collected = ChunkMesher.collectFaces(chunk, false);
                if (collected != null && !collected.isEmpty()) {
                    faces.addAll(collected);
                }
            }
        }
        Vector3 rayOrigin = new Vector3(origin);
        Vector3 direction = Vector3.down();
        double closest = maxDist;
        Vector3 bestPos = null;
        Vector3 bestNormal = null;
        if (!faces.isEmpty()) {
            List<TriangleData> tris = new ArrayList<>(faces.size() * 2);
            for (Face face : faces) {
                addTriangles(face, tris);
            }
            for (TriangleData tri : tris) {
                double t = intersectRayTriangle(rayOrigin, direction, tri);
                if (t >= 0 && t < closest) {
                    closest = t;
                    bestPos = rayOrigin.add(direction.multiply(t));
                    bestNormal = tri.normal;
                }
            }
        }
        if (bestPos == null || bestNormal == null) {
            RaycastResult hit = RaycastUtil.performRaycast(
                    instance,
                    new Pos(origin.x, origin.y, origin.z),
                    new Vec(0, -1, 0),
                    maxDist,
                    null
            );
            if (hit != null && hit.didHit()) {
                bestPos = hit.position();
                bestNormal = hit.normal() != null ? hit.normal() : Vector3.up();
                closest = hit.distance();
            }
        }
        if (bestPos == null || bestNormal == null) {
            return null;
        }
        return new Hit(bestPos, bestNormal.normalize(), closest);
    }

    private static void addTriangles(Face face, List<TriangleData> out) {
        Vector3 p1;
        Vector3 p2;
        Vector3 p3;
        Vector3 p4;
        switch (face.blockFace()) {
            case TOP -> {
                p1 = new Vector3(face.minX() + face.blockX(), face.maxY() + face.blockY(), face.minZ() + face.blockZ());
                p2 = new Vector3(face.maxX() + face.blockX(), face.maxY() + face.blockY(), face.minZ() + face.blockZ());
                p3 = new Vector3(face.maxX() + face.blockX(), face.maxY() + face.blockY(), face.maxZ() + face.blockZ());
                p4 = new Vector3(face.minX() + face.blockX(), face.maxY() + face.blockY(), face.maxZ() + face.blockZ());
            }
            case BOTTOM -> {
                p1 = new Vector3(face.maxX() + face.blockX(), face.maxY() + face.blockY(), face.maxZ() + face.blockZ());
                p2 = new Vector3(face.maxX() + face.blockX(), face.maxY() + face.blockY(), face.minZ() + face.blockZ());
                p3 = new Vector3(face.minX() + face.blockX(), face.maxY() + face.blockY(), face.minZ() + face.blockZ());
                p4 = new Vector3(face.minX() + face.blockX(), face.maxY() + face.blockY(), face.maxZ() + face.blockZ());
            }
            case WEST -> {
                p1 = new Vector3(face.maxX() + face.blockX(), face.minY() + face.blockY(), face.minZ() + face.blockZ());
                p2 = new Vector3(face.maxX() + face.blockX(), face.maxY() + face.blockY(), face.minZ() + face.blockZ());
                p3 = new Vector3(face.maxX() + face.blockX(), face.maxY() + face.blockY(), face.maxZ() + face.blockZ());
                p4 = new Vector3(face.maxX() + face.blockX(), face.minY() + face.blockY(), face.maxZ() + face.blockZ());
            }
            case EAST -> {
                p1 = new Vector3(face.maxX() + face.blockX(), face.maxY() + face.blockY(), face.maxZ() + face.blockZ());
                p2 = new Vector3(face.maxX() + face.blockX(), face.maxY() + face.blockY(), face.minZ() + face.blockZ());
                p3 = new Vector3(face.maxX() + face.blockX(), face.minY() + face.blockY(), face.minZ() + face.blockZ());
                p4 = new Vector3(face.maxX() + face.blockX(), face.minY() + face.blockY(), face.maxZ() + face.blockZ());
            }
            case SOUTH -> {
                p1 = new Vector3(face.maxX() + face.blockX(), face.maxY() + face.blockY(), face.minZ() + face.blockZ());
                p2 = new Vector3(face.maxX() + face.blockX(), face.minY() + face.blockY(), face.minZ() + face.blockZ());
                p3 = new Vector3(face.minX() + face.blockX(), face.minY() + face.blockY(), face.minZ() + face.blockZ());
                p4 = new Vector3(face.minX() + face.blockX(), face.maxY() + face.blockY(), face.minZ() + face.blockZ());
            }
            case NORTH -> {
                p1 = new Vector3(face.minX() + face.blockX(), face.minY() + face.blockY(), face.minZ() + face.blockZ());
                p2 = new Vector3(face.maxX() + face.blockX(), face.minY() + face.blockY(), face.minZ() + face.blockZ());
                p3 = new Vector3(face.maxX() + face.blockX(), face.maxY() + face.blockY(), face.minZ() + face.blockZ());
                p4 = new Vector3(face.minX() + face.blockX(), face.maxY() + face.blockY(), face.minZ() + face.blockZ());
            }
            default -> {
                return;
            }
        }
        Vector3 normal1 = p2.subtract(p3).cross(p1.subtract(p3));
        out.add(new TriangleData(p3, p2, p1, normal1));
        Vector3 normal2 = p4.subtract(p1).cross(p3.subtract(p1));
        out.add(new TriangleData(p1, p4, p3, normal2));
    }

    private static double intersectRayTriangle(Vector3 origin, Vector3 dir, TriangleData tri) {
        Vector3 edge1 = tri.b.subtract(tri.a);
        Vector3 edge2 = tri.c.subtract(tri.a);
        Vector3 pvec = dir.cross(edge2);
        double det = edge1.dot(pvec);
        if (Math.abs(det) < 1e-6) {
            return -1;
        }
        double invDet = 1.0 / det;
        Vector3 tvec = origin.subtract(tri.a);
        double u = tvec.dot(pvec) * invDet;
        if (u < 0 || u > 1) {
            return -1;
        }
        Vector3 qvec = tvec.cross(edge1);
        double v = dir.dot(qvec) * invDet;
        if (v < 0 || u + v > 1) {
            return -1;
        }
        double t = edge2.dot(qvec) * invDet;
        return t >= 0 ? t : -1;
    }

    private record TriangleData(Vector3 a, Vector3 b, Vector3 c, Vector3 normal) {
    }

    public record Hit(Vector3 position, Vector3 normal, double distance) {
    }
}