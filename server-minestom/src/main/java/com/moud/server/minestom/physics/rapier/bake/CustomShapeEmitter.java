package com.moud.server.minestom.physics.rapier.bake;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.Shape;
import net.minestom.server.collision.ShapeImpl;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;

import java.util.List;

/**
 * Emits AABB-face triangles for non-full-cube blocks (slabs, stairs, fences, walls,
 * panes, ...) into the same vertex/index buffers consumed by Rapier's trimesh.
 *
 * <p>The greedy mesher (operating on a boolean full-cube mask) does not see these
 * shapes; the section baker is expected to route those blocks here instead.</p>
 */
public final class CustomShapeEmitter {

    private CustomShapeEmitter() {
    }

    /**
     * Emits the AABB face set of {@code block} placed at section-local
     * ({@code localX},{@code localY},{@code localZ}) into the provided buffers.
     *
     * <p>No-op if the block has empty collision or is a full cube (caller should
     * have routed full cubes through {@link GreedyMesher}).</p>
     */
    public static void emit(Block block, int localX, int localY, int localZ,
                            FloatArrayList verts, IntArrayList indices) {
        Shape shape = block.registry().collisionShape();
        if (shape == null) return;
        if (isFullCubeOrEmpty(block)) return;

        List<BoundingBox> boxes = collisionBoxes(shape);
        if (boxes.isEmpty()) return;

        for (BoundingBox box : boxes) {
            emitBox(verts, indices,
                    localX + (float) box.minX(), localY + (float) box.minY(), localZ + (float) box.minZ(),
                    localX + (float) box.maxX(), localY + (float) box.maxY(), localZ + (float) box.maxZ());
        }
    }

    /**
     * @return {@code true} when {@code block} has empty collision or is a full 1m^3 cube.
     */
    public static boolean isFullCubeOrEmpty(Block block) {
        Shape shape = block.registry().collisionShape();
        if (shape == null) return true;

        List<BoundingBox> boxes = collisionBoxes(shape);
        if (boxes.isEmpty()) return true;

        // Full cube: all six faces fully covered. Cheaper than scanning AABBs.
        for (BlockFace face : BlockFace.values()) {
            if (!shape.isFaceFull(face)) return false;
        }
        // Bounds also need to span [0,1]^3 - paranoia in case isFaceFull only
        // tracks occlusion: re-check via the shape's relative span.
        return shape.relativeStart().x() == 0 && shape.relativeStart().y() == 0 && shape.relativeStart().z() == 0
                && shape.relativeEnd().x() == 1 && shape.relativeEnd().y() == 1 && shape.relativeEnd().z() == 1;
    }

    private static List<BoundingBox> collisionBoxes(Shape shape) {
        if (shape instanceof ShapeImpl impl) {
            return impl.collisionBoundingBoxes();
        }
        // Unknown Shape implementation: fall back to a single bounding-box quad.
        BoundingBox bb = new BoundingBox(
                shape.relativeEnd().x() - shape.relativeStart().x(),
                shape.relativeEnd().y() - shape.relativeStart().y(),
                shape.relativeEnd().z() - shape.relativeStart().z(),
                new Vec(shape.relativeStart().x(), shape.relativeStart().y(), shape.relativeStart().z()));
        return List.of(bb);
    }

    private static void emitBox(FloatArrayList verts, IntArrayList indices,
                                float x0, float y0, float z0,
                                float x1, float y1, float z1) {
        // Six faces, CCW from outside.
        // -X face (normal -X, viewed from -X): (x0,y0,z1)->(x0,y0,z0)->(x0,y1,z0)->(x0,y1,z1)
        emitQuad(verts, indices,
                x0, y0, z1,  x0, y0, z0,  x0, y1, z0,  x0, y1, z1);
        // +X face: (x1,y0,z0)->(x1,y0,z1)->(x1,y1,z1)->(x1,y1,z0)
        emitQuad(verts, indices,
                x1, y0, z0,  x1, y0, z1,  x1, y1, z1,  x1, y1, z0);
        // -Y face: (x0,y0,z1)->(x1,y0,z1)->(x1,y0,z0)->(x0,y0,z0)
        emitQuad(verts, indices,
                x0, y0, z1,  x1, y0, z1,  x1, y0, z0,  x0, y0, z0);
        // +Y face: (x0,y1,z0)->(x1,y1,z0)->(x1,y1,z1)->(x0,y1,z1)
        emitQuad(verts, indices,
                x0, y1, z0,  x1, y1, z0,  x1, y1, z1,  x0, y1, z1);
        // -Z face: (x0,y0,z0)->(x1,y0,z0)->(x1,y1,z0)->(x0,y1,z0)
        emitQuad(verts, indices,
                x0, y0, z0,  x1, y0, z0,  x1, y1, z0,  x0, y1, z0);
        // +Z face: (x1,y0,z1)->(x0,y0,z1)->(x0,y1,z1)->(x1,y1,z1)
        emitQuad(verts, indices,
                x1, y0, z1,  x0, y0, z1,  x0, y1, z1,  x1, y1, z1);
    }

    private static void emitQuad(FloatArrayList verts, IntArrayList indices,
                                 float ax, float ay, float az,
                                 float bx, float by, float bz,
                                 float cx, float cy, float cz,
                                 float dx, float dy, float dz) {
        int base = verts.size() / 3;
        verts.add(ax); verts.add(ay); verts.add(az);
        verts.add(bx); verts.add(by); verts.add(bz);
        verts.add(cx); verts.add(cy); verts.add(cz);
        verts.add(dx); verts.add(dy); verts.add(dz);

        indices.add(base);
        indices.add(base + 1);
        indices.add(base + 2);
        indices.add(base);
        indices.add(base + 2);
        indices.add(base + 3);
    }
}
