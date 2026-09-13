package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.collision.BoxCollider;
import com.meekdev.bkun.collision.ColliderSink;
import com.meekdev.bkun.collision.SurfaceMaterial;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.Aabb;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.part.CollisionGroup;
import com.meekdev.moud.core.part.CollisionGroups;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.query.SpatialIndex;
import com.meekdev.moud.core.space.Broadphase;
import com.meekdev.moud.net.replicate.Change;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public final class Colliders {

    private static final double SQUARE = 1.0e-4;
    private static final double CELL = 4.0;

    private final Broadphase grid = new Broadphase(CELL);
    private static final PropertyDef GROUP = Classes.PART.property("collisionGroup");

    private @Nullable InstanceTree tree;

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
            case Change.Wrote wrote -> refresh(wrote.id())
                    || source.byId(wrote.id()) instanceof Part && wrote.property() == GROUP.index();
            case Change.Moved moved -> refresh(moved.id());
            case Change.Tagged ignored -> false;
        };
        return statics || groupsChanged && grid.size() > 0;
    }

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
        return grid.put(part, SpatialIndex.bounds(world, part.size));
    }

    private boolean removeById(int id) {
        Instance instance = tree == null ? null : tree.byId(id);
        return instance != null && grid.remove(instance);
    }
}
