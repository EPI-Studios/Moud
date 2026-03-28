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
import org.lwjgl.opengl.GL20C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

final class MeshShaderRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MeshShaderRenderer.class);

    private final String defaultVert;
    private final String defaultFrag;
    private final String meshVertForMaterial;

    private final Map<Long, VeilMaterialBinding> materialBindings = new HashMap<>();
    private ShaderProgram defaultShaderProgram;
    private final Map<Long, Integer> meshVaoCache = new HashMap<>();

    private final Object MATERIAL_TEX_LOCK = new Object();
    private final Map<String, MaterialTexCache> textureByMaterialPath = new HashMap<>();
    private final SceneLights sceneLights = new SceneLights();
    private final Set<String> loggedShaderErrors = new HashSet<>();

    SceneLights sceneLights() { return sceneLights; }

    MeshShaderRenderer() {
        defaultVert = loadResource("assets/moud/shaders/builtin/default_mesh.vert");
        defaultFrag = loadResource("assets/moud/shaders/builtin/default_mesh.frag");
        meshVertForMaterial = loadResource("assets/moud/shaders/builtin/mesh_material.vert");
    }

    private static String loadResource(String path) {
        try (InputStream is = MeshShaderRenderer.class.getClassLoader().getResourceAsStream(path)) {
            if (is != null) return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
        return "";
    }

    void collectLights(List<SceneSnapshot.NodeSnapshot> nodes,
                       Function<Long, VeilSceneNodeRenderer.Pose> poseResolver) {
        sceneLights.collect(nodes, poseResolver);
    }

    boolean renderNode(SceneSnapshot.NodeSnapshot node, VeilSceneNodeRenderer.Pose world,
                       Vec3d camPos, Camera camera, MinecraftClient client, float tickDelta) {
        String materialPath = VeilSceneNodeRenderer.stringProp(node, "material");
        VeilMaterialBinding binding = null;
        ShaderProgram program;
        Identifier shaderErrorId = null;

        if (materialPath != null && !materialPath.isBlank()) {
            binding = materialBindings.computeIfAbsent(node.nodeId(), id -> new VeilMaterialBinding());
            binding.configure(materialPath, null);
            program = resolveMeshProgram(binding);
            if (program == null && binding.programId() != null) shaderErrorId = binding.programId();
        } else {
            program = getDefaultShaderProgram();
            if (program == null) shaderErrorId = Identifier.of("moud", "builtin/default_mesh");
        }

        if (program == null || !program.isValid()) {
            if (shaderErrorId != null) {
                String error = VeilDynamicShaders.getLastError(shaderErrorId);
                if (error != null && loggedShaderErrors.add(shaderErrorId + ":" + error)) {
                    LOGGER.error("[Moud] Shader compilation failed for '{}': {}", shaderErrorId, error);
                }
            }
            return false;
        }

        MoudMeshBuffer.ensureInitialized();

        float tintR  = clampedProp(node, "color_tint_r", 1f);
        float tintG  = clampedProp(node, "color_tint_g", 1f);
        float tintB  = clampedProp(node, "color_tint_b", 1f);
        float opacity = clampedProp(node, "opacity", 1f);

        Matrix4f worldMat = new Matrix4f()
                .translate(world.pos.x, world.pos.y, world.pos.z)
                .rotate(world.rot)
                .scale(world.scale.x, world.scale.y, world.scale.z)
                .translate(-0.5f, -0.5f, -0.5f);

        Matrix4f modelMat = new Matrix4f()
                .translate((float)(world.pos.x - camPos.x),
                        (float)(world.pos.y - camPos.y),
                        (float)(world.pos.z - camPos.z))
                .rotate(world.rot)
                .scale(world.scale.x, world.scale.y, world.scale.z)
                .translate(-0.5f, -0.5f, -0.5f);

        ShaderBlock<CameraMatrices> camBlock = VeilRenderSystem.getBlock(VeilShaderBufferRegistry.CAMERA.get());
        CameraMatrices veilCam = camBlock != null ? camBlock.getValue() : null;
        Matrix4f viewMat = veilCam != null ? new Matrix4f(veilCam.getViewMatrix()) : new Matrix4f();
        Matrix4f projMat = veilCam != null ? new Matrix4f(veilCam.getProjectionMatrix()) : new Matrix4f(RenderSystem.getProjectionMatrix());

        boolean translucent = opacity < 1f;
        if (translucent) { RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); }
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(!translucent);

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
            sceneLights.applyUniforms(pid);

            if (binding != null) binding.applyMaterial(program);
            else program.clearSamplers();

            program.setSampler("Texture0", resolveNodeTexture(node));
            program.bindSamplers(0);

            String meshType = VeilSceneNodeRenderer.stringProp(node, "mesh");
            if ("plane".equals(meshType)) {
                MoudMeshBuffer.ensurePlaneInitialized();
                drawMesh(MoudMeshBuffer.planeVbo(), MoudMeshBuffer.planeEbo(), MoudMeshBuffer.planeIndexCount());
            } else {
                drawMesh(MoudMeshBuffer.vbo(), MoudMeshBuffer.ebo(), MoudMeshBuffer.indexCount());
            }
        } finally {
            ShaderProgram.unbind();
            if (translucent) { RenderSystem.disableBlend(); RenderSystem.depthMask(true); }
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
                for (MoudMaterial.Param p : mat.params().values()) {
                    if (p instanceof MoudMaterial.Param.Texture t) {
                        textureId = MoudTextures.resolve(t.textureRef());
                        break;
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

    private record MaterialTexCache(String materialText, Identifier textureId) {}
}
