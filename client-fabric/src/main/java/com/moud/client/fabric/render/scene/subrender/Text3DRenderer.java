package com.moud.client.fabric.render.scene.subrender;

import com.moud.client.fabric.render.scene.math.Pose;
import com.moud.client.fabric.render.scene.util.NodePropertyUtils;
import com.moud.net.protocol.SceneSnapshot;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class Text3DRenderer {
    public void render(SceneSnapshot.NodeSnapshot node,
                       Pose world,
                       VertexConsumerProvider.Immediate consumers,
                       MatrixStack matrices,
                       Vec3d camPos,
                       Camera camera,
                       MinecraftClient client) {
        if (node == null || world == null || consumers == null || matrices == null
                || camPos == null || camera == null || client == null || client.textRenderer == null) {
            return;
        }

        String text = NodePropertyUtils.stringProp(node, "text");
        if (text == null || text.isEmpty()) {
            text = "Text3D";
        }

        TextRenderer textRenderer = client.textRenderer;
        float size = Math.max(0.001f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "size"), 0.025f));
        boolean billboard = NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "billboard"), true);
        boolean shadow = NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "shadow"), true);
        boolean seeThrough = NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "see_through"), false);
        boolean background = NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "background"), true);
        boolean doubleSided = NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "double_sided"), false);
        boolean unlit = NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "unlit"), false);
        float maxDistance = Math.max(0.0f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "max_distance"), 0.0f));
        if (maxDistance > 0.0f && camPos.squaredDistanceTo(world.pos.x, world.pos.y, world.pos.z) > (double) (maxDistance * maxDistance)) {
            return;
        }

        int a = Math.round(NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "opacity"), 1.0f)) * 255.0f);
        int r = Math.round(NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "color_tint_r"), 1.0f)) * 255.0f);
        int g = Math.round(NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "color_tint_g"), 1.0f)) * 255.0f);
        int b = Math.round(NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "color_tint_b"), 1.0f)) * 255.0f);
        int color = (a << 24) | (r << 16) | (g << 8) | b;
        int light = unlit
                ? 0x00F000F0
                : WorldRenderer.getLightmapCoordinates(client.world, BlockPos.ofFloored(world.pos.x, world.pos.y, world.pos.z));
        if (light <= 0) {
            light = 0x00F000F0;
        }

        matrices.push();
        matrices.translate(world.pos.x - camPos.x, world.pos.y - camPos.y, world.pos.z - camPos.z);
        String faceCameraMode = NodePropertyUtils.normalizedTextMode(
                NodePropertyUtils.stringProp(node, "face_camera_mode"),
                billboard ? "full" : "none");
        switch (faceCameraMode) {
            case "full" -> matrices.multiply(camera.getRotation());
            case "yaw_only" -> matrices.multiply(new Quaternionf().rotationY((float) Math.toRadians(-camera.getYaw())));
            default -> matrices.multiply(world.rot);
        }

        float scaleFactor = size * Math.max(Math.abs(world.scale.x), Math.max(Math.abs(world.scale.y), Math.abs(world.scale.z)));
        Matrix4f posMatrix = matrices.peek().getPositionMatrix();
        posMatrix.rotate((float) Math.PI, 0.0f, 1.0f, 0.0f);
        posMatrix.scale(-scaleFactor, -scaleFactor, -scaleFactor);

        float maxWidthWorld = Math.max(0.0f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "max_width"), 0.0f));
        int wrapWidthPx = maxWidthWorld > 0.0f
                ? Math.max(1, Math.round(maxWidthWorld / Math.max(scaleFactor, 1e-6f)))
                : 0;
        List<OrderedText> lines = wrapWidthPx > 0
                ? textRenderer.wrapLines(Text.literal(text), wrapWidthPx)
                : List.of(Text.literal(text).asOrderedText());
        if (lines.isEmpty()) {
            lines = List.of(Text.literal("").asOrderedText());
        }

        int[] lineWidths = new int[lines.size()];
        int maxLineWidth = 0;
        for (int i = 0; i < lines.size(); i++) {
            lineWidths[i] = textRenderer.getWidth(lines.get(i));
            maxLineWidth = Math.max(maxLineWidth, lineWidths[i]);
        }

        float lineAdvance = Math.max(1.0f,
                (textRenderer.fontHeight + 1) * Math.max(0.1f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "line_spacing"), 1.0f)));
        float blockHeight = textRenderer.fontHeight + lineAdvance * (lines.size() - 1);
        posMatrix.translate(
                horizontalAnchorOffset(NodePropertyUtils.normalizedTextMode(NodePropertyUtils.stringProp(node, "horizontal_anchor"), "center"), maxLineWidth),
                verticalAnchorOffset(NodePropertyUtils.normalizedTextMode(NodePropertyUtils.stringProp(node, "vertical_anchor"), "center"),
                        textRenderer.fontHeight, blockHeight),
                0.0f
        );

        int bgA = Math.round(NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "background_opacity"), 0.25f)) * 255.0f);
        int bgR = Math.round(NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "background_color_r"), 0.0f)) * 255.0f);
        int bgG = Math.round(NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "background_color_g"), 0.0f)) * 255.0f);
        int bgB = Math.round(NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "background_color_b"), 0.0f)) * 255.0f);
        int bgColor = (bgA << 24) | (bgR << 16) | (bgG << 8) | bgB;
        String alignment = NodePropertyUtils.normalizedTextMode(NodePropertyUtils.stringProp(node, "alignment"), "center");

        drawTextBlock(textRenderer, consumers, lines, lineWidths, maxLineWidth, lineAdvance, blockHeight,
                posMatrix, color, bgColor, light, shadow, seeThrough, background, alignment);
        if (doubleSided) {
            drawTextBlock(textRenderer, consumers, lines, lineWidths, maxLineWidth, lineAdvance, blockHeight,
                    new Matrix4f(posMatrix).rotateY((float) Math.PI),
                    color, bgColor, light, shadow, seeThrough, background, alignment);
        }
        matrices.pop();
    }

    private void drawTextBlock(TextRenderer textRenderer,
                               VertexConsumerProvider.Immediate consumers,
                               List<OrderedText> lines,
                               int[] lineWidths,
                               int maxLineWidth,
                               float lineAdvance,
                               float blockHeight,
                               Matrix4f posMatrix,
                               int color,
                               int bgColor,
                               int light,
                               boolean shadow,
                               boolean seeThrough,
                               boolean background,
                               String alignment) {
        if (background && (bgColor >>> 24) > 0) {
            VertexConsumer bg = consumers.getBuffer(seeThrough
                    ? RenderLayer.getTextBackgroundSeeThrough()
                    : RenderLayer.getTextBackground());
            bg.vertex(posMatrix, -1.0f, -1.0f, 0.0f).color(bgColor).light(light);
            bg.vertex(posMatrix, -1.0f, blockHeight, 0.0f).color(bgColor).light(light);
            bg.vertex(posMatrix, maxLineWidth, blockHeight, 0.0f).color(bgColor).light(light);
            bg.vertex(posMatrix, maxLineWidth, -1.0f, 0.0f).color(bgColor).light(light);
        }

        TextRenderer.TextLayerType layer = seeThrough ? TextRenderer.TextLayerType.SEE_THROUGH : TextRenderer.TextLayerType.NORMAL;
        for (int i = 0; i < lines.size(); i++) {
            float x = alignedLineOffset(alignment, maxLineWidth, lineWidths[i]);
            float y = i * lineAdvance;
            textRenderer.draw(lines.get(i), x, y, color, shadow, posMatrix, consumers, layer, 0, light);
        }
    }

    private float horizontalAnchorOffset(String anchor, int width) {
        return switch (anchor) {
            case "left" -> 0.0f;
            case "right" -> -width;
            default -> -width * 0.5f;
        };
    }

    private float verticalAnchorOffset(String anchor, int fontHeight, float blockHeight) {
        return switch (anchor) {
            case "top" -> 0.0f;
            case "bottom" -> -blockHeight;
            case "baseline" -> -(fontHeight - 1.0f);
            default -> -blockHeight * 0.5f;
        };
    }

    private float alignedLineOffset(String alignment, int blockWidth, int lineWidth) {
        return switch (alignment) {
            case "left" -> 0.0f;
            case "right" -> blockWidth - lineWidth;
            default -> (blockWidth - lineWidth) * 0.5f;
        };
    }
}
