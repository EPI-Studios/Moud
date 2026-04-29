package com.moud.client.fabric.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.client.fabric.assets.MoudTextAssets;
import com.moud.client.fabric.render.material.*;
import com.moud.client.fabric.render.mesh.MoudMeshBuffer;
import com.moud.client.fabric.render.mesh.ProceduralMeshGpuCache;
import com.moud.client.fabric.render.mesh.cache.ClientMeshBindings;
import com.moud.client.fabric.render.scene.math.Pose;
import com.moud.client.fabric.render.shadow.ShadowMaps;
import com.moud.client.fabric.render.sprite.SpriteSheets;
import com.moud.client.fabric.render.veil.*;
import com.moud.client.fabric.util.ClientDebugLog;
import com.moud.core.assets.ResPath;
import com.moud.net.protocol.SceneSnapshot;
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
import org.lwjgl.opengl.GL20C;
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
    private final String shadowDepthVert;
    private final String shadowDepthFrag;

    private final Map<Long, VeilMaterialBinding> materialBindings = new ConcurrentHashMap<>();
    private ShaderProgram defaultShaderProgram;
    private ShaderProgram pbrShaderProgram;
    private ShaderProgram shadowDepthProgram;
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
        shadowDepthVert = loadResource("assets/moud/shaders/builtin/shadow_depth.vert");
        shadowDepthFrag = loadResource("assets/moud/shaders/builtin/shadow_depth.frag");
    }

    ShaderProgram getShadowDepthProgram() {
        if (shadowDepthProgram != null && shadowDepthProgram.isValid()) return shadowDepthProgram;
        if (shadowDepthVert.isEmpty() || shadowDepthFrag.isEmpty()) return null;
        Int2ObjectMap<String> stages = new Int2ObjectArrayMap<>();
        stages.put(GL20C.GL_VERTEX_SHADER, shadowDepthVert);
        stages.put(GL20C.GL_FRAGMENT_SHADER, shadowDepthFrag);
        shadowDepthProgram = VeilDynamicShaders.getOrCompile(Identifier.of("moud", "builtin/shadow_depth"), stages);
        return shadowDepthProgram;
    }

    void renderNodeDepthOnly(SceneSnapshot.NodeSnapshot node, Pose world, Matrix4fc lightViewProj) {
        if (!RenderSystem.isOnRenderThread() || node == null || world == null) return;
        ShaderProgram program = getShadowDepthProgram();
        if (program == null || !program.isValid()) return;

        MoudMeshBuffer.ensureInitialized();

        String meshType = VeilSceneNodeRenderer.stringProp(node, "mesh");
        boolean isSprite3D = "Sprite3D".equals(node.type());
        meshType = normalizeMeshType(meshType, isSprite3D);
        boolean isPlanar = isPlanarMesh(meshType);

        Matrix4f modelMat;
        float halfPi = (float) (Math.PI / 2.0);
        if (isPlanar) {
            modelMat = new Matrix4f()
                    .translate(world.pos.x, world.pos.y, world.pos.z)
                    .rotate(world.rot)
                    .scale(world.scale.x, world.scale.y, world.scale.z)
                    .rotateX(-halfPi)
                    .translate(-0.5f, 0.0f, -0.5f);
        } else {
            modelMat = new Matrix4f()
                    .translate(world.pos.x, world.pos.y, world.pos.z)
                    .rotate(world.rot)
                    .scale(world.scale.x, world.scale.y, world.scale.z)
                    .translate(-0.5f, -0.5f, -0.5f);
        }

        boolean doubleSided = VeilSceneNodeRenderer.parseBool(
                VeilSceneNodeRenderer.stringProp(node, "double_sided"), false);
        if (doubleSided) RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        try {
            VeilRenderSystem.setShader(program);
            program.bind();
            int pid = GlUtil.currentProgram();
            GlUtil.uniformMat4(pid, "ModelMat", modelMat);
            GlUtil.uniformMat4(pid, "LightViewProj", new Matrix4f(lightViewProj));
            if ("subdivided_plane".equals(meshType)) {
                MoudMeshBuffer.ensurePlaneInitialized();
                drawMesh(MoudMeshBuffer.planeVbo(), MoudMeshBuffer.planeEbo(), MoudMeshBuffer.planeIndexCount());
            } else if (isPlanar) {
                MoudMeshBuffer.ensureQuadInitialized();
                drawMesh(MoudMeshBuffer.quadVbo(), MoudMeshBuffer.quadEbo(), MoudMeshBuffer.quadIndexCount());
            } else {
                drawMesh(MoudMeshBuffer.vbo(), MoudMeshBuffer.ebo(), MoudMeshBuffer.indexCount());
            }
        } finally {
            ShaderProgram.unbind();
            if (doubleSided) RenderSystem.enableCull();
        }
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
                       Function<Long, Pose> poseResolver) {
        sceneLights.collect(nodes, poseResolver);
    }

    void collectLightsAdd(List<SceneSnapshot.NodeSnapshot> nodes,
                          Function<Long, Pose> poseResolver) {
        sceneLights.collectAdd(nodes, poseResolver);
    }

    boolean renderNode(SceneSnapshot.NodeSnapshot node, Pose world,
                       Vec3d camPos, Camera camera, Matrix4fc viewMatrix, Matrix4fc projectionMatrix,
                       MinecraftClient client, float tickDelta) {
        if (!RenderSystem.isOnRenderThread()) return false;
        String boundHash = ClientMeshBindings.hashFor(node.nodeId()).orElse(null);
        boolean useProceduralMesh = boundHash != null;
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
            program = getDefaultShaderProgram();
            if (program == null) shaderErrorId = Identifier.of("moud", "builtin/default_mesh");
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
                    ClientDebugLog.error("Shaders", "Shader compilation failed nodeId=" + node.nodeId()
                            + " material=" + materialPath
                            + " shader=" + (binding == null ? null : binding.shaderPath())
                            + " program=" + shaderErrorId
                            + " error=" + error);
                }
            }
            return false;
        }

        MoudMeshBuffer.ensureInitialized();

        float tintR  = clampedProp(node, "color_tint_r", 1f);
        float tintG  = clampedProp(node, "color_tint_g", 1f);
        float tintB  = clampedProp(node, "color_tint_b", 1f);
        float opacity = clampedProp(node, "opacity", 1f);
        TextureSample textureSample = resolveTextureSample(node);

        Matrix4f viewMat = viewMatrix != null ? new Matrix4f(viewMatrix) : new Matrix4f();
        Matrix4f projMat = projectionMatrix != null ? new Matrix4f(projectionMatrix) : new Matrix4f(RenderSystem.getProjectionMatrix());

        boolean isSprite3D = "Sprite3D".equals(node.type());
        boolean billboard = VeilSceneNodeRenderer.parseBool(
                VeilSceneNodeRenderer.stringProp(node, "billboard"), isSprite3D);

        String meshType = VeilSceneNodeRenderer.stringProp(node, "mesh");
        meshType = normalizeMeshType(meshType, isSprite3D);
        boolean isPlanar = isPlanarMesh(meshType) && !useProceduralMesh;
        boolean centerPivotOffset = !useProceduralMesh;

        float HALF_PI = (float) (Math.PI / 2.0);

        final Matrix4f worldMat;
        final Matrix4f modelMat;
        if (billboard) {
            Quaternionf camRot = viewMat.getNormalizedRotation(new Quaternionf()).conjugate();
            if (isPlanar) {
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
                        .scale(world.scale.x, world.scale.y, world.scale.z);
                if (centerPivotOffset) worldMat.translate(-0.5f, -0.5f, -0.5f);
                modelMat = new Matrix4f()
                        .translate((float)(world.pos.x - camPos.x),
                                (float)(world.pos.y - camPos.y),
                                (float)(world.pos.z - camPos.z))
                        .rotate(camRot)
                        .scale(world.scale.x, world.scale.y, world.scale.z);
                if (centerPivotOffset) modelMat.translate(-0.5f, -0.5f, -0.5f);
            }
        } else {
            if (isPlanar) {
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
                        .scale(world.scale.x, world.scale.y, world.scale.z);
                if (centerPivotOffset) worldMat.translate(-0.5f, -0.5f, -0.5f);
                modelMat = new Matrix4f()
                        .translate((float)(world.pos.x - camPos.x),
                                (float)(world.pos.y - camPos.y),
                                (float)(world.pos.z - camPos.z))
                        .rotate(world.rot)
                        .scale(world.scale.x, world.scale.y, world.scale.z);
                if (centerPivotOffset) modelMat.translate(-0.5f, -0.5f, -0.5f);
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
            float uvScaleX = textureSample.uvScaleX() * VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, "uv_scale_x"), 1f);
            float uvScaleY = textureSample.uvScaleY() * VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, "uv_scale_y"), 1f);
            float uvOffX   = textureSample.uvOffsetX() + VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, "uv_offset_x"), 0f) * textureSample.uvScaleX();
            float uvOffY   = textureSample.uvOffsetY() + VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, "uv_offset_y"), 0f) * textureSample.uvScaleY();
            GlUtil.uniform2f(pid, "UvScale", uvScaleX, uvScaleY);
            GlUtil.uniform2f(pid, "UvOffset", uvOffX, uvOffY);
            sceneLights.applyUniforms(pid);
            ShadowMaps.uploadSpotShadowScalarUniforms(pid);
            boolean fullbright = VeilSceneNodeRenderer.parseBool(VeilSceneNodeRenderer.stringProp(node, "fullbright"), false);
            GlUtil.uniform1i(pid, "fullbright", fullbright ? 1 : 0);

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

            Identifier nodeTexture = textureSample.textureId();
            int nodeTextureGl = MoudTextures.boundGlId(nodeTexture);
            program.setSampler("Texture0", nodeTextureGl, 0);
            if (binding == null || !binding.hasTextureParam("albedo_texture")) {
                program.setSampler("albedo_texture", nodeTextureGl, 0);
            }
            // veil 3.3.3 cascades sampler units when any declared sampler is unset, must bind SpotShadowMap every frame even with no shadow
            Identifier shadowId = ShadowMaps.hasActiveSpotShadow()
                    ? ShadowMaps.spotShadowTextureId()
                    : MoudTextures.white();
            int shadowGl = MoudTextures.boundGlId(shadowId);
            program.setSampler("SpotShadowMap", shadowGl, 0);
            program.bindSamplers(0);

            if (useProceduralMesh) {
                ProceduralMeshGpuCache.Handle h
                        = ProceduralMeshGpuCache.getOrUpload(boundHash);
                if (h != null) {
                    int progId = GlUtil.currentProgram();
                    long key = ((long) progId << 32) | (h.vbo() & 0xFFFFFFFFL);
                    int vao = meshVaoCache.computeIfAbsent(key, k -> GlUtil.createMeshVao(progId, h.vbo(), h.ebo()));
                    Identifier nodeFallback = textureSample.textureId();
                    boolean anyPerSurfaceTex = false;
                    for (var range : h.surfaces()) {
                        String matId = range.materialId();
                        if (matId != null && !matId.isBlank()) { anyPerSurfaceTex = true; break; }
                    }
                    if (!anyPerSurfaceTex) {
                        // single texture across whole mesh, faster path
                        GlUtil.drawElements(vao, h.indexCount());
                    } else {
                        for (var range : h.surfaces()) {
                            String matId = range.materialId();
                            Identifier surfaceTex = (matId != null && !matId.isBlank())
                                    ? MoudTextures.resolve(matId)
                                    : nodeFallback;
                            if (surfaceTex == null) surfaceTex = nodeFallback;
                            int gl = MoudTextures.boundGlId(surfaceTex);
                            program.setSampler("Texture0", gl, 0);
                            if (binding == null || !binding.hasTextureParam("albedo_texture")) {
                                program.setSampler("albedo_texture", gl, 0);
                            }
                            program.bindSamplers(0);
                            GlUtil.drawElementsRange(vao, range.indexCount(), range.firstIndex());
                        }
                    }
                }
            } else if ("subdivided_plane".equals(meshType)) {
                MoudMeshBuffer.ensurePlaneInitialized();
                drawMesh(MoudMeshBuffer.planeVbo(), MoudMeshBuffer.planeEbo(), MoudMeshBuffer.planeIndexCount());
            } else if (isPlanar) {
                MoudMeshBuffer.ensureQuadInitialized();
                drawMesh(MoudMeshBuffer.quadVbo(), MoudMeshBuffer.quadEbo(), MoudMeshBuffer.quadIndexCount());
            } else {
                drawMesh(MoudMeshBuffer.vbo(), MoudMeshBuffer.ebo(), MoudMeshBuffer.indexCount());
            }
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

    private static String normalizeMeshType(String meshType, boolean sprite) {
        if (meshType == null || meshType.isBlank()) {
            return sprite ? "sprite_quad" : "cube";
        }
        return meshType;
    }

    private static boolean isPlanarMesh(String meshType) {
        return "plane".equals(meshType)
                || "quad".equals(meshType)
                || "sprite_quad".equals(meshType)
                || "subdivided_plane".equals(meshType);
    }

    Identifier resolveNodeTexture(SceneSnapshot.NodeSnapshot node) {
        return resolveTextureSample(node).textureId();
    }

    TextureSample resolveTextureSample(SceneSnapshot.NodeSnapshot node) {
        if (node == null) return new TextureSample(MoudTextures.white(), 1f, 1f, 0f, 0f);
        if ("AnimatedSprite3D".equals(node.type())) {
            SpriteSheets.ResolvedFrame frame = SpriteSheets.resolve(
                    VeilSceneNodeRenderer.stringProp(node, "sprite_sheet"),
                    VeilSceneNodeRenderer.stringProp(node, "texture"),
                    VeilSceneNodeRenderer.stringProp(node, "animation"),
                    VeilSceneNodeRenderer.parseBool(VeilSceneNodeRenderer.stringProp(node, "playing"), true),
                    VeilSceneNodeRenderer.parseBool(VeilSceneNodeRenderer.stringProp(node, "loop"), true),
                    VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, "speed_scale"), 1f),
                    Math.round(VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, "frame"), 0f)),
                    System.nanoTime() / 1_000_000L
            );
            if (frame != null) {
                return new TextureSample(
                        frame.textureId(),
                        frame.u1() - frame.u0(),
                        frame.v1() - frame.v0(),
                        frame.u0(),
                        frame.v0()
                );
            }
        }
        Identifier fromMaterial = resolveMaterialTexture(VeilSceneNodeRenderer.stringProp(node, "material"));
        if (fromMaterial != null) return new TextureSample(fromMaterial, 1f, 1f, 0f, 0f);
        Identifier id = MoudTextures.resolve(VeilSceneNodeRenderer.stringProp(node, "texture"));
        if (id != null && "moud".equals(id.getNamespace())
                && id.getPath() != null && id.getPath().startsWith("bbmodel/")
                && !MoudTextures.isRawReady(id)) {
            return new TextureSample(MoudTextures.white(), 1f, 1f, 0f, 0f);
        }
        return textureRegionSample(node, id);
    }

    private TextureSample textureRegionSample(SceneSnapshot.NodeSnapshot node, Identifier id) {
        if (node == null || id == null) {
            return new TextureSample(id, 1f, 1f, 0f, 0f);
        }

        Float u0 = floatProp(node, "texture_u0");
        Float v0 = floatProp(node, "texture_v0");
        Float u1 = floatProp(node, "texture_u1");
        Float v1 = floatProp(node, "texture_v1");
        if (u0 != null && v0 != null && u1 != null && v1 != null) {
            return normalizedRegion(id, u0, v0, u1 - u0, v1 - v0);
        }

        TextureRegion region = firstRegion(node,
                "texture_region_x", "texture_region_y", "texture_region_w", "texture_region_h",
                "atlas_x", "atlas_y", "atlas_w", "atlas_h",
                "texture_x", "texture_y", "texture_w", "texture_h");
        if (region == null) {
            return new TextureSample(id, 1f, 1f, 0f, 0f);
        }

        if (isNormalizedRegion(region)) {
            return normalizedRegion(id, region.x(), region.y(), region.w(), region.h());
        }

        MoudTextures.TextureSize size = MoudTextures.sizeOf(id);
        float texW = Math.max(1, size.width());
        float texH = Math.max(1, size.height());
        return normalizedRegion(id, region.x() / texW, region.y() / texH, region.w() / texW, region.h() / texH);
    }

    private static TextureSample normalizedRegion(Identifier id, float x, float y, float w, float h) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(w) || !Float.isFinite(h)
                || w <= 0f || h <= 0f) {
            return new TextureSample(id, 1f, 1f, 0f, 0f);
        }
        return new TextureSample(id, w, h, x, y);
    }

    private static boolean isNormalizedRegion(TextureRegion region) {
        return region.x() >= 0f && region.y() >= 0f && region.w() > 0f && region.h() > 0f
                && region.x() <= 1f && region.y() <= 1f
                && region.x() + region.w() <= 1.0001f
                && region.y() + region.h() <= 1.0001f;
    }

    private static TextureRegion firstRegion(SceneSnapshot.NodeSnapshot node, String... keys) {
        for (int i = 0; i + 3 < keys.length; i += 4) {
            Float x = floatProp(node, keys[i]);
            Float y = floatProp(node, keys[i + 1]);
            Float w = floatProp(node, keys[i + 2]);
            Float h = floatProp(node, keys[i + 3]);
            if (x != null && y != null && w != null && h != null) {
                return new TextureRegion(x, y, w, h);
            }
        }
        return null;
    }

    private static Float floatProp(SceneSnapshot.NodeSnapshot node, String key) {
        String raw = VeilSceneNodeRenderer.stringProp(node, key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            float value = Float.parseFloat(raw.trim());
            return Float.isFinite(value) ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
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
        prevUniforms.clear();
        currUniforms.clear();
        meshVaoCache.values().forEach(GlUtil::deleteVao);
        meshVaoCache.clear();
        defaultShaderProgram = null;
        pbrShaderProgram = null;
        shadowDepthProgram = null;
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
    private record TextureRegion(float x, float y, float w, float h) {}

    record TextureSample(Identifier textureId, float uvScaleX, float uvScaleY, float uvOffsetX, float uvOffsetY) {
        TextureSample {
            textureId = textureId == null ? MoudTextures.white() : textureId;
        }
    }
}
