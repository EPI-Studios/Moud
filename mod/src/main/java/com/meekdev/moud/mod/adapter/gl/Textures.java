package com.meekdev.moud.mod.adapter.gl;

import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;

public final class Textures {

    private Textures() {}

    public static void readPixels(int x, int y, int width, int height, ByteBuffer rgba) {
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(x, y, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
    }
}
