package com.moud.server.minestom.engine;

import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneOpBatch;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.server.minestom.engine.csg.CsgBlockWriter;
import com.moud.server.minestom.engine.nodes.RootNode;
import net.minestom.server.instance.InstanceContainer;

import java.util.Objects;

public final class ServerScene {
    private final String sceneId;
    private final String displayName;
    private final InstanceContainer instance;
    private final Engine engine;
    private final SceneOpApplier applier;
    private final CsgBlockWriter csgWriter;

    public ServerScene(String sceneId, String displayName, InstanceContainer instance) {
        this.sceneId = Objects.requireNonNull(sceneId, "sceneId");
        this.displayName = displayName == null ? "" : displayName;
        this.instance = Objects.requireNonNull(instance, "instance");
        this.engine = new Engine(new RootNode("root"), EngineSchema.createDefault());
        this.applier = new SceneOpApplier(engine);
        this.csgWriter = new CsgBlockWriter(instance, engine);
    }

    public String sceneId() {
        return sceneId;
    }

    public String displayName() {
        return displayName;
    }

    public InstanceContainer instance() {
        return instance;
    }

    public Engine engine() {
        return engine;
    }

    public void tick(double dtSeconds) {
        engine.tick(dtSeconds);
        csgWriter.tick();
    }

    public SceneSnapshot snapshot(long requestId) {
        return engine.snapshot(requestId);
    }

    public SceneOpAck apply(SceneOpBatch batch) {
        return applier.apply(batch);
    }

    public SceneOpApplier applier() {
        return applier;
    }
}

