package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.box3d.B3Hull;
import com.meekdev.bkun.api.SubLevels;
import com.meekdev.bkun.sublevel.SubLevelModel;
import com.meekdev.box3d.Quat;
import com.meekdev.moud.core.math.Vector3;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import com.meekdev.box3d.Vec3;

public final class PartShapes {

    private static final float[] UNIT_CUBE = {
        -0.5f, -0.5f, -0.5f,  0.5f, -0.5f, -0.5f,  0.5f,  0.5f, -0.5f, -0.5f,  0.5f, -0.5f,
        -0.5f, -0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f,
    };

    private static final Map<String, SubLevelModel> BY_SIZE = new ConcurrentHashMap<>();

    private static B3Hull unit;

    private PartShapes() {}

    public static SubLevelModel of(Vector3 size) {
        String key = key(size);
        SubLevelModel cached = BY_SIZE.get(key);
        return cached != null ? cached : bake(key, size);
    }

    private static synchronized SubLevelModel bake(String key, Vector3 size) {
        SubLevelModel cached = BY_SIZE.get(key);
        if (cached != null) return cached;

        if (unit == null) unit = B3Hull.bake(UNIT_CUBE, 8);
        B3Hull hull = unit.transformed(
                new Vec3(0, 0, 0),
                new Quat(0, 0, 0, 1),
                new Vec3(size.x(), size.y(), size.z()));

        double hx = size.x() * 0.5;
        double hy = size.y() * 0.5;
        double hz = size.z() * 0.5;
        AABB box = new AABB(-hx, -hy, -hz, hx, hy, hz);
        SubLevelModel model = SubLevels.registerModel(new SubLevelModel(
                Identifier.fromNamespaceAndPath("moud", "part_" + key), List.of(hull), List.of(box), box.getCenter()));
        BY_SIZE.put(key, model);
        return model;
    }

    private static String key(Vector3 size) {
        return Math.round(size.x() * 1000) + "_" + Math.round(size.y() * 1000)
                + "_" + Math.round(size.z() * 1000);
    }
}
