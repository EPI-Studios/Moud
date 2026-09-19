package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.api.SubLevels;
import com.meekdev.bkun.sublevel.SubLevelModel;
import com.meekdev.box3d.B3Hull;
import com.meekdev.box3d.Quat;
import com.meekdev.box3d.Vec3;
import com.meekdev.moud.core.math.Aabb;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.PartShape;
import com.meekdev.moud.core.part.Shapes;
import com.meekdev.moud.mod.MoudMod;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

public final class PartShapes {

    private static final float[] UNIT_CUBE = {
        -0.5f, -0.5f, -0.5f,  0.5f, -0.5f, -0.5f,  0.5f,  0.5f, -0.5f, -0.5f,  0.5f, -0.5f,
        -0.5f, -0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f,
    };

    private static final int STEPS = 8;
    private static final int MOST_POINTS = 60;
    private static final int CYLINDER_SIDES = 16;

    private static final Map<String, SubLevelModel> BY_SIZE = new ConcurrentHashMap<>();

    private static B3Hull unit;

    private PartShapes() {}

    public static SubLevelModel of(Vector3 size, PartShape shape) {
        String key = key(size, shape);
        SubLevelModel cached = BY_SIZE.get(key);
        return cached != null ? cached : bake(key, size, shape);
    }

    private static synchronized SubLevelModel bake(String key, Vector3 size, PartShape shape) {
        SubLevelModel cached = BY_SIZE.get(key);
        if (cached != null) return cached;

        List<AABB> boxes = new ArrayList<>();
        for (Aabb slab : Shapes.slabs(shape, size, STEPS)) {
            boxes.add(new AABB(slab.minX(), slab.minY(), slab.minZ(), slab.maxX(), slab.maxY(), slab.maxZ()));
        }
        AABB bounds = new AABB(-size.x() * 0.5, -size.y() * 0.5, -size.z() * 0.5,
                size.x() * 0.5, size.y() * 0.5, size.z() * 0.5);
        SubLevelModel model = SubLevels.registerModel(new SubLevelModel(
                Identifier.fromNamespaceAndPath("moud", "part_" + key), List.of(hull(size, shape)),
                boxes, bounds.getCenter()));
        BY_SIZE.put(key, model);
        return model;
    }

    private static B3Hull hull(Vector3 size, PartShape shape) {
        if (shape == PartShape.CYLINDER) {
            double radius = Shapes.across(shape, size);
            double half = size.x() * 0.5;
            float[] cloud = new float[CYLINDER_SIDES * 6];
            int at = 0;
            for (int side = 0; side < CYLINDER_SIDES; side++) {
                double turn = Math.PI * (2 * side + 1) / CYLINDER_SIDES;
                float y = (float) (radius * Math.cos(turn));
                float z = (float) (radius * Math.sin(turn));
                for (double end : new double[] {-half, half}) {
                    cloud[at++] = (float) end;
                    cloud[at++] = y;
                    cloud[at++] = z;
                }
            }
            return B3Hull.bake(cloud, CYLINDER_SIDES * 2);
        }
        if (shape != PartShape.BLOCK) {
            List<Vector3> corners = Shapes.corners(shape, size, Shapes.COLLIDING_SIDES, Shapes.COLLIDING_RINGS);
            float[] cloud = new float[corners.size() * 3];
            int at = 0;
            for (Vector3 corner : corners) {
                cloud[at++] = (float) corner.x();
                cloud[at++] = (float) corner.y();
                cloud[at++] = (float) corner.z();
            }
            try {
                return B3Hull.bake(cloud, Math.min(corners.size(), MOST_POINTS));
            } catch (RuntimeException e) {
                MoudMod.LOG.warn("a {} of {} collides as a box, its hull could not be built: {}", shape, size, e.getMessage());
            }
        }
        if (unit == null) unit = B3Hull.bake(UNIT_CUBE, 8);
        return unit.transformed(new Vec3(0, 0, 0), new Quat(0, 0, 0, 1),
                new Vec3(size.x(), size.y(), size.z()));
    }

    private static String key(Vector3 size, PartShape shape) {
        return shape.name().toLowerCase(Locale.ROOT) + "_" + Math.round(size.x() * 1000)
                + "_" + Math.round(size.y() * 1000) + "_" + Math.round(size.z() * 1000);
    }
}
