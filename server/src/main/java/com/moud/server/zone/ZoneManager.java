package com.moud.server.zone;

import com.moud.api.math.Vector3;
import com.moud.server.MoudEngine;
import com.moud.server.profiler.model.ScriptExecutionMetadata;
import com.moud.server.profiler.model.ScriptExecutionType;
import com.moud.server.proxy.PlayerProxy;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ZoneManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZoneManager.class);
    private static final int GRID_CELL_SIZE = 16;

    private final MoudEngine engine;
    private final Map<String, Zone> zonesById = new ConcurrentHashMap<>();
    private final Map<Long, Set<Zone>> spatialGrid = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Zone>> playerActiveZones = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerGridCells = new ConcurrentHashMap<>();

    public ZoneManager(MoudEngine engine) {
        this.engine = engine;
        MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent.class, this::onPlayerSpawn);
        MinecraftServer.getGlobalEventHandler().addListener(PlayerMoveEvent.class, this::onPlayerMove);
        MinecraftServer.getGlobalEventHandler().addListener(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
    }

    public void createZone(String id, Vector3 corner1, Vector3 corner2, Value onEnter, Value onLeave) {
        if (zonesById.containsKey(id)) {
            LOGGER.warn("Zone with id '{}' already exists. It will be replaced.", id);
            removeZone(id);
        }

        Zone zone = new Zone(id, corner1, corner2, onEnter, onLeave);
        zonesById.put(id, zone);

        int minCellX = (int) Math.floor(zone.getMin().x / GRID_CELL_SIZE);
        int maxCellX = (int) Math.floor(zone.getMax().x / GRID_CELL_SIZE);
        int minCellZ = (int) Math.floor(zone.getMin().z / GRID_CELL_SIZE);
        int maxCellZ = (int) Math.floor(zone.getMax().z / GRID_CELL_SIZE);

        for (int x = minCellX; x <= maxCellX; x++) {
            for (int z = minCellZ; z <= maxCellZ; z++) {
                long cellHash = getCellHash(x, z);
                spatialGrid.computeIfAbsent(cellHash, k -> ConcurrentHashMap.newKeySet()).add(zone);
            }
        }

        LOGGER.info("Created zone '{}' in spatial grid cells ({},{}) to ({},{})", id, minCellX, minCellZ, maxCellX, maxCellZ);
    }

    public void removeZone(String id) {
        Zone zone = zonesById.remove(id);
        if (zone != null) {
            spatialGrid.values().forEach(set -> set.remove(zone));
            playerActiveZones.values().forEach(set -> set.remove(zone));
            LOGGER.info("Removed zone '{}'", id);
        }
    }

    private void onPlayerSpawn(PlayerSpawnEvent event) {
        if (!event.isFirstSpawn()) return;

        Player player = event.getPlayer();
        playerActiveZones.put(player.getUuid(), ConcurrentHashMap.newKeySet());

        MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
            updatePlayerZones(player);
        });
    }

    private void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        int newCellX = (int) Math.floor(event.getNewPosition().x() / GRID_CELL_SIZE);
        int newCellZ = (int) Math.floor(event.getNewPosition().z() / GRID_CELL_SIZE);
        long newCellHash = getCellHash(newCellX, newCellZ);
        playerGridCells.put(player.getUuid(), newCellHash);
        updatePlayerZones(player);
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        Player player = event.getPlayer();
        Set<Zone> activeZones = playerActiveZones.remove(player.getUuid());
        playerGridCells.remove(player.getUuid());

        if (activeZones != null) {
            for (Zone zone : activeZones) {
                triggerLeave(zone, player);
            }
        }
    }

    private void updatePlayerZones(Player player) {
        int cellX = (int) Math.floor(player.getPosition().x() / GRID_CELL_SIZE);
        int cellZ = (int) Math.floor(player.getPosition().z() / GRID_CELL_SIZE);

        Set<Zone> nearbyZones = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long cellHash = getCellHash(cellX + dx, cellZ + dz);
                Set<Zone> zonesInCell = spatialGrid.get(cellHash);
                if (zonesInCell != null) {
                    nearbyZones.addAll(zonesInCell);
                }
            }
        }

        Set<Zone> activeZones = playerActiveZones.get(player.getUuid());
        if (activeZones == null) {
            activeZones = ConcurrentHashMap.newKeySet();
            playerActiveZones.put(player.getUuid(), activeZones);
        }

        Set<Zone> zonesToRemove = new HashSet<>(activeZones);

        for (Zone zone : nearbyZones) {
            boolean isInside = zone.contains(player);
            boolean wasInside = zone.isPlayerInside(player);

            if (isInside && !wasInside) {
                triggerEnter(zone, player);
                activeZones.add(zone);
            } else if (!isInside && wasInside) {
                triggerLeave(zone, player);
                activeZones.remove(zone);
            }

            zonesToRemove.remove(zone);
        }

        for (Zone zone : zonesToRemove) {
            triggerLeave(zone, player);
            activeZones.remove(zone);
        }
    }

    private void triggerEnter(Zone zone, Player player) {
        if (zone.isPlayerInside(player)) {
            return;
        }
        zone.addPlayer(player);
        Value callback = zone.getOnEnterCallback();
        if (callback != null && callback.canExecute()) {
            ScriptExecutionMetadata metadata = ScriptExecutionMetadata.of(
                    ScriptExecutionType.EVENT,
                    "zone.enter",
                    zone.getId()
            );
            engine.getRuntime().executeCallback(callback, metadata, new PlayerProxy(player), zone.getId());
        }
    }

    private void triggerLeave(Zone zone, Player player) {
        if (!zone.isPlayerInside(player)) {
            return;
        }
        zone.removePlayer(player);
        Value callback = zone.getOnLeaveCallback();
        if (callback != null && callback.canExecute()) {
            ScriptExecutionMetadata metadata = ScriptExecutionMetadata.of(
                    ScriptExecutionType.EVENT,
                    "zone.leave",
                    zone.getId()
            );
            engine.getRuntime().executeCallback(callback, metadata, new PlayerProxy(player), zone.getId());
        }
    }

    private long getCellHash(int cellX, int cellZ) {
        return (long) cellX << 32 | (long) cellZ & 0xFFFFFFFFL;
    }
}
