package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.render.Sky;
import com.meekdev.moud.mod.adapter.gl.Programs;
import com.meekdev.moud.mod.adapter.gl.RenderState;
import com.meekdev.moud.mod.adapter.gl.VertexArray;
import com.meekdev.moud.mod.adapter.ui.UiImages;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

public final class SkyBox {

    private record Face(int texture, Vector3f middle, Vector3f right, Vector3f up, float half) {}

    private static final Identifier VERTEX = Identifier.fromNamespaceAndPath("moud", "shaders/sky/box.vsh");
    private static final Identifier FRAGMENT = Identifier.fromNamespaceAndPath("moud", "shaders/sky/box.fsh");
    private static final int FLOATS = 5;
    private static final int VERTICES = 6;

    private static final List<Face> FRAME = new ArrayList<>();

    private static ShaderProgram program;
    private static FloatBuffer upload;
    private static VertexArray vertices;

    private SkyBox() {}

    public static void register() {
        Pipeline.add(RenderStage.GEOMETRY, 5, "moud sky", context -> draw());
    }

    private static void draw() {
        Sky sky = Environment.sky();
        if (sky == null) return;
        CameraSnapshot camera = CameraSnapshot.current();
        if (camera == null) return;
        FRAME.clear();
        Matrix4f turn = turn(sky.skyboxOrientation);
        box(sky, turn);
        bodies(sky);
        if (FRAME.isEmpty()) return;
        ensure();
        fill();
        int previous = MainTargetFramebuffer.bind();
        try {
            program.begin();
            program.setMatrix4("ViewProj", camera.viewProj);
            program.setSampler("Sky", 0);
            program.setVec4("Tint", 1, 1, 1, 1);
            vertices.stream(upload);
            RenderState.blend(true);
            RenderState.alphaBlend();
            RenderState.cull(false);
            RenderState.depthMask(false);
            RenderState.depthTest(true);
            RenderState.depthLessOrEqual();
            for (int at = 0; at < FRAME.size(); at++) {
                GlState.bindTexture(0, FRAME.get(at).texture());
                vertices.draw(at * VERTICES, VERTICES);
            }
        } finally {
            VertexArray.unbind();
            Programs.use(0);
            RenderState.depthMask(true);
            RenderState.cull(true);
            RenderState.blend(false);
            MainTargetFramebuffer.restore(previous);
        }
    }

    private static void box(Sky sky, Matrix4f turn) {
        face(sky.skyboxFt, turn, new Vector3f(0, 0, -1), new Vector3f(1, 0, 0), new Vector3f(0, 1, 0));
        face(sky.skyboxBk, turn, new Vector3f(0, 0, 1), new Vector3f(-1, 0, 0), new Vector3f(0, 1, 0));
        face(sky.skyboxRt, turn, new Vector3f(1, 0, 0), new Vector3f(0, 0, 1), new Vector3f(0, 1, 0));
        face(sky.skyboxLf, turn, new Vector3f(-1, 0, 0), new Vector3f(0, 0, -1), new Vector3f(0, 1, 0));
        face(sky.skyboxUp, turn, new Vector3f(0, 1, 0), new Vector3f(1, 0, 0), new Vector3f(0, 0, 1));
        face(sky.skyboxDn, turn, new Vector3f(0, -1, 0), new Vector3f(1, 0, 0), new Vector3f(0, 0, -1));
    }

    private static void bodies(Sky sky) {
        if (!sky.celestialBodiesShown) return;
        body(sky.sunTextureId, Environment.sunDirection(), sky.sunAngularSize);
        body(sky.moonTextureId, Environment.sunDirection().neg(), sky.moonAngularSize);
    }

    private static void body(String texture, Vector3 towards, double angularSize) {
        if (texture.isEmpty() || angularSize <= 0) return;
        int gl = UiImages.texture(texture);
        if (gl == 0) return;
        Vector3f middle = new Vector3f((float) towards.x(), (float) towards.y(), (float) towards.z()).normalize();
        Vector3f helper = Math.abs(middle.y) > 0.99f ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(middle).cross(helper).normalize();
        Vector3f up = new Vector3f(right).cross(middle).normalize();
        FRAME.add(new Face(gl, middle, right, up, (float) Math.tan(Math.toRadians(angularSize) / 2)));
    }

    private static void face(String source, Matrix4f turn, Vector3f middle, Vector3f right, Vector3f up) {
        if (source.isEmpty()) return;
        int gl = UiImages.texture(source);
        if (gl == 0) return;
        FRAME.add(new Face(gl, turn.transformDirection(middle), turn.transformDirection(right), turn.transformDirection(up), 1));
    }

    private static Matrix4f turn(Vector3 degrees) {
        return new Matrix4f().rotateXYZ((float) Math.toRadians(degrees.x()),
                (float) Math.toRadians(degrees.y()), (float) Math.toRadians(degrees.z()));
    }

    private static void fill() {
        int floats = FRAME.size() * VERTICES * FLOATS;
        if (upload == null || upload.capacity() < floats) {
            if (upload != null) MemoryUtil.memFree(upload);
            upload = MemoryUtil.memAllocFloat(Math.max(floats, VERTICES * FLOATS * 8));
        }
        upload.clear();
        for (Face face : FRAME) {
            corner(face, -1, 1, 0, 0);
            corner(face, 1, 1, 1, 0);
            corner(face, 1, -1, 1, 1);
            corner(face, -1, 1, 0, 0);
            corner(face, 1, -1, 1, 1);
            corner(face, -1, -1, 0, 1);
        }
        upload.flip();
    }

    private static void corner(Face face, float across, float down, float u, float v) {
        float half = face.half();
        upload.put(face.middle().x + face.right().x * across * half + face.up().x * down * half)
                .put(face.middle().y + face.right().y * across * half + face.up().y * down * half)
                .put(face.middle().z + face.right().z * across * half + face.up().z * down * half)
                .put(u)
                .put(v);
    }

    private static void ensure() {
        if (program != null) return;
        program = new ShaderProgram(VERTEX, FRAGMENT);
        vertices = VertexArray.interleaved(3, 2);
    }
}
