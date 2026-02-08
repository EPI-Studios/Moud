package com.moud.client.model;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.client.collision.ClientCollisionManager;
import com.moud.client.collision.ModelCollisionManager;
import com.moud.client.editor.runtime.RuntimeObjectRegistry;
import com.moud.client.model.gltf.GltfSkinnedModelLoader;
import com.moud.client.util.IdentifierUtils;
import com.moud.client.util.OBJLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientModelManager {
    private static final ClientModelManager INSTANCE = new ClientModelManager();
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientModelManager.class);
    private final Map<Long, RenderableModel> models = new ConcurrentHashMap<>();
    private final Map<Identifier, EmbeddedAlphaKind> embeddedAlphaKinds = new ConcurrentHashMap<>();
    private final Map<Identifier, CompletableFuture<ParsedModelAsset>> assetCache = new ConcurrentHashMap<>();
    private final Set<Identifier> registeredEmbeddedTextures = ConcurrentHashMap.newKeySet();
    private final ExecutorService modelLoadExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Moud-ModelLoader");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean gltfDefaultTexturesRegistered = false;

    private static final Identifier DEFAULT_BASE_COLOR = Identifier.of("minecraft", "textures/block/white_concrete.png");
    private static final Identifier DEFAULT_NORMAL = Identifier.of("moud", "gltf/default_normal");
    private static final Identifier DEFAULT_METALLIC_ROUGHNESS = Identifier.of("moud", "gltf/default_metallic_roughness");
    private static final Identifier DEFAULT_EMISSIVE = Identifier.of("moud", "gltf/default_emissive");
    private static final Identifier DEFAULT_OCCLUSION = Identifier.of("moud", "gltf/default_occlusion");

    private ClientModelManager() {}

    public static ClientModelManager getInstance() {
        return INSTANCE;
    }

    public void createModel(long id, String modelPath) {
        models.computeIfAbsent(id, key -> {
            LOGGER.info("Creating client-side model ID {} with path {}", id, modelPath);
            RenderableModel model = new RenderableModel(id, modelPath);
            loadModelData(model);
            RuntimeObjectRegistry.getInstance().syncModel(model);
            return model;
        });
    }

    public void reloadModels() {
        if (models.isEmpty()) return;
        LOGGER.info("Reloading {} client-side models...", models.size());
        assetCache.clear();
        registeredEmbeddedTextures.clear();
        embeddedAlphaKinds.clear();
        for (RenderableModel model : models.values()) {
            loadModelData(model);
        }
    }

    private void loadModelData(RenderableModel model) {
        Identifier modelIdentifier = IdentifierUtils.resolveModelIdentifier(model.getModelPath());
        if (modelIdentifier == null) {
            LOGGER.error("Invalid model identifier for model {}: {}", model.getId(), model.getModelPath());
            return;
        }

        MinecraftClient.getInstance().execute(() -> {
            String lowerPath = model.getModelPath() != null ? model.getModelPath().toLowerCase() : "";
            if (lowerPath.endsWith(".gltf")) {
                LOGGER.error("GLTF (.gltf) is not supported yet. Please convert to binary GLB (.glb): {}", model.getModelPath());
                return;
            }

            CompletableFuture<ParsedModelAsset> future = assetCache.computeIfAbsent(modelIdentifier, id -> {
                long startNanos = System.nanoTime();
                try {
                    Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(id);
                    if (resource.isEmpty()) {
                        return CompletableFuture.failedFuture(new IllegalStateException("Missing model resource: " + id));
                    }
                    byte[] bytes;
                    try (InputStream inputStream = resource.get().getInputStream()) {
                        bytes = inputStream.readAllBytes();
                    }

                    String ext = lowerPath.endsWith(".glb") ? ".glb" : ".obj";
                    byte[] payload = bytes;
                    return CompletableFuture.supplyAsync(() -> parseModelAsset(ext, payload), modelLoadExecutor)
                            .whenComplete((ignored, err) -> {
                                long ms = (System.nanoTime() - startNanos) / 1_000_000L;
                                if (err == null) {
                                    LOGGER.info("Parsed model asset {} in {}ms", id, ms);
                                } else {
                                    LOGGER.warn("Failed to parse model asset {} after {}ms", id, ms, err);
                                }
                            });
                } catch (Throwable t) {
                    return CompletableFuture.failedFuture(t);
                }
            });

            future.whenComplete((asset, err) -> MinecraftClient.getInstance().execute(() -> {
                if (err != null) {
                    LOGGER.error("Failed to load model resource for path: {}", modelIdentifier, err);
                    return;
                }
                if (models.get(model.getId()) != model) {
                    return;
                }
                try {
                    applyParsedModelAsset(model, modelIdentifier, asset);
                    ModelCollisionManager.getInstance().sync(model);
                    RuntimeObjectRegistry.getInstance().syncModel(model);
                    LOGGER.info("Successfully loaded and uploaded model data for {}", model.getModelPath());
                } catch (Throwable t) {
                    LOGGER.error("Failed to apply model asset for path: {}", modelIdentifier, t);
                }
            }));
        });
    }

    private sealed interface ParsedModelAsset permits ParsedGlbAsset, ParsedObjAsset {}

    private record ParsedGlbAsset(GltfSkinnedModelLoader.ParsedGlbModel parsed) implements ParsedModelAsset {}

    private record ParsedObjAsset(OBJLoader.OBJMesh mesh) implements ParsedModelAsset {}

    private static ParsedModelAsset parseModelAsset(String ext, byte[] bytes) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            if (".glb".equals(ext)) {
                return new ParsedGlbAsset(GltfSkinnedModelLoader.parseGlb(in));
            }
            return new ParsedObjAsset(OBJLoader.load(in));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void applyParsedModelAsset(RenderableModel model, Identifier modelIdentifier, ParsedModelAsset asset) {
        if (asset instanceof ParsedGlbAsset glb) {
            GltfSkinnedModelLoader.ParsedGlbModel parsed = glb.parsed();
            RenderableModel.MeshAnimator animator = parsed.createAnimator();
            GltfSkinnedModelLoader.LoadedGltfModel loaded = parsed.toLoadedModel(animator);

            LOGGER.info(
                    "Loaded GLB {}: vertices={}, indices={}, skin={}, importScale={}, submeshes={}, animations={}",
                    model.getModelPath(),
                    loaded.vertices().length / RenderableModel.FLOATS_PER_VERTEX,
                    loaded.indices().length,
                    loaded.hasSkin(),
                    loaded.importScale(),
                    loaded.submeshes() != null ? loaded.submeshes().size() : 0,
                    loaded.animations() != null ? loaded.animations().size() : 0
            );
            model.setMeshData(loaded.vertices(), loaded.indices(), false);
            model.setBindPoseVertices(loaded.vertices());
            model.setMeshAnimator(loaded.animator());
            if (loaded.animations() != null && !loaded.animations().isEmpty()) {
                List<RenderableModel.AnimationInfo> animations = getAnimationInfos(loaded);
                model.setAnimations(animations);
            } else {
                model.setAnimations(null);
            }
            applyGltfSubmeshes(model, modelIdentifier, loaded);
            return;
        }

        if (asset instanceof ParsedObjAsset obj) {
            model.uploadMesh(obj.mesh());
            return;
        }
        throw new IllegalStateException("Unhandled model asset type: " + asset.getClass());
    }

    private static @NotNull List<RenderableModel.AnimationInfo> getAnimationInfos(GltfSkinnedModelLoader.LoadedGltfModel loaded) {
        List<RenderableModel.AnimationInfo> animations = new ArrayList<>();
        for (int i = 0; i < loaded.animations().size(); i++) {
            var anim = loaded.animations().get(i);
            String name = anim.name() != null ? anim.name() : "";
            if (name.isBlank()) {
                name = "animation_" + i;
            }
            animations.add(new RenderableModel.AnimationInfo(name, anim.duration(), anim.channelCount()));
        }
        return animations;
    }

    private void applyGltfSubmeshes(RenderableModel model, Identifier modelIdentifier, GltfSkinnedModelLoader.LoadedGltfModel loaded) {
        if (model == null || loaded == null || loaded.submeshes() == null || loaded.submeshes().isEmpty()) {
            model.setSubmeshes(null);
            return;
        }
        ensureGltfDefaultTextures();
        Identifier safeModelIdentifier = modelIdentifier != null ? modelIdentifier : Identifier.of("moud", "unknown");
        List<RenderableModel.Submesh> submeshes = new ArrayList<>();
        int opaqueCount = 0;
        int maskCount = 0;
        int blendCount = 0;
        for (int i = 0; i < loaded.submeshes().size(); i++) {
            GltfSkinnedModelLoader.Submesh gltfSubmesh = loaded.submeshes().get(i);
            if (gltfSubmesh == null || gltfSubmesh.indexCount() <= 0) {
                continue;
            }
            GltfSkinnedModelLoader.Material mat = gltfSubmesh.material();
            Identifier baseColorTexture = model.getTexture();
            Identifier normalTexture = DEFAULT_NORMAL;
            Identifier metallicRoughnessTexture = DEFAULT_METALLIC_ROUGHNESS;
            Identifier emissiveTexture = DEFAULT_EMISSIVE;
            Identifier occlusionTexture = DEFAULT_OCCLUSION;

            float r = mat != null ? mat.baseColorR() : 1.0f;
            float g = mat != null ? mat.baseColorG() : 1.0f;
            float b = mat != null ? mat.baseColorB() : 1.0f;
            float a = mat != null ? mat.baseColorA() : 1.0f;

            float metallicFactor = mat != null ? mat.metallicFactor() : 1.0f;
            float roughnessFactor = mat != null ? mat.roughnessFactor() : 1.0f;
            float emissiveR = mat != null ? mat.emissiveR() : 0.0f;
            float emissiveG = mat != null ? mat.emissiveG() : 0.0f;
            float emissiveB = mat != null ? mat.emissiveB() : 0.0f;

            int primaryTexcoord = 0;
            if (mat != null && mat.baseColorTexture() != null) {
                primaryTexcoord = mat.baseColorTexture().texcoord();
            }

            boolean hasEmbeddedBaseColor = mat != null
                    && mat.baseColorTexture() != null
                    && mat.baseColorTexture().imageBytes() != null
                    && mat.baseColorTexture().imageBytes().length > 0;
            if (hasEmbeddedBaseColor) {
                baseColorTexture = registerEmbeddedTexture(safeModelIdentifier, i, "basecolor", mat.baseColorTexture(), true);
            }

            if (mat != null && mat.normalTexture() != null && mat.normalTexture().imageBytes() != null
                    && mat.normalTexture().imageBytes().length > 0) {
                if (mat.normalTexture().texcoord() == primaryTexcoord) {
                    normalTexture = registerEmbeddedTexture(safeModelIdentifier, i, "normal", mat.normalTexture(), false);
                } else {
                    LOGGER.debug("GLB {} submesh {} normalTexcoord={} != baseColorTexcoord={}, ignoring normal map",
                            model.getModelPath(), i, mat.normalTexture().texcoord(), primaryTexcoord);
                }
            }

            if (mat != null && mat.metallicRoughnessTexture() != null && mat.metallicRoughnessTexture().imageBytes() != null
                    && mat.metallicRoughnessTexture().imageBytes().length > 0) {
                if (mat.metallicRoughnessTexture().texcoord() == primaryTexcoord) {
                    metallicRoughnessTexture = registerEmbeddedTexture(safeModelIdentifier, i, "metallic_roughness", mat.metallicRoughnessTexture(), false);
                } else {
                    LOGGER.debug("GLB {} submesh {} metallicRoughnessTexcoord={} != baseColorTexcoord={}, ignoring MR map",
                            model.getModelPath(), i, mat.metallicRoughnessTexture().texcoord(), primaryTexcoord);
                }
            }

            if (mat != null && mat.emissiveTexture() != null && mat.emissiveTexture().imageBytes() != null
                    && mat.emissiveTexture().imageBytes().length > 0) {
                if (mat.emissiveTexture().texcoord() == primaryTexcoord) {
                    emissiveTexture = registerEmbeddedTexture(safeModelIdentifier, i, "emissive", mat.emissiveTexture(), false);
                } else {
                    LOGGER.debug("GLB {} submesh {} emissiveTexcoord={} != baseColorTexcoord={}, ignoring emissive map",
                            model.getModelPath(), i, mat.emissiveTexture().texcoord(), primaryTexcoord);
                }
            }

            if (mat != null && mat.occlusionTexture() != null && mat.occlusionTexture().imageBytes() != null
                    && mat.occlusionTexture().imageBytes().length > 0) {
                if (mat.occlusionTexture().texcoord() == primaryTexcoord) {
                    occlusionTexture = registerEmbeddedTexture(safeModelIdentifier, i, "occlusion", mat.occlusionTexture(), false);
                } else {
                    LOGGER.debug("GLB {} submesh {} occlusionTexcoord={} != baseColorTexcoord={}, ignoring occlusion map",
                            model.getModelPath(), i, mat.occlusionTexture().texcoord(), primaryTexcoord);
                }
            }

            RenderableModel.AlphaMode alphaMode = RenderableModel.AlphaMode.OPAQUE;
            float alphaCutoff = 0.5f;
            boolean doubleSided = true;
            if (mat != null) {
                if (mat.alphaMode() != null) {
                    try {
                        alphaMode = RenderableModel.AlphaMode.valueOf(mat.alphaMode().name());
                    } catch (Exception ignored) {
                    }
                }
                alphaCutoff = mat.alphaCutoff();
                doubleSided = mat.doubleSided();
            }
            if (hasEmbeddedBaseColor) {
                EmbeddedAlphaKind alphaKind = embeddedAlphaKinds.getOrDefault(baseColorTexture, EmbeddedAlphaKind.UNKNOWN);
                if (alphaKind == EmbeddedAlphaKind.OPAQUE) {
                    alphaMode = RenderableModel.AlphaMode.OPAQUE;
                } else if (alphaKind == EmbeddedAlphaKind.BINARY && alphaMode == RenderableModel.AlphaMode.BLEND) {
                    alphaMode = RenderableModel.AlphaMode.MASK;
                }
            }
            if (alphaMode != RenderableModel.AlphaMode.BLEND) {
                a = 1.0f;
            }
            switch (alphaMode) {
                case OPAQUE -> opaqueCount++;
                case MASK -> maskCount++;
                case BLEND -> blendCount++;
            }
            if (LOGGER.isDebugEnabled()) {
                EmbeddedAlphaKind alphaKind = hasEmbeddedBaseColor
                        ? embeddedAlphaKinds.getOrDefault(baseColorTexture, EmbeddedAlphaKind.UNKNOWN)
                        : EmbeddedAlphaKind.UNKNOWN;
                LOGGER.debug(
                        "GLB submesh {}: start={}, count={}, alphaMode={}, doubleSided={}, baseColor=[{},{},{},{}], embeddedBaseColor={}, embeddedAlpha={}, MR={}, Emissive={}",
                        i,
                        gltfSubmesh.indexStart(),
                        gltfSubmesh.indexCount(),
                        alphaMode,
                        doubleSided,
                        r,
                        g,
                        b,
                        a,
                        hasEmbeddedBaseColor,
                        alphaKind,
                        metallicRoughnessTexture,
                        emissiveTexture
                );
            }
            submeshes.add(new RenderableModel.Submesh(
                    gltfSubmesh.indexStart(),
                    gltfSubmesh.indexCount(),
                    baseColorTexture,
                    normalTexture,
                    metallicRoughnessTexture,
                    emissiveTexture,
                    occlusionTexture,
                    r,
                    g,
                    b,
                    a,
                    metallicFactor,
                    roughnessFactor,
                    emissiveR,
                    emissiveG,
                    emissiveB,
                    alphaMode,
                    alphaCutoff,
                    doubleSided
            ));
        }
        model.setSubmeshes(submeshes);
        LOGGER.info(
                "GLB {} submeshes applied: total={}, opaque={}, mask={}, blend={}",
                model.getModelPath(),
                submeshes.size(),
                opaqueCount,
                maskCount,
                blendCount
        );
    }

    private Identifier registerEmbeddedTexture(Identifier modelIdentifier,
                                               int submeshIndex,
                                               String kind,
                                               GltfSkinnedModelLoader.TextureInfo textureInfo,
                                               boolean classifyAlpha) {
        Identifier fallback = switch (kind) {
            case "normal" -> DEFAULT_NORMAL;
            case "metallic_roughness" -> DEFAULT_METALLIC_ROUGHNESS;
            case "emissive" -> DEFAULT_EMISSIVE;
            case "occlusion" -> DEFAULT_OCCLUSION;
            default -> DEFAULT_BASE_COLOR;
        };
        if (textureInfo == null || textureInfo.imageBytes() == null || textureInfo.imageBytes().length == 0) {
            return fallback;
        }
        String basePath = "gltf/embedded/" + modelIdentifier.getNamespace() + "/" + modelIdentifier.getPath();
        Identifier id = Identifier.of("moud", basePath + "/mat_" + submeshIndex + "_" + kind);
        if (!registeredEmbeddedTextures.add(id)) {
            return id;
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(textureInfo.imageBytes())) {
            NativeImage image = NativeImage.read(in);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            try {
                if (RenderSystem.isOnRenderThread()) {
                    texture.upload();
                } else {
                    RenderSystem.recordRenderCall(texture::upload);
                }
            } catch (Throwable ignored) {
            }
            if (classifyAlpha) {
                embeddedAlphaKinds.put(id, classifyAlpha(image));
            }
            try {
                AbstractTexture t = MinecraftClient.getInstance().getTextureManager().getTexture(id);
                if (t != null) {
                    boolean blur = shouldBlur(textureInfo.magFilter());
                    boolean requestedMipmaps = shouldMipmap(textureInfo.minFilter());
                    t.setFilter(blur, false);
                    RenderSystem.bindTexture(t.getGlId());
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, mapWrap(textureInfo.wrapS()));
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, mapWrap(textureInfo.wrapT()));
                    int min = mapMinFilter(textureInfo.minFilter(), blur);
                    int mag = mapMagFilter(textureInfo.magFilter(), blur);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
                    if (requestedMipmaps) {
                        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
                    }
                }
            } catch (Throwable ignored) {
            }
            discardCpuCopy(texture);
        } catch (Exception e) {
            registeredEmbeddedTextures.remove(id);
            LOGGER.warn("Failed to register embedded GLB texture for model {} submesh {} kind {}", modelIdentifier, submeshIndex, kind, e);
            return fallback;
        }
        return id;
    }

    private void ensureGltfDefaultTextures() {
        if (gltfDefaultTexturesRegistered) {
            return;
        }
        gltfDefaultTexturesRegistered = true;
        registerSolidColorTexture(DEFAULT_NORMAL, 128, 128, 255, 255);
        registerSolidColorTexture(DEFAULT_METALLIC_ROUGHNESS, 255, 255, 255, 255);
        registerSolidColorTexture(DEFAULT_EMISSIVE, 0, 0, 0, 255);
        registerSolidColorTexture(DEFAULT_OCCLUSION, 255, 255, 255, 255);
    }

    private void registerSolidColorTexture(Identifier id, int r, int g, int b, int a) {
        try {
            NativeImage image = new NativeImage(1, 1, true);
            int color = (a & 255) << 24 | (b & 255) << 16 | (g & 255) << 8 | (r & 255);
            image.setColor(0, 0, color);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            try {
                if (RenderSystem.isOnRenderThread()) {
                    texture.upload();
                } else {
                    RenderSystem.recordRenderCall(texture::upload);
                }
            } catch (Throwable ignored) {
            }
            try {
                AbstractTexture t = MinecraftClient.getInstance().getTextureManager().getTexture(id);
                if (t != null) {
                    t.setFilter(true, false);
                    RenderSystem.bindTexture(t.getGlId());
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
                }
            } catch (Throwable ignored) {
            }
            discardCpuCopy(texture);
        } catch (Throwable t) {
            LOGGER.debug("Failed to register GLB default texture {}", id, t);
        }
    }

    private static void discardCpuCopy(NativeImageBackedTexture texture) {
        if (texture == null) {
            return;
        }
        try {
            if (RenderSystem.isOnRenderThread()) {
                texture.setImage(null);
            } else {
                RenderSystem.recordRenderCall(() -> texture.setImage(null));
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean shouldMipmap(Integer minFilter) {
        if (minFilter == null) {
            return false;
        }
        return switch (minFilter.intValue()) {
            case 9984, 9985, 9986, 9987 -> true;
            default -> false;
        };
    }

    private static boolean shouldBlur(Integer magFilter) {
        if (magFilter == null) {
            return true;
        }
        return magFilter.intValue() != 9728;
    }

    private static int mapMinFilter(Integer minFilter, boolean blur) {
        if (minFilter == null) {
            return blur ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        }
        return switch (minFilter.intValue()) {
            case 9728 -> GL11.GL_NEAREST;
            case 9729 -> GL11.GL_LINEAR;
            case 9984, 9985 -> GL11.GL_NEAREST;
            case 9986, 9987 -> GL11.GL_LINEAR;
            default -> blur ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        };
    }

    private static int mapMagFilter(Integer magFilter, boolean blur) {
        if (magFilter == null) {
            return blur ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        }
        return switch (magFilter.intValue()) {
            case 9728 -> GL11.GL_NEAREST;
            case 9729 -> GL11.GL_LINEAR;
            default -> blur ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        };
    }

    private static int mapWrap(Integer wrap) {
        if (wrap == null) {
            return GL11.GL_REPEAT;
        }
        return switch (wrap.intValue()) {
            case 33071 -> GL12.GL_CLAMP_TO_EDGE;
            case 33648 -> GL14.GL_MIRRORED_REPEAT;
            default -> GL11.GL_REPEAT;
        };
    }

    private EmbeddedAlphaKind classifyAlpha(NativeImage image) {
        if (image == null) {
            return EmbeddedAlphaKind.UNKNOWN;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) {
            return EmbeddedAlphaKind.UNKNOWN;
        }

        boolean sawZero = false;
        boolean sawFull = false;
        boolean sawOther = false;

        int stepX = Math.max(1, width / 128);
        int stepY = Math.max(1, height / 128);
        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                int a = (image.getColor(x, y) >>> 24) & 0xFF;
                if (a == 0) {
                    sawZero = true;
                } else if (a == 255) {
                    sawFull = true;
                } else {
                    sawOther = true;
                    break;
                }
            }
            if (sawOther) {
                break;
            }
        }

        if (sawOther) {
            return EmbeddedAlphaKind.GRADIENT;
        }
        if (sawZero) {
            return EmbeddedAlphaKind.BINARY;
        }
        if (sawFull) {
            return EmbeddedAlphaKind.OPAQUE;
        }
        return EmbeddedAlphaKind.UNKNOWN;
    }

    private enum EmbeddedAlphaKind {
        OPAQUE,
        BINARY,
        GRADIENT,
        UNKNOWN
    }

    public void removeModel(long id) {
        RenderableModel model = models.remove(id);
        if (model != null) {
            model.destroy();
            LOGGER.info("Removed client-side model ID {}", id);
        }
        ModelCollisionManager.getInstance().removeModel(id);
        ClientCollisionManager.unregisterModel(id);
        RuntimeObjectRegistry.getInstance().removeModel(id);
    }

    public RenderableModel getModel(long id) {
        return models.get(id);
    }

    public Collection<RenderableModel> getModels() {
        return models.values();
    }

    public void clear() {
        for (Long id : new ArrayList<>(models.keySet())) {
            removeModel(id);
        }
        models.clear();
        ClientCollisionManager.clear();
        ModelCollisionManager.getInstance().clear();
        LOGGER.info("Cleared all client-side models.");
    }
}
