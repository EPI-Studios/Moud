package com.meekdev.moud.mod.adapter.gl;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLCapabilities;

public final class WindowContext {

    private static final Identifier VERTEX = Identifier.fromNamespaceAndPath("moud", "shaders/window/canvas.vsh");
    private static final Identifier FRAGMENT = Identifier.fromNamespaceAndPath("moud", "shaders/window/canvas.fsh");

    private static int program;

    private final long window;
    private final GLCapabilities capabilities;
    private final int vao;

    private WindowContext(long window, GLCapabilities capabilities, int vao) {
        this.window = window;
        this.capabilities = capabilities;
        this.vao = vao;
    }

    public static WindowContext create(long window) {
        long main = mainWindow();
        GLCapabilities mainCapabilities = GL.getCapabilities();
        GLFW.glfwMakeContextCurrent(window);
        GLCapabilities capabilities = GL.createCapabilities();
        GLFW.glfwSwapInterval(0);
        int vao = GL30.glGenVertexArrays();
        GLFW.glfwMakeContextCurrent(main);
        GL.setCapabilities(mainCapabilities);
        return new WindowContext(window, capabilities, vao);
    }

    public void present(int texture, int width, int height, boolean transparent) {
        if (program == 0) program = Programs.link("window shader", VERTEX, FRAGMENT);
        inside(() -> {
            GL11.glViewport(0, 0, width, height);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_BLEND);
            RenderState.clear(0, 0, 0, transparent ? 0 : 1);
            if (texture != 0) {
                GL20.glUseProgram(program);
                GL20.glUniform1i(GL20.glGetUniformLocation(program, "Canvas"), 0);
                GL20.glUniform1f(GL20.glGetUniformLocation(program, "Opaque"), transparent ? 0 : 1);
                GL30.glBindVertexArray(vao);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                GL30.glBindVertexArray(0);
                GL20.glUseProgram(0);
            }
            GLFW.glfwSwapBuffers(window);
        });
    }

    public void close() {
        inside(() -> GL30.glDeleteVertexArrays(vao));
    }

    private void inside(Runnable work) {
        long main = mainWindow();
        GLCapabilities mainCapabilities = GL.getCapabilities();
        GLFW.glfwMakeContextCurrent(window);
        GL.setCapabilities(capabilities);
        try {
            work.run();
        } finally {
            GLFW.glfwMakeContextCurrent(main);
            GL.setCapabilities(mainCapabilities);
        }
    }

    private static long mainWindow() {
        return Minecraft.getInstance().getWindow().handle();
    }
}
