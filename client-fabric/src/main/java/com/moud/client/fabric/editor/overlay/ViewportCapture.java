package com.moud.client.fabric.editor.overlay;

import com.miry.graphics.Texture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

final class ViewportCapture {
    private Texture texture;
    private int copyFbo;
    private int width;
    private int height;

    Texture texture() {
        return texture;
    }

    void ensureInitialized() {
        if (GLFW.glfwGetCurrentContext() == 0L) {
            close();
            return;
        }
        if (copyFbo == 0) {
            copyFbo = GL30.glGenFramebuffers();
        }
    }

    void capture(Window window) {
        if (window == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer src = client != null ? client.getFramebuffer() : null;
        if (src == null || src.fbo == 0 || src.getColorAttachment() == 0) {
            clearTexture();
            return;
        }

        int targetW = Math.max(1, Math.min(window.getFramebufferWidth(), src.textureWidth));
        int targetH = Math.max(1, Math.min(window.getFramebufferHeight(), src.textureHeight));
        if (targetW <= 0 || targetH <= 0) {
            clearTexture();
            return;
        }

        ensureTarget(targetW, targetH);
        if (texture == null || copyFbo == 0) {
            return;
        }

        int prevReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDrawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, src.fbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copyFbo);
        GL30.glBlitFramebuffer(
                0, 0, targetW, targetH,
                0, 0, targetW, targetH,
                GL11.GL_COLOR_BUFFER_BIT,
                GL11.GL_NEAREST
        );
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFbo);
    }

    void close() {
        clearTexture();
        if (copyFbo != 0) {
            GL30.glDeleteFramebuffers(copyFbo);
            copyFbo = 0;
        }
    }

    private void ensureTarget(int targetW, int targetH) {
        if (texture != null && width == targetW && height == targetH) {
            return;
        }

        clearTexture();
        texture = new Texture();
        texture.allocateRgba(targetW, targetH);
        texture.setFilteringLinear();

        texture.bind(0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        texture.unbind();

        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copyFbo);
        GL30.glFramebufferTexture2D(
                GL30.GL_DRAW_FRAMEBUFFER,
                GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D,
                texture.id(),
                0
        );

        int status = GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            clearTexture();
            return;
        }

        width = targetW;
        height = targetH;
    }

    private void clearTexture() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
        width = 0;
        height = 0;
    }
}
