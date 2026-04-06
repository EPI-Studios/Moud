package com.moud.server.minestom.engine;

import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneOpBatch;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.server.minestom.engine.anvil.AnvilWorldLoader;
import com.moud.server.minestom.engine.csg.CsgBlockWriter;
import com.moud.server.minestom.engine.nodes.RootNode;
import com.moud.server.minestom.physics.JoltPhysicsWorld;
import net.minestom.server.instance.InstanceContainer;
import com.moud.server.minestom.engine.EngineSchema;

import java.util.Objects;

public final class ServerScene {
    private final String sceneId;
    private final String displayName;
    private final InstanceContainer instance;
    private final Engine engine;
    private final SceneOpApplier applier;
    private final CsgBlockWriter csgWriter;
    private final AnvilWorldLoader anvilLoader;
    private final JoltPhysicsWorld physics;

    public ServerScene(String sceneId, String displayName, InstanceContainer instance) {
        this.sceneId = Objects.requireNonNull(sceneId, "sceneId");
        this.displayName = displayName == null ? "" : displayName;
        this.instance = Objects.requireNonNull(instance, "instance");
        this.engine = new Engine(new RootNode("root"), EngineSchema.createDefault());
        this.engine.nodeTypes().applyDefaults(this.engine.sceneTree().root(), "Root");
        this.applier = new SceneOpApplier(engine);
        this.csgWriter = new CsgBlockWriter(instance, engine);
        this.anvilLoader = new AnvilWorldLoader(instance, engine);
        this.physics = JoltPhysicsWorld.tryCreate();
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

    public void tickPlay(double dtSeconds) {
        tick(dtSeconds, true);
    }

    public void tickEditor() {
        tick(0.0, false);
    }

    private void tick(double dtSeconds, boolean simulate) {
        engine.tick(simulate ? dtSeconds : 0.0);
        anvilLoader.tick();
        csgWriter.tick();
        if (physics != null) {
            physics.syncStaticColliders(engine);
            physics.syncCollisionFilters(engine);
            if (simulate) {
                physics.step((float) dtSeconds);
                physics.writeDynamicBodiesBack(engine);
                physics.tickRaycasts(engine);
            }
        }
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

    public JoltPhysicsWorld physics() {
        return physics;
    }
}
