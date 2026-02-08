package com.moud.client.model.gltf;

import com.moud.client.model.RenderableModel;
import com.moud.client.model.gltf.GltfSkinnedModelLoader.MeshPart;
import de.javagl.jgltf.model.AnimationModel;
import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.SkinModel;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class GltfSceneModelAnimator implements RenderableModel.MeshAnimator, RenderableModel.AnimationController {
    private static final Logger LOGGER = LoggerFactory.getLogger(GltfSceneModelAnimator.class);
    private static final float SECONDS_PER_TICK = 1.0f / 20.0f;

    private final List<MeshPartRuntime> parts;
    private final int[] indices;
    private final float importScale;
    private final List<AnimationClip> clips;
    private AnimationClip activeClip;

    private float timeSeconds = 0.0f;
    private boolean playing;
    private boolean loop = true;
    private float speed = 1.0f;
    private boolean writeToA = true;

    private final float[] vertexBufferA;
    private final float[] vertexBufferB;
    private final Map<NodeModel, NodeTransform> nodeTransforms = new IdentityHashMap<>();
    private final Map<NodeModel, Matrix4f> globalTransformCache = new IdentityHashMap<>();
    private final List<NodeModel> allNodes = new ArrayList<>();

    private final Vector3f tmpVec = new Vector3f();
    private final Quaternionf tmpQuat = new Quaternionf();

    static RenderableModel.MeshAnimator create(
            List<MeshPart> parts,
            int[] indices,
            float importScale,
            List<AnimationModel> animationModels,
            List<String> animationNames
    ) {
        if (parts == null || parts.isEmpty()) {
            return model -> {};
        }
        int totalVertexCount = parts.stream()
                .filter(p -> p != null)
                .mapToInt(p -> p.vertexCount)
                .sum();

        List<MeshPartRuntime> runtimes = parts.stream()
                .filter(p -> p != null)
                .map(MeshPartRuntime::new)
                .toList();

        List<AnimationClip> clips = buildAnimationClips(animationModels, animationNames);

        int vertexSize = totalVertexCount * RenderableModel.FLOATS_PER_VERTEX;
        float[] a = new float[vertexSize];
        float[] b = new float[vertexSize];
        prefillUvs(parts, a);
        prefillUvs(parts, b);

        return new GltfSceneModelAnimator(runtimes, indices, importScale, clips, a, b);
    }

    static float[] buildInitialVertices(List<MeshPart> parts, float importScale) {
        if (parts == null || parts.isEmpty()) {
            return new float[0];
        }
        int totalVertexCount = parts.stream()
                .filter(p -> p != null)
                .mapToInt(p -> p.vertexCount)
                .sum();

        float[] vertices = new float[totalVertexCount * RenderableModel.FLOATS_PER_VERTEX];
        prefillUvs(parts, vertices);

        List<MeshPartRuntime> runtimes = parts.stream()
                .filter(p -> p != null)
                .map(MeshPartRuntime::new)
                .toList();

        Map<NodeModel, NodeTransform> transforms = new IdentityHashMap<>();
        Map<NodeModel, Matrix4f> globalCache = new IdentityHashMap<>();
        Set<NodeModel> allNodeSet = collectAllNodes(runtimes);
        List<NodeModel> allNodes = new ArrayList<>(allNodeSet);

        allNodes.forEach(node -> transforms.put(node, NodeTransform.fromNode(node)));
        allNodes.forEach(node -> computeGlobalTransform(node, transforms, globalCache));
        runtimes.forEach(runtime -> runtime.writeIntoWithTransforms(vertices, importScale, globalCache));

        return vertices;
    }

    private static List<AnimationClip> buildAnimationClips(List<AnimationModel> animationModels, List<String> animationNames) {
        if (animationModels == null || animationModels.isEmpty()) {
            return List.of();
        }

        List<AnimationClip> clips = new ArrayList<>();
        for (int i = 0; i < animationModels.size(); i++) {
            AnimationModel animationModel = animationModels.get(i);
            if (animationModel == null) continue;

            String name = (animationNames != null && i < animationNames.size())
                    ? animationNames.get(i)
                    : "animation_" + i;
            if (name == null || name.isBlank()) {
                name = "animation_" + i;
            }

            List<Channel> channels = new ArrayList<>();
            float duration = 0.0f;
            List<AnimationModel.Channel> rawChannels = animationModel.getChannels();

            if (rawChannels != null) {
                for (AnimationModel.Channel channel : rawChannels) {
                    NodeModel nodeModel = channel.getNodeModel();
                    if (nodeModel == null) continue;

                    ChannelPath path = ChannelPath.from(channel.getPath());
                    if (path == null) continue;

                    AnimationModel.Sampler sampler = channel.getSampler();
                    if (sampler == null) continue;

                    float[] times = GltfAccessorReaders.readFloatArray(sampler.getInput());
                    float[] values = GltfAccessorReaders.readFloatArray(sampler.getOutput());
                    channels.add(new Channel(nodeModel, path, sampler.getInterpolation(), times, values));

                    if (times.length > 0) {
                        duration = Math.max(duration, times[times.length - 1]);
                    }
                }
            }

            clips.add(new AnimationClip(name, channels, duration));
        }
        return clips;
    }

    private static Set<NodeModel> collectAllNodes(List<MeshPartRuntime> runtimes) {
        Set<NodeModel> nodes = new LinkedHashSet<>();
        for (MeshPartRuntime runtime : runtimes) {
            if (runtime.part != null && runtime.part.node != null) {
                collectNodeHierarchy(runtime.part.node, nodes);
            }
            if (runtime.part.skinModel != null) {
                runtime.part.skinModel.getJoints().forEach(joint -> collectNodeHierarchy(joint, nodes));
            }
        }
        return nodes;
    }

    private static void collectNodeHierarchy(NodeModel node, Set<NodeModel> nodes) {
        if (node == null || nodes.contains(node)) return;
        NodeModel parent = node.getParent();
        if (parent != null) {
            collectNodeHierarchy(parent, nodes);
        }
        nodes.add(node);
        if (node.getChildren() != null) {
            node.getChildren().forEach(child -> collectNodeHierarchy(child, nodes));
        }
    }

    private static Matrix4f computeGlobalTransform(NodeModel node, Map<NodeModel, NodeTransform> transforms, Map<NodeModel, Matrix4f> cache) {
        Matrix4f cached = cache.get(node);
        if (cached != null) return cached;

        NodeTransform local = transforms.get(node);
        Matrix4f localMatrix = local != null ? local.toMatrix() : new Matrix4f();

        NodeModel parent = node.getParent();
        Matrix4f result = parent != null
                ? new Matrix4f(computeGlobalTransform(parent, transforms, cache)).mul(localMatrix)
                : localMatrix;

        cache.put(node, result);
        return result;
    }

    private static void prefillUvs(List<MeshPart> parts, float[] vertices) {
        for (MeshPart part : parts) {
            if (part == null) continue;
            float[] uvs = part.uvs;
            for (int i = 0; i < part.vertexCount; i++) {
                int globalVertex = part.vertexStart + i;
                int o = globalVertex * RenderableModel.FLOATS_PER_VERTEX;
                int t = i * 2;
                vertices[o + 3] = uvs != null && (t + 1) < uvs.length ? uvs[t] : 0.0f;
                vertices[o + 4] = uvs != null && (t + 1) < uvs.length ? uvs[t + 1] : 0.0f;
            }
        }
    }

    private GltfSceneModelAnimator(
            List<MeshPartRuntime> parts,
            int[] indices,
            float importScale,
            List<AnimationClip> clips,
            float[] vertexBufferA,
            float[] vertexBufferB
    ) {
        this.parts = parts;
        this.indices = indices;
        this.importScale = importScale;
        this.clips = clips != null ? List.copyOf(clips) : List.of();
        this.vertexBufferA = vertexBufferA;
        this.vertexBufferB = vertexBufferB;

        Set<NodeModel> allNodeSet = new LinkedHashSet<>();
        for (AnimationClip clip : this.clips) {
            if (clip != null && clip.channels() != null) {
                clip.channels().forEach(channel -> collectNodeHierarchy(channel.node, allNodeSet));
            }
        }
        allNodeSet.addAll(collectAllNodes(parts));
        allNodes.addAll(allNodeSet);

        allNodes.forEach(node -> nodeTransforms.put(node, NodeTransform.fromNode(node)));

        int channelCount = this.clips.stream()
                .filter(c -> c != null && c.channels() != null)
                .mapToInt(c -> c.channels().size())
                .sum();

        LOGGER.debug("Animator initialized with {} nodes, {} channels, {} clips",
                allNodes.size(), channelCount, this.clips.size());
    }

    @Override
    public void tick(RenderableModel model) {
        AnimationClip clip = activeClip;
        if (clip == null) return;

        float durationSeconds = clip.durationSeconds();
        if (durationSeconds > 0.0f && playing) {
            timeSeconds += SECONDS_PER_TICK * Math.max(0.0f, speed);
            if (timeSeconds > durationSeconds) {
                if (loop) {
                    timeSeconds = timeSeconds % durationSeconds;
                } else {
                    timeSeconds = durationSeconds;
                    playing = false;
                }
            }
        }

        nodeTransforms.values().forEach(NodeTransform::resetToBase);
        clip.channels().forEach(channel -> {
            NodeTransform transform = nodeTransforms.get(channel.node);
            if (transform != null) {
                channel.sampleInto(transform, timeSeconds, tmpVec, tmpQuat);
            }
        });

        globalTransformCache.clear();
        allNodes.forEach(node -> computeGlobalTransform(node, nodeTransforms, globalTransformCache));

        float[] out = writeToA ? vertexBufferA : vertexBufferB;
        writeToA = !writeToA;
        parts.forEach(runtime -> runtime.writeIntoWithTransforms(out, importScale, globalTransformCache));
        model.setMeshData(out, indices, false);
    }

    @Override
    public boolean selectAnimation(String name) {
        if (name == null) return false;
        String target = name.trim();
        if (target.isEmpty()) return false;

        return clips.stream()
                .filter(clip -> clip != null && target.equalsIgnoreCase(clip.name()))
                .findFirst()
                .map(clip -> { activeClip = clip; return true; })
                .orElse(false);
    }

    @Override
    public boolean selectAnimation(int index) {
        if (index < 0 || index >= clips.size()) return false;
        activeClip = clips.get(index);
        return activeClip != null;
    }

    @Override
    public void clearAnimation() {
        activeClip = null;
        timeSeconds = 0.0f;
        playing = false;
        loop = true;
        speed = 1.0f;
    }

    @Override
    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    @Override
    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    @Override
    public void setSpeed(float speed) {
        this.speed = speed;
    }

    @Override
    public void seek(float timeSeconds) {
        this.timeSeconds = Math.max(0.0f, timeSeconds);
    }

    private static class NodeTransform {
        private final Vector3f baseTranslation = new Vector3f();
        private final Quaternionf baseRotation = new Quaternionf();
        private final Vector3f baseScale = new Vector3f(1, 1, 1);
        private final Matrix4f baseMatrix = new Matrix4f();
        private boolean usesMatrix;

        final Vector3f translation = new Vector3f();
        final Quaternionf rotation = new Quaternionf();
        final Vector3f scale = new Vector3f(1, 1, 1);
        final Matrix4f matrix = new Matrix4f();

        static NodeTransform fromNode(NodeModel node) {
            NodeTransform t = new NodeTransform();
            float[] matrix = node != null ? node.getMatrix() : null;

            if (matrix != null && matrix.length >= 16 && !isIdentityMatrix(matrix)) {
                t.usesMatrix = true;
                t.baseMatrix.set(matrix);
                t.matrix.set(t.baseMatrix);
            } else if (node != null) {
                float[] trans = node.getTranslation();
                float[] rot = node.getRotation();
                float[] scl = node.getScale();

                if (trans != null && trans.length >= 3) {
                    t.baseTranslation.set(trans[0], trans[1], trans[2]);
                }
                if (rot != null && rot.length >= 4) {
                    t.baseRotation.set(rot[0], rot[1], rot[2], rot[3]);
                }
                if (scl != null && scl.length >= 3) {
                    t.baseScale.set(scl[0], scl[1], scl[2]);
                }
            }
            t.resetToBase();
            return t;
        }

        void resetToBase() {
            if (usesMatrix) {
                matrix.set(baseMatrix);
            } else {
                translation.set(baseTranslation);
                rotation.set(baseRotation);
                scale.set(baseScale);
            }
        }

        Matrix4f toMatrix() {
            return usesMatrix
                    ? new Matrix4f(matrix)
                    : new Matrix4f().translate(translation).rotate(rotation).scale(scale);
        }

        private static boolean isIdentityMatrix(float[] matrix) {
            if (matrix == null || matrix.length < 16) return true;
            float[] identity = {1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
            for (int i = 0; i < 16; i++) {
                if (Math.abs(matrix[i] - identity[i]) > 1.0e-6f) return false;
            }
            return true;
        }
    }

    private enum ChannelPath {
        TRANSLATION, ROTATION, SCALE;

        static ChannelPath from(String gltfPath) {
            if (gltfPath == null) return null;
            return switch (gltfPath) {
                case "translation" -> TRANSLATION;
                case "rotation" -> ROTATION;
                case "scale" -> SCALE;
                default -> null;
            };
        }
    }

    private record Channel(
            NodeModel node,
            ChannelPath path,
            AnimationModel.Interpolation interpolation,
            float[] times,
            float[] values
    ) {
        void sampleInto(NodeTransform transform, float timeSeconds, Vector3f tmpVec, Quaternionf tmpQuat) {
            if (times == null || times.length == 0 || values == null) return;

            int idx = findKeyframeIndex(timeSeconds);
            int nextIdx = Math.min(idx + 1, times.length - 1);
            float t0 = times[idx];
            float t1 = times[nextIdx];
            float deltaTime = t1 - t0;

            float alpha = (interpolation == AnimationModel.Interpolation.STEP || deltaTime <= 0.0f)
                    ? 0.0f
                    : Math.max(0.0f, Math.min(1.0f, (timeSeconds - t0) / deltaTime));

            boolean isCubic = interpolation == AnimationModel.Interpolation.CUBICSPLINE;
            int components = path == ChannelPath.ROTATION ? 4 : 3;
            int stride = components * (isCubic ? 3 : 1);
            int aBase = idx * stride;
            int bBase = nextIdx * stride;

            if (values.length < bBase + stride) return;

            switch (path) {
                case TRANSLATION -> {
                    if (isCubic) {
                        interpolateCubicSplineVec3(aBase, bBase, components, alpha, deltaTime, transform.translation);
                    } else {
                        lerpVec3(aBase, bBase, alpha, transform.translation, tmpVec);
                    }
                }
                case SCALE -> {
                    if (isCubic) {
                        interpolateCubicSplineVec3(aBase, bBase, components, alpha, deltaTime, transform.scale);
                    } else {
                        lerpVec3(aBase, bBase, alpha, transform.scale, tmpVec);
                    }
                }
                case ROTATION -> {
                    if (isCubic) {
                        interpolateCubicSplineQuat(aBase, bBase, components, alpha, deltaTime, transform.rotation);
                    } else {
                        slerpQuat(aBase, bBase, alpha, transform.rotation, tmpQuat);
                    }
                }
            }
        }

        private void lerpVec3(int aBase, int bBase, float alpha, Vector3f result, Vector3f tmp) {
            tmp.set(values[aBase], values[aBase + 1], values[aBase + 2]);
            result.set(values[bBase], values[bBase + 1], values[bBase + 2]);
            tmp.lerp(result, alpha, result);
        }

        private void slerpQuat(int aBase, int bBase, float alpha, Quaternionf result, Quaternionf tmp) {
            tmp.set(values[aBase], values[aBase + 1], values[aBase + 2], values[aBase + 3]);
            result.set(values[bBase], values[bBase + 1], values[bBase + 2], values[bBase + 3]);
            tmp.slerp(result, alpha, result);
        }

        private void interpolateCubicSplineVec3(int aBase, int bBase, int components, float alpha, float deltaTime, Vector3f result) {
            float t = alpha, t2 = t * t, t3 = t2 * t;
            float h00 = 2f * t3 - 3f * t2 + 1f;
            float h10 = t3 - 2f * t2 + t;
            float h01 = -2f * t3 + 3f * t2;
            float h11 = t3 - t2;

            int valOff = components, outOff = components * 2;
            float p0x = values[aBase + valOff], p0y = values[aBase + valOff + 1], p0z = values[aBase + valOff + 2];
            float m0x = values[aBase + outOff] * deltaTime, m0y = values[aBase + outOff + 1] * deltaTime, m0z = values[aBase + outOff + 2] * deltaTime;
            float p1x = values[bBase + valOff], p1y = values[bBase + valOff + 1], p1z = values[bBase + valOff + 2];
            float m1x = values[bBase] * deltaTime, m1y = values[bBase + 1] * deltaTime, m1z = values[bBase + 2] * deltaTime;

            result.set(
                    h00 * p0x + h10 * m0x + h01 * p1x + h11 * m1x,
                    h00 * p0y + h10 * m0y + h01 * p1y + h11 * m1y,
                    h00 * p0z + h10 * m0z + h01 * p1z + h11 * m1z
            );
        }

        private void interpolateCubicSplineQuat(int aBase, int bBase, int components, float alpha, float deltaTime, Quaternionf result) {
            float t = alpha, t2 = t * t, t3 = t2 * t;
            float h00 = 2f * t3 - 3f * t2 + 1f;
            float h10 = t3 - 2f * t2 + t;
            float h01 = -2f * t3 + 3f * t2;
            float h11 = t3 - t2;

            int valOff = components, outOff = components * 2;
            float p0x = values[aBase + valOff], p0y = values[aBase + valOff + 1], p0z = values[aBase + valOff + 2], p0w = values[aBase + valOff + 3];
            float m0x = values[aBase + outOff] * deltaTime, m0y = values[aBase + outOff + 1] * deltaTime, m0z = values[aBase + outOff + 2] * deltaTime, m0w = values[aBase + outOff + 3] * deltaTime;
            float p1x = values[bBase + valOff], p1y = values[bBase + valOff + 1], p1z = values[bBase + valOff + 2], p1w = values[bBase + valOff + 3];
            float m1x = values[bBase] * deltaTime, m1y = values[bBase + 1] * deltaTime, m1z = values[bBase + 2] * deltaTime, m1w = values[bBase + 3] * deltaTime;

            result.set(
                    h00 * p0x + h10 * m0x + h01 * p1x + h11 * m1x,
                    h00 * p0y + h10 * m0y + h01 * p1y + h11 * m1y,
                    h00 * p0z + h10 * m0z + h01 * p1z + h11 * m1z,
                    h00 * p0w + h10 * m0w + h01 * p1w + h11 * m1w
            ).normalize();
        }

        private int findKeyframeIndex(float timeSeconds) {
            if (timeSeconds <= times[0]) return 0;
            int last = times.length - 1;
            if (timeSeconds >= times[last]) return last;
            int low = 0, high = last;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                if (times[mid] < timeSeconds) low = mid + 1;
                else if (times[mid] > timeSeconds) high = mid - 1;
                else return mid;
            }
            return Math.max(0, low - 1);
        }
    }

    private static final class MeshPartRuntime {
        private final MeshPart part;
        private final Matrix4f meshGlobal = new Matrix4f();
        private final Matrix4f invBind = new Matrix4f();
        private final Matrix4f jointMatrix = new Matrix4f();
        private final Vector3f tmpPos = new Vector3f();
        private final Vector3f tmpNrm = new Vector3f();
        private final Matrix4f[] jointMatrices;
        private final List<NodeModel> jointNodes;

        private MeshPartRuntime(MeshPart part) {
            this.part = part;
            SkinModel skinModel = part.skinModel;
            if (skinModel != null && skinModel.getJoints() != null) {
                this.jointNodes = skinModel.getJoints();
                this.jointMatrices = new Matrix4f[jointNodes.size()];
                for (int i = 0; i < jointMatrices.length; i++) {
                    jointMatrices[i] = new Matrix4f();
                }
            } else {
                this.jointNodes = Collections.emptyList();
                this.jointMatrices = new Matrix4f[0];
            }
        }

        void writeIntoWithTransforms(float[] outVertices, float importScale, Map<NodeModel, Matrix4f> globalTransforms) {
            if (part == null || part.node == null || outVertices == null) return;

            Matrix4f meshNodeGlobal = globalTransforms.get(part.node);
            if (meshNodeGlobal != null) {
                meshGlobal.set(meshNodeGlobal);
            } else {
                meshGlobal.set(part.node.computeGlobalTransform(new float[16]));
            }

            boolean skinned = part.skinModel != null && part.joints != null && part.weights != null && jointMatrices.length > 0;
            if (skinned) {
                updateJointMatrices(globalTransforms);
            }

            float[] positions = part.positions;
            float[] normals = part.normals;
            int[] joints = part.joints;
            float[] weights = part.weights;
            int globalBaseVertex = part.vertexStart;

            for (int i = 0; i < part.vertexCount; i++) {
                int p = i * 3;
                int globalVertex = globalBaseVertex + i;
                int o = globalVertex * RenderableModel.FLOATS_PER_VERTEX;

                float px = positions[p], py = positions[p + 1], pz = positions[p + 2];
                float nx = normals[p], ny = normals[p + 1], nz = normals[p + 2];

                if (!skinned) {
                    meshGlobal.transformPosition(px, py, pz, tmpPos);
                    outVertices[o] = tmpPos.x * importScale;
                    outVertices[o + 1] = tmpPos.y * importScale;
                    outVertices[o + 2] = tmpPos.z * importScale;

                    meshGlobal.transformDirection(nx, ny, nz, tmpNrm).normalize();
                    outVertices[o + 5] = tmpNrm.x;
                    outVertices[o + 6] = tmpNrm.y;
                    outVertices[o + 7] = tmpNrm.z;
                    continue;
                }

                int w = i * 4;
                float weightSum = weights[w] + weights[w + 1] + weights[w + 2] + weights[w + 3];
                if (weightSum <= 0) weightSum = 1;

                tmpPos.zero();
                tmpNrm.zero();

                for (int k = 0; k < 4; k++) {
                    float wk = weights[w + k] / weightSum;
                    if (wk == 0) continue;
                    int jointIndex = joints[i * 4 + k];
                    if (jointIndex < 0 || jointIndex >= jointMatrices.length) continue;

                    Matrix4f m = jointMatrices[jointIndex];
                    Vector3f pos = new Vector3f();
                    Vector3f nrm = new Vector3f();
                    m.transformPosition(px, py, pz, pos);
                    m.transformDirection(nx, ny, nz, nrm);
                    tmpPos.add(pos.mul(wk));
                    tmpNrm.add(nrm.mul(wk));
                }

                outVertices[o] = tmpPos.x * importScale;
                outVertices[o + 1] = tmpPos.y * importScale;
                outVertices[o + 2] = tmpPos.z * importScale;

                tmpNrm.normalize();
                outVertices[o + 5] = tmpNrm.x;
                outVertices[o + 6] = tmpNrm.y;
                outVertices[o + 7] = tmpNrm.z;
            }
        }

        private void updateJointMatrices(Map<NodeModel, Matrix4f> globalTransforms) {
            SkinModel skinModel = part.skinModel;
            if (skinModel == null) return;

            for (int i = 0; i < jointNodes.size(); i++) {
                NodeModel jointNode = jointNodes.get(i);
                Matrix4f jointGlobal = globalTransforms.get(jointNode);
                if (jointGlobal == null) {
                    jointGlobal = new Matrix4f().set(jointNode.computeGlobalTransform(new float[16]));
                }

                invBind.set(skinModel.getInverseBindMatrix(i, new float[16]));
                jointMatrices[i].set(jointGlobal).mul(invBind);
            }
        }
    }

    private record AnimationClip(String name, List<Channel> channels, float durationSeconds) {}
}