package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.sublevel.SubLevel;
import com.meekdev.bkun.sublevel.SubLevelContainer;
import com.meekdev.bkun.sublevel.SubLevelEntity;
import com.meekdev.box3d.B3Body;
import com.meekdev.box3d.B3BodyType;
import com.meekdev.box3d.Quat;
import com.meekdev.box3d.Vec3;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.net.replicate.Change;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

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

    public void settle() {
        if (tree == null) return;
        List<Integer> letGo = null;
        for (Map.Entry<Integer, SubLevel> entry : byInstance.entrySet()) {
            Instance instance = tree.byId(entry.getKey());
            if (!(instance instanceof Part part)) continue;
            if (!needsSubLevel(part)) {
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

    private static void drive(SubLevel subLevel, Part part) {
        B3Body body = subLevel.body();
        if (body == null) return;
        CFrame world = Transforms.world(part);
        Vector3 position = world.position();
        var r = world.rotation();
        body.setTransform(
                new Vec3(position.x(), position.y(), position.z()),
                new Quat(
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
            case Change.Tagged ignored -> { }
            case Change.Renamed ignored -> { }
        }
    }

    private static boolean available = true;

    public int instanceOf(SubLevelEntity deck) {
        for (Map.Entry<Integer, SubLevelEntity> entry : entities.entrySet()) {
            if (entry.getValue() == deck) return entry.getKey();
        }
        return 0;
    }

    public static boolean available() {
        return available;
    }

    private void refresh(int id) {
        if (!available) return;
        try {
            refreshOrThrow(id);
        } catch (Throwable e) {
            available = false;
            clear();
            MoudMod.LOG.error("sub levels unavailable, rotated parts collide as boxes", e);
        }
    }

    private void refreshOrThrow(int id) {
        Instance instance = tree == null ? null : tree.byId(id);
        if (!(instance instanceof Part part)) return;
        CFrame world = Transforms.world(part);
        if (!needsSubLevel(part)) {
            SubLevel settled = byInstance.get(id);
            if (settled != null) pose(settled, part, world);
            return;
        }
        SubLevel subLevel = byInstance.get(id);
        if (subLevel == null) {
            subLevel = allocate(id, world);
            if (subLevel == null) return;
            subLevel.setModel(PartShapes.of(part.size));
            subLevel.recentreOrigin();
            subLevel.markShapesDirty();
            SubLevelEntity spawned = SubLevelEntity.spawn(level, subLevel);
            spawned.setOwner(id);
            entities.put(id, spawned);
        }
        pose(subLevel, part, world);
    }

    private static void pose(SubLevel subLevel, Part part, CFrame world) {
        var rotation = world.rotation();
        subLevel.pose().setRotation(
                (float) rotation.x(), (float) rotation.y(), (float) rotation.z(), (float) rotation.w());
        subLevel.setPosition(world.position().x(), world.position().y(), world.position().z());

        type(subLevel, part);
    }

    private static void type(SubLevel subLevel, Part part) {
        B3Body body = subLevel.body();
        if (body == null) return;
        B3BodyType wanted = part.anchored ? B3BodyType.KINEMATIC : B3BodyType.DYNAMIC;
        if (body.type() != wanted) body.setType(wanted);
    }

    private static final int SQUARE_TICKS = 40;

    private static boolean needsSubLevel(Part part) {
        return part.collides && (!part.anchored || !Colliders.isAxisAligned(part) || Physics.boxes().moving(part));
    }

    static void mirror(InstanceTree tree, Change change) {
        if (!available) return;
        int id = switch (change) {
            case Change.Created created -> created.id();
            case Change.Wrote wrote -> wrote.id();
            case Change.Moved moved -> moved.id();
            case Change.Reset ignored -> -1;
            case Change.Destroyed ignored -> -1;
            case Change.Tagged ignored -> -1;
            case Change.Renamed ignored -> -1;
        };
        if (id < 0) return;
        if (tree.byId(id) instanceof Part part && needsSubLevel(part)) PartShapes.of(part.size);
    }

    static boolean wantsSubLevel(Part part) {
        return needsSubLevel(part);
    }

    private @Nullable SubLevel allocate(int id, CFrame world) {
        SubLevel subLevel = SubLevelContainer.get(level)
                .allocate(world.position().x(), world.position().y(), world.position().z());
        if (subLevel == null) {
            if (!warned) {
                MoudMod.LOG.error("out of sub level plots at {}", size());
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
