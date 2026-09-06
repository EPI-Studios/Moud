package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.collision.BoxCollider;
import com.meekdev.bkun.collision.ColliderProvider;
import com.meekdev.bkun.collision.ColliderSink;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.interp.Motion;
import com.meekdev.moud.core.math.Aabb;
import com.meekdev.moud.core.space.Broadphase;
import net.minecraft.world.phys.AABB;

// design 12.1: a part with collides is a box in bkun's world, region queried rather than rebuilt
public final class Colliders implements ColliderProvider {

    // parts are metre scale, so a cell of four holds a handful rather than a crowd
    private static final double CELL = 4.0;

    private final InstanceTree tree;
    private final Motion motion;
    private final Broadphase grid = new Broadphase(CELL);

    private long structure = -1;

    public Colliders(InstanceTree tree, Motion motion) {
        this.tree = tree;
        this.motion = motion;
    }

    public int size() {
        return grid.size();
    }

    // the tree changed shape, or something moved: both are cheap to fold into the grid
    public void sync() {
        if (tree.structureEpoch() != structure) {
            structure = tree.structureEpoch();
            grid.rebuild(tree.ofClass(Classes.PART), i -> box((Part) i));
            return;
        }
        for (Instance instance : motion.moving()) {
            if (instance instanceof Part part) grid.put(part, box(part));
        }
    }

    @Override
    public void collect(AABB region, ColliderSink sink) {
        Aabb query = new Aabb(region.minX, region.minY, region.minZ, region.maxX, region.maxY, region.maxZ);
        grid.query(query, instance -> {
            Aabb box = grid.boundsOf(instance);
            if (box != null) sink.add(new BoxCollider(new AABB(
                    box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())));
        });
    }

    private Aabb box(Part part) {
        // a part that does not collide is simply not in the grid, so it costs nothing to skip
        return part.collides ? Aabb.around(motion.sample(part).position(), part.size) : null;
    }
}
