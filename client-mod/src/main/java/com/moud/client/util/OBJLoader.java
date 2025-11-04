package com.moud.client.util;

import org.joml.Vector2f;
import org.joml.Vector3f;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OBJLoader {

    public record OBJMesh(float[] vertices, int[] indices) {}

    public static OBJMesh load(InputStream inputStream) {
        List<Vector3f> positions = new ArrayList<>();
        List<Vector2f> texCoords = new ArrayList<>();
        List<Vector3f> normals = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        Map<String, Integer> vertexCache = new HashMap<>();
        List<Vector3f> vertexPositions = new ArrayList<>();

        List<Float> finalVertices = new ArrayList<>();
        int nextVertexIndex = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                switch (parts[0]) {
                    case "v" -> positions.add(new Vector3f(Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])));
                    case "vt" -> texCoords.add(new Vector2f(Float.parseFloat(parts[1]), 1.0f - Float.parseFloat(parts[2]))); // Invert V for OpenGL
                    case "vn" -> normals.add(new Vector3f(Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])));
                    case "f" -> {
                        int vertexCount = parts.length - 1;
                        if (vertexCount < 3) {
                            continue;
                        }

                        int[] faceIndices = new int[vertexCount];
                        for (int i = 0; i < vertexCount; i++) {
                            String vertexKey = parts[i + 1];
                            Integer cachedIndex = vertexCache.get(vertexKey);
                            if (cachedIndex == null) {
                                String[] faceParts = vertexKey.split("/");
                                int posIdx = Integer.parseInt(faceParts[0]) - 1;
                                int texIdx = faceParts.length > 1 && !faceParts[1].isEmpty() ? Integer.parseInt(faceParts[1]) - 1 : -1;
                                int normIdx = faceParts.length > 2 && !faceParts[2].isEmpty() ? Integer.parseInt(faceParts[2]) - 1 : -1;

                                Vector3f pos = positions.get(posIdx);
                                finalVertices.add(pos.x);
                                finalVertices.add(pos.y);
                                finalVertices.add(pos.z);

                                Vector2f tex = (texIdx != -1 && texCoords.size() > texIdx) ? texCoords.get(texIdx) : new Vector2f(0, 0);
                                finalVertices.add(tex.x);
                                finalVertices.add(tex.y);

                                Vector3f norm = (normIdx != -1 && normals.size() > normIdx) ? normals.get(normIdx) : new Vector3f(0, 1, 0);
                                finalVertices.add(norm.x);
                                finalVertices.add(norm.y);
                                finalVertices.add(norm.z);

                                cachedIndex = nextVertexIndex;
                                vertexCache.put(vertexKey, nextVertexIndex);
                                vertexPositions.add(new Vector3f(pos));
                                nextVertexIndex++;
                            }
                            faceIndices[i] = cachedIndex;
                        }

                        for (int i = 1; i < vertexCount - 1; i++) {
                            int idxA = faceIndices[0];
                            int idxB = faceIndices[i];
                            int idxC = faceIndices[i + 1];
                            if (idxA == idxB || idxA == idxC || idxB == idxC) {
                                continue;
                            }

                            Vector3f posA = vertexPositions.get(idxA);
                            Vector3f posB = vertexPositions.get(idxB);
                            Vector3f posC = vertexPositions.get(idxC);

                            Vector3f edge1 = new Vector3f(posB).sub(posA);
                            Vector3f edge2 = new Vector3f(posC).sub(posA);
                            Vector3f cross = edge1.cross(edge2);
                            if (cross.lengthSquared() < 1e-10f) {
                                continue;
                            }

                            indices.add(idxA);
                            indices.add(idxB);
                            indices.add(idxC);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load OBJ model", e);
        }

        float[] verticesArray = new float[finalVertices.size()];
        for (int i = 0; i < finalVertices.size(); i++) {
            verticesArray[i] = finalVertices.get(i);
        }
        int[] indicesArray = indices.stream().mapToInt(i -> i).toArray();

        return new OBJMesh(verticesArray, indicesArray);
    }
}
