package com.moud.server.assets.objatlas.parser;

import com.moud.api.math.Vector2;
import com.moud.api.math.Vector3;
import com.moud.server.assets.objatlas.obj.ObjFace;
import com.moud.server.assets.objatlas.obj.ObjMaterial;
import com.moud.server.assets.objatlas.obj.ObjModel;
import com.moud.server.assets.objatlas.obj.ObjVertexRef;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple OBJ parser.
 * Supports: v, vt, vn, f, o, g, s, mtllib, usemtl.
 */
public final class ObjParser {

    private final MtlParser mtlParser = new MtlParser();

    public ObjModel parse(Path objPath) throws IOException {
        List<Vector3> positions = new ArrayList<>();
        List<Vector3> normals = new ArrayList<>();
        List<Vector2> texCoords = new ArrayList<>();
        List<ObjFace> faces = new ArrayList<>();
        Map<String, ObjMaterial> materials = new LinkedHashMap<>();

        String modelName = stripExtension(objPath.getFileName().toString());
        String currentGroup = null;
        String currentMaterial = null;
        int currentSmoothingGroup = -1;

        Path baseDir = objPath.toAbsolutePath().getParent();

        try (BufferedReader reader = Files.newBufferedReader(objPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                String[] tokens = trimmed.split("\\s+");
                String keyword = tokens[0];

                switch (keyword) {
                    case "o" -> {
                        if (tokens.length > 1) {
                            modelName = tokens[1];
                        }
                    }
                    case "v" -> {
                        float x = parseFloat(tokens, 1, 0.0f);
                        float y = parseFloat(tokens, 2, 0.0f);
                        float z = parseFloat(tokens, 3, 0.0f);
                        positions.add(new Vector3(x, y, z));
                    }
                    case "vt" -> {
                        float u = parseFloat(tokens, 1, 0.0f);
                        float v = parseFloat(tokens, 2, 0.0f);
                        texCoords.add(new Vector2(u, v));
                    }
                    case "vn" -> {
                        float x = parseFloat(tokens, 1, 0.0f);
                        float y = parseFloat(tokens, 2, 0.0f);
                        float z = parseFloat(tokens, 3, 0.0f);
                        normals.add(new Vector3(x, y, z));
                    }
                    case "f" -> {
                        List<ObjVertexRef> verts = new ArrayList<>(tokens.length - 1);
                        for (int i = 1; i < tokens.length; i++) {
                            String vertToken = tokens[i];
                            ObjVertexRef ref = parseVertexRef(
                                    vertToken,
                                    positions.size(),
                                    texCoords.size(),
                                    normals.size()
                            );
                            verts.add(ref);
                        }
                        faces.add(new ObjFace(
                                List.copyOf(verts),
                                currentMaterial,
                                currentGroup,
                                currentSmoothingGroup
                        ));
                    }
                    case "g" -> {
                        // Only keep the first group name if multiple are given
                        currentGroup = tokens.length > 1 ? tokens[1] : null;
                    }
                    case "s" -> {
                        if (tokens.length > 1) {
                            if ("off".equalsIgnoreCase(tokens[1])) {
                                currentSmoothingGroup = -1;
                            } else {
                                try {
                                    currentSmoothingGroup = Integer.parseInt(tokens[1]);
                                } catch (NumberFormatException e) {
                                    currentSmoothingGroup = -1;
                                }
                            }
                        } else {
                            currentSmoothingGroup = -1;
                        }
                    }
                    case "usemtl" -> {
                        currentMaterial = tokens.length > 1 ? tokens[1] : null;
                    }
                    case "mtllib" -> {
                        if (baseDir != null) {
                            for (int i = 1; i < tokens.length; i++) {
                                String mtlFileName = tokens[i];
                                Path mtlPath = baseDir.resolve(mtlFileName);
                                if (Files.exists(mtlPath)) {
                                    Map<String, ObjMaterial> parsed = mtlParser.parse(mtlPath);
                                    materials.putAll(parsed);
                                }
                            }
                        }
                    }
                    default -> {
                        // Unknown/unsupported keyword: ignore
                    }
                }
            }
        }

        return new ObjModel(
                modelName,
                List.copyOf(positions),
                List.copyOf(normals),
                List.copyOf(texCoords),
                List.copyOf(faces),
                Map.copyOf(materials)
        );
    }

    private ObjVertexRef parseVertexRef(String token,
                                        int posCount,
                                        int texCount,
                                        int normCount) {
        String[] parts = token.split("/");
        String vStr = parts.length > 0 ? parts[0] : "";
        String vtStr = parts.length > 1 ? parts[1] : "";
        String vnStr = parts.length > 2 ? parts[2] : "";

        int vIdx = parseIndex(vStr, posCount);
        int vtIdx = parseIndex(vtStr, texCount);
        int vnIdx = parseIndex(vnStr, normCount);

        return new ObjVertexRef(vIdx, vtIdx, vnIdx);
    }

    /**
     * Parses an OBJ index which can be positive (1-based) or negative (relative).
     * Returns 0-based index, or -1 if not present.
     */
    private int parseIndex(String str, int size) {
        if (str == null || str.isEmpty()) {
            return -1;
        }
        try {
            int idx = Integer.parseInt(str);
            if (idx > 0) {
                return idx - 1;               // OBJ is 1-based
            } else if (idx < 0) {
                return size + idx;            // Negative indices are relative to the end
            } else {
                return -1;
            }
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private float parseFloat(String[] tokens, int index, float defaultValue) {
        if (tokens.length <= index) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(tokens[index]);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return (dot >= 0) ? name.substring(0, dot) : name;
    }
}
