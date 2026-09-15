package com.meekdev.moud.mod.adapter.gl;

import com.mojang.blaze3d.opengl.GlStateManager;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

public final class Textures {

    private static int opaqueFramebuffer;

    private Textures() {}

    public static void bindDirectly(int texture) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    public static void activateDirectly(int unit) {
        GL13.glActiveTexture(unit);
    }

    public static void readPixels(int x, int y, int width, int height, ByteBuffer rgba) {
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(x, y, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
    }

    public static void makeOpaque(int texture) {
        if (opaqueFramebuffer == 0) opaqueFramebuffer = GlStateManager.glGenFramebuffers();
        int previous = GlStateManager.getFrameBuffer(GL30.GL_DRAW_FRAMEBUFFER);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, opaqueFramebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        RenderState.colorMask(false, false, false, true);
        RenderState.noScissor();
        RenderState.clear(0.0f, 0.0f, 0.0f, 1.0f);
        RenderState.colorMask(true, true, true, true);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
    }
}
