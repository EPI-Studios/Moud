package com.meekdev.moud.mod.adapter.gl;

import com.meekdev.amnetic.client.render.ShaderProgram;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL41C;
import org.lwjgl.opengl.GLCapabilities;

public final class Programs {

    private static Boolean separate;

    private Programs() {}

    public static boolean separateUniforms() {
        if (separate == null) {
            GLCapabilities caps = GL.getCapabilities();
            separate = caps.OpenGL41 || caps.GL_ARB_separate_shader_objects;
        }
        return separate;
    }

    public static boolean exists(int program) {
        return GL20.glIsProgram(program);
    }

    public static int current() {
        return GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    }

    public static void use(int program) {
        GL20.glUseProgram(program);
    }

    public static int location(int program, String name) {
        return GL20.glGetUniformLocation(program, name);
    }

    public static void uniform(int program, int location, int value, boolean direct) {
        if (direct) GL41C.glProgramUniform1i(program, location, value);
        else GL20.glUniform1i(location, value);
    }

    public static void uniform(int program, int location, float[] values, int size, boolean direct) {
        float[] v = new float[4];
        System.arraycopy(values, 0, v, 0, Math.min(size, values.length));
        switch (size) {
            case 1 -> {
                if (direct) GL41C.glProgramUniform1f(program, location, v[0]);
                else GL20.glUniform1f(location, v[0]);
            }
            case 2 -> {
                if (direct) GL41C.glProgramUniform2f(program, location, v[0], v[1]);
                else GL20.glUniform2f(location, v[0], v[1]);
            }
            case 3 -> {
                if (direct) GL41C.glProgramUniform3f(program, location, v[0], v[1], v[2]);
                else GL20.glUniform3f(location, v[0], v[1], v[2]);
            }
            default -> {
                if (direct) GL41C.glProgramUniform4f(program, location, v[0], v[1], v[2], v[3]);
                else GL20.glUniform4f(location, v[0], v[1], v[2], v[3]);
            }
        }
    }

    public static int link(String name, Identifier vertex, Identifier fragment) {
        int vertexShader = compile(name, GL20.GL_VERTEX_SHADER, ShaderProgram.readSource(vertex));
        int fragmentShader = compile(name, GL20.GL_FRAGMENT_SHADER, ShaderProgram.readSource(fragment));
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(program);
            GL20.glDeleteProgram(program);
            throw new IllegalStateException(name + " did not link: " + log);
        }
        return program;
    }

    private static int compile(String name, int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(name + " did not compile: " + log);
        }
        return shader;
    }
}
