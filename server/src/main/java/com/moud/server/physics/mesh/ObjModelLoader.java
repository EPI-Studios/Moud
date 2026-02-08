package com.moud.server.physics.mesh;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class ObjModelLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObjModelLoader.class);

    private ObjModelLoader() {
    }

    static ModelCollisionLibrary.MeshData loadMesh(InputStream inputStream) throws IOException {
        List<float[]> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("v ")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length < 4) {
                        continue;
                    }
                    try {
                        float x = Float.parseFloat(parts[1]);
                        float y = Float.parseFloat(parts[2]);
                        float z = Float.parseFloat(parts[3]);
                        vertices.add(new float[]{x, y, z});
                    } catch (NumberFormatException ex) {
                        LOGGER.debug("Skipping malformed vertex entry '{}': {}", line, ex.getMessage());
                    }
                } else if (line.startsWith("f ")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length < 4) {
                        continue;
                    }
                    int v0 = parseIndex(parts[1]);
                    for (int i = 2; i < parts.length - 1; i++) {
                        int v1 = parseIndex(parts[i]);
                        int v2 = parseIndex(parts[i + 1]);
                        if (v0 >= 0 && v1 >= 0 && v2 >= 0) {
                            indices.add(v0);
                            indices.add(v1);
                            indices.add(v2);
                        }
                    }
                }
            }
        }

        float[] data = new float[vertices.size() * 3];
        for (int i = 0; i < vertices.size(); i++) {
            float[] v = vertices.get(i);
            data[i * 3] = v[0];
            data[i * 3 + 1] = v[1];
            data[i * 3 + 2] = v[2];
        }
        int[] indexArray = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            indexArray[i] = indices.get(i);
        }
        return new ModelCollisionLibrary.MeshData(data, indexArray);
    }

    private static int parseIndex(String token) {
        if (token == null || token.isEmpty()) {
            return -1;
        }
        String[] parts = token.split("/");
        try {
            int idx = Integer.parseInt(parts[0]);
            return idx - 1; // onj is 1-based
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
