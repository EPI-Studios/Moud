package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.model.Animator;
import com.meekdev.amnetic.client.model.Model;
import com.meekdev.amnetic.client.model.ModelFormat;
import com.meekdev.amnetic.client.model.Models;
import com.meekdev.amnetic.client.model.internal.ammesh.AmmeshConverter;
import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.MeshPart;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.client.PlaceFiles;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
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
    private static final Quaternionf TURN = new Quaternionf();

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
            if (!part.visible || part.transparency >= 1 || part.meshId.isEmpty()) continue;
            Model model = model(part.meshId);
            if (model == null || !model.isReady()) continue;
            draw(part, model, partialTick, dt);
        }
        ANIMATORS.keySet().removeIf(id -> !(tree.byId(id) instanceof MeshPart));
    }

    private static void draw(MeshPart part, Model model, float partialTick, float dt) {
        CFrame world = ClientScene.motion().sample(part, partialTick);
        Vector3 at = world.position();
        Quat turn = world.rotation();
        Vector3f min = model.boundsMin();
        Vector3f max = model.boundsMax();
        float sx = stretch(part.size.x(), max.x - min.x);
        float sy = stretch(part.size.y(), max.y - min.y);
        float sz = stretch(part.size.z(), max.z - min.z);
        WORLD.translation((float) at.x(), (float) at.y(), (float) at.z())
                .rotate(TURN.set((float) turn.x(), (float) turn.y(), (float) turn.z(), (float) turn.w()))
                .scale(sx, sy, sz)
                .translate(-(min.x + max.x) / 2, -(min.y + max.y) / 2, -(min.z + max.z) / 2);

        if (part.animation.isEmpty() || !model.isAnimated()) {
            ANIMATORS.remove(part.id());
            model.render(WORLD);
            return;
        }
        Playing playing = ANIMATORS.get(part.id());
        if (playing == null || playing.animator().model() != model || !playing.clip().equals(part.animation)) {
            if (!model.clipNames().contains(part.animation)) {
                if (MISSING.add(part.meshId + "#" + part.animation)) {
                    MoudMod.LOG.warn("{} has no clip {}, it has {}", part.meshId, part.animation, model.clipNames());
                }
                model.render(WORLD);
                return;
            }
            playing = new Playing(model.createAnimator().play(part.animation), part.animation);
            ANIMATORS.put(part.id(), playing);
        }
        Animator animator = playing.animator();
        animator.loop(part.animationLooped).speed((float) part.animationSpeed).update(dt);
        Matrix4f[] pose = animator.pose();
        Matrix4f[] copy = new Matrix4f[pose.length];
        for (int n = 0; n < pose.length; n++) copy[n] = new Matrix4f(pose[n]);
        model.renderPosed(WORLD, copy);
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
        } catch (RuntimeException broken) {
            return missing(meshId, broken.getMessage());
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
        } catch (RuntimeException broken) {
            return known != null ? known.model() : missing(meshId, broken.getMessage());
        }
    }

    private static @Nullable Model missing(String meshId, String why) {
        if (MISSING.add(meshId)) MoudMod.LOG.warn("mesh {} cannot be drawn: {}", meshId, why);
        return null;
    }
}
