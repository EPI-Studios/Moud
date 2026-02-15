package com.moud.server.minestom.engine;

import com.moud.net.protocol.SceneInfo;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.anvil.AnvilLoader;

import java.util.*;

public final class ServerScenes {
    private final InstanceManager instanceManager;
    private final Map<String, ServerScene> scenes = new LinkedHashMap<>();
    private long scenesRevision;

    public ServerScenes(InstanceManager instanceManager) {
        this.instanceManager = Objects.requireNonNull(instanceManager, "instanceManager");
    }

    public long scenesRevision() {
        return scenesRevision;
    }

    public ServerScene get(String sceneId) {
        if (sceneId == null || sceneId.isBlank()) {
            return null;
        }
        return scenes.get(sceneId);
    }

    public ServerScene ensureDefault(String sceneId, String displayName) {
        return scenes.computeIfAbsent(sceneId, id -> {
            scenesRevision++;
            return createScene(id, displayName);
        });
    }

    public ServerScene create(String sceneId, String displayName) {
        if (sceneId == null || sceneId.isBlank()) {
            throw new IllegalArgumentException("sceneId empty");
        }
        if (scenes.containsKey(sceneId)) {
            return scenes.get(sceneId);
        }
        scenesRevision++;
        ServerScene scene = createScene(sceneId, displayName);
        scenes.put(sceneId, scene);
        return scene;
    }

    private ServerScene createScene(String sceneId, String displayName) {
        InstanceContainer instance = instanceManager.createInstanceContainer();
        instance.setChunkSupplier(LightingChunk::new);
        instance.setGenerator(unit -> {
        });
        instance.setChunkLoader(new AnvilLoader("world_" + sceneId));
        return new ServerScene(sceneId, displayName, instance);
    }

    public List<SceneInfo> snapshotInfo() {
        ArrayList<SceneInfo> out = new ArrayList<>(scenes.size());
        for (ServerScene scene : scenes.values()) {
            if (scene == null) {
                continue;
            }
            out.add(new SceneInfo(scene.sceneId(), scene.displayName()));
        }
        out.sort(Comparator.comparing(SceneInfo::uiLabel).thenComparing(SceneInfo::sceneId));
        return List.copyOf(out);
    }

    public void tickAll(double dtSeconds) {
        for (ServerScene scene : scenes.values()) {
            if (scene != null) {
                scene.tick(dtSeconds);
            }
        }
    }
}
