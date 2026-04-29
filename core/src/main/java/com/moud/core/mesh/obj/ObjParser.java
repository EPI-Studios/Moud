package com.moud.core.mesh.obj;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class ObjParser {

    private static final String DEFAULT_GROUP = "default";

    private ObjParser() {
    }

    public static ObjModel parse(String text) {
        if (text == null || text.isEmpty()) {
            return new ObjModel(new float[0], new float[0], new float[0], List.of(), null);
        }

        ArrayList<Float> positions = new ArrayList<>();
        ArrayList<Float> uvs = new ArrayList<>();
        ArrayList<Float> normals = new ArrayList<>();
        LinkedHashMap<String, ArrayList<int[]>> groupFaces = new LinkedHashMap<>();
        String currentGroup = DEFAULT_GROUP;
        groupFaces.put(currentGroup, new ArrayList<>());
        String mtlLib = null;

        for (String rawLine : text.split("\n")) {
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) continue;
            String[] tokens = line.split("\\s+");
            String head = tokens[0].toLowerCase();
            switch (head) {
                case "v" -> {
                    if (tokens.length >= 4) {
                        positions.add(parseFloat(tokens[1], 0f));
                        positions.add(parseFloat(tokens[2], 0f));
                        positions.add(parseFloat(tokens[3], 0f));
                    }
                }
                case "vt" -> {
                    if (tokens.length >= 3) {
                        uvs.add(parseFloat(tokens[1], 0f));
                        uvs.add(1f - parseFloat(tokens[2], 0f));
                    } else if (tokens.length >= 2) {
                        uvs.add(parseFloat(tokens[1], 0f));
                        uvs.add(0f);
                    }
                }
                case "vn" -> {
                    if (tokens.length >= 4) {
                        normals.add(parseFloat(tokens[1], 0f));
                        normals.add(parseFloat(tokens[2], 0f));
                        normals.add(parseFloat(tokens[3], 0f));
                    }
                }
                case "f" -> {
                    if (tokens.length < 4) break;
                    ArrayList<int[]> verts = new ArrayList<>(tokens.length - 1);
                    for (int i = 1; i < tokens.length; i++) {
                        verts.add(parseFaceVertex(tokens[i],
                                positions.size() / 3,
                                uvs.size() / 2,
                                normals.size() / 3));
                    }
                    ArrayList<int[]> currentFaces = groupFaces.get(currentGroup);
                    for (int i = 1; i + 1 < verts.size(); i++) {
                        int[] a = verts.get(0);
                        int[] b = verts.get(i);
                        int[] c = verts.get(i + 1);
                        int[] tri = new int[]{a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2]};
                        currentFaces.add(tri);
                    }
                }
                case "usemtl" -> {
                    String name = tokens.length > 1 ? tokens[1] : DEFAULT_GROUP;
                    currentGroup = name;
                    groupFaces.computeIfAbsent(currentGroup, k -> new ArrayList<>());
                }
                case "mtllib" -> {
                    if (tokens.length > 1) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 1; i < tokens.length; i++) {
                            if (i > 1) sb.append(' ');
                            sb.append(tokens[i]);
                        }
                        mtlLib = sb.toString();
                    }
                }
                default -> {
                }
            }
        }

        ArrayList<ObjGroup> groups = new ArrayList<>();
        for (var entry : groupFaces.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            groups.add(new ObjGroup(entry.getKey(), entry.getValue()));
        }
        if (groups.isEmpty()) {
            return new ObjModel(toArray(positions), toArray(uvs), toArray(normals), List.of(), mtlLib);
        }
        return new ObjModel(toArray(positions), toArray(uvs), toArray(normals), groups, mtlLib);
    }

    private static int[] parseFaceVertex(String token, int posCount, int uvCount, int normalCount) {
        String[] parts = token.split("/", -1);
        int p = parts.length > 0 ? parseIndex(parts[0], posCount) : -1;
        int t = parts.length > 1 ? parseIndex(parts[1], uvCount) : -1;
        int n = parts.length > 2 ? parseIndex(parts[2], normalCount) : -1;
        return new int[]{p, t, n};
    }

    private static int parseIndex(String value, int currentCount) {
        if (value == null || value.isBlank()) return -1;
        try {
            int parsed = Integer.parseInt(value);
            if (parsed > 0) return parsed - 1;
            if (parsed < 0) return currentCount + parsed;
            return -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash < 0 ? line : line.substring(0, hash);
    }

    private static float[] toArray(ArrayList<Float> list) {
        float[] out = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            out[i] = list.get(i);
        }
        return out;
    }
}
