package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.sublevel.SubLevel;
import com.meekdev.bkun.sublevel.SubLevelContainer;
import com.meekdev.bkun.sublevel.SubLevelEntity;
import com.meekdev.box3d.B3Body;
import com.meekdev.box3d.B3BodyType;
import com.meekdev.box3d.B3Shape;
import com.meekdev.box3d.B3ShapeType;
import com.meekdev.box3d.Quat;
import com.meekdev.box3d.Vec3;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.part.PartShape;
import com.meekdev.moud.core.part.Shapes;
import com.meekdev.moud.core.ui.ViewportFrame;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.net.replicate.Change;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

public final class SubLevels {

    private final Map<Integer, SubLevel> byInstance = new HashMap<>();
    private final Map<Integer, SubLevelEntity> entities = new HashMap<>();
    private final Map<Integer, Integer> square = new HashMap<>();
    private final Map<Integer, CFrame> written = new HashMap<>();
    private static boolean simulating = true;
    private final Map<Integer, B3Body> dressed = new HashMap<>();
    private final Map<Integer, List<Consumer<B3Body>>> pending = new HashMap<>();

    private static final PropertyDef CFRAME = Classes.SPATIAL.property("cframe");
    private static final PropertyDef PIVOT = Classes.SPATIAL.property("pivot");
    private static final PropertyDef VELOCITY = Classes.PART.property("velocity");
    private static final PropertyDef ANGULAR_VELOCITY = Classes.PART.property("angularVelocity");
    private static final double STILL = 1.0e-4;
    private static final float MASSLESS_DENSITY = 0.001f;
    private @Nullable ServerLevel level;
    private @Nullable InstanceTree tree;
    private boolean warned;

