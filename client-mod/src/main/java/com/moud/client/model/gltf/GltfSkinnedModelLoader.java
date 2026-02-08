package com.moud.client.model.gltf;

import com.moud.client.model.RenderableModel;
import de.javagl.jgltf.model.AccessorModel;
import de.javagl.jgltf.model.AnimationModel;
import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.ImageModel;
import de.javagl.jgltf.model.MaterialModel;
import de.javagl.jgltf.model.MeshModel;
import de.javagl.jgltf.model.MeshPrimitiveModel;
import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.SceneModel;
import de.javagl.jgltf.model.SkinModel;
import de.javagl.jgltf.model.TextureModel;
import de.javagl.jgltf.model.io.GltfModelReader;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GltfSkinnedModelLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(GltfSkinnedModelLoader.class);

    private static final int GLTF_MODE_TRIANGLES = 4;
    private static final int GLTF_MODE_TRIANGLE_STRIP = 5;
    private static final int GLTF_MODE_TRIANGLE_FAN = 6;
    private static final float DEFAULT_CHARACTER_HEIGHT_BLOCKS = 1.8f;
    private static final float CHARACTER_HEIGHT_RESCALE_THRESHOLD_BLOCKS = 4.0f;

    private GltfSkinnedModelLoader() {}

    public record LoadedGltfModel(
            float[] vertices,
            int[] indices,
            RenderableModel.MeshAnimator animator,
            float importScale,
            List<Submesh> submeshes,
            String animationName,
            int animationChannels,
            boolean hasSkin,
            List<LoadedAnimation> animations
    ) {}

    public record ParsedGlbModel(
            float[] bindPoseVertices,
            int[] indices,
            float importScale,
            List<Submesh> submeshes,
            String animationName,
            int animationChannels,
            boolean hasSkin,
            List<LoadedAnimation> animations,
            List<MeshPart> parts,
            List<AnimationModel> animationModels,
            List<String> animationNames
    ) {
        public RenderableModel.MeshAnimator createAnimator() {
            return GltfSceneModelAnimator.create(parts, indices, importScale, animationModels, animationNames);
        }

        public LoadedGltfModel toLoadedModel(RenderableModel.MeshAnimator animator) {
            return new LoadedGltfModel(
                    bindPoseVertices,
                    indices,
                    animator,
                    importScale,
                    submeshes,
                    animationName,
                    animationChannels,
                    hasSkin,
                    animations
            );
        }
    }

    public record LoadedAnimation(String name, float duration, int channelCount) {}

    public enum AlphaMode {
        OPAQUE,
        MASK,
        BLEND
    }

    public record TextureInfo(
            byte[] imageBytes,
            Integer wrapS,
            Integer wrapT,
            Integer minFilter,
            Integer magFilter,
            int texcoord
    ) {}

    public record Material(
            float baseColorR,
            float baseColorG,
            float baseColorB,
            float baseColorA,
            AlphaMode alphaMode,
            float alphaCutoff,
            boolean doubleSided,
            float metallicFactor,
            float roughnessFactor,
            float normalScale,
            float occlusionStrength,
            float emissiveR,
            float emissiveG,
            float emissiveB,
            TextureInfo baseColorTexture,
            TextureInfo normalTexture,
            TextureInfo metallicRoughnessTexture,
            TextureInfo emissiveTexture,
            TextureInfo occlusionTexture
    ) {}

    public record Submesh(int indexStart, int indexCount, Material material) {}

    private record ParsedMesh(List<RawMeshPart> parts, boolean hasSkin, int skipped) {}

    public static LoadedGltfModel loadGlb(InputStream inputStream) throws IOException {
        ParsedGlbModel parsed = parseGlb(inputStream);
        RenderableModel.MeshAnimator animator = parsed.createAnimator();
        return parsed.toLoadedModel(animator);
    }

    public static ParsedGlbModel parseGlb(InputStream inputStream) throws IOException {
        GltfModelReader reader = new GltfModelReader();
        GltfModel gltfModel = reader.readWithoutReferences(inputStream);

        logModelStructure(gltfModel);

        var mesh = parseMesh(gltfModel);
        var submeshes = new ArrayList<Submesh>();

        if (mesh.parts().isEmpty()) {
            throw new IOException("No supported mesh primitives found in GLB. Check if meshes are nested in scene hierarchy.");
        }
        if (mesh.skipped() > 0) {
            LOGGER.info("Skipped {} unsupported GLB primitives", mesh.skipped());
        }
        int totalVertexCount = 0;
        int totalIndexCount = 0;
        for (RawMeshPart part : mesh.parts()) {
            if (part == null) {
                continue;
            }
            totalVertexCount += part.positions.length / 3;
            totalIndexCount += part.indices.length;
        }
        LOGGER.debug("GLB loaded: parts={}, vertices={}, indices={}, skin={}",
                mesh.parts().size(), totalVertexCount, totalIndexCount, mesh.hasSkin());

        int[] indices = new int[totalIndexCount];
        List<MeshPart> parts = new ArrayList<>(mesh.parts().size());

        int vertexOffset = 0;
        int indexOffset = 0;
        for (RawMeshPart raw : mesh.parts()) {
            int partVertexCount = raw.positions.length / 3;
            int[] idx = raw.indices;

            int submeshIndexStart = indexOffset;
            for (int i = 0; i < idx.length; i++) {
                indices[indexOffset + i] = idx[i] + vertexOffset;
            }
            indexOffset += idx.length;
            submeshes.add(new Submesh(submeshIndexStart, idx.length, raw.material));
            parts.add(new MeshPart(
                    raw.node,
                    raw.skinModel,
                    raw.positions,
                    raw.normals,
                    raw.uvs,
                    raw.joints,
                    raw.weights,
                    vertexOffset,
                    partVertexCount
            ));
            vertexOffset += partVertexCount;
        }

        List<LoadedAnimation> loadedAnimations = new ArrayList<>();
        List<AnimationModel> animationModels = gltfModel.getAnimationModels();
        List<String> animationNames = new ArrayList<>();

        if (animationModels != null && !animationModels.isEmpty()) {
            Map<String, Integer> nameCounts = new HashMap<>();
            for (int i = 0; i < animationModels.size(); i++) {
                AnimationModel anim = animationModels.get(i);
                if (anim == null) {
                    animationNames.add("animation_" + i);
                    continue;
                }

                String baseName = anim.getName();
                if (baseName == null || baseName.isBlank()) {
                    baseName = "animation_" + i;
                }
                int count = nameCounts.getOrDefault(baseName, 0) + 1;
                nameCounts.put(baseName, count);
                String resolvedName = count > 1 ? baseName + "_" + count : baseName;
                animationNames.add(resolvedName);

                float duration = computeAnimationDuration(anim);
                int channels = anim.getChannels() != null ? anim.getChannels().size() : 0;
                loadedAnimations.add(new LoadedAnimation(resolvedName, duration, channels));
            }
            LOGGER.debug("Loaded {} animations: {}", loadedAnimations.size(),
                    loadedAnimations.stream().map(LoadedAnimation::name).toList());
        }

        String animationName = !animationNames.isEmpty() ? animationNames.get(0) : null;
        int animationChannels = animationModels != null && !animationModels.isEmpty()
                && animationModels.get(0) != null && animationModels.get(0).getChannels() != null
                ? animationModels.get(0).getChannels().size() : 0;

        float importScale = computeSceneImportScale(parts, mesh.hasSkin());
        float[] bindPoseVertices = GltfSceneModelAnimator.buildInitialVertices(parts, importScale);

        return new ParsedGlbModel(
                bindPoseVertices,
                indices,
                importScale,
                submeshes,
                animationName,
                animationChannels,
                mesh.hasSkin(),
                loadedAnimations,
                parts,
                animationModels,
                animationNames
        );
    }

    public static List<LoadedAnimation> readAnimations(InputStream inputStream) throws IOException {
        GltfModelReader reader = new GltfModelReader();
        GltfModel gltfModel = reader.readWithoutReferences(inputStream);

        List<LoadedAnimation> loadedAnimations = new ArrayList<>();
        List<AnimationModel> animationModels = gltfModel.getAnimationModels();
        if (animationModels == null || animationModels.isEmpty()) {
            return loadedAnimations;
        }

        Map<String, Integer> nameCounts = new HashMap<>();
        for (int i = 0; i < animationModels.size(); i++) {
            AnimationModel anim = animationModels.get(i);
            if (anim == null) {
                loadedAnimations.add(new LoadedAnimation("animation_" + i, 0f, 0));
                continue;
            }

            String baseName = anim.getName();
            if (baseName == null || baseName.isBlank()) {
                baseName = "animation_" + i;
            }
            int count = nameCounts.getOrDefault(baseName, 0) + 1;
            nameCounts.put(baseName, count);
            String resolvedName = count > 1 ? baseName + "_" + count : baseName;

            float duration = computeAnimationDuration(anim);
            int channels = anim.getChannels() != null ? anim.getChannels().size() : 0;
            loadedAnimations.add(new LoadedAnimation(resolvedName, duration, channels));
        }
        return loadedAnimations;
    }

    private static void logModelStructure(GltfModel gltfModel) {
        LOGGER.debug("GLB structure: scenes={}, nodes={}, meshes={}, animations={}, skins={}",
                gltfModel.getSceneModels() != null ? gltfModel.getSceneModels().size() : 0,
                gltfModel.getNodeModels() != null ? gltfModel.getNodeModels().size() : 0,
                gltfModel.getMeshModels() != null ? gltfModel.getMeshModels().size() : 0,
                gltfModel.getAnimationModels() != null ? gltfModel.getAnimationModels().size() : 0,
                gltfModel.getSkinModels() != null ? gltfModel.getSkinModels().size() : 0);

        if (LOGGER.isDebugEnabled()) {
            for (NodeModel node : gltfModel.getNodeModels()) {
                logNodeHierarchy(node, 0);
            }
        }
    }

    private static void logNodeHierarchy(NodeModel node, int depth) {
        String indent = "  ".repeat(depth);
        int meshCount = node.getMeshModels() != null ? node.getMeshModels().size() : 0;
        int childCount = node.getChildren() != null ? node.getChildren().size() : 0;
        LOGGER.debug("{}Node '{}': meshes={}, children={}, skin={}",
                indent, node.getName(), meshCount, childCount, node.getSkinModel() != null);
        if (node.getChildren() != null) {
            for (NodeModel child : node.getChildren()) {
                logNodeHierarchy(child, depth + 1);
            }
        }
    }

    private static void collectMeshNodesRecursive(NodeModel node, List<NodeModel> meshNodes, Set<NodeModel> visited) {
        if (node == null || visited.contains(node)) {
            return;
        }
        visited.add(node);

        if (node.getMeshModels() != null && !node.getMeshModels().isEmpty()) {
            meshNodes.add(node);
        }

        if (node.getChildren() != null) {
            for (NodeModel child : node.getChildren()) {
                collectMeshNodesRecursive(child, meshNodes, visited);
            }
        }
    }

    private static SkinModel findSkinModel(GltfModel gltfModel) {
        List<SkinModel> skins = gltfModel.getSkinModels();
        if (skins != null && !skins.isEmpty()) {
            return skins.get(0);
        }

        for (NodeModel node : gltfModel.getNodeModels()) {
            SkinModel skin = findSkinInNode(node);
            if (skin != null) {
                return skin;
            }
        }
        return null;
    }

    private static SkinModel findSkinInNode(NodeModel node) {
        if (node == null) {
            return null;
        }
        if (node.getSkinModel() != null) {
            return node.getSkinModel();
        }
        if (node.getChildren() != null) {
            for (NodeModel child : node.getChildren()) {
                SkinModel skin = findSkinInNode(child);
                if (skin != null) {
                    return skin;
                }
            }
        }
        return null;
    }

    private static float[] readUvs(Map<String, AccessorModel> attributes, int vertexCount, int texcoordIndex) {
        if (attributes == null) {
            return new float[vertexCount * 2];
        }
        AccessorModel uvAccessor = attributes.get("TEXCOORD_" + texcoordIndex);
        if (uvAccessor == null && texcoordIndex != 0) {
            uvAccessor = attributes.get("TEXCOORD_0");
        }
        if (uvAccessor == null) {
            for (int i = 1; i < 4; i++) {
                uvAccessor = attributes.get("TEXCOORD_" + i);
                if (uvAccessor != null) {
                    break;
                }
            }
        }
        if (uvAccessor == null) {
            return new float[vertexCount * 2];
        }
        float[] uvs = GltfAccessorReaders.readFloatArray(uvAccessor);
        if (uvs.length < vertexCount * 2) {
            return new float[vertexCount * 2];
        }
        return uvs;
    }

    private static int[] readIndices(MeshPrimitiveModel primitive, int vertexCount, int mode) {
        int[] indices;
        if (primitive.getIndices() != null) {
            indices = GltfAccessorReaders.readUnsignedIntArray(primitive.getIndices());
        } else {
            indices = new int[vertexCount];
            for (int i = 0; i < vertexCount; i++) {
                indices[i] = i;
            }
        }
        return expandToTriangles(mode, indices);
    }

    private static float[] readNormals(Map<String, AccessorModel> attributes, float[] positions, int[] indices) {
        AccessorModel normalAccessor = attributes.get("NORMAL");
        if (normalAccessor != null) {
            return GltfAccessorReaders.readFloatArray(normalAccessor);
        }
        return computeNormals(positions, indices);
    }

    private static int[] readJoints(Map<String, AccessorModel> attributes, int vertexCount) {
        AccessorModel jointsAccessor = attributes.get("JOINTS_0");
        if (jointsAccessor != null) {
            return GltfAccessorReaders.readUnsignedIntArray(jointsAccessor);
        }
        return new int[vertexCount * 4];
    }

    private static float[] readWeights(Map<String, AccessorModel> attributes, int vertexCount) {
        AccessorModel weightsAccessor = attributes.get("WEIGHTS_0");
        if (weightsAccessor != null) {
            return GltfAccessorReaders.readFloatArray(weightsAccessor);
        }
        float[] weights = new float[vertexCount * 4];
        for (int i = 0; i < vertexCount; i++) {
            weights[i * 4] = 1.0f;
        }
        return weights;
    }

    private static ParsedMesh parseMesh(GltfModel gltfModel) {
        List<NodeModel> meshNodes = new ArrayList<>();
        Set<NodeModel> visited = new HashSet<>();

        List<SceneModel> sceneModels = gltfModel.getSceneModels();
        if (sceneModels != null && !sceneModels.isEmpty()) {
            for (SceneModel scene : sceneModels) {
                if (scene == null || scene.getNodeModels() == null) {
                    continue;
                }
                for (NodeModel rootNode : scene.getNodeModels()) {
                    collectMeshNodesRecursive(rootNode, meshNodes, visited);
                }
            }
        }
        if (meshNodes.isEmpty() && gltfModel.getNodeModels() != null) {
            for (NodeModel node : gltfModel.getNodeModels()) {
                collectMeshNodesRecursive(node, meshNodes, visited);
            }
        }

        SkinModel globalSkin = findSkinModel(gltfModel);
        boolean hasSkin = globalSkin != null;

        List<RawMeshPart> rawParts = new ArrayList<>();
        int skipped = 0;

        for (NodeModel node : meshNodes) {
            if (node == null || node.getMeshModels() == null) {
                continue;
            }
            SkinModel skinModel = node.getSkinModel() != null ? node.getSkinModel() : globalSkin;
            for (MeshModel meshModel : node.getMeshModels()) {
                if (meshModel == null || meshModel.getMeshPrimitiveModels() == null) {
                    continue;
                }
                for (MeshPrimitiveModel primitive : meshModel.getMeshPrimitiveModels()) {
                    if (primitive == null) {
                        continue;
                    }
                    int mode = primitive.getMode();
                    if (mode != GLTF_MODE_TRIANGLES && mode != GLTF_MODE_TRIANGLE_STRIP && mode != GLTF_MODE_TRIANGLE_FAN) {
                        skipped++;
                        continue;
                    }

                    Map<String, AccessorModel> attributes = primitive.getAttributes();
                    AccessorModel positionAccessor = attributes != null ? attributes.get("POSITION") : null;
                    if (positionAccessor == null) {
                        skipped++;
                        continue;
                    }

                    float[] positions = GltfAccessorReaders.readFloatArray(positionAccessor);
                    int vertexCount = positions.length / 3;
                    if (vertexCount <= 0) {
                        skipped++;
                        continue;
                    }

                    Material material = extractMaterial(primitive);
                    int primaryTexcoord = 0;
                    if (material != null && material.baseColorTexture() != null) {
                        primaryTexcoord = material.baseColorTexture().texcoord();
                    }

                    float[] uvs = readUvs(attributes, vertexCount, primaryTexcoord);
                    int[] indices = readIndices(primitive, vertexCount, mode);
                    float[] normals = readNormals(attributes, positions, indices);

                    int[] joints = null;
                    float[] weights = null;
                    if (skinModel != null) {
                        joints = readJoints(attributes, vertexCount);
                        weights = readWeights(attributes, vertexCount);
                    }

                    rawParts.add(new RawMeshPart(node, skinModel, positions, normals, uvs, joints, weights, indices, material));
                }
            }
        }

        return new ParsedMesh(rawParts, hasSkin, skipped);
    }

    private static float computeAnimationDuration(AnimationModel animation) {
        float duration = 0.0f;
        if (animation.getChannels() == null) return 0.0f;

        for (AnimationModel.Channel channel : animation.getChannels()) {
            AnimationModel.Sampler sampler = channel.getSampler();
            if (sampler == null) continue;

            float[] times = GltfAccessorReaders.readFloatArray(sampler.getInput());
            if (times.length > 0) {
                duration = Math.max(duration, times[times.length - 1]);
            }
        }
        return duration;
    }

    private static float computeSceneImportScale(List<MeshPart> parts, boolean hasSkin) {
        if (!hasSkin || parts == null || parts.isEmpty()) {
            return 1.0f;
        }
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        Vector3f tmp = new Vector3f();
        Matrix4f nodeGlobal = new Matrix4f();

        for (MeshPart part : parts) {
            if (part == null || part.node == null || part.positions == null) {
                continue;
            }
            nodeGlobal.set(part.node.computeGlobalTransform(new float[16]));
            float[] pos = part.positions;
            for (int i = 0; i + 2 < pos.length; i += 3) {
                nodeGlobal.transformPosition(pos[i], pos[i + 1], pos[i + 2], tmp);
                minY = Math.min(minY, tmp.y);
                maxY = Math.max(maxY, tmp.y);
            }
        }
        if (!Float.isFinite(minY) || !Float.isFinite(maxY)) {
            return 1.0f;
        }
        float height = maxY - minY;
        if (height <= 0.0f || height < CHARACTER_HEIGHT_RESCALE_THRESHOLD_BLOCKS) {
            return 1.0f;
        }
        float scale = DEFAULT_CHARACTER_HEIGHT_BLOCKS / height;
        if (scale > 1.0f) {
            return 1.0f;
        }
        if (scale < 0.0001f) {
            return 0.0001f;
        }
        LOGGER.info("GLB import scale: height={} -> scale={}", height, scale);
        return scale;
    }

    private static float[] computeNormals(float[] positions, int[] indices) {
        int vertexCount = positions.length / 3;
        float[] normals = new float[vertexCount * 3];

        for (int i = 0; i + 2 < indices.length; i += 3) {
            int i0 = indices[i];
            int i1 = indices[i + 1];
            int i2 = indices[i + 2];
            int p0 = i0 * 3;
            int p1 = i1 * 3;
            int p2 = i2 * 3;

            float x0 = positions[p0], y0 = positions[p0 + 1], z0 = positions[p0 + 2];
            float x1 = positions[p1], y1 = positions[p1 + 1], z1 = positions[p1 + 2];
            float x2 = positions[p2], y2 = positions[p2 + 1], z2 = positions[p2 + 2];

            float e1x = x1 - x0, e1y = y1 - y0, e1z = z1 - z0;
            float e2x = x2 - x0, e2y = y2 - y0, e2z = z2 - z0;

            float nx = e1y * e2z - e1z * e2y;
            float ny = e1z * e2x - e1x * e2z;
            float nz = e1x * e2y - e1y * e2x;

            normals[p0] += nx; normals[p0 + 1] += ny; normals[p0 + 2] += nz;
            normals[p1] += nx; normals[p1 + 1] += ny; normals[p1 + 2] += nz;
            normals[p2] += nx; normals[p2 + 1] += ny; normals[p2 + 2] += nz;
        }

        for (int i = 0; i < vertexCount; i++) {
            int p = i * 3;
            float nx = normals[p], ny = normals[p + 1], nz = normals[p + 2];
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1.0e-6f) {
                normals[p] = nx / len;
                normals[p + 1] = ny / len;
                normals[p + 2] = nz / len;
            } else {
                normals[p] = 0.0f;
                normals[p + 1] = 1.0f;
                normals[p + 2] = 0.0f;
            }
        }
        return normals;
    }

    private static int[] expandToTriangles(int mode, int[] indices) {
        if (indices == null || indices.length < 3) {
            return indices != null ? indices : new int[0];
        }
        if (mode == GLTF_MODE_TRIANGLES) {
            return indices;
        }
        if (mode == GLTF_MODE_TRIANGLE_FAN) {
            int triCount = Math.max(0, indices.length - 2);
            int[] out = new int[triCount * 3];
            int outPos = 0;
            int a0 = indices[0];
            for (int i = 1; i + 1 < indices.length; i++) {
                int b0 = indices[i];
                int c0 = indices[i + 1];
                if (a0 == b0 || a0 == c0 || b0 == c0) continue;
                out[outPos++] = a0;
                out[outPos++] = b0;
                out[outPos++] = c0;
            }
            return outPos == out.length ? out : Arrays.copyOf(out, outPos);
        }
        if (mode == GLTF_MODE_TRIANGLE_STRIP) {
            int triCount = Math.max(0, indices.length - 2);
            int[] out = new int[triCount * 3];
            int outPos = 0;
            for (int i = 0; i + 2 < indices.length; i++) {
                int i0 = indices[i];
                int i1 = indices[i + 1];
                int i2 = indices[i + 2];
                if (i0 == i1 || i0 == i2 || i1 == i2) continue;
                if ((i & 1) == 0) {
                    out[outPos++] = i0;
                    out[outPos++] = i1;
                    out[outPos++] = i2;
                } else {
                    out[outPos++] = i1;
                    out[outPos++] = i0;
                    out[outPos++] = i2;
                }
            }
            return outPos == out.length ? out : Arrays.copyOf(out, outPos);
        }
        return indices;
    }

    private record RawMeshPart(
            NodeModel node,
            SkinModel skinModel,
            float[] positions,
            float[] normals,
            float[] uvs,
            int[] joints,
            float[] weights,
            int[] indices,
            Material material
    ) {}

    public static final class MeshPart {
        final NodeModel node;
        final SkinModel skinModel;
        final float[] positions;
        final float[] normals;
        final float[] uvs;
        final int[] joints;
        final float[] weights;
        final int vertexStart;
        final int vertexCount;

        MeshPart(NodeModel node, SkinModel skinModel, float[] positions, float[] normals,
                 float[] uvs, int[] joints, float[] weights, int vertexStart, int vertexCount) {
            this.node = node;
            this.skinModel = skinModel;
            this.positions = positions;
            this.normals = normals;
            this.uvs = uvs;
            this.joints = joints;
            this.weights = weights;
            this.vertexStart = vertexStart;
            this.vertexCount = vertexCount;
        }
    }

    private static Material extractMaterial(MeshPrimitiveModel primitive) {
        float r = 1.0f;
        float g = 1.0f;
        float b = 1.0f;
        float a = 1.0f;
        AlphaMode alphaMode = AlphaMode.OPAQUE;
        float alphaCutoff = 0.5f;
        boolean doubleSided = true;

        float metallicFactor = 1.0f;
        float roughnessFactor = 1.0f;
        float normalScale = 1.0f;
        float occlusionStrength = 1.0f;
        float emissiveR = 0.0f;
        float emissiveG = 0.0f;
        float emissiveB = 0.0f;

        TextureInfo baseColorTexture = null;
        TextureInfo normalTexture = null;
        TextureInfo metallicRoughnessTexture = null;
        TextureInfo emissiveTexture = null;
        TextureInfo occlusionTexture = null;

        MaterialModel materialModel = primitive != null ? primitive.getMaterialModel() : null;
        if (materialModel instanceof de.javagl.jgltf.model.v2.MaterialModelV2 v2) {
            doubleSided = v2.isDoubleSided();
            alphaMode = parseAlphaMode(v2.getAlphaMode());
            alphaCutoff = v2.getAlphaCutoff();

            float[] factor = v2.getBaseColorFactor();
            if (factor != null && factor.length >= 4) {
                r = factor[0];
                g = factor[1];
                b = factor[2];
                a = factor[3];
            }

            metallicFactor = v2.getMetallicFactor();
            roughnessFactor = v2.getRoughnessFactor();
            normalScale = v2.getNormalScale();
            occlusionStrength = v2.getOcclusionStrength();

            float[] emissiveFactor = v2.getEmissiveFactor();
            if (emissiveFactor != null && emissiveFactor.length >= 3) {
                emissiveR = emissiveFactor[0];
                emissiveG = emissiveFactor[1];
                emissiveB = emissiveFactor[2];
            }

            baseColorTexture = extractTextureInfo(v2.getBaseColorTexture(), v2.getBaseColorTexcoord());
            normalTexture = extractTextureInfo(v2.getNormalTexture(), v2.getNormalTexcoord());
            metallicRoughnessTexture = extractTextureInfo(v2.getMetallicRoughnessTexture(), v2.getMetallicRoughnessTexcoord());
            emissiveTexture = extractTextureInfo(v2.getEmissiveTexture(), v2.getEmissiveTexcoord());
            occlusionTexture = extractTextureInfo(v2.getOcclusionTexture(), v2.getOcclusionTexcoord());
        }

        return new Material(
                r,
                g,
                b,
                a,
                alphaMode,
                alphaCutoff,
                doubleSided,
                metallicFactor,
                roughnessFactor,
                normalScale,
                occlusionStrength,
                emissiveR,
                emissiveG,
                emissiveB,
                baseColorTexture,
                normalTexture,
                metallicRoughnessTexture,
                emissiveTexture,
                occlusionTexture
        );
    }

    private static TextureInfo extractTextureInfo(TextureModel textureModel, Integer texcoord) {
        if (textureModel == null) {
            return null;
        }
        int texcoordIndex = texcoord != null ? texcoord.intValue() : 0;
        byte[] bytes = null;
        ImageModel imageModel = textureModel.getImageModel();
        java.nio.ByteBuffer data = imageModel != null ? imageModel.getImageData() : null;
        if (data != null) {
            java.nio.ByteBuffer dup = data.slice();
            bytes = new byte[dup.remaining()];
            dup.get(bytes);
        }
        return new TextureInfo(
                bytes,
                textureModel.getWrapS(),
                textureModel.getWrapT(),
                textureModel.getMinFilter(),
                textureModel.getMagFilter(),
                texcoordIndex
        );
    }

    private static AlphaMode parseAlphaMode(Object value) {
        if (value == null) return AlphaMode.OPAQUE;
        String s;
        if (value instanceof String str) s = str;
        else if (value instanceof Enum<?> e) s = e.name();
        else s = value.toString();
        return switch (s) {
            case "MASK" -> AlphaMode.MASK;
            case "BLEND" -> AlphaMode.BLEND;
            default -> AlphaMode.OPAQUE;
        };
    }
}
