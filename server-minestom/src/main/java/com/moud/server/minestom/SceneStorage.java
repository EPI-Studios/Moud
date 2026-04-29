package com.moud.server.minestom;

import com.moud.core.assets.AssetMeta;
import com.moud.core.assets.ResPath;
import com.moud.core.scene.SceneTreeMutator;
import com.moud.server.minestom.engine.SceneInstancer;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.engine.ServerScenes;
import com.moud.server.minestom.scene.SceneFileIO;
import java.util.ArrayList;
import java.util.List;
import com.moud.server.minestom.scripting.ScriptService;
import com.moud.server.minestom.util.DebugLog;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Objects;

final class SceneStorage {
    private final Path projectRoot;
    private final ServerScenes scenes;
    private final SceneInstancer instancer;
    private final ScriptService scripts;

    SceneStorage(Path projectRoot, ServerScenes scenes, SceneInstancer instancer, ScriptService scripts) {
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot");
        this.scenes = Objects.requireNonNull(scenes, "scenes");
        this.instancer = Objects.requireNonNull(instancer, "instancer");
        this.scripts = Objects.requireNonNull(scripts, "scripts");
    }

    void loadScenesFromDisk() {
        Path scenesDir = projectRoot.resolve("scenes");
        if (!Files.exists(scenesDir) || !Files.isDirectory(scenesDir)) {
            return;
        }

        try (var stream = Files.list(scenesDir)) {
            stream.filter(p -> p != null && p.getFileName() != null && p.getFileName().toString().endsWith(".moud.scene"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(p -> {
                        String filename = p.getFileName().toString();
                        String sceneId = filename.substring(0, filename.length() - ".moud.scene".length());
                        if (sceneId.isBlank()) {
                            return;
                        }

                        try {
                            String json = Files.readString(p, StandardCharsets.UTF_8);
                            var file = SceneFileIO.parse(json);
                            String displayName = file.displayName() == null || file.displayName().isBlank()
                                    ? sceneId
                                    : file.displayName();

                            ServerScene scene = scenes.ensureDefault(sceneId, displayName);
                            var specs = SceneFileIO.toNodeSpecs(file);
                            SceneTreeMutator.replaceRootChildren(scene.engine().sceneTree(), specs, scene.engine().nodeTypes());
                            scene.engine().bumpSceneRevision();
                            scene.engine().bumpCsgRevision();
                            scene.engine().bumpPhysicsRevision();
                            DebugLog.info("scene", "loaded '" + sceneId + "' from " + p);
                        } catch (Exception e) {
                            DebugLog.error("scene", "failed to load '" + sceneId + "': " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            DebugLog.error("scene", "failed to scan scenes/: " + e.getMessage());
        }
    }

    void onAssetUploaded(ResPath path, AssetMeta meta, byte[] bytes) {
        if (path == null || !path.path().endsWith(".moud.scene")) {
            return;
        }

        String filename = path.path().substring(path.path().lastIndexOf('/') + 1);
        String sceneId = filename.substring(0, filename.length() - ".moud.scene".length());
        if (sceneId.isBlank()) {
            DebugLog.warn("scene", "invalid scene filename: " + filename);
            return;
        }

        try {
            String json = new String(bytes, StandardCharsets.UTF_8);
            var file = SceneFileIO.parse(json);
            String displayName = file.displayName() == null || file.displayName().isBlank()
                    ? sceneId
                    : file.displayName();

            ServerScene scene = scenes.ensureDefault(sceneId, displayName);
            var specs = SceneFileIO.toNodeSpecs(file);
            SceneTreeMutator.replaceRootChildren(scene.engine().sceneTree(), specs, scene.engine().nodeTypes());
            scene.engine().bumpSceneRevision();
            scene.engine().bumpCsgRevision();
            scene.engine().bumpPhysicsRevision();
            DebugLog.info("scene", "imported '" + sceneId + "' from " + path.path());
            try {
                persistSceneToDisk(scene);
            } catch (Exception e) {
                DebugLog.error("scene", "failed to persist imported scene '" + sceneId + "': " + e.getMessage());
            }
            instancer.syncAll(scenes);
        } catch (Exception e) {
            DebugLog.error("scene", "failed to import '" + sceneId + "': " + e.getMessage());
        }
    }

    void persistSceneToDisk(ServerScene scene) throws Exception {
        if (scene == null) {
            throw new IllegalArgumentException("scene null");
        }
        Path out = sceneFilePath(scene.sceneId());
        Files.createDirectories(out.getParent());
        String json = SceneFileIO.toJson(scene.sceneId(), scene.displayName(), scene.snapshot(0L));
        Path tmp = out.resolveSibling(out.getFileName().toString() + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, out, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    boolean deleteSceneFile(String sceneId) throws Exception {
        if (sceneId == null || sceneId.isBlank()) {
            return false;
        }
        return Files.deleteIfExists(sceneFilePath(sceneId));
    }

    void onSceneDeleted(String placeId) {
        if (placeId == null || placeId.isBlank()) {
            return;
        }
        List<ServerScene> live = scenes.instancesOfPlace(placeId);
        List<String> ids = new ArrayList<>(live.size());
        for (ServerScene s : live) {
            if (s != null) ids.add(s.instanceId());
        }
        scripts.onPlaceDeleted(ids);
    }

    Path sceneFilePath(String sceneId) {
        return projectRoot.resolve("scenes").resolve(sceneId + ".moud.scene");
    }
}