    void simulating(boolean on) {
        if (simulating == on) return;
        simulating = on;
        written.clear();
        for (Map.Entry<Integer, SubLevel> entry : byInstance.entrySet()) {
            if (tree != null && tree.byId(entry.getKey()) instanceof Part part) {
                type(entry.getValue(), part);
                pose(entry.getValue(), part, Transforms.world(part));
            }
        }
    }

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
            B3Body body = subLevel.body();
            if (body != null) {
                if (dressed.get(entry.getKey()) != body) dress(body, part);
                else round(body, part);
                List<Consumer<B3Body>> queued = pending.remove(entry.getKey());
                if (queued != null) queued.forEach(action -> action.accept(body));
            }
            if (part.anchored || !simulating) drive(subLevel, part);
            else follow(subLevel, part);
        }
        if (letGo != null) {
            for (int id : letGo) release(id);
        }
    }

    private void follow(SubLevel subLevel, Part part) {
        B3Body body = subLevel.body();
        if (body == null || body.type() != B3BodyType.DYNAMIC) return;
        CFrame world = BoxFrames.of(body);
        CFrame was = Transforms.world(part);
        if (world.position().sub(was.position()).lengthSq() > STILL * STILL || turned(world, was)) {
            written.put(part.id(), world);
            Instances.setObj(part, CFRAME, Transforms.localFor(part, world));
        }
        Vector3 speed = BoxFrames.vector(body.linearVelocity());
        Vector3 spin = BoxFrames.vector(body.angularVelocity());
        if (speed.sub(part.velocity).lengthSq() > STILL) Instances.setObj(part, VELOCITY, speed);
        if (spin.sub(part.angularVelocity).lengthSq() > STILL) Instances.setObj(part, ANGULAR_VELOCITY, spin);
    }

    private static boolean same(CFrame a, @Nullable CFrame b) {
        return b != null && a.position().sub(b.position()).lengthSq() <= STILL * STILL && !turned(a, b);
    }

    private static boolean turned(CFrame a, CFrame b) {
        var p = a.rotation();
        var q = b.rotation();
        double dot = Math.abs(p.x() * q.x() + p.y() * q.y() + p.z() * q.z() + p.w() * q.w());
        return dot < 1 - STILL * STILL;
    }

    private void dress(B3Body body, Part part) {
        dressed.put(part.id(), body);
        round(body, part);
        float density = part.massless ? MASSLESS_DENSITY : (float) part.density;
        for (B3Shape shape : body.shapes()) {
            shape.setDensity(density);
            shape.setFriction((float) part.friction);
            shape.setRestitution((float) part.elasticity);
        }
        body.recomputeMass();
    }

    private static void round(B3Body body, Part part) {
        if (part.shape != PartShape.BALL) return;
        float radius = (float) Shapes.across(PartShape.BALL, part.size);
        List<B3Shape> shapes = body.shapes();
        if (shapes.size() == 1 && shapes.getFirst().type() == B3ShapeType.SPHERE
                && Math.abs(shapes.getFirst().sphere().radius() - radius) < 1.0e-4f) {
            return;
        }
        for (B3Shape shape : shapes) shape.destroy();
        body.addSphere(radius);
    }

    public @Nullable B3Body body(int id) {
        SubLevel subLevel = byInstance.get(id);
        return subLevel == null ? null : subLevel.body();
    }

    public void withBody(Part part, Consumer<B3Body> action) {
        B3Body body = body(part.id());
        if (body != null && dressed.get(part.id()) == body) {
            action.accept(body);
            return;
        }
        pending.computeIfAbsent(part.id(), id -> new ArrayList<>()).add(action);
        refresh(part.id());
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
            case Change.Wrote wrote -> {
                if (wrote.property() == CFRAME.index() || wrote.property() == PIVOT.index()) refreshBranch(wrote.id());
                else refresh(wrote.id());
            }
            case Change.Moved moved -> refreshBranch(moved.id());
            case Change.Tagged ignored -> { }
            case Change.Renamed ignored -> { }
            case Change.Attributed ignored -> { }
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

    private void refreshBranch(int id) {
        refresh(id);
        Instance instance = tree == null ? null : tree.byId(id);
        if (instance == null || byInstance.isEmpty()) return;
        for (Instance child : instance.children()) refreshBranch(child.id());
    }

    void refresh(int id) {
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
        if (subLevel != null && subLevel.model() != PartShapes.of(part.size, part.shape)) {
            subLevel.setModel(PartShapes.of(part.size, part.shape));
            subLevel.recentreOrigin();
            dressed.remove(id);
        }
        if (subLevel != null && !part.anchored && simulating && same(world, written.get(id))) {
            type(subLevel, part);
            B3Body body = subLevel.body();
            if (body != null) dress(body, part);
            return;
        }
        if (subLevel == null) {
            subLevel = allocate(id, world);
            if (subLevel == null) return;
            subLevel.setModel(PartShapes.of(part.size, part.shape));
            subLevel.recentreOrigin();
            subLevel.markShapesDirty();
            SubLevelEntity spawned = SubLevelEntity.spawn(level, subLevel);
            spawned.setOwner(id);
            entities.put(id, spawned);
        }
        pose(subLevel, part, world);
        B3Body body = subLevel.body();
        if (body != null) dress(body, part);
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
        B3BodyType wanted = part.anchored || !simulating ? B3BodyType.KINEMATIC : B3BodyType.DYNAMIC;
        if (body.type() != wanted) body.setType(wanted);
    }

    private static final int SQUARE_TICKS = 40;

    private static boolean needsSubLevel(Part part) {
        return part.collides && !ViewportFrame.inside(part) && (!part.anchored || !Colliders.staysPut(part)
                || Physics.boxes().moving(part) || Joints.holds(part.id()));
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
            case Change.Attributed ignored -> -1;
        };
        if (id < 0) return;
        if (tree.byId(id) instanceof Part part && needsSubLevel(part)) PartShapes.of(part.size, part.shape);
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
        written.remove(id);
        dressed.remove(id);
        pending.remove(id);
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
        written.clear();
        dressed.clear();
        pending.clear();
        warned = false;
    }
}
