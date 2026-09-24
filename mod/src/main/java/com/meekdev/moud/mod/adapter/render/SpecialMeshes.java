package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.model.Model;
import com.meekdev.amnetic.client.model.ModelLook;
import com.meekdev.amnetic.client.model.Models;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.MeshPart;
import com.meekdev.moud.core.part.MeshType;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.part.SpecialMesh;
import com.meekdev.moud.core.ui.ViewportFrame;
import com.meekdev.moud.mod.adapter.render.effect.EffectTextures;
import com.meekdev.moud.mod.client.ClientScene;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class SpecialMeshes {

    private static final Map<Part, SpecialMesh> BY_PART = new IdentityHashMap<>();
    private static final Set<Part> BEFORE = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<SpecialMesh, ModelLook> LOOKS = new WeakHashMap<>();
    private static final Matrix4f WORLD = new Matrix4f();

    private SpecialMeshes() {}

    public static void register() {
        Models.onFrame(context -> frame());
    }

    public static void prepareFrame() {
        BY_PART.clear();
        InstanceTree tree = ClientScene.tree();
        if (tree != null) {
            for (Instance instance : tree.ofClass(Classes.SPECIAL_MESH)) {
                if (!(instance instanceof SpecialMesh mesh) || !(mesh.parent() instanceof Part part)) continue;
                if (part instanceof MeshPart || Skins.wearsSkin(part)) continue;
                BY_PART.putIfAbsent(part, mesh);
            }
        }
        if (!BEFORE.equals(BY_PART.keySet())) {
            BEFORE.clear();
            BEFORE.addAll(BY_PART.keySet());
            Parts.invalidateStatic();
        }
    }

    public static boolean drawn(Part part) {
        return BY_PART.containsKey(part);
    }

    private static void frame() {
        if (BY_PART.isEmpty()) return;
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (Map.Entry<Part, SpecialMesh> entry : BY_PART.entrySet()) {
            Part part = entry.getKey();
            SpecialMesh mesh = entry.getValue();
            boolean ghost = EditorView.ghost(part);
            if (!ghost && (!part.visible || part.transparency >= 1)) continue;
            if (!part.isAlive() || !mesh.isAlive() || ViewportFrame.inside(part)) continue;
            Model model = model(mesh);
            if (model == null || !model.isReady()) continue;
            CFrame world = ClientScene.motion().sample(part, partialTick);
            ModelLook look = look(part, mesh, model, ghost);
            Matrix4f placed = place(world.position(), world.rotation(), part, mesh, model, look.piece(), WORLD);
            model.render(placed, null, null, look);
        }
    }

    public static boolean fillMask(Part part, Matrix4fc projectionView, Vector3 camera, float partialTick, float r, float g, float b, float a) {
        SpecialMesh mesh = BY_PART.get(part);
        if (mesh == null) return false;
        Model model = model(mesh);
        if (model == null || !model.isReady()) return true;
        CFrame world = ClientScene.motion().sample(part, partialTick);
        Model.Piece piece = piece(mesh, model);
        Matrix4f matrix = place(world.position().sub(camera), world.rotation(), part, mesh, model, piece, new Matrix4f());
        model.fillMask(projectionView, matrix, null, piece, r, g, b, a);
        return true;
    }

    private static @Nullable Model model(SpecialMesh mesh) {
        if (mesh.meshType != MeshType.FILE_MESH) return SpecialShapes.of(mesh.meshType);
        return mesh.meshId.isEmpty() ? null : Meshes.modelFor(mesh.meshId);
    }

    private static Model.@Nullable Piece piece(SpecialMesh mesh, Model model) {
        return mesh.meshType == MeshType.FILE_MESH ? MeshLooks.piece(mesh.meshId, model) : null;
    }

    private static Matrix4f place(Vector3 at, Quat turn, Part part, SpecialMesh mesh, Model model, Model.@Nullable Piece piece, Matrix4f into) {
        Vector3 offset = mesh.offset;
        Vector3 scale = mesh.scale;
        Quaternionf rotation = new Quaternionf((float) turn.x(), (float) turn.y(), (float) turn.z(), (float) turn.w());
        into.translationRotate((float) at.x(), (float) at.y(), (float) at.z(), rotation);
        into.translate((float) offset.x(), (float) offset.y(), (float) offset.z());
        if (mesh.meshType != MeshType.FILE_MESH) {
            Vector3 size = part.size;
            return into.scale((float) (size.x() * scale.x()), (float) (size.y() * scale.y()), (float) (size.z() * scale.z()));
        }
        into.scale((float) scale.x(), (float) scale.y(), (float) scale.z());
        if (EditableModels.owns(model)) return into;
        Vector3f min = piece == null ? model.boundsMin() : piece.min();
        Vector3f max = piece == null ? model.boundsMax() : piece.max();
        return into.translate(-(min.x + max.x) / 2, -(min.y + max.y) / 2, -(min.z + max.z) / 2);
    }

    private static ModelLook look(Part part, SpecialMesh mesh, Model model, boolean ghost) {
        boolean file = mesh.meshType == MeshType.FILE_MESH;
        ModelLook look = LOOKS.computeIfAbsent(mesh, ignored -> new ModelLook());
        look.reset().piece(piece(mesh, model));
        boolean textured = !mesh.textureId.isEmpty();
        Color tint = file || textured ? mesh.vertexColor : part.color;
        float opacity = ghost ? EditorView.GHOST_ALPHA : (float) (1 - part.transparency);
        look.all().tint(tint.r(), tint.g(), tint.b()).opacity(opacity);
        if (!file) look.all().roughness((float) part.roughness).metallic((float) part.metallic);
        if (textured) look.all().baseColorGlTexture(EffectTextures.of(mesh.textureId).gl());
        return look;
    }
}
