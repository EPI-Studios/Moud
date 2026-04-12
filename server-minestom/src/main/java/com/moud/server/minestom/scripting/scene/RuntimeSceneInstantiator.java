package com.moud.server.minestom.scripting.scene;

import com.moud.core.scene.SceneFile;
import com.moud.core.scene.SceneTreeMutator;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneOpBatch;
import com.moud.net.protocol.SceneOpResult;
import com.moud.server.minestom.engine.SceneBatchIds;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.project.ProjectService;
import com.moud.server.minestom.scene.SceneFileIO;
import com.moud.server.minestom.scripting.lang.RuntimeScriptKeys;
import com.moud.server.minestom.util.DebugLog;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RuntimeSceneInstantiator {
    private static final String LOG_TAG = "script-runtime";

    private final ProjectService project;
    private final SceneMutator mutator;

    public RuntimeSceneInstantiator(ProjectService project, SceneMutator mutator) {
        this.project = Objects.requireNonNull(project, "project");
        this.mutator = Objects.requireNonNull(mutator, "mutator");
    }

    public long instantiate(ServerScene scene, String scenePath, long parentId) {
        if (scene == null || scenePath == null || scenePath.isBlank() || parentId < 0L) {
            return 0L;
        }
        mutator.flush(scene);

        Path file;
        try {
            file = project.resolveProjectPath(scenePath);
        } catch (Exception e) {
            DebugLog.error(LOG_TAG, "instantiate: cannot resolve path '" + scenePath + "': " + e.getMessage());
            return 0L;
        }

        String json;
        try {
            json = Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            DebugLog.error(LOG_TAG, "instantiate: cannot read file '" + file + "': " + e.getMessage());
            return 0L;
        }

        SceneFile sceneFile;
        try {
            sceneFile = SceneFileIO.parse(json);
        } catch (Exception e) {
            DebugLog.error(LOG_TAG, "instantiate: parse error: " + e.getMessage());
            return 0L;
        }

        List<SceneTreeMutator.NodeSpec> specs = SceneFileIO.toNodeSpecs(sceneFile);
        if (specs.isEmpty()) {
            return 0L;
        }

        HashMap<Long, SceneTreeMutator.NodeSpec> specById = new HashMap<>(specs.size());
        for (SceneTreeMutator.NodeSpec spec : specs) {
            if (spec != null && spec.nodeId() > 0L) {
                specById.put(spec.nodeId(), spec);
            }
        }

        HashMap<Long, Long> idMap = new HashMap<>(specs.size());
        Deque<Long> queue = new ArrayDeque<>();
        long firstRootId = 0L;

        for (SceneTreeMutator.NodeSpec spec : specs) {
            if (spec != null && (spec.parentId() <= 0L || !specById.containsKey(spec.parentId()))) {
                queue.add(spec.nodeId());
            }
        }

        while (!queue.isEmpty()) {
            long fileId = queue.poll();
            SceneTreeMutator.NodeSpec spec = specById.get(fileId);
            if (spec == null) {
                continue;
            }

            long targetParent;
            if (spec.parentId() <= 0L || !specById.containsKey(spec.parentId())) {
                targetParent = parentId;
            } else {
                Long mapped = idMap.get(spec.parentId());
                if (mapped == null) {
                    continue;
                }
                targetParent = mapped;
            }

            long batchId = SceneBatchIds.markRuntime((scene.engine().ticks() << 32) ^ System.nanoTime());
            SceneOpAck ack = scene.applier().apply(
                    new SceneOpBatch(batchId, true, List.of(new SceneOp.CreateNode(targetParent, spec.name(), spec.typeId()))));
            if (ack == null || ack.results() == null || ack.results().isEmpty()) {
                continue;
            }
            SceneOpResult result = ack.results().getFirst();
            if (result == null || !result.ok() || result.createdId() <= 0L) {
                continue;
            }

            long newId = result.createdId();
            idMap.put(fileId, newId);
            if (firstRootId == 0L && targetParent == parentId) {
                firstRootId = newId;
            }

            mutator.queueSet(newId, RuntimeScriptKeys.PROP_RUNTIME, "true");
            for (Map.Entry<String, String> entry : spec.properties().entrySet()) {
                String key = entry.getKey();
                if (key == null || key.startsWith("@")) {
                    continue;
                }
                mutator.queueSet(newId, key, entry.getValue());
            }

            for (SceneTreeMutator.NodeSpec child : specs) {
                if (child != null && child.parentId() == fileId) {
                    queue.add(child.nodeId());
                }
            }
        }

        return firstRootId;
    }
}
