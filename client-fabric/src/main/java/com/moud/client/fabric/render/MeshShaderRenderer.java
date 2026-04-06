package com.moud.client.fabric.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.client.fabric.assets.MoudTextAssets;
import com.moud.client.fabric.render.material.*;
import com.moud.client.fabric.render.mesh.MoudMeshBuffer;
import com.moud.client.fabric.render.veil.*;
import com.moud.core.assets.ResPath;
import com.moud.net.protocol.SceneSnapshot;
import foundry.veil.api.client.render.CameraMatrices;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.block.ShaderBlock;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.registry.VeilShaderBufferRegistry;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import foundry.veil.api.client.render.dynamicbuffer.DynamicBufferType;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

final class MeshShaderRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MeshShaderRenderer.class);

    private final String defaultVert;
    private final String defaultFrag;
    private final String pbrVert;
    private final String pbrFrag;
    private final String meshVertForMaterial;

    private final Map<Long, VeilMaterialBinding> materialBindings = new ConcurrentHashMap<>();
    private ShaderProgram defaultShaderProgram;
    private ShaderProgram pbrShaderProgram;
    private final Map<Long, Integer> meshVaoCache = new ConcurrentHashMap<>();

    private final Object MATERIAL_TEX_LOCK = new Object();
    private final Map<String, MaterialTexCache> textureByMaterialPath = new ConcurrentHashMap<>();
    private final SceneLights sceneLights = new SceneLights();
    private final Map<Long, Map<String, float[]>> prevUniforms = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, float[]>> currUniforms = new ConcurrentHashMap<>();
    private final Set<String> loggedShaderErrors = new HashSet<>();

    SceneLights sceneLights() { return sceneLights; }

    MeshShaderRenderer() {
        defaultVert = loadResource("assets/moud/shaders/builtin/default_mesh.vert");
        defaultFrag = loadResource("assets/moud/shaders/builtin/default_mesh.frag");
        pbrVert = loadResource("assets/moud/shaders/builtin/pbr_mesh.vert");
        pbrFrag = loadResource("assets/moud/shaders/builtin/pbr_mesh.frag");
        meshVertForMaterial = loadResource("assets/moud/shaders/builtin/mesh_material.vert");
    }

    private static String loadResource(String path) {
        try (InputStream is = MeshShaderRenderer.class.getClassLoader().getResourceAsStream(path)) {
            if (is != null) return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
        return "";
    }

    void onSnapshotUpdate(List<SceneSnapshot.NodeSnapshot> nodes) {
        for (SceneSnapshot.NodeSnapshot node : nodes) {
            if (node == null) continue;
            List<SceneSnapshot.Uniform> uniforms = node.uniforms();
            if (uniforms == null || uniforms.isEmpty()) continue;
            long id = node.nodeId();

            Map<String, float[]> curr = currUniforms.get(id);
            if (curr != null && !curr.isEmpty()) {
                Map<String, float[]> prev = prevUniforms.computeIfAbsent(id, k -> new HashMap<>());
                for (Map.Entry<String, float[]> e : curr.entrySet()) {
                    prev.put(e.getKey(), e.getValue().clone());
                }
            }

            Map<String, float[]> next = currUniforms.computeIfAbsent(id, k -> new HashMap<>());
            for (SceneSnapshot.Uniform u : uniforms) {
                if (u == null || u.key() == null || u.values() == null) continue;
                List<Float> vals = u.values();
                float[] arr = new float[vals.size()];
                for (int i = 0; i < vals.size(); i++) arr[i] = vals.get(i);
                next.put(u.key(), arr);
            }
        }
    }

    private void applyInterpolatedUniforms(int pid, long nodeId, SceneSnapshot.NodeSnapshot node, float tickDelta) {
        List<SceneSnapshot.Uniform> uniforms = node.uniforms();
        if (uniforms == null || uniforms.isEmpty()) return;

        Map<String, float[]> prev = prevUniforms.get(nodeId);
        Map<String, float[]> curr = currUniforms.get(nodeId);

        for (SceneSnapshot.Uniform u : uniforms) {
            if (u == null || u.key() == null || u.values() == null) continue;
            String key = u.key();
            List<Float> vals = u.values();
            int size = vals.size();
            if (size < 1 || size > 4) continue;

            float[] c = curr != null ? curr.get(key) : null;
            float[] p = prev != null ? prev.get(key) : null;

            if (c != null && p != null && c.length == p.length) {
                float t = tickDelta;
                switch (size) {
                    case 1 -> GlUtil.uniform1f(pid, key, p[0] + (c[0] - p[0]) * t);
                    case 2 -> GlUtil.uniform2f(pid, key,
                            p[0] + (c[0] - p[0]) * t,
                            p[1] + (c[1] - p[1]) * t);
                    case 3 -> GlUtil.uniform3f(pid, key,
                            p[0] + (c[0] - p[0]) * t,
                            p[1] + (c[1] - p[1]) * t,
                            p[2] + (c[2] - p[2]) * t);
                    case 4 -> GlUtil.uniform4f(pid, key,
                            p[0] + (c[0] - p[0]) * t,
                            p[1] + (c[1] - p[1]) * t,
                            p[2] + (c[2] - p[2]) * t,
                            p[3] + (c[3] - p[3]) * t);
                }
            } else {
                switch (size) {
                    case 1 -> GlUtil.uniform1f(pid, key, vals.get(0));
                    case 2 -> GlUtil.uniform2f(pid, key, vals.get(0), vals.get(1));
                    case 3 -> GlUtil.uniform3f(pid, key, vals.get(0), vals.get(1), vals.get(2));
                    case 4 -> GlUtil.uniform4f(pid, key, vals.get(0), vals.get(1), vals.get(2), vals.get(3));
                }
            }
        }
    }

    void collectLights(List<SceneSnapshot.NodeSnapshot> nodes,
                       Function<Long, VeilSceneNodeRenderer.Pose> poseResolver) {
        sceneLights.collect(nodes, poseResolver);
    }

    void collectLightsAdd(List<SceneSnapshot.NodeSnapshot> nodes,
                          Function<Long, VeilSceneNodeRenderer.Pose> poseResolver) {
        sceneLights.collectAdd(nodes, poseResolver);
    }

    boolean renderNode(SceneSnapshot.NodeSnapshot node, VeilSceneNodeRenderer.Pose world,
                       Vec3d camPos, Camera camera, Matrix4fc viewMatrix, Matrix4fc projectionMatrix,
                       MinecraftClient client, float tickDelta) {
        if (!RenderSystem.isOnRenderThread()) return false;
        String materialPath = VeilSceneNodeRenderer.stringProp(node, "material");
        VeilMaterialBinding binding = null;
        ShaderProgram program;
        Identifier shaderErrorId = null;

        if (materialPath != null && !materialPath.isBlank()) {
            binding = materialBindings.computeIfAbsent(node.nodeId(), id -> new VeilMaterialBinding());
            binding.configure(materialPath, null);
            program = resolveMeshProgram(binding);
            if (program == null) {
                program = getPbrShaderProgram();
                if (program == null) program = getDefaultShaderProgram();
            }
        } else {
            program = getPbrShaderProgram();
            if (program == null) {
                String pbrError = VeilDynamicShaders.getLastError(Identifier.of("moud", "builtin/pbr_mesh"));
                if (pbrError != null && loggedShaderErrors.add("pbr_fallback:" + pbrError)) {
                    LOGGER.warn("[Moud] PBR shader unavailable ({}), falling back to default", pbrError);
                }
                program = getDefaultShaderProgram();
            }
            if (program == null) shaderErrorId = Identifier.of("moud", "builtin/pbr_mesh");
        }

        if (program == null || !program.isValid()) {
            if (shaderErrorId != null) {
                String error = VeilDynamicShaders.getLastError(shaderErrorId);
                if (error != null && loggedShaderErrors.add(shaderErrorId + ":" + error)) {
                    LOGGER.error("[Moud] Shader compilation failed nodeId={} material={} shader={} program={} error={}",
                            node.nodeId(),
                            materialPath,
                            binding == null ? null : binding.shaderPath(),
                            shaderErrorId,
                            error);
                }
            }
            return false;
        }

        MoudMeshBuffer.ensureInitialized();

        float tintR  = clampedProp(node, "color_tint_r", 1f);
        float tintG  = clampedProp(node, "color_tint_g", 1f);
        float tintB  = clampedProp(node, "color_tint_b", 1f);
        float opacity = clampedProp(node, "opacity", 1f);

        ShaderBlock<CameraMatrices> camBlock = VeilRenderSystem.getBlock(VeilShaderBufferRegistry.CAMERA.get());
        CameraMatrices veilCam = camBlock != null ? camBlock.getValue() : null;
        Matrix4f viewMat = veilCam != null ? new Matrix4f(veilCam.getViewMatrix())
                : (viewMatrix != null ? new Matrix4f(viewMatrix) : new Matrix4f());
        Matrix4f projMat = veilCam != null ? new Matrix4f(veilCam.getProjectionMatrix())
                : (projectionMatrix != null ? new Matrix4f(projectionMatrix) : new Matrix4f(RenderSystem.getProjectionMatrix()));

        boolean isSprite3D = "Sprite3D".equals(node.type());
        boolean billboard = VeilSceneNodeRenderer.parseBool(
                VeilSceneNodeRenderer.stringProp(node, "billboard"), isSprite3D);

        String meshType = VeilSceneNodeRenderer.stringProp(node, "mesh");
        if (isSprite3D && (meshType == null || meshType.isBlank())) meshType = "plane";
        boolean isPlane = "plane".equals(meshType);

        float HALF_PI = (float) (Math.PI / 2.0);

        final Matrix4f worldMat;
        final Matrix4f modelMat;
        if (billboard) {
            Quaternionf camRot = viewMat.getNormalizedRotation(new Quaternionf()).conjugate();
            if (isPlane) {
                worldMat = new Matrix4f()
                        .translate(world.pos.x, world.pos.y, world.pos.z)
                        .rotate(camRot)
                        .scale(world.scale.x, world.scale.y, world.scale.z)
                        .rotateX(-HALF_PI)
                        .translate(-0.5f, 0.0f, -0.5f);
                modelMat = new Matrix4f()
                        .translate((float)(world.pos.x - camPos.x),
                                (float)(world.pos.y - camPos.y),
                                (float)(world.pos.z - camPos.z))
                        .rotate(camRot)
                        .scale(world.scale.x, world.scale.y, world.scale.z)
                        .rotateX(-HALF_PI)
                        .translate(-0.5f, 0.0f, -0.5f);
            } else {
                worldMat = new Matrix4f()
                        .translate(world.pos.x, world.pos.y, world.pos.z)
                        .rotate(camRot)
                        .scale(world.scale.x, world.scale.y, world.scale.z)
                        .translate(-0.5f, -0.5f, -0.5f);
                modelMat = new Matrix4f()
                        .translate((float)(world.pos.x - camPos.x),
                                (float)(world.pos.y - camPos.y),
                                (float)(world.pos.z - camPos.z))
                        .rotate(camRot)
                        .scale(world.scale.x, world.scale.y, world.scale.z)
                        .translate(-0.5f, -0.5f, -0.5f);
            }
        } else {
            if (isPlane) {
                worldMat = new Matrix4f()
                        .translate(world.pos.x, world.pos.y, world.pos.z)
                        .rotate(world.rot)
                        .scale(world.scale.x, world.scale.y, world.scale.z)
                        .rotateX(-HALF_PI)
                        .translate(-0.5f, 0.0f, -0.5f);
                modelMat = new Matrix4f()
                        .translate((float)(world.pos.x - camPos.x),
                                (float)(world.pos.y - camPos.y),
                                (float)(world.pos.z - camPos.z))
                        .rotate(world.rot)
                        .scale(world.scale.x, world.scale.y, world.scale.z)
                        .rotateX(-HALF_PI)
                        .translate(-0.5f, 0.0f, -0.5f);
            } else {
                worldMat = new Matrix4f()
                        .translate(world.pos.x, world.pos.y, world.pos.z)
                        .rotate(world.rot)
                        .scale(world.scale.x, world.scale.y, world.scale.z)
                        .translate(-0.5f, -0.5f, -0.5f);
                modelMat = new Matrix4f()
                        .translate((float)(world.pos.x - camPos.x),
                                (float)(world.pos.y - camPos.y),
                                (float)(world.pos.z - camPos.z))
                        .rotate(world.rot)
                        .scale(world.scale.x, world.scale.y, world.scale.z)
                        .translate(-0.5f, -0.5f, -0.5f);
            }
        }

        boolean doubleSided = VeilSceneNodeRenderer.parseBool(
                VeilSceneNodeRenderer.stringProp(node, "double_sided"), false);

        boolean translucent = opacity < 1f;
        if (translucent) { RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); }
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(!translucent);
        if (doubleSided) RenderSystem.disableCull();

        try {
            VeilRenderSystem.setShader(program);
            program.bind();

            int pid = GlUtil.currentProgram();

            GlUtil.uniformMat4(pid, "ModelMat", modelMat);
            GlUtil.uniformMat4(pid, "WorldMat", worldMat);
            GlUtil.uniformMat4(pid, "ViewMat", viewMat);
            GlUtil.uniformMat4(pid, "ProjMat", projMat);
            GlUtil.uniform4f(pid, "Tint", tintR, tintG, tintB, opacity);

            if (client.world != null) {
                long totalTicks = client.world.getTime();
                GlUtil.uniform1i(pid, "TimeTicks", (int)(client.world.getTimeOfDay() % 24_000L));
                GlUtil.uniform1f(pid, "GameTime", (totalTicks + tickDelta) / 24_000f);
                GlUtil.uniform1f(pid, "Time", (totalTicks + tickDelta) * 0.05f);
            }
            GlUtil.uniform1f(pid, "DeltaTime", tickDelta);
            GlUtil.uniform3f(pid, "CameraPos", (float) camPos.x, (float) camPos.y, (float) camPos.z);
            float uvScaleX = VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, "uv_scale_x"), 1f);
            float uvScaleY = VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, "uv_scale_y"), 1f);
            float uvOffX   = VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, "uv_offset_x"), 0f);
            float uvOffY   = VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, "uv_offset_y"), 0f);
            GlUtil.uniform2f(pid, "UvScale", uvScaleX, uvScaleY);
            GlUtil.uniform2f(pid, "UvOffset", uvOffX, uvOffY);
            sceneLights.applyUniforms(pid);

            // param_* node properties as shader uniforms
            List<SceneSnapshot.Property> props = node.properties();
            if (props != null) {
                for (SceneSnapshot.Property p : props) {
                    if (p == null || p.key() == null || !p.key().startsWith("param_")) continue;
                    try {
                        GlUtil.uniform1f(pid, p.key().substring(6), Float.parseFloat(p.value()));
                    } catch (NumberFormatException ignored) {}
                }
            }

            applyInterpolatedUniforms(pid, node.nodeId(), node, tickDelta);

            if (binding != null) binding.applyMaterial(program);
            else program.clearSamplers();

            Identifier nodeTexture = resolveNodeTexture(node);
            program.setSampler("Texture0", nodeTexture);
            if (binding == null || !binding.hasTextureParam("albedo_texture")) {
                program.setSampler("albedo_texture", nodeTexture);
            }
            program.bindSamplers(0);

            var dbm = VeilRenderSystem.renderer().getDynamicBufferManger();
            int activeBufferMask = VeilRenderSystem.renderer().getActiveBuffers();
            DynamicBufferType[] activeTypes = DynamicBufferType.decode(activeBufferMask);
            for (int i = 0; i < activeTypes.length; i++) {
                int tex = dbm.getBufferTexture(activeTypes[i]);
                GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER,
                        GL30C.GL_COLOR_ATTACHMENT0 + 1 + i, GL30C.GL_TEXTURE_2D, tex, 0);
            }
            int[] drawBuffers = new int[1 + activeTypes.length];
            for (int i = 0; i < drawBuffers.length; i++) {
                drawBuffers[i] = GL30C.GL_COLOR_ATTACHMENT0 + i;
            }
            GL30C.glDrawBuffers(drawBuffers);

            if (isPlane) {
                MoudMeshBuffer.ensurePlaneInitialized();
                drawMesh(MoudMeshBuffer.planeVbo(), MoudMeshBuffer.planeEbo(), MoudMeshBuffer.planeIndexCount());
            } else {
                drawMesh(MoudMeshBuffer.vbo(), MoudMeshBuffer.ebo(), MoudMeshBuffer.indexCount());
            }

            for (int i = 0; i < activeTypes.length; i++) {
                GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER,
                        GL30C.GL_COLOR_ATTACHMENT0 + 1 + i, GL30C.GL_TEXTURE_2D, 0, 0);
            }
            GL30C.glDrawBuffers(new int[]{ GL30C.GL_COLOR_ATTACHMENT0 });
        } finally {
            ShaderProgram.unbind();
            if (translucent) { RenderSystem.disableBlend(); RenderSystem.depthMask(true); }
            if (doubleSided) RenderSystem.enableCull();
        }
        return true;
    }

    ShaderProgram resolveMeshProgram(VeilMaterialBinding binding) {
        ShaderProgram direct = binding.resolveProgram();
        MoudShaderFile sf = binding.shaderFile();
        Identifier baseId = binding.programId();
        if (sf == null || baseId == null) return null;

        Int2ObjectMap<String> stages = sf.stageSources();
        String vertSrc = stages.get(GL20C.GL_VERTEX_SHADER);
        boolean isBlit = vertSrc != null && vertSrc.contains("gl_VertexID");
        if (!isBlit) return direct;

        Identifier meshId = Identifier.of(baseId.getNamespace(), "mesh/" + baseId.getPath());
        Int2ObjectMap<String> meshStages = new Int2ObjectArrayMap<>(stages);
        meshStages.put(GL20C.GL_VERTEX_SHADER, meshVertForMaterial);
        return VeilDynamicShaders.getOrCompile(meshId, meshStages);
    }

    ShaderProgram getDefaultShaderProgram() {
        if (defaultShaderProgram != null && defaultShaderProgram.isValid()) return defaultShaderProgram;
        if (defaultVert.isEmpty() || defaultFrag.isEmpty()) return null;
        Int2ObjectMap<String> stages = new Int2ObjectArrayMap<>();
        stages.put(GL20C.GL_VERTEX_SHADER, defaultVert);
        stages.put(GL20C.GL_FRAGMENT_SHADER, defaultFrag);
        defaultShaderProgram = VeilDynamicShaders.getOrCompile(Identifier.of("moud", "builtin/default_mesh"), stages);
        return defaultShaderProgram;
    }

    ShaderProgram getPbrShaderProgram() {
        if (pbrShaderProgram != null && pbrShaderProgram.isValid()) return pbrShaderProgram;
        if (pbrVert.isEmpty() || pbrFrag.isEmpty()) return null;
        Int2ObjectMap<String> stages = new Int2ObjectArrayMap<>();
        stages.put(GL20C.GL_VERTEX_SHADER, pbrVert);
        stages.put(GL20C.GL_FRAGMENT_SHADER, pbrFrag);
        pbrShaderProgram = VeilDynamicShaders.getOrCompile(Identifier.of("moud", "builtin/pbr_mesh"), stages);
        return pbrShaderProgram;
    }

    void drawMesh(int vbo, int ebo, int indexCount) {
        int pid = GlUtil.currentProgram();
        if (pid <= 0) return;
        long key = ((long) pid << 32) | (vbo & 0xFFFFFFFFL);
        int vao = meshVaoCache.computeIfAbsent(key, k -> GlUtil.createMeshVao(pid, vbo, ebo));
        GlUtil.drawElements(vao, indexCount);
    }

    Identifier resolveNodeTexture(SceneSnapshot.NodeSnapshot node) {
        if (node == null) return MoudTextures.white();
        Identifier fromMaterial = resolveMaterialTexture(VeilSceneNodeRenderer.stringProp(node, "material"));
        if (fromMaterial != null) return fromMaterial;
        Identifier id = MoudTextures.resolve(VeilSceneNodeRenderer.stringProp(node, "texture"));
        if (id != null && "moud".equals(id.getNamespace())
                && id.getPath() != null && id.getPath().startsWith("bbmodel/")
                && !MoudTextures.isRawReady(id)) {
            return MoudTextures.white();
        }
        return id;
    }

    Identifier resolveMaterialTexture(String materialPathRaw) {
        if (materialPathRaw == null || materialPathRaw.isBlank()) return null;
        String materialPath = materialPathRaw.trim();
        if (!materialPath.startsWith(ResPath.SCHEME)) return null;

        String txt = MoudTextAssets.readText(materialPath);
        if (txt == null) return null;

        synchronized (MATERIAL_TEX_LOCK) {
            MaterialTexCache cached = textureByMaterialPath.get(materialPath);
            if (cached != null && Objects.equals(cached.materialText, txt)) return cached.textureId;
        }

        Identifier textureId = null;
        try {
            MoudMaterial mat = MoudMaterialParser.parse(txt);
            if (mat != null && mat.params() != null) {
                textureId = resolveMaterialTextureParam(mat, "albedo_texture");
                if (textureId == null) {
                    for (Map.Entry<String, MoudMaterial.Param> entry : mat.params().entrySet()) {
                        Identifier resolved = resolveTextureParam(entry.getValue());
                        if (resolved != null) {
                            textureId = resolved;
                            break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        boolean resolved = textureId != null
                && !MoudTextures.WHITE_ID.equals(textureId)
                && !TextureManager.MISSING_IDENTIFIER.equals(textureId);
        if (resolved) {
            synchronized (MATERIAL_TEX_LOCK) {
                textureByMaterialPath.put(materialPath, new MaterialTexCache(txt, textureId));
            }
        }
        return textureId;
    }

    void clear() {
        synchronized (MATERIAL_TEX_LOCK) { textureByMaterialPath.clear(); }
        materialBindings.clear();
        meshVaoCache.values().forEach(GlUtil::deleteVao);
        meshVaoCache.clear();
        defaultShaderProgram = null;
        loggedShaderErrors.clear();
    }

    private static float clampedProp(SceneSnapshot.NodeSnapshot node, String key, float def) {
        return VeilSceneNodeRenderer.clamp01(
                VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, key), def));
    }

    private static Identifier resolveMaterialTextureParam(MoudMaterial mat, String key) {
        if (mat == null || mat.params() == null || key == null || key.isBlank()) {
            return null;
        }
        return resolveTextureParam(mat.params().get(key));
    }

    private static Identifier resolveTextureParam(MoudMaterial.Param param) {
        if (param instanceof MoudMaterial.Param.Texture t) {
            return MoudTextures.resolve(t.textureRef());
        }
        if (param instanceof MoudMaterial.Param.StringParam s) {
            return MoudTextures.resolve(s.value());
        }
        return null;
    }

    private record MaterialTexCache(String materialText, Identifier textureId) {}
}
