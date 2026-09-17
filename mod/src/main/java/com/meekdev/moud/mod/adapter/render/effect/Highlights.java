package com.meekdev.moud.mod.adapter.render.effect;

import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.effect.Adornees;
import com.meekdev.moud.core.effect.DepthMode;
import com.meekdev.moud.core.effect.Highlight;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.ui.ViewportFrame;
import com.meekdev.moud.mod.adapter.render.EditorView;
import com.meekdev.moud.mod.adapter.render.MaskPass;
import com.meekdev.moud.mod.client.ClientScene;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

final class Highlights {

    private static final Identifier COMPOSITE = Identifier.fromNamespaceAndPath("moud", "shaders/effect/highlight.fsh");
    private static final float OUTLINE_RADIUS = 2.0f;

    private static final List<Part> PARTS = new ArrayList<>();
    private static final MaskPass PASS = new MaskPass("Moud Highlight Mask", COMPOSITE, OUTLINE_RADIUS,
            "highlights failed to draw and are off until restart");

    private Highlights() {}

    static void register() {
        Pipeline.add(RenderStage.POST, 80, "moud highlights", context -> EditorView.filled(Highlights::draw));
    }

    private static void draw() {
        InstanceTree tree = ClientScene.tree();
        if (tree == null || PASS.broken()) return;
        List<Highlight> highlights = tree.ofClass(Classes.HIGHLIGHT);
        if (highlights.isEmpty()) return;
        CameraSnapshot camera = CameraSnapshot.current();
        if (camera == null) return;
        PASS.run(() -> drawAll(highlights, camera));
    }

    private static void drawAll(List<Highlight> highlights, CameraSnapshot camera) {
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vector3 eye = new Vector3(camera.eye.x, camera.eye.y, camera.eye.z);
        Matrix4f projectionView = new Matrix4f(camera.projection).mul(camera.view);
        for (Highlight highlight : highlights) {
            if (!highlight.enabled || ViewportFrame.inside(highlight)) continue;
            if (highlight.fillTransparency >= 1 && highlight.outlineTransparency >= 1) continue;
            PARTS.clear();
            for (Part part : Adornees.parts(Adornees.target(highlight, highlight.adornee))) {
                if (part.visible && part.transparency < 1 && !ViewportFrame.inside(part)) PARTS.add(part);
            }
            if (PARTS.isEmpty()) continue;
            fillMask(highlight.depthMode, projectionView, eye, partialTick);
            blend(highlight);
        }
    }

    private static void fillMask(DepthMode depth, Matrix4f projectionView, Vector3 eye, float partialTick) {
        PASS.beginMask(depth == DepthMode.OCCLUDED);
        for (Part part : PARTS) MaskPass.fill(part, projectionView, eye, partialTick, 1, 1, 1, 1);
        PASS.endMask();
    }

    private static void blend(Highlight highlight) {
        ShaderProgram composite = PASS.beginComposite();
        composite.setVec4("FillColor", highlight.fillColor.r(), highlight.fillColor.g(), highlight.fillColor.b(),
                (float) (1 - highlight.fillTransparency));
        composite.setVec4("OutlineColor", highlight.outlineColor.r(), highlight.outlineColor.g(), highlight.outlineColor.b(),
                (float) (1 - highlight.outlineTransparency));
        PASS.endComposite();
    }
}
