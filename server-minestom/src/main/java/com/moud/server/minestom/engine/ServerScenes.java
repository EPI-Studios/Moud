package com.moud.server.minestom.engine;

import com.moud.net.protocol.SceneInfo;
import com.moud.server.minestom.collision.CollisionBakeService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.anvil.AnvilLoader;

public final class ServerScenes {
    private final InstanceManager instanceManager;
    private final CollisionBakeService collisionBakeService;
    private final Map<String, ServerScene> primaryByPlace = new LinkedHashMap<>();
    private final Map<String, ServerScene> byInstanceId = new ConcurrentHashMap<>();
    private final Map<String, List<ServerScene>> instancesByPlace = new ConcurrentHashMap<>();
    private final List<Consumer<ServerScene>> instanceRemovedListeners = new CopyOnWriteArrayList<>();
    private final ExecutorService tickExecutor;
    private long scenesRevision;

    public ServerScenes(InstanceManager instanceManager, CollisionBakeService collisionBakeService) {
        this.instanceManager = Objects.requireNonNull(instanceManager, "instanceManager");
        this.collisionBakeService = Objects.requireNonNull(collisionBakeService, "collisionBakeService");
        int workers = Math.max(2, Runtime.getRuntime().availableProcessors());
        AtomicInteger threadCounter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread t = new Thread(runnable, "moud-instance-worker-" + threadCounter.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        this.tickExecutor = Executors.newFixedThreadPool(workers, factory);
    }

    public ExecutorService tickExecutor() {
        return tickExecutor;
    }

    public long scenesRevision() {
        return scenesRevision;
    }

    public ServerScene get(String placeId) {
        if (placeId == null || placeId.isBlank()) {
            return null;
        }
        return primaryByPlace.get(placeId);
    }

    public ServerScene getByInstanceId(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return null;
        }
        return byInstanceId.get(instanceId);
    }

    public List<ServerScene> instancesOfPlace(String placeId) {
        if (placeId == null || placeId.isBlank()) {
            return List.of();
        }
        List<ServerScene> live = instancesByPlace.get(placeId);
        return live == null ? List.of() : List.copyOf(live);
    }

    public ServerScene ensureDefault(String placeId, String displayName) {
        ServerScene existing = primaryByPlace.get(placeId);
        if (existing != null) {
            return existing;
        }
        scenesRevision++;
        ServerScene scene = createScene(placeId, displayName);
        registerInstance(placeId, scene, true);
        return scene;
    }

    public ServerScene create(String placeId, String displayName) {
        if (placeId == null || placeId.isBlank()) {
            throw new IllegalArgumentException("placeId empty");
        }
        ServerScene existing = primaryByPlace.get(placeId);
        if (existing != null) {
            return existing;
        }
        scenesRevision++;
        ServerScene scene = createScene(placeId, displayName);
        registerInstance(placeId, scene, true);
        return scene;
    }

    public ServerScene spawnInstance(String placeId, String displayName) {
        if (placeId == null || placeId.isBlank()) {
            throw new IllegalArgumentException("placeId empty");
        }
        scenesRevision++;
        ServerScene scene = createScene(placeId, displayName);
        registerInstance(placeId, scene, false);
        return scene;
    }

    public ServerScene delete(String placeId) {
        if (placeId == null || placeId.isBlank()) {
            return null;
        }
        ServerScene removed = primaryByPlace.remove(placeId);
        if (removed != null) {
            unregisterInstance(placeId, removed);
            scenesRevision++;
        }
        return removed;
    }

    public boolean removeInstance(ServerScene scene) {
        if (scene == null) {
            return false;
        }
        boolean wasPrimary = primaryByPlace.get(scene.placeId()) == scene;
        if (wasPrimary) {
            primaryByPlace.remove(scene.placeId());
        }
        boolean removed = unregisterInstance(scene.placeId(), scene);
        if (removed) {
            if (wasPrimary) {
                List<ServerScene> live = instancesByPlace.get(scene.placeId());
                if (live != null) {
                    for (ServerScene candidate : live) {
                        if (candidate != null && !candidate.isDisposed() && !candidate.isPrivate()) {
                            primaryByPlace.put(scene.placeId(), candidate);
                            break;
                        }
                    }
                }
            }
            scenesRevision++;
            scene.dispose();
            for (Consumer<ServerScene> listener : instanceRemovedListeners) {
                try {
                    listener.accept(scene);
                } catch (Throwable ignored) {
                }
            }
            try {
                instanceManager.unregisterInstance(scene.instance());
            } catch (Throwable ignored) {
            }
        }
        return removed;
    }

    public void addInstanceRemovedListener(Consumer<ServerScene> listener) {
        if (listener != null) {
            instanceRemovedListeners.add(listener);
        }
    }

    private void registerInstance(String placeId, ServerScene scene, boolean primary) {
        byInstanceId.put(scene.instanceId(), scene);
        instancesByPlace.computeIfAbsent(placeId, k -> new CopyOnWriteArrayList<>()).add(scene);
        if (primary) {
            primaryByPlace.put(placeId, scene);
        }
    }

    private boolean unregisterInstance(String placeId, ServerScene scene) {
        byInstanceId.remove(scene.instanceId(), scene);
        List<ServerScene> live = instancesByPlace.get(placeId);
        if (live == null) {
            return false;
        }
        boolean removed = live.remove(scene);
        if (live.isEmpty()) {
            instancesByPlace.remove(placeId, live);
        }
        return removed;
    }

    private ServerScene createScene(String placeId, String displayName) {
        InstanceContainer instance = instanceManager.createInstanceContainer();
        instance.setChunkSupplier(LightingChunk::new);
        instance.setGenerator(unit -> {
        });
        instance.setChunkLoader(new AnvilLoader("world_" + placeId));
        return new ServerScene(placeId, displayName, instance, collisionBakeService);
    }

    public List<SceneInfo> snapshotInfo() {
        ArrayList<SceneInfo> out = new ArrayList<>(primaryByPlace.size());
        for (ServerScene scene : primaryByPlace.values()) {
            if (scene == null) {
                continue;
            }
            out.add(new SceneInfo(scene.sceneId(), scene.displayName()));
        }
        out.sort(Comparator.comparing(SceneInfo::uiLabel).thenComparing(SceneInfo::sceneId));
        return List.copyOf(out);
    }

    public void tickAll(double dtSeconds) {
        tickAllPlay(dtSeconds);
    }

    public void tickAllPlay(double dtSeconds) {
        runInParallel(scene -> scene.tickPlay(dtSeconds));
    }

    public void tickAllEditor() {
        runInParallel(ServerScene::tickEditor);
    }

    private void runInParallel(Consumer<ServerScene> work) {
        ArrayList<ServerScene> targets = new ArrayList<>(byInstanceId.size());
        for (ServerScene scene : byInstanceId.values()) {
            if (scene != null && !scene.isDisposed()) {
                targets.add(scene);
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        if (targets.size() == 1) {
            work.accept(targets.get(0));
            return;
        }
        ArrayList<Future<?>> futures = new ArrayList<>(targets.size());
        for (ServerScene scene : targets) {
            futures.add(tickExecutor.submit(() -> work.accept(scene)));
        }
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                throw cause instanceof RuntimeException re ? re : new RuntimeException(cause);
            }
        }
    }

    public List<ServerScene> allScenes() {
        return List.copyOf(primaryByPlace.values());
    }

    public List<ServerScene> allLiveInstances() {
        return List.copyOf(byInstanceId.values());
    }
}
