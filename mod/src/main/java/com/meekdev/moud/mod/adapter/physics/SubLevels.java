package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.box3d.B3Body;
import com.meekdev.box3d.B3BodyType;
import com.meekdev.bkun.sublevel.SubLevel;
import com.meekdev.bkun.sublevel.SubLevelContainer;
import com.meekdev.bkun.sublevel.SubLevelEntity;
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
    private final Map<Integer, SubLevelEntity> entities = new HashMap<>();
    private @Nullable ServerLevel level;
    private @Nullable InstanceTree tree;
    private boolean warned;
    private int ticks;

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
        ticks++;
        boolean trace = ticks == 60 || ticks == 200 || ticks == 600;
        if (trace) {
            MoudMod.LOG.info("trace settle tick={} subLevels={} level={}",
                    ticks, byInstance.size(), level == null ? "none" : "set");
        }
        for (Map.Entry<Integer, SubLevel> entry : byInstance.entrySet()) {
            Instance instance = tree.byId(entry.getKey());
            if (!(instance instanceof Part part)) continue;
            SubLevel subLevel = entry.getValue();
            type(subLevel, part);
            drive(subLevel, part);
            if (trace) trace(subLevel, part);
        }
    }

    // where the rotation stops: the part has it, the body is told it, the pose is copied back
    // from the body. printing all three says which link drops it
    private static void trace(SubLevel subLevel, Part part) {
        Quat want = Transforms.world(part).rotation();
        B3Body body = subLevel.body();
        MoudMod.LOG.info("trace part=({} {} {} {}) pose=({} {} {} {}) body={} type={}",
                fmt(want.x()), fmt(want.y()), fmt(want.z()), fmt(want.w()),
                fmt(subLevel.pose().rotation().x()), fmt(subLevel.pose().rotation().y()),
                fmt(subLevel.pose().rotation().z()), fmt(subLevel.pose().rotation().w()),
                body == null ? "none" : body.rotation(),
                body == null ? "none" : body.type());
    }

    private static String fmt(double v) {
        return String.format("%.4f", v);
    }

    // the body carries the transform, and sub level tick copies it back over the pose every tick.
    // writing the pose alone is why every ramp collided as an axis aligned box
    private static void drive(SubLevel subLevel, Part part) {
        B3Body body = subLevel.body();
        if (body == null) return;
        CFrame world = Transforms.world(part);
        Quat r = world.rotation();
        body.setTransform(
                new com.meekdev.box3d.Vec3(
                        world.position().x(), world.position().y(), world.position().z()),
                new com.meekdev.box3d.Quat(
                        (float) r.x(), (float) r.y(), (float) r.z(), (float) r.w()));
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

    // box3d is a native library, so it can fail at load rather than at build. one failure turns
    // sub levels off for the run and the rotated parts fall back to boxes, which is wrong by the
    // width of the rotation but is a great deal better than no collision and a dead server
    private static boolean available = true;

    public static boolean available() {
        return available;
    }

    private void refresh(int id) {
        if (!available) return;
        try {
            refreshOrThrow(id);
        } catch (Throwable failure) {
            available = false;
            clear();
            MoudMod.LOG.error("sub levels are off for this run, parts collide as boxes", failure);
        }
    }

    private void refreshOrThrow(int id) {
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
            // the plot is only the shape. collision finds sub levels through their entity, and so
            // does the client, so a plot nobody spawned is invisible to both
            SubLevelEntity spawned = SubLevelEntity.spawn(level, subLevel);
            entities.put(id, spawned);
            // the plot's own "body built" line comes from bkun and says nothing about the entity,
            // which is the half collision and the client both go through
            MoudMod.LOG.info("sub level entity {} for part {} at {} model {}",
                    spawned.getId(), id, world.position(), subLevel.model() == null ? "none" : "set");
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

    // only the shape's name reaches the other side, so a client that never baked it resolves null
    // and the sub level is neither solid nor drawn. singleplayer hides this behind a shared static
    // map. the name is the size, so the mirror can bake the same shape without being told
    static void mirror(InstanceTree tree, Change change) {
        if (!available) return;
        int id = switch (change) {
            case Change.Created created -> created.id();
            case Change.Wrote wrote -> wrote.id();
            case Change.Reset ignored -> -1;
            case Change.Destroyed ignored -> -1;
        };
        if (id < 0) return;
        if (tree.byId(id) instanceof Part part && wants(part)) PartShapes.of(part.size);
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
        SubLevelEntity entity = entities.remove(id);
        if (entity != null) entity.discard();
        SubLevel subLevel = byInstance.remove(id);
        if (subLevel != null && level != null) SubLevelContainer.get(level).remove(subLevel);
    }

    private void clear() {
        for (SubLevelEntity entity : entities.values()) entity.discard();
        entities.clear();
        if (level != null) {
            for (SubLevel subLevel : byInstance.values()) SubLevelContainer.get(level).remove(subLevel);
        }
        byInstance.clear();
        warned = false;
    }
}
