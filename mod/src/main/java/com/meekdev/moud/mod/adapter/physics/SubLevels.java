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
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.net.replicate.Change;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

// a part a player has to stand on accurately is a sub level: a real obb with rotation, carrying
// and ground reporting, which an axis aligned box cannot express (12.1.1)
public final class SubLevels {

    private final Map<Integer, SubLevel> byInstance = new HashMap<>();
    private final Map<Integer, SubLevelEntity> entities = new HashMap<>();
    private final Map<Integer, Integer> square = new HashMap<>();
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
        List<Integer> letGo = null;
        for (Map.Entry<Integer, SubLevel> entry : byInstance.entrySet()) {
            Instance instance = tree.byId(entry.getKey());
            if (!(instance instanceof Part part)) continue;
            // a turning part passes exactly through square once a revolution, and giving the plot
            // back there destroys the entity and every rider's tracking with it. a client riding it
            // then sends a plot frame the server can no longer decode, and lands in the plot. the
            // count runs here rather than on a write, so a part that stops moving still lets go
            if (!wants(part)) {
                if (square.merge(entry.getKey(), 1, Integer::sum) >= SQUARE_TICKS) {
                    if (letGo == null) letGo = new ArrayList<>(1);
                    letGo.add(entry.getKey());
                }
                continue;
            }
            square.remove(entry.getKey());
            SubLevel subLevel = entry.getValue();
            type(subLevel, part);
            drive(subLevel, part);
        }
        if (letGo != null) {
            for (int id : letGo) release(id);
        }
    }

    // the body carries the transform, and sub level tick copies it back over the pose every tick.
    // writing the pose alone is why every ramp collided as an axis aligned box
    //
    // and the body is woken, because a sleeping one does not move its shapes in the broadphase: the
    // place would draw the part turning while the world kept colliding against where it used to be.
    // no velocity goes with it. gravimity sets one wherever it drives a body by hand, but it drives
    // dynamic bodies, which the solver integrates from wherever they were put. an anchored part is
    // kinematic, so a velocity would be integrated on top of the teleport and leave the deck a tick
    // ahead of the place that owns it
    private static void drive(SubLevel subLevel, Part part) {
        B3Body body = subLevel.body();
        if (body == null) return;
        CFrame world = Transforms.world(part);
        Vec3 position = world.position();
        Quat r = world.rotation();
        body.setTransform(
                new com.meekdev.box3d.Vec3(position.x(), position.y(), position.z()),
                new com.meekdev.box3d.Quat(
                        (float) r.x(), (float) r.y(), (float) r.z(), (float) r.w()));
        body.setAwake(true);
    }

    public void apply(InstanceTree source, Change change) {
        tree = source;
        if (level == null) return;
        switch (change) {
            case Change.Reset ignored -> clear();
            case Change.Destroyed destroyed -> release(destroyed.id());
            case Change.Created created -> refresh(created.id());
            case Change.Wrote wrote -> refresh(wrote.id());
            case Change.Moved moved -> refresh(moved.id());
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
        CFrame world = Transforms.world(part);
        if (!wants(part)) {
            // settle decides when a square part gives its plot back, so one that is only passing
            // through square keeps being posed rather than being torn down and rebuilt
            SubLevel settled = byInstance.get(id);
            if (settled != null) pose(settled, part, world);
            return;
        }
        SubLevel subLevel = byInstance.get(id);
        if (subLevel == null) {
            subLevel = allocate(id, world);
            if (subLevel == null) return;
            subLevel.setModel(PartShapes.of(part.size));
            // the origin belongs to the shape's centre, which is what spawnModel does between
            // setting a model and spawning its entity. createBody would settle it a tick later
            subLevel.recentreOrigin();
            subLevel.markShapesDirty();
            // the plot is only the shape. collision finds sub levels through their entity, and so
            // does the client, so a plot nobody spawned is invisible to both
            entities.put(id, SubLevelEntity.spawn(level, subLevel));
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

    // how long a sub level's part has to stay square before its plot is given back
    private static final int SQUARE_TICKS = 40;

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
            case Change.Moved moved -> moved.id();
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
        square.remove(id);
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
        square.clear();
        warned = false;
    }
}
