package com.moud.client.fabric.render.mesh.generate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.moud.client.fabric.render.mesh.cache.ClientMeshBindings;
import com.moud.client.fabric.render.mesh.upload.ProceduralMeshUploader;
import com.moud.core.mesh.ArrayMesh;
import com.moud.core.mesh.MeshRegistry;
import com.moud.core.mesh.source.GeneratorMesh;
import com.moud.core.mesh.source.MeshAuthority;
import com.moud.client.fabric.util.ClientDebugLog;
import com.moud.net.protocol.MeshGeneratorPublish;

import java.util.Optional;
import java.util.function.Function;


public final class ClientMeshGenerator {
    private static volatile Function<GeneratorMesh, Optional<ArrayMesh>> runner;

    private ClientMeshGenerator() {
    }

    public static void setRunner(Function<GeneratorMesh, Optional<ArrayMesh>> r) {
        runner = r;
    }

    public static void onPublish(MeshGeneratorPublish publish) {
        var current = runner;
        if (current == null) {
            ClientDebugLog.debug("[mesh] client mesh generator runner not installed; dropping " + publish.scriptPath());
            return;
        }
        JsonObject params;
        try {
            params = publish.paramsJson() == null || publish.paramsJson().isBlank()
                    ? new JsonObject()
                    : JsonParser.parseString(publish.paramsJson()).getAsJsonObject();
        } catch (RuntimeException e) {
            ClientDebugLog.warn("mesh", "generator params json parse failed: " + e.getMessage());
            return;
        }
        var spec = new GeneratorMesh(
                publish.scriptPath(),
                params,
                publish.seed(),
                MeshAuthority.CLIENT);
        var produced = current.apply(spec);
        if (produced.isEmpty()) {
            return;
        }
        ArrayMesh mesh = MeshRegistry.instance().register(produced.get());
        ProceduralMeshUploader.enqueue(mesh);
        ClientMeshBindings.bind(publish.nodeId(), mesh.hash());
    }
}
