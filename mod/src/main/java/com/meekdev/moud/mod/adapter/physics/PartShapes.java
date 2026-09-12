package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.box3d.B3Hull;
import com.meekdev.bkun.api.SubLevels;
import com.meekdev.bkun.sublevel.SubLevelModel;
import com.meekdev.box3d.Quat;
import com.meekdev.moud.core.math.Vec3;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

// one hull per distinct part size, because a place is mostly a few sizes repeated and baking a
// hull per part would be a hull per part
public final class PartShapes {

    private static final float[] UNIT_CUBE = {
        -0.5f, -0.5f, -0.5f,  0.5f, -0.5f, -0.5f,  0.5f,  0.5f, -0.5f, -0.5f,  0.5f, -0.5f,
        -0.5f, -0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f,
    };

    private static final Map<String, SubLevelModel> BY_SIZE = new ConcurrentHashMap<>();

    // baking is the expensive half and the unit cube is the same every time, so it is baked once
    // and every size is a transform of it. it lives as long as the game does, so it is never closed
    private static B3Hull unit;

    private PartShapes() {}

    public static SubLevelModel of(Vec3 size) {
        String key = key(size);
        SubLevelModel cached = BY_SIZE.get(key);
        return cached != null ? cached : bake(key, size);
    }

    // the server bakes these as the place loads and the mirror bakes the same ones on the client,
    // so both threads can reach a cold cache for one name at the same moment
    private static synchronized SubLevelModel bake(String key, Vec3 size) {
        SubLevelModel cached = BY_SIZE.get(key);
        if (cached != null) return cached;

        if (unit == null) unit = B3Hull.bake(UNIT_CUBE, 8);
        B3Hull hull = unit.transformed(
                new com.meekdev.box3d.Vec3(0, 0, 0),
                new Quat(0, 0, 0, 1),
                new com.meekdev.box3d.Vec3(size.x(), size.y(), size.z()));

        double hx = size.x() * 0.5;
        double hy = size.y() * 0.5;
        double hz = size.z() * 0.5;
        SubLevelModel model = SubLevels.registerModel(new SubLevelModel(
                Identifier.fromNamespaceAndPath("moud", "part_" + key),
                List.of(hull),
                List.of(new AABB(-hx, -hy, -hz, hx, hy, hz)),
                new net.minecraft.world.phys.Vec3(0, 0, 0)));
        BY_SIZE.put(key, model);
        return model;
    }

    private static String key(Vec3 size) {
        return Math.round(size.x() * 1000) + "_" + Math.round(size.y() * 1000)
                + "_" + Math.round(size.z() * 1000);
    }
}
