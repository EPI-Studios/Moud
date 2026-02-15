package com.moud.client.fabric.editor.overlay;

import com.miry.graphics.Texture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

final class ViewportCapture {
    private static final long MIN_CAPTURE_INTERVAL_NS = 16_666_667L; // ~60 FPS cap

    private Texture front;
    private Texture back;
    private long lastCaptureNs;

    Texture texture() {
        return front;
    }

    void ensureInitialized() {
        if (front != null && back != null) {
            return;
        }

        close();
        if (GLFW.glfwGetCurrentContext() == 0L) {
            return;
        }

        front = new Texture();
        front.setFilteringLinear();
        back = new Texture();
        back.setFilteringLinear();
    }

    void capture(Window window) {
        if (window == null) {
            return;
        }
        ensureInitialized();

        long now = System.nanoTime();
        if (now - lastCaptureNs < MIN_CAPTURE_INTERVAL_NS) {
            return;
        }
        lastCaptureNs = now;

        Framebuffer src = null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            src = client.getFramebuffer();
        }

        int fbw = window.getFramebufferWidth();
        int fbh = window.getFramebufferHeight();
        if (src != null) {
            fbw = src.textureWidth;
            fbh = src.textureHeight;
        }
        if (fbw <= 0 || fbh <= 0) {
            return;
        }

        Texture write = back;
        if (write.width() != fbw || write.height() != fbh) {
            write.allocateRgba(fbw, fbh);
            write.setFilteringLinear();
        }

        int prevReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        if (src != null) {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, src.fbo);
            GL11.glReadBuffer(src.fbo == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
        }
        int prevActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int prev = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, write.id());
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, fbw, fbh);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
            GL13.glActiveTexture(prevActive);
            if (src != null) {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
                GL11.glReadBuffer(prevReadBuffer);
            }
        }

        Texture tmp = front;
        front = back;
        back = tmp;
    }

    void close() {
        if (front == null && back == null) {
            return;
        }
        if (GLFW.glfwGetCurrentContext() == 0L) {
            front = null;
            back = null;
            return;
        }
        if (front != null) {
            front.close();
            front = null;
        }
        if (back != null) {
            back.close();
            back = null;
        }
    }
}
