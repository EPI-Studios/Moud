package com.moud.api.rendering.model.loader;

import com.moud.api.math.Vector3;
import com.moud.api.rendering.mesh.Mesh;
import com.moud.api.rendering.mesh.MeshBuilder;
import com.moud.api.rendering.model.ModelLoader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal OBJ loader that supports positions, normals and texture coordinates.
 *
 * <p>This implementation is intentionally simple and designed to provide
 * enough functionality for prototyping custom rendering.</p>
 */
public final class SimpleObjModelLoader implements ModelLoader {
    private static final String DEFAULT_PART = "default";

    @Override
    public Mesh load(InputStream stream) throws IOException {
        List<Vector3> positions = new ArrayList<>();
        List<Vector3> normals = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();
        List<VertexData> builtVertices = new ArrayList<>();
        Map<String, Integer> vertexLookup = new HashMap<>();
        List<int[]> triangles = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String rawLine;
            while ((rawLine = reader.readLine()) != null) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] tokens = line.split("\\s+");
                String keyword = tokens[0].toLowerCase(Locale.ROOT);
                switch (keyword) {
                    case "v" -> positions.add(parseVector(tokens));
                    case "vn" -> normals.add(parseVector(tokens));
                    case "vt" -> uvs.add(parseUv(tokens));
                    case "f" -> parseFace(tokens, positions, normals, uvs, builtVertices, vertexLookup, triangles);
                    default -> {
                        // ignore unsupported tokens
                    }
                }
            }
        }

        MeshBuilder builder = Mesh.builder();
        builder.part(DEFAULT_PART, part -> {
            for (VertexData vertex : builtVertices) {
                part.vertex(v -> {
                    v.position(vertex.position);
                    if (vertex.normal != null) {
                        v.normal(vertex.normal);
                    }
                    v.uv(vertex.u, vertex.v);
                });
            }
            for (int[] tri : triangles) {
                part.triangle(tri[0], tri[1], tri[2]);
            }
        });
        return builder.build();
    }

    private static Vector3 parseVector(String[] tokens) {
        if (tokens.length < 4) {
            throw new IllegalArgumentException("Invalid vector definition");
        }
        return new Vector3(Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]), Float.parseFloat(tokens[3]));
    }

    private static float[] parseUv(String[] tokens) {
        if (tokens.length < 3) {
            throw new IllegalArgumentException("Invalid texture coordinate definition");
        }
        float u = Float.parseFloat(tokens[1]);
        float v = Float.parseFloat(tokens[2]);
        return new float[] {u, v};
    }

    private static void parseFace(
            String[] tokens,
            List<Vector3> positions,
            List<Vector3> normals,
            List<float[]> uvs,
            List<VertexData> builtVertices,
            Map<String, Integer> vertexLookup,
            List<int[]> triangles
    ) {
        if (tokens.length < 4) {
            throw new IllegalArgumentException("Face must specify at least three vertices");
        }
        List<Integer> faceIndices = new ArrayList<>();
        for (int i = 1; i < tokens.length; i++) {
            String vertexToken = tokens[i];
            int index = vertexLookup.computeIfAbsent(vertexToken, key -> {
                VertexReference ref = parseVertexReference(key);
                Vector3 position = ref.resolvePosition(positions);
                Vector3 normal = ref.resolveNormal(normals);
                float[] uv = ref.resolveUv(uvs);
                VertexData vertex = new VertexData(position, normal, uv != null ? uv[0] : 0.0f, uv != null ? uv[1] : 0.0f);
                builtVertices.add(vertex);
                return builtVertices.size() - 1;
            });
            faceIndices.add(index);
        }

        for (int i = 1; i + 1 < faceIndices.size(); i++) {
            int a = faceIndices.get(0);
            int b = faceIndices.get(i);
            int c = faceIndices.get(i + 1);
            triangles.add(new int[] {a, b, c});
        }
    }

    private static VertexReference parseVertexReference(String token) {
        String[] parts = token.split("/");
        int positionIndex = parts.length > 0 && !parts[0].isEmpty() ? Integer.parseInt(parts[0]) : 0;
        Integer uvIndex = parts.length > 1 && !parts[1].isEmpty() ? Integer.parseInt(parts[1]) : null;
        Integer normalIndex = parts.length > 2 && !parts[2].isEmpty() ? Integer.parseInt(parts[2]) : null;
        return new VertexReference(positionIndex, uvIndex, normalIndex);
    }

    private record VertexData(Vector3 position, Vector3 normal, float u, float v) {
    }

    private record VertexReference(int positionIndex, Integer uvIndex, Integer normalIndex) {
        private Vector3 resolvePosition(List<Vector3> positions) {
            int index = normalizeIndex(positionIndex, positions.size());
            return positions.get(index);
        }

        private Vector3 resolveNormal(List<Vector3> normals) {
            if (normalIndex == null || normals.isEmpty()) {
                return null;
            }
            int index = normalizeIndex(normalIndex, normals.size());
            return normals.get(index);
        }

        private float[] resolveUv(List<float[]> uvs) {
            if (uvIndex == null || uvs.isEmpty()) {
                return null;
            }
            int index = normalizeIndex(uvIndex, uvs.size());
            return uvs.get(index);
        }

        private static int normalizeIndex(int objIndex, int size) {
            int resolved = objIndex;
            if (resolved < 0) {
                resolved = size + resolved;
            } else {
                resolved = resolved - 1;
            }
            if (resolved < 0 || resolved >= size) {
                throw new IndexOutOfBoundsException("Invalid OBJ index: " + objIndex + " for size " + size);
            }
            return resolved;
        }
    }
}
