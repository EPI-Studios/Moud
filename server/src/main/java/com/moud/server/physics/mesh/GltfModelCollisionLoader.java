package com.moud.server.physics.mesh;

import de.javagl.jgltf.model.AccessorModel;
import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.MeshModel;
import de.javagl.jgltf.model.MeshPrimitiveModel;
import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.SceneModel;
import de.javagl.jgltf.model.io.GltfModelReader;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class GltfModelCollisionLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(GltfModelCollisionLoader.class);

    private static final int GLTF_MODE_TRIANGLES = 4;
    private static final int GLTF_MODE_TRIANGLE_STRIP = 5;
    private static final int GLTF_MODE_TRIANGLE_FAN = 6;
    private static final float DEFAULT_CHARACTER_HEIGHT_BLOCKS = 1.8f;
    private static final float CHARACTER_HEIGHT_RESCALE_THRESHOLD_BLOCKS = 4.0f;

    private GltfModelCollisionLoader() {
    }

    static ModelCollisionLibrary.MeshData loadGlbMesh(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IOException("Missing GLB input stream");
        }

        GltfModelReader reader = new GltfModelReader();
        GltfModel gltfModel = reader.readWithoutReferences(inputStream);

        List<NodeModel> meshNodes = new ArrayList<>();
        Set<NodeModel> visited = new HashSet<>();

        List<SceneModel> scenes = gltfModel.getSceneModels();
        if (scenes != null && !scenes.isEmpty()) {
            for (SceneModel scene : scenes) {
                if (scene == null || scene.getNodeModels() == null) {
                    continue;
                }
                for (NodeModel root : scene.getNodeModels()) {
                    collectMeshNodesRecursive(root, meshNodes, visited);
                }
            }
        } else if (gltfModel.getNodeModels() != null) {
            for (NodeModel node : gltfModel.getNodeModels()) {
                collectMeshNodesRecursive(node, meshNodes, visited);
            }
        }

        if (meshNodes.isEmpty()) {
            throw new IOException("No mesh nodes found in GLB");
        }

        List<float[]> partPositions = new ArrayList<>();
        List<int[]> partIndices = new ArrayList<>();
        int totalVertices = 0;
        int totalIndices = 0;
        int skipped = 0;

        Matrix4f nodeGlobal = new Matrix4f();
        Vector3f tmp = new Vector3f();
        boolean hasSkin = gltfModel.getSkinModels() != null && !gltfModel.getSkinModels().isEmpty();
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;

        for (NodeModel node : meshNodes) {
            if (node == null || node.getMeshModels() == null) {
                continue;
            }
            nodeGlobal.set(node.computeGlobalTransform(new float[16]));
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
                    AccessorModel posAccessor = attributes != null ? attributes.get("POSITION") : null;
                    if (posAccessor == null) {
                        skipped++;
                        continue;
                    }

                    float[] positions = GltfServerAccessorReaders.readFloatArray(posAccessor);
                    int vertexCount = positions.length / 3;
                    if (vertexCount <= 0) {
                        skipped++;
                        continue;
                    }

                    int[] indices;
                    if (primitive.getIndices() != null) {
                        indices = GltfServerAccessorReaders.readUnsignedIntArray(primitive.getIndices());
                    } else {
                        indices = new int[vertexCount];
                        for (int i = 0; i < vertexCount; i++) {
                            indices[i] = i;
                        }
                    }
                    indices = expandToTriangles(mode, indices);

                    float[] transformed = new float[vertexCount * 3];
                    for (int i = 0; i < vertexCount; i++) {
                        int p = i * 3;
                        nodeGlobal.transformPosition(positions[p], positions[p + 1], positions[p + 2], tmp);
                        transformed[p] = tmp.x;
                        transformed[p + 1] = tmp.y;
                        transformed[p + 2] = tmp.z;
                        minY = Math.min(minY, tmp.y);
                        maxY = Math.max(maxY, tmp.y);
                    }

                    partPositions.add(transformed);
                    partIndices.add(indices);
                    totalVertices += vertexCount;
                    totalIndices += indices.length;
                }
            }
        }

        if (skipped > 0) {
            LOGGER.info("Skipped {} unsupported GLB primitives for collision (mode/position)", skipped);
        }
        if (totalVertices <= 0 || totalIndices <= 0) {
            throw new IOException("GLB contains no triangle data");
        }

        float[] outVertices = new float[totalVertices * 3];
        int[] outIndices = new int[totalIndices];

        int vertexOffset = 0;
        int indexOffset = 0;
        for (int part = 0; part < partPositions.size(); part++) {
            float[] pos = partPositions.get(part);
            int[] idx = partIndices.get(part);
            int partVertexCount = pos.length / 3;

            System.arraycopy(pos, 0, outVertices, vertexOffset * 3, pos.length);
            for (int i = 0; i < idx.length; i++) {
                outIndices[indexOffset + i] = idx[i] + vertexOffset;
            }
            vertexOffset += partVertexCount;
            indexOffset += idx.length;
        }

        float importScale = computeImportScale(hasSkin, minY, maxY);
        if (importScale != 1.0f) {
            for (int i = 0; i < outVertices.length; i++) {
                outVertices[i] *= importScale;
            }
        }
        return new ModelCollisionLibrary.MeshData(outVertices, outIndices);
    }

    private static float computeImportScale(boolean hasSkin, float minY, float maxY) {
        if (!hasSkin || !Float.isFinite(minY) || !Float.isFinite(maxY)) {
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
        LOGGER.info("GLB collision import scale: height={} -> scale={}", height, scale);
        return scale;
    }

    static boolean isGlbPath(String modelPath) {
        return modelPath != null && modelPath.toLowerCase(Locale.ROOT).endsWith(".glb");
    }

    private static void collectMeshNodesRecursive(NodeModel node, List<NodeModel> meshNodes, Set<NodeModel> visited) {
        if (node == null || !visited.add(node)) {
            return;
        }
        if (node.getMeshModels() != null && !node.getMeshModels().isEmpty()) {
            meshNodes.add(node);
        }
        if (node.getChildren() != null) {
            for (NodeModel child : node.getChildren()) {
                collectMeshNodesRecursive(child, meshNodes, visited);
            }
        }
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
                if (a0 == b0 || a0 == c0 || b0 == c0) {
                    continue;
                }
                out[outPos++] = a0;
                out[outPos++] = b0;
                out[outPos++] = c0;
            }
            if (outPos == out.length) {
                return out;
            }
            int[] trimmed = new int[outPos];
            System.arraycopy(out, 0, trimmed, 0, outPos);
            return trimmed;
        }
        if (mode == GLTF_MODE_TRIANGLE_STRIP) {
            int triCount = Math.max(0, indices.length - 2);
            int[] out = new int[triCount * 3];
            int outPos = 0;
            for (int i = 0; i + 2 < indices.length; i++) {
                int i0 = indices[i];
                int i1 = indices[i + 1];
                int i2 = indices[i + 2];
                if (i0 == i1 || i0 == i2 || i1 == i2) {
                    continue;
                }
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
            if (outPos == out.length) {
                return out;
            }
            int[] trimmed = new int[outPos];
            System.arraycopy(out, 0, trimmed, 0, outPos);
            return trimmed;
        }
        return indices;
    }
}
