package com.moud.server.minestom.scene;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.moud.core.scene.SceneFile;
import com.moud.core.scene.SceneTreeMutator;
import com.moud.net.protocol.SceneSnapshot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SceneFileIO {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private SceneFileIO() {
    }

    public static String toJson(String sceneId, String displayName, SceneSnapshot snapshot) {
        SceneFile file = toSceneFile(sceneId, displayName, snapshot);
        return GSON.toJson(file);
    }

    public static SceneFile parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Scene file empty");
        }
        SceneFile file = GSON.fromJson(json, SceneFile.class);
        if (file == null) {
            throw new IllegalArgumentException("Scene file invalid");
        }
        int format = file.format();
        if (format != SceneFile.FORMAT_V1) {
            throw new IllegalArgumentException("Unsupported scene file format: " + format);
        }
        return file;
    }

    public static List<SceneTreeMutator.NodeSpec> toNodeSpecs(SceneFile file) {
        Objects.requireNonNull(file, "file");
        if (file.nodes() == null || file.nodes().isEmpty()) {
            return List.of();
        }
        ArrayList<SceneTreeMutator.NodeSpec> out = new ArrayList<>(file.nodes().size());
        for (SceneFile.NodeEntry node : file.nodes()) {
            if (node == null || node.id() <= 0L) {
                continue;
            }
            if (node.name() == null || node.name().isBlank()) {
                continue;
            }
            out.add(new SceneTreeMutator.NodeSpec(
                    node.id(),
                    node.parent(),
                    node.name(),
                    node.type(),
                    node.properties()
            ));
        }
        return List.copyOf(out);
    }

    private static SceneFile toSceneFile(String sceneId, String displayName, SceneSnapshot snapshot) {
        if (snapshot == null || snapshot.nodes() == null || snapshot.nodes().isEmpty()) {
            return new SceneFile(SceneFile.FORMAT_V1, sceneId, displayName, List.of());
        }

        long rootId = 0L;
        for (SceneSnapshot.NodeSnapshot node : snapshot.nodes()) {
            if (node != null && node.parentId() == 0L) {
                rootId = node.nodeId();
                break;
            }
        }

        HashMap<Long, ArrayList<Long>> childrenByParent = new HashMap<>();
        for (SceneSnapshot.NodeSnapshot node : snapshot.nodes()) {
            if (node == null || node.nodeId() <= 0L) {
                continue;
            }
            childrenByParent.computeIfAbsent(node.parentId(), ignored -> new ArrayList<>()).add(node.nodeId());
        }

        HashSet<Long> excluded = new HashSet<>();
        for (SceneSnapshot.NodeSnapshot node : snapshot.nodes()) {
            if (node == null || node.nodeId() <= 0L) {
                continue;
            }
            if (!isRuntimePlayerNode(node)) {
                continue;
            }
            excluded.add(node.nodeId());
        }

        if (!excluded.isEmpty()) {
            Deque<Long> stack = new ArrayDeque<>(excluded);
            while (!stack.isEmpty()) {
                long parent = stack.pop();
                ArrayList<Long> kids = childrenByParent.get(parent);
                if (kids == null || kids.isEmpty()) {
                    continue;
                }
                for (long child : kids) {
                    if (excluded.add(child)) {
                        stack.push(child);
                    }
                }
            }
        }

        ArrayList<SceneFile.NodeEntry> nodes = new ArrayList<>(snapshot.nodes().size());
        for (SceneSnapshot.NodeSnapshot node : snapshot.nodes()) {
            if (node == null || node.nodeId() <= 0L) {
                continue;
            }
            if (node.nodeId() == rootId) {
                continue;
            }
            if (excluded.contains(node.nodeId())) {
                continue;
            }
            long parent = node.parentId();
            if (parent == rootId) {
                parent = 0L;
            }

            LinkedHashMap<String, String> props = new LinkedHashMap<>();
            if (node.properties() != null) {
                for (SceneSnapshot.Property prop : node.properties()) {
                    if (prop == null || prop.key() == null || prop.key().isBlank() || prop.value() == null) {
                        continue;
                    }
                    if ("@type".equals(prop.key())) {
                        continue;
                    }
                    props.put(prop.key(), prop.value());
                }
            }
            nodes.add(new SceneFile.NodeEntry(node.nodeId(), parent, node.name(), node.type(), props));
        }

        return new SceneFile(SceneFile.FORMAT_V1, sceneId, displayName, List.copyOf(nodes));
    }

    private static boolean isRuntimePlayerNode(SceneSnapshot.NodeSnapshot node) {
        if (node == null) {
            return false;
        }
        String name = node.name();
        if (name == null || !name.startsWith("player_")) {
            return false;
        }
        return "CharacterBody3D".equals(node.type());
    }
}

