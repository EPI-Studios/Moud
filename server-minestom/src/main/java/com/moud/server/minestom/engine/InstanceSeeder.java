package com.moud.server.minestom.engine;

import com.moud.core.scene.SceneTreeMutator;
import com.moud.server.minestom.scene.SceneFileIO;
import com.moud.server.minestom.util.DebugLog;

public final class InstanceSeeder {
    private InstanceSeeder() {
    }

    public static boolean seedFromSource(ServerScene source, ServerScene target) {
        if (source == null || target == null || source == target) {
            return false;
        }
        try {
            String json = SceneFileIO.toJson(source.sceneId(), source.displayName(), source.snapshot(0L));
            var file = SceneFileIO.parse(json);
            var specs = SceneFileIO.toNodeSpecs(file);
            SceneTreeMutator.replaceRootChildren(target.engine().sceneTree(), specs, target.engine().nodeTypes());
            target.engine().bumpSceneRevision();
            target.engine().bumpCsgRevision();
            target.engine().bumpPhysicsRevision();
            return true;
        } catch (Exception e) {
            DebugLog.error("instance", "seed failed place=" + source.placeId() + " target=" + target.instanceId() + ": " + e.getMessage());
            return false;
        }
    }
}
