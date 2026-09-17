package com.meekdev.moud.mod.adapter.gl;

import com.mojang.blaze3d.opengl.GlStateManager;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

public final class Textures {

    public static final int NEAREST = GL11.GL_NEAREST;
    public static final int LINEAR = GL11.GL_LINEAR;
    public static final int REPEAT = GL11.GL_REPEAT;
    public static final int CLAMP = GL12.GL_CLAMP_TO_EDGE;

    private static int opaqueFramebuffer;

    private Textures() {}

    public static void bindDirectly(int texture) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    public static void activateDirectly(int unit) {
        GL13.glActiveTexture(unit);
    }

    public static int upload(int size, ByteBuffer rgba) {
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, size, size, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
        return texture;
    }

    public static int sampler(int filter, int wrap) {
        int sampler = GL33.glGenSamplers();
        GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_S, wrap);
        GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_T, wrap);
        return sampler;
    }

    public static void bindSampler(int unit, int sampler) {
        GL33.glBindSampler(unit, sampler);
    }

    public static void filterNearest(int texture) {
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
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
