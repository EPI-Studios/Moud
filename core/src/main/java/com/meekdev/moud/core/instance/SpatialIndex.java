package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.math.Aabb;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.space.Broadphase;
import java.util.ArrayList;
import java.util.List;

public final class SpatialIndex {

    private static final double CELL = 8;

    private final InstanceTree tree;
    private final Broadphase grid = new Broadphase(CELL);
    private long builtAt = -1;

    SpatialIndex(InstanceTree tree) {
        this.tree = tree;
    }

    public void candidates(Aabb region, List<Part> out) {
        fresh();
        grid.query(region, instance -> {
            if (instance instanceof Part part && part.isAlive()) out.add(part);
        });
    }

    private void fresh() {
        if (builtAt != tree.structureEpoch()) {
            grid.clear();
            for (Part part : tree.ofClass(Classes.PART)) put(part);
            tree.spatialTouched.clear();
            builtAt = tree.structureEpoch();
            return;
        }
        if (tree.spatialTouched.isEmpty()) return;
        List<Instance> touched = new ArrayList<>(tree.spatialTouched);
        tree.spatialTouched.clear();
        for (Instance instance : touched) {
            if (instance.isAlive()) refresh(instance);
        }
    }

    private void refresh(Instance instance) {
        if (instance instanceof Part part) put(part);
        for (Instance child : instance.children()) refresh(child);
    }

    private void put(Part part) {
        grid.put(part, bounds(Transforms.world(part), part.size));
    }

    public static Aabb bounds(CFrame frame, Vector3 size) {
        Vector3 right = frame.rotation().rotate(Vector3.RIGHT);
        Vector3 up = frame.rotation().rotate(Vector3.UP);
        Vector3 back = frame.rotation().rotate(new Vector3(0, 0, 1));
        double hx = size.x() * 0.5;
        double hy = size.y() * 0.5;
        double hz = size.z() * 0.5;
        double ex = Math.abs(right.x()) * hx + Math.abs(up.x()) * hy + Math.abs(back.x()) * hz;
        double ey = Math.abs(right.y()) * hx + Math.abs(up.y()) * hy + Math.abs(back.y()) * hz;
        double ez = Math.abs(right.z()) * hx + Math.abs(up.z()) * hy + Math.abs(back.z()) * hz;
        Vector3 c = frame.position();
        return new Aabb(c.x() - ex, c.y() - ey, c.z() - ez, c.x() + ex, c.y() + ey, c.z() + ez);
    }
}
