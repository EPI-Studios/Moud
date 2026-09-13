package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.collision.BoxCollider;
import com.meekdev.bkun.collision.ColliderSink;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.Aabb;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.space.Broadphase;
import com.meekdev.moud.net.replicate.Change;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

// axis aligned parts only. a rotated one is not honestly an aabb, so it goes to a sub level
// instead where the collision is a real obb (12.1.1)
public final class Colliders {

    private static final double SQUARE = 1.0e-4;
    private static final double CELL = 4.0;

    private final Broadphase grid = new Broadphase(CELL);
    private @Nullable InstanceTree tree;

    public int size() {
        return grid.size();
    }

    public static boolean isAxisAligned(Part part) {
        Quat rotation = Transforms.world(part).rotation();
        return Math.abs(rotation.x()) < SQUARE && Math.abs(rotation.y()) < SQUARE
                && Math.abs(rotation.z()) < SQUARE;
    }

    // it follows the same change stream the mirror does, so the tick drains dirty exactly once
    //
    // returns whether the static set actually moved, because whoever caches it downstream has to
    // throw that cache away and rebuilding it costs the whole world
    public boolean apply(InstanceTree source, Change change) {
        tree = source;
        return switch (change) {
            case Change.Reset ignored -> {
                boolean had = grid.size() > 0;
                grid.clear();
                yield had;
            }
            case Change.Destroyed destroyed -> removeById(destroyed.id());
            case Change.Created created -> refresh(created.id());
            case Change.Wrote wrote -> refresh(wrote.id());
            // a moved part keeps every property and lands somewhere else, so its box is stale
            case Change.Moved moved -> refresh(moved.id());
            case Change.Tagged ignored -> false;
        };
    }

    public void collect(AABB region, ColliderSink sink) {
        grid.query(new Aabb(region.minX, region.minY, region.minZ,
                region.maxX, region.maxY, region.maxZ), instance -> {
            Aabb box = grid.boundsOf(instance);
            if (box != null) sink.add(new BoxCollider(new AABB(
                    box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())));
        });
    }

    private boolean refresh(int id) {
        Instance instance = tree == null ? null : tree.byId(id);
        if (!(instance instanceof Part part)) return false;
        if (!part.collides || (!isAxisAligned(part) && SubLevels.available())) {
            return grid.remove(part);
        }
        CFrame world = Transforms.world(part);
        return grid.put(part, Aabb.around(world.position(), part.size));
    }

    private boolean removeById(int id) {
        Instance instance = tree == null ? null : tree.byId(id);
        return instance != null && grid.remove(instance);
    }
}
