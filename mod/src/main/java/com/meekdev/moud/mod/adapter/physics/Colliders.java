package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.collision.BoxCollider;
import com.meekdev.bkun.collision.ColliderSink;
import com.meekdev.bkun.collision.SurfaceMaterial;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.CollisionGroup;
import com.meekdev.moud.core.instance.CollisionGroups;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.instance.SpatialIndex;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.Aabb;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.space.Broadphase;
import com.meekdev.moud.net.replicate.Change;
import net.minecraft.world.phys.AABB;
import java.util.HashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

// axis aligned parts only. a rotated one is not honestly an aabb, so it goes to a sub level
// instead where the collision is a real obb (12.1.1)
public final class Colliders {

    private static final double SQUARE = 1.0e-4;
    private static final double CELL = 4.0;

    private final Broadphase grid = new Broadphase(CELL);
    private static final PropertyDef GROUP = Classes.PART.property("collisionGroup");

    private @Nullable InstanceTree tree;

    // the groups as bits, rebuilt when a group is made, written or destroyed. the ids are kept
    // because a destroyed instance is already gone from the tree when the change arrives
    private @Nullable CollisionGroups groups;
    private final Set<Integer> groupIds = new HashSet<>();
    private boolean groupsChanged;

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
        groupsChanged = switch (change) {
            case Change.Reset ignored -> true;
            case Change.Destroyed destroyed -> groupIds.remove(destroyed.id());
            case Change.Created created -> source.byId(created.id()) instanceof CollisionGroup;
            case Change.Wrote wrote -> source.byId(wrote.id()) instanceof CollisionGroup;
            case Change.Moved moved -> source.byId(moved.id()) instanceof CollisionGroup;
            case Change.Tagged ignored -> false;
        };
        if (groupsChanged) groups = null;
        boolean statics = switch (change) {
            case Change.Reset ignored -> {
                boolean had = grid.size() > 0;
                grid.clear();
                yield had;
            }
            case Change.Destroyed destroyed -> removeById(destroyed.id());
            case Change.Created created -> refresh(created.id());
            // a part that changes group has the same box and a different category, which is still a
            // different world to sweep against
            case Change.Wrote wrote -> refresh(wrote.id())
                    || source.byId(wrote.id()) instanceof Part && wrote.property() == GROUP.index();
            // a moved part keeps every property and lands somewhere else, so its box is stale
            case Change.Moved moved -> refresh(moved.id());
            case Change.Tagged ignored -> false;
        };
        return statics || groupsChanged && grid.size() > 0;
    }

    // whether the last change altered what any group collides with, so a body's filter is stale
    public boolean groupsChanged() {
        return groupsChanged;
    }

    public CollisionGroups groups() {
        CollisionGroups known = groups;
        if (known != null) return known;
        if (tree == null) return CollisionGroups.of(new InstanceTree());
        groupIds.clear();
        for (CollisionGroup group : tree.ofClass(Classes.COLLISION_GROUP)) groupIds.add(group.id());
        known = CollisionGroups.of(tree);
        groups = known;
        return known;
    }

    public void collect(AABB region, ColliderSink sink) {
        grid.query(new Aabb(region.minX, region.minY, region.minZ,
                region.maxX, region.maxY, region.maxZ), instance -> {
            Aabb box = grid.boundsOf(instance);
            if (box == null || !(instance instanceof Part part)) return;
            long category = groups().category(CollisionGroups.groupOf(part));
            sink.add(new BoxCollider(new AABB(box.minX(), box.minY(), box.minZ(),
                    box.maxX(), box.maxY(), box.maxZ()), SurfaceMaterial.DEFAULT, category));
        });
    }

    private boolean refresh(int id) {
        Instance instance = tree == null ? null : tree.byId(id);
        if (!(instance instanceof Part part)) return false;
        if (!part.collides || (!isAxisAligned(part) && SubLevels.available())) {
            return grid.remove(part);
        }
        CFrame world = Transforms.world(part);
        // the box around the part as it is turned. the size alone is the box of a part that is not, and a
        // ramp turned a quarter about up collided along the wrong axis
        return grid.put(part, SpatialIndex.bounds(world, part.size));
    }

    private boolean removeById(int id) {
        Instance instance = tree == null ? null : tree.byId(id);
        return instance != null && grid.remove(instance);
    }
}
