package com.moud.server.minestom.engine;

import com.moud.server.minestom.runtime.PlayRuntime;
import com.moud.server.minestom.util.DebugLog;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

public final class InstanceMatchmaker {

    private final ServerScenes scenes;
    private final MatchmakerConfig config;
    private final Map<String, ReentrantLock> placeLocks = new ConcurrentHashMap<>();
    private final Map<String, Long> emptySinceMillis = new ConcurrentHashMap<>();
    private final Map<String, String> reservationTokens = new ConcurrentHashMap<>();
    private Task housekeepingTask;
    private TeleportArrivalHandler arrivalHandler;

    public InstanceMatchmaker(ServerScenes scenes, MatchmakerConfig config) {
        this.scenes = Objects.requireNonNull(scenes, "scenes");
        this.config = Objects.requireNonNull(config, "config");
        scenes.addInstanceRemovedListener(scene -> {
            if (scene == null) return;
            emptySinceMillis.remove(scene.instanceId());
            reservationTokens.remove(scene.instanceId());
        });
    }

    public MatchmakerConfig config() {
        return config;
    }

    public ServerScene joinOrCreate(JoinContext ctx, String displayName) {
        Objects.requireNonNull(ctx, "ctx");
        if (ctx.hasReservation()) {
            ServerScene reserved = scenes.getByInstanceId(ctx.reservedInstanceId());
            if (reserved == null || reserved.isDisposed()) {
                return null;
            }
            String expected = reservationTokens.get(reserved.instanceId());
            if (expected != null && !expected.equals(ctx.reservationToken())) {
                return null;
            }
            if (reserved.playerCount() >= config.playersPerInstance()) {
                return null;
            }
            return reserved;
        }

        String placeId = ctx.placeId();
        ReentrantLock lock = placeLocks.computeIfAbsent(placeId, k -> new ReentrantLock());
        lock.lock();
        try {
            for (ServerScene scene : scenes.instancesOfPlace(placeId)) {
                if (scene == null || scene.isDisposed() || scene.isPrivate()) continue;
                if (scene.playerCount() < config.playersPerInstance()) {
                    return scene;
                }
            }
            if (scenes.allLiveInstances().size() >= config.maxInstances()) {
                DebugLog.warn("matchmaker", "rejecting join for place=" + placeId + ", max instances reached");
                return null;
            }
            return spawnSeeded(placeId, displayName == null ? placeId : displayName);
        } finally {
            lock.unlock();
        }
    }

    private ServerScene spawnSeeded(String placeId, String displayName) {
        ServerScene primary = scenes.get(placeId);
        ServerScene fresh = scenes.spawnInstance(placeId, displayName);
        if (primary != null && primary != fresh && !primary.isDisposed()) {
            InstanceSeeder.seedFromSource(primary, fresh);
        }
        return fresh;
    }

    public PrivateInstanceTicket createPrivateInstance(String placeId, String displayName) {
        if (placeId == null || placeId.isBlank()) {
            throw new IllegalArgumentException("placeId blank");
        }
        ReentrantLock lock = placeLocks.computeIfAbsent(placeId, k -> new ReentrantLock());
        lock.lock();
        try {
            if (scenes.allLiveInstances().size() >= config.maxInstances()) {
                return null;
            }
            ServerScene scene = spawnSeeded(placeId, displayName == null ? placeId : displayName);
            scene.setPrivate(true);
            String token = UUID.randomUUID().toString();
            reservationTokens.put(scene.instanceId(), token);
            return new PrivateInstanceTicket(scene.instanceId(), token);
        } finally {
            lock.unlock();
        }
    }

    public void start() {
        if (housekeepingTask != null) return;
        housekeepingTask = MinecraftServer.getSchedulerManager()
                .buildTask(this::housekeep)
                .repeat(TaskSchedule.duration(Duration.ofMillis(config.housekeepingIntervalMillis())))
                .schedule();
    }

    public void stop() {
        if (housekeepingTask != null) {
            housekeepingTask.cancel();
            housekeepingTask = null;
        }
    }

    public void housekeep() {
        long now = System.currentTimeMillis();
        Map<ServerScene, Boolean> snapshot = new HashMap<>();
        for (ServerScene scene : scenes.allLiveInstances()) {
            if (scene == null || scene.isDisposed()) continue;
            snapshot.put(scene, scene.playerCount() == 0);
        }
        for (Map.Entry<ServerScene, Boolean> entry : snapshot.entrySet()) {
            ServerScene scene = entry.getKey();
            if (entry.getValue()) {
                Long since = emptySinceMillis.putIfAbsent(scene.instanceId(), now);
                long elapsed = now - (since == null ? now : since);
                if (elapsed >= config.emptyShutdownGraceMillis() && shouldRetire(scene)) {
                    scenes.removeInstance(scene);
                }
            } else {
                emptySinceMillis.remove(scene.instanceId());
            }
        }
    }

    public void setArrivalHandler(TeleportArrivalHandler handler) {
        this.arrivalHandler = handler;
    }

    public CompletableFuture<ServerScene> teleport(Player player, String placeId, byte[] payload) {
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }
        ServerScene chosen = joinOrCreate(JoinContext.forInstanceWithPayload(placeId, null, null, payload), placeId);
        return moveTo(player, chosen, payload);
    }

    public CompletableFuture<ServerScene> teleportToInstance(Player player, String instanceId, String reservationToken, byte[] payload) {
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }
        ServerScene target = scenes.getByInstanceId(instanceId);
        if (target == null || target.isDisposed()) {
            return CompletableFuture.completedFuture(null);
        }
        ServerScene chosen = joinOrCreate(
                JoinContext.forInstanceWithPayload(target.placeId(), instanceId, reservationToken, payload),
                target.placeId());
        return moveTo(player, chosen, payload);
    }

    private CompletableFuture<ServerScene> moveTo(Player player, ServerScene target, byte[] payload) {
        if (target == null) {
            return CompletableFuture.completedFuture(null);
        }
        Pos startPos = PlayRuntime.findPlayerStartPos(target);
        Pos targetPos = startPos != null ? startPos : new Pos(0, 64, 0);
        return player.setInstance(target.instance(), targetPos).thenApply(unused -> {
            TeleportArrivalHandler h = arrivalHandler;
            if (h != null) {
                try {
                    h.onArrive(player, target, payload);
                } catch (Throwable t) {
                    DebugLog.error("matchmaker", "arrival handler failed: " + t.getMessage());
                }
            }
            return target;
        });
    }

    private boolean shouldRetire(ServerScene scene) {
        if (scene.isPrivate()) {
            return true;
        }
        if (scenes.get(scene.placeId()) == scene) {
            return false;
        }
        List<ServerScene> live = scenes.instancesOfPlace(scene.placeId());
        long publicLive = live.stream().filter(s -> s != null && !s.isPrivate() && !s.isDisposed()).count();
        return publicLive > 1;
    }
}
