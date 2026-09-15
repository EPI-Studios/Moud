package com.meekdev.moud.mod.adapter.gl;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL11;

public final class RenderState {

    private static final float OFFSET_FACTOR = -1.0f;
    private static final float OFFSET_UNITS = -4.0f;

    private RenderState() {}

    public static void blend(boolean on) {
        if (on) GlStateManager._enableBlend();
        else GlStateManager._disableBlend();
        capability(GL11.GL_BLEND, on);
    }

    public static void depthTest(boolean on) {
        if (on) GlStateManager._enableDepthTest();
        else GlStateManager._disableDepthTest();
        capability(GL11.GL_DEPTH_TEST, on);
    }

    public static void depthMask(boolean on) {
        GlStateManager._depthMask(on);
        GL11.glDepthMask(on);
    }

    public static void depthLessOrEqual() {
        GlStateManager._depthFunc(GL11.GL_LEQUAL);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
    }

    public static void cull(boolean on) {
        if (on) GlStateManager._enableCull();
        else GlStateManager._disableCull();
        capability(GL11.GL_CULL_FACE, on);
    }

    public static void scissor(int x, int y, int width, int height) {
        GlStateManager._enableScissorTest();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x, y, width, height);
    }

    public static void noScissor() {
        GlStateManager._disableScissorTest();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public static void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        GL11.glColorMask(red, green, blue, alpha);
    }

    public static void polygonOffset() {
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(OFFSET_FACTOR, OFFSET_UNITS);
    }

    public static void noPolygonOffset() {
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
    }

    public static void clear(float red, float green, float blue, float alpha) {
        GL11.glClearColor(red, green, blue, alpha);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    private static void capability(int capability, boolean on) {
        if (on) GL11.glEnable(capability);
        else GL11.glDisable(capability);
    }
}
