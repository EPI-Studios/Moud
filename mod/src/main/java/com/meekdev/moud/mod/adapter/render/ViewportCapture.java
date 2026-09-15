package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.gl.Textures;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

public final class ViewportCapture {

    private static boolean active;
    private static boolean captured;
    private static boolean broken;
    private static int background;
    private static @Nullable TextureTarget target;

    private ViewportCapture() {}

    public static void register() {
        Pipeline.add(RenderStage.BEFORE_GUI, 1000, "moud editor viewport capture", context -> capture());
    }

    public static void show(int clearColorArgb) {
        active = true;
        background = clearColorArgb;
    }

    public static void hide() {
        active = false;
        captured = false;
    }

    public static @Nullable RenderTarget frame() {
        return active && captured ? target : null;
    }

    private static void makeOpaque(RenderTarget frame) {
        if (frame.getColorTexture() instanceof GlTexture texture) Textures.makeOpaque(texture.glId());
    }

    private static void capture() {
        captured = false;
        if (!active || broken) return;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null || main.getColorTexture() == null) return;
        try {
            if (target == null) {
                target = new TextureTarget("Moud Editor Viewport", main.width, main.height, false);
            } else if (target.width != main.width || target.height != main.height) {
                target.resize(main.width, main.height);
            }
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(main.getColorTexture(), target.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
            makeOpaque(target);
            encoder.clearColorTexture(main.getColorTexture(), background);
            captured = true;
        } catch (RuntimeException e) {
            broken = true;
            MoudMod.LOG.error("the editor viewport capture failed and is off until restart", e);
        }
    }
}
