package com.moud.client.fabric.render.hud;

import com.moud.client.fabric.render.MoudTextures;
import com.moud.client.fabric.render.sprite.SpriteSheets;
import com.moud.net.protocol.SceneSnapshot;
import net.minecraft.util.Identifier;

public final class TextureRectRenderer implements ControlRenderer {

    public static final TextureRectRenderer INSTANCE = new TextureRectRenderer();

    private TextureRectRenderer() {}

    @Override
    public void render(ControlRenderContext ctx, SceneSnapshot.NodeSnapshot node, int x, int y, int w, int h) {
        if (renderAnimated(ctx, node, x, y, w, h)) {
            return;
        }
        if (renderStatic(ctx, node, x, y, w, h)) {
            return;
        }
        ctx.fill(x, y, w, h, ControlRenderContext.BG);
        ctx.border(x, y, w, h, ControlRenderContext.BORDER);
    }

    private boolean renderAnimated(ControlRenderContext ctx, SceneSnapshot.NodeSnapshot node, int x, int y, int w, int h) {
        if (!"AnimatedTextureRect".equals(node.type())) {
            return false;
        }
        SpriteSheets.ResolvedFrame frame = SpriteSheets.resolve(
                ControlRenderContext.strProp(node, "sprite_sheet", ""),
                ControlRenderContext.strProp(node, "texture", ""),
                ControlRenderContext.strProp(node, "animation", ""),
                ControlRenderContext.boolProp(node, "playing", true),
                ControlRenderContext.boolProp(node, "loop", true),
                ControlRenderContext.floatProp(node, "speed_scale", 1f),
                Math.round(ControlRenderContext.floatProp(node, "frame", 0f)),
                System.nanoTime() / 1_000_000L
        );
        if (frame == null) {
            return false;
        }
        drawFrame(ctx, node, x, y, w, h, frame.textureId(), frame.u0(), frame.v0(), frame.u1(), frame.v1(),
                frame.textureWidth(), frame.textureHeight(), frame.offsetX(), frame.offsetY(), frame.frameWidth(), frame.frameHeight(), frame.sourceWidth(), frame.sourceHeight());
        return true;
    }

    private boolean renderStatic(ControlRenderContext ctx, SceneSnapshot.NodeSnapshot node, int x, int y, int w, int h) {
        String textureRef = switch (node.type()) {
            case "TextureButton" -> firstNonBlank(
                    ControlRenderContext.strProp(node, "texture_normal", ""),
                    ControlRenderContext.strProp(node, "texture_hover", ""),
                    ControlRenderContext.strProp(node, "texture_pressed", ""),
                    ControlRenderContext.strProp(node, "texture_disabled", "")
            );
            default -> ControlRenderContext.strProp(node, "texture", "");
        };
        if (textureRef == null || textureRef.isBlank()) {
            return false;
        }
        Identifier textureId = MoudTextures.resolve(textureRef);
        MoudTextures.TextureSize size = MoudTextures.sizeOf(textureId);

        int patchL = (int) ControlRenderContext.floatProp(node, "patch_margin_left", 0f);
        int patchT = (int) ControlRenderContext.floatProp(node, "patch_margin_top", 0f);
        int patchR = (int) ControlRenderContext.floatProp(node, "patch_margin_right", 0f);
        int patchB = (int) ControlRenderContext.floatProp(node, "patch_margin_bottom", 0f);
        if (patchL > 0 || patchT > 0 || patchR > 0 || patchB > 0) {
            ctx.drawNinePatch(textureId, x, y, w, h, size.width(), size.height(),
                    patchL, patchT, patchR, patchB, 0xFFFFFFFF);
            return true;
        }

        drawFrame(ctx, node, x, y, w, h, textureId, 0f, 0f, 1f, 1f, size.width(), size.height(), 0, 0, size.width(), size.height(), size.width(), size.height());
        return true;
    }

    private void drawFrame(ControlRenderContext ctx,
                           SceneSnapshot.NodeSnapshot node,
                           int x, int y, int w, int h,
                           Identifier textureId,
                           float u0, float v0, float u1, float v1,
                           int textureWidth, int textureHeight,
                           int offsetX, int offsetY,
                           int frameWidth, int frameHeight,
                           int sourceWidth, int sourceHeight) {
        int drawX = x + Math.round(offsetX * (w / (float) Math.max(1, sourceWidth)));
        int drawY = y + Math.round(offsetY * (h / (float) Math.max(1, sourceHeight)));
        int drawW = Math.max(1, Math.round(frameWidth * (w / (float) Math.max(1, sourceWidth))));
        int drawH = Math.max(1, Math.round(frameHeight * (h / (float) Math.max(1, sourceHeight))));
        ctx.drawTexture(textureId, drawX, drawY, drawW, drawH, u0, v0, u1, v1, textureWidth, textureHeight, 0xFFFFFFFF);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
