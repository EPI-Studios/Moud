package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.model.Animator;
import com.meekdev.amnetic.client.model.Model;
import com.meekdev.amnetic.client.model.ModelFormat;
import com.meekdev.amnetic.client.model.Models;
import com.meekdev.amnetic.client.model.internal.ammesh.AmmeshConverter;
import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Attachment;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.MeshPart;
import com.meekdev.moud.core.physics.RopeConstraint;
import com.meekdev.moud.core.physics.RopeCurve;
import com.meekdev.moud.core.ui.ViewportFrame;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.client.PlaceFiles;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class Meshes {

    private record Loaded(Model model, int hash) {}

    private record Playing(Animator animator, String clip) {}

    private static final Map<String, Loaded> MODELS = new HashMap<>();
    private static final Map<Integer, Playing> ANIMATORS = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();

    private static final Matrix4f WORLD = new Matrix4f();
    private static final int ROPE_SEGMENTS = 24;
    private static final int MOST_LINKS = 512;
    private static final double MIN_LINK = 0.01;

    private Meshes() {}

    public static void register() {
        Models.onFrame(context -> frame());
    }

    private static void frame() {
        InstanceTree tree = ClientScene.tree();
        if (tree == null) return;
        Minecraft client = Minecraft.getInstance();
        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float dt = client.getDeltaTracker().getRealtimeDeltaTicks() / 20f;
        for (MeshPart part : tree.ofClass(Classes.MESH_PART)) {
            if (!part.visible || part.transparency >= 1 || part.meshId.isEmpty() || ViewportFrame.inside(part)) continue;
            Model model = model(part.meshId);
            if (model == null || !model.isReady()) continue;
            draw(part, model, partialTick, dt);
        }
        ANIMATORS.keySet().removeIf(id -> !(tree.byId(id) instanceof MeshPart));
        for (Instance instance : tree.ofClass(Classes.ROPE_CONSTRAINT)) {
            if (instance instanceof RopeConstraint rope) links(rope, partialTick);
        }
    }

    public static boolean linked(RopeConstraint rope) {
        if (rope.mesh.isEmpty()) return false;
        Model model = model(rope.mesh);
        if (model == null || !model.isReady()) return false;
        double spacing = rope.meshLength > 0 ? rope.meshLength : model.boundsMax().z - model.boundsMin().z;
        return spacing > MIN_LINK;
    }

    private static void links(RopeConstraint rope, float partialTick) {
        if (!rope.visible || !rope.enabled || rope.mesh.isEmpty()) return;
        if (!(rope.attachment0 instanceof Attachment a0) || !a0.isAlive() || ViewportFrame.inside(a0)) return;
        if (!(rope.attachment1 instanceof Attachment a1) || !a1.isAlive()) return;
        if (!linked(rope)) return;
        Model model = model(rope.mesh);
        Vector3f min = model.boundsMin();
        Vector3f max = model.boundsMax();
        float along = max.z - min.z;
        double spacing = rope.meshLength > 0 ? rope.meshLength : along;
        Vector3 from = ClientScene.motion().sample(a0, partialTick).position();
        Vector3 to = ClientScene.motion().sample(a1, partialTick).position();
        List<Vector3> points = RopeCurve.points(from, to, rope.length, ROPE_SEGMENTS);
        for (RopeCurve.Link link : RopeCurve.links(points, spacing, Math.toRadians(rope.meshTwist), MOST_LINKS)) {
            Vector3 z = link.forward();
            Vector3 y = link.up();
            Vector3 x = y.cross(z);
            Vector3 at = link.position();
            float scale = along < 1e-5f ? (float) (link.length() / spacing) : (float) (link.length() / along);
            WORLD.set((float) x.x(), (float) x.y(), (float) x.z(), 0,
                    (float) y.x(), (float) y.y(), (float) y.z(), 0,
                    (float) z.x(), (float) z.y(), (float) z.z(), 0,
                    (float) at.x(), (float) at.y(), (float) at.z(), 1)
                    .scale(scale)
                    .translate(-(min.x + max.x) / 2, -(min.y + max.y) / 2, -(min.z + max.z) / 2);
            model.render(WORLD);
        }
    }

    private static void draw(MeshPart part, Model model, float partialTick, float dt) {
        CFrame world = ClientScene.motion().sample(part, partialTick);
        placement(world, part.size, model, WORLD);
        Matrix4f[] pose = pose(part, model, dt);
        if (pose == null) model.render(WORLD);
        else model.renderPosed(WORLD, pose);
    }

    static Matrix4f placement(CFrame world, Vector3 size, Model model, Matrix4f into) {
        Vector3 at = world.position();
        Quat turn = world.rotation();
        Vector3f min = model.boundsMin();
        Vector3f max = model.boundsMax();
        float sx = stretch(size.x(), max.x - min.x);
        float sy = stretch(size.y(), max.y - min.y);
        float sz = stretch(size.z(), max.z - min.z);
        return into.translation((float) at.x(), (float) at.y(), (float) at.z())
                .rotate(new Quaternionf((float) turn.x(), (float) turn.y(), (float) turn.z(), (float) turn.w()))
                .scale(sx, sy, sz)
                .translate(-(min.x + max.x) / 2, -(min.y + max.y) / 2, -(min.z + max.z) / 2);
    }

    static Matrix4f @Nullable [] pose(MeshPart part, Model model, float dt) {
        if (part.animation.isEmpty() || !model.isAnimated()) {
            ANIMATORS.remove(part.id());
            return null;
        }
        Playing playing = ANIMATORS.get(part.id());
        if (playing == null || playing.animator().model() != model || !playing.clip().equals(part.animation)) {
            if (!model.clipNames().contains(part.animation)) {
                if (MISSING.add(part.meshId + "#" + part.animation)) {
                    MoudMod.LOG.warn("{} has no clip {}, it has {}", part.meshId, part.animation, model.clipNames());
                }
                return null;
            }
            playing = new Playing(model.createAnimator().play(part.animation), part.animation);
            ANIMATORS.put(part.id(), playing);
        }
        Animator animator = playing.animator();
        animator.loop(part.animationLooped).speed((float) part.animationSpeed).update(dt);
        Matrix4f[] pose = animator.pose();
        Matrix4f[] copy = new Matrix4f[pose.length];
        for (int n = 0; n < pose.length; n++) copy[n] = new Matrix4f(pose[n]);
        return copy;
    }

    static @Nullable Model modelFor(String meshId) {
        return model(meshId);
    }

    public static @Nullable Vector3 naturalSize(String meshId) {
        Model model = model(meshId);
        if (model == null || !model.isReady()) return null;
        Vector3f min = model.boundsMin();
        Vector3f max = model.boundsMax();
        return new Vector3(Math.max(0.05, max.x - min.x), Math.max(0.05, max.y - min.y), Math.max(0.05, max.z - min.z));
    }

    public static void fillMask(MeshPart part, Matrix4fc projectionView, Vector3 camera, float partialTick, float r, float g, float b, float a) {
        if (part.meshId.isEmpty()) return;
        Model model = model(part.meshId);
        if (model == null || !model.isReady()) return;
        CFrame world = ClientScene.motion().sample(part, partialTick);
        Vector3 at = world.position().sub(camera);
        Quat turn = world.rotation();
        Vector3f min = model.boundsMin();
        Vector3f max = model.boundsMax();
        Matrix4f matrix = new Matrix4f()
                .translation((float) at.x(), (float) at.y(), (float) at.z())
                .rotate(new Quaternionf((float) turn.x(), (float) turn.y(), (float) turn.z(), (float) turn.w()))
                .scale(stretch(part.size.x(), max.x - min.x), stretch(part.size.y(), max.y - min.y), stretch(part.size.z(), max.z - min.z))
                .translate(-(min.x + max.x) / 2, -(min.y + max.y) / 2, -(min.z + max.z) / 2);
        Playing playing = ANIMATORS.get(part.id());
        Matrix4f[] pose = playing != null && playing.animator().model() == model ? playing.animator().pose() : null;
        model.fillMask(projectionView, matrix, pose, r, g, b, a);
    }

    private static float stretch(double size, float own) {
        return own < 1e-5f ? 1f : (float) (size / own);
    }

    private static @Nullable Model model(String meshId) {
        if (meshId.startsWith(Res.SCHEME)) return fromPlace(meshId);
        Loaded known = MODELS.get(meshId);
        if (known != null) return known.model();
        if (MISSING.contains(meshId)) return null;
        Identifier id = Identifier.tryParse(meshId);
        if (id == null || ModelFormat.fromIdentifier(id) == null) return missing(meshId, "is not a model file");
        try {
            Model model = Models.load(id);
            MODELS.put(meshId, new Loaded(model, 0));
            return model;
        } catch (RuntimeException e) {
            return missing(meshId, e.getMessage());
        }
    }

    private static final Map<String, Long> CHECKED = new HashMap<>();

    private static @Nullable Model fromPlace(String meshId) {
        Loaded known = MODELS.get(meshId);
        long now = System.currentTimeMillis();
        Long last = CHECKED.get(meshId);
        if (known != null && last != null && now - last < 1000) return known.model();
        CHECKED.put(meshId, now);
        byte[] bytes = PlaceFiles.read(meshId);
        if (bytes == null) return known != null ? known.model() : missing(meshId, "is not in the place");
        int hash = Arrays.hashCode(bytes);
        if (known != null && known.hash() == hash) return known.model();
        ModelFormat format = ModelFormat.fromPath(meshId);
        if (format == null) return missing(meshId, "is not a model file");
        try {
            Model model = format == ModelFormat.GLTF
                    ? Models.load(AmmeshConverter.convert(bytes, PlaceFiles.idOf(meshId)), ModelFormat.AMMESH)
                    : Models.load(bytes, format);
            if (known != null) known.model().dispose();
            MODELS.put(meshId, new Loaded(model, hash));
            MISSING.remove(meshId);
            return model;
        } catch (RuntimeException e) {
            return known != null ? known.model() : missing(meshId, e.getMessage());
        }
    }

    private static @Nullable Model missing(String meshId, String why) {
        if (MISSING.add(meshId)) MoudMod.LOG.warn("mesh {} cannot be drawn: {}", meshId, why);
        return null;
    }
}
