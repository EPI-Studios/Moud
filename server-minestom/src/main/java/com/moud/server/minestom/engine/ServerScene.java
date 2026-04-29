package com.moud.server.minestom.engine;

import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneOpBatch;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.server.minestom.collision.CollisionBakeService;
import com.moud.server.minestom.engine.anvil.AnvilWorldLoader;
import com.moud.server.minestom.engine.csg.CsgBlockWriter;
import com.moud.server.minestom.engine.nodes.RootNode;
import com.moud.server.minestom.physics.PhysicsClock;
import com.moud.server.minestom.physics.rapier.RapierScenePhysicsWorld;
import com.moud.server.minestom.util.DebugLog;
import net.minestom.server.instance.InstanceContainer;
import com.moud.server.minestom.engine.EngineSchema;

import java.util.Objects;
import java.util.UUID;

public final class ServerScene {
    private final String instanceId;
    private final String sceneId;
    private final String displayName;
    private final InstanceContainer instance;
    private final Engine engine;
    private final SceneOpApplier applier;
    private final CsgBlockWriter csgWriter;
    private final AnvilWorldLoader anvilLoader;
    private final RapierScenePhysicsWorld physics;
    private final PhysicsClock physicsClock = new PhysicsClock();
    private volatile boolean isPrivate;
    private volatile boolean disposed;
    private volatile TickBudgetConfig budgetConfig = TickBudgetConfig.defaults();
    private int consecutiveOverruns;
    private int consecutiveUnderruns;
    private volatile boolean degraded;
    private volatile long lastTickNanos;

    public ServerScene(String sceneId, String displayName, InstanceContainer instance, CollisionBakeService collisionBakeService) {
        this(UUID.randomUUID().toString(), sceneId, displayName, instance, collisionBakeService);
    }

    public ServerScene(String instanceId, String sceneId, String displayName, InstanceContainer instance, CollisionBakeService collisionBakeService) {
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
        this.sceneId = Objects.requireNonNull(sceneId, "sceneId");
        this.displayName = displayName == null ? "" : displayName;
        this.instance = Objects.requireNonNull(instance, "instance");
        this.engine = new Engine(new RootNode("root"), EngineSchema.createDefault());
        this.engine.nodeTypes().applyDefaults(this.engine.sceneTree().root(), "Root");
        this.applier = new SceneOpApplier(engine);
        this.csgWriter = new CsgBlockWriter(instance, engine);
        this.anvilLoader = new AnvilWorldLoader(instance, engine);
        this.physics = new RapierScenePhysicsWorld(collisionBakeService);
    }

    public String instanceId() {
        return instanceId;
    }

    public String placeId() {
        return sceneId;
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

    public synchronized void tickPlay(double dtSeconds) {
        long start = System.nanoTime();
        try {
            tick(dtSeconds, true);
        } finally {
            recordTickDuration(System.nanoTime() - start);
        }
    }

    public synchronized void tickEditor() {
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
                physics.syncDynamicBodies(engine);
                physicsClock.accumulate(dtSeconds);
                int steps = physicsClock.consumeSteps();
                for (int i = 0; i < steps; i++) {
                    physics.tickFixed();
                }
                physics.writeDynamicBodiesBack(engine);
                physics.tickRaycasts(engine);
            }
        }
    }

    private void recordTickDuration(long nanos) {
        lastTickNanos = nanos;
        TickBudgetConfig cfg = budgetConfig;
        if (nanos > cfg.budgetNanos()) {
            consecutiveOverruns++;
            consecutiveUnderruns = 0;
            if (!degraded && consecutiveOverruns >= cfg.degradeAfterConsecutiveOverruns()) {
                degraded = true;
                DebugLog.warn("tick", "instance " + instanceId + " entering degraded mode after "
                        + consecutiveOverruns + " consecutive overruns (last=" + (nanos / 1_000_000) + "ms budget="
                        + (cfg.budgetNanos() / 1_000_000) + "ms)");
            }
        } else {
            consecutiveUnderruns++;
            consecutiveOverruns = 0;
            if (degraded && consecutiveUnderruns >= cfg.recoverAfterConsecutiveUnderruns()) {
                degraded = false;
                DebugLog.info("tick", "instance " + instanceId + " recovered from degraded mode");
            }
        }
    }

    public boolean isDegraded() {
        return degraded;
    }

    public long lastTickNanos() {
        return lastTickNanos;
    }

    public TickBudgetConfig budgetConfig() {
        return budgetConfig;
    }

    public void setBudgetConfig(TickBudgetConfig config) {
        if (config != null) {
            this.budgetConfig = config;
        }
    }

    public synchronized SceneSnapshot snapshot(long requestId) {
        return engine.snapshot(requestId);
    }

    public synchronized SceneOpAck apply(SceneOpBatch batch) {
        return applier.apply(batch);
    }

    public SceneOpApplier applier() {
        return applier;
    }

    public RapierScenePhysicsWorld physics() {
        return physics;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean value) {
        this.isPrivate = value;
    }

    public boolean isDisposed() {
        return disposed;
    }

    public int playerCount() {
        return instance.getPlayers().size();
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (physics != null) {
            try {
                physics.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
