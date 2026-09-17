package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.ui.ViewportFrame;
import com.meekdev.moud.mod.adapter.gl.Textures;
import com.meekdev.moud.mod.client.ClientScene;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

public final class EditorOverlay {

    private static final Identifier OUTLINE = Identifier.fromNamespaceAndPath("moud", "shaders/editor/outline.fsh");
    private static final float OUTLINE_RADIUS = 2.5f;
    private static final float[] SELECTED = {1.0f, 0.541f, 0.122f, 1.0f};
    private static final float[] HOVERED = {1.0f, 1.0f, 1.0f, 0.45f};
    private static final float FILL_ALPHA = 0.08f;

    private static boolean active;
    private static Predicate<Instance> pickable = instance -> false;
    private static Set<Integer> selected = Set.of();
    private static int hovered;
    private static boolean pickRequested;
    private static float pickX;
    private static float pickY;
    private static int picked;
    private static Framebuffer ids;
    private static final ByteBuffer PIXEL = BufferUtils.createByteBuffer(4);
    private static final MaskPass PASS = new MaskPass("Moud Editor Mask", OUTLINE, OUTLINE_RADIUS,
            "the editor selection pass failed and is off until restart");

    private EditorOverlay() {}

    public static void register() {
        Pipeline.add(RenderStage.POST, 90, "moud editor selection", context -> EditorView.filled(EditorOverlay::draw));
    }

    public static void show(Predicate<Instance> canPick, Set<Integer> selection, int hover) {
        active = true;
        pickable = canPick;
        selected = selection;
        hovered = hover;
    }

    public static void hide() {
        active = false;
        picked = 0;
    }

    public static void requestPick(float fractionX, float fractionY) {
        pickRequested = true;
        pickX = fractionX;
        pickY = fractionY;
    }

    public static int picked() {
        return picked;
    }

    private static void draw() {
        if (!active || PASS.broken()) return;
        CameraSnapshot camera = CameraSnapshot.current();
        InstanceTree tree = ClientScene.tree();
        if (camera == null || tree == null) return;
        PASS.run(() -> drawPasses(camera, tree));
    }

    private static void drawPasses(CameraSnapshot camera, InstanceTree tree) {
        if (ids == null) ids = Framebuffers.screen("Moud Editor Ids", FramebufferSpec.builder().color(ColorFormat.RGBA8).depthTexture().build());
        Vector3 eye = new Vector3(camera.eye.x, camera.eye.y, camera.eye.z);
        Matrix4f projectionView = new Matrix4f(camera.projection).mul(camera.view);
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        List<Part> drawable = drawable(tree);
        if (pickRequested) pick(drawable, projectionView, eye, partialTick);
        if (!selected.isEmpty() || hovered != 0) outline(drawable, projectionView, eye, partialTick);
    }

    private static List<Part> drawable(InstanceTree tree) {
        List<Part> out = new ArrayList<>();
        for (Part part : tree.ofClass(Classes.PART)) {
            if (pickable.test(part) && !ViewportFrame.inside(part)) out.add(part);
        }
        return out;
    }

    private static void pick(List<Part> drawable, Matrix4f projectionView, Vector3 eye, float partialTick) {
        pickRequested = false;
        MaskPass.begin(ids, true);
        for (int index = 0; index < drawable.size(); index++) {
            int code = index + 1;
            MaskPass.fill(drawable.get(index), projectionView, eye, partialTick,
                    (code & 0xFF) / 255.0f, ((code >> 8) & 0xFF) / 255.0f, ((code >> 16) & 0xFF) / 255.0f, 1.0f);
        }
        int x = Math.clamp((int) (pickX * ids.width()), 0, ids.width() - 1);
        int y = Math.clamp((int) ((1.0f - pickY) * ids.height()), 0, ids.height() - 1);
        PIXEL.clear();
        Textures.readPixels(x, y, 1, 1, PIXEL);
        ids.end();
        int code = (PIXEL.get(0) & 0xFF) | (PIXEL.get(1) & 0xFF) << 8 | (PIXEL.get(2) & 0xFF) << 16;
        picked = code > 0 && code <= drawable.size() ? drawable.get(code - 1).id() : 0;
    }

    private static void outline(List<Part> drawable, Matrix4f projectionView, Vector3 eye, float partialTick) {
        PASS.beginMask(true);
        for (Part part : drawable) {
            boolean isSelected = selected.contains(part.id());
            boolean isHovered = part.id() == hovered;
            if (!isSelected && !isHovered) continue;
            MaskPass.fill(part, projectionView, eye, partialTick, isSelected ? 1.0f : 0.0f, isHovered ? 1.0f : 0.0f, 0.0f, 1.0f);
        }
        PASS.endMask();
        ShaderProgram outline = PASS.beginComposite();
        outline.setVec4("SelectedColor", SELECTED[0], SELECTED[1], SELECTED[2], SELECTED[3]);
        outline.setVec4("HoveredColor", HOVERED[0], HOVERED[1], HOVERED[2], HOVERED[3]);
        outline.setFloat("FillAlpha", FILL_ALPHA);
        PASS.endComposite();
    }
}
