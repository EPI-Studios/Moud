package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.box3d.B3Body;
import com.meekdev.box3d.B3BodyType;
import com.meekdev.bkun.sublevel.SubLevel;
import com.meekdev.bkun.sublevel.SubLevelContainer;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.net.replicate.Change;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

// a part a player has to stand on accurately is a sub level: a real obb with rotation, carrying
// and ground reporting, which an axis aligned box cannot express (12.1.1)
public final class SubLevels {

    private final Map<Integer, SubLevel> byInstance = new HashMap<>();
    private @Nullable ServerLevel level;
    private @Nullable InstanceTree tree;
    private boolean warned;

    public int size() {
        return byInstance.size();
    }

    public void level(ServerLevel serverLevel) {
        level = serverLevel;
    }

    // the same change stream the mirror and the broadphase read, so dirty is drained once
    // a body is created lazily on the first tick, so the type it wants is settled then, not at
    // allocation time when body() is still null
    public void settle() {
        if (tree == null) return;
        for (Map.Entry<Integer, SubLevel> entry : byInstance.entrySet()) {
            Instance instance = tree.byId(entry.getKey());
            if (instance instanceof Part part) type(entry.getValue(), part);
        }
    }

    public void apply(InstanceTree source, Change change) {
        tree = source;
        if (level == null) return;
        switch (change) {
            case Change.Reset ignored -> clear();
            case Change.Destroyed destroyed -> release(destroyed.id());
            case Change.Created created -> refresh(created.id());
            case Change.Wrote wrote -> refresh(wrote.id());
        }
    }

    private void refresh(int id) {
        Instance instance = tree == null ? null : tree.byId(id);
        if (!(instance instanceof Part part)) return;
        if (!wants(part)) {
            release(id);
            return;
        }

        CFrame world = Transforms.world(part);
        SubLevel subLevel = byInstance.get(id);
        if (subLevel == null) {
            subLevel = allocate(id, world);
            if (subLevel == null) return;
            subLevel.setModel(PartShapes.of(part.size));
            subLevel.markShapesDirty();
        }
        pose(subLevel, part, world);
    }

    // the pose carries the rotation and setPosition is what pushes both into the body, so the
    // orientation has to be written first or the obb would sit square while the part looks tilted
    private static void pose(SubLevel subLevel, Part part, CFrame world) {
        Quat rotation = world.rotation();
        subLevel.pose().setRotation(
                (float) rotation.x(), (float) rotation.y(), (float) rotation.z(), (float) rotation.w());
        subLevel.setPosition(world.position().x(), world.position().y(), world.position().z());

        type(subLevel, part);
    }

    // a sub level body is dynamic, so an anchored part would fall out of the world without this
    private static void type(SubLevel subLevel, Part part) {
        B3Body body = subLevel.body();
        if (body == null) return;
        B3BodyType wanted = part.anchored ? B3BodyType.KINEMATIC : B3BodyType.DYNAMIC;
        if (body.type() != wanted) body.setType(wanted);
    }

    // rotated or unanchored: the two cases an axis aligned box gets wrong
    private static boolean wants(Part part) {
        return part.collides && (!part.anchored || !Colliders.isAxisAligned(part));
    }

    private @Nullable SubLevel allocate(int id, CFrame world) {
        SubLevel subLevel = SubLevelContainer.get(level)
                .allocate(world.position().x(), world.position().y(), world.position().z());
        if (subLevel == null) {
            // the plot grid is four thousand slots, and a place can ask for more than that
            if (!warned) {
                MoudMod.LOG.error("out of sub level plots at {}, the rest stay axis aligned", size());
                warned = true;
            }
            return null;
        }
        byInstance.put(id, subLevel);
        return subLevel;
    }

    private void release(int id) {
        SubLevel subLevel = byInstance.remove(id);
        if (subLevel != null && level != null) SubLevelContainer.get(level).remove(subLevel);
    }

    private void clear() {
        if (level != null) {
            for (SubLevel subLevel : byInstance.values()) SubLevelContainer.get(level).remove(subLevel);
        }
        byInstance.clear();
        warned = false;
    }
}
