// File: src/main/java/com/moud/server/zone/ZoneManager.java
package com.moud.server.zone;

import com.moud.api.math.Vector3;
import com.moud.server.MoudEngine;
import com.moud.server.proxy.PlayerProxy;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ZoneManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZoneManager.class);
    private final MoudEngine engine;

    private final Map<Long, Set<Zone>> zonesByChunk = new ConcurrentHashMap<>();
    private final Map<String, Zone> zonesById = new ConcurrentHashMap<>();

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

        int minChunkX = (int) Math.floor(zone.getMin().x / 16.0);
        int maxChunkX = (int) Math.floor(zone.getMax().x / 16.0);
        int minChunkZ = (int) Math.floor(zone.getMin().z / 16.0);
        int maxChunkZ = (int) Math.floor(zone.getMax().z / 16.0);

        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                long chunkHash = getChunkHash(x, z);
                zonesByChunk.computeIfAbsent(chunkHash, k -> new HashSet<>()).add(zone);
            }
        }
        LOGGER.info("Created zone '{}' spanning from chunk ({},{}) to ({},{})", id, minChunkX, minChunkZ, maxChunkX, maxChunkZ);
    }

    public void removeZone(String id) {
        Zone zone = zonesById.remove(id);
        if (zone != null) {
            zonesByChunk.values().forEach(set -> set.remove(zone));
            LOGGER.info("Removed zone '{}'", id);
        }
    }

    private void onPlayerSpawn(PlayerSpawnEvent event) {
        if (!event.isFirstSpawn()) return;

        Player player = event.getPlayer();
        MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
            // --- FIX: Utilisation de getPosition().chunkX() ---
            long chunkHash = getChunkHash(player.getPosition().chunkX(), player.getPosition().chunkZ());
            Collection<Zone> zonesInChunk = zonesByChunk.get(chunkHash);
            if (zonesInChunk != null) {
                checkPlayerAgainstZones(player, zonesInChunk);
            }
        });
    }

    private void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        // --- FIX: L'ancienne position est la position actuelle du joueur AVANT le mouvement. ---
        // La nouvelle position est donnée par l'événement.
        Point from = player.getPosition();
        Point to = event.getNewPosition();

        if (from.chunkX() == to.chunkX() && from.chunkZ() == to.chunkZ()) {
            return;
        }

        long oldChunkHash = getChunkHash(from.chunkX(), from.chunkZ());
        long newChunkHash = getChunkHash(to.chunkX(), to.chunkZ());

        Set<Zone> zonesToCheck = new HashSet<>();
        if (zonesByChunk.containsKey(oldChunkHash)) {
            zonesToCheck.addAll(zonesByChunk.get(oldChunkHash));
        }
        if (zonesByChunk.containsKey(newChunkHash)) {
            zonesToCheck.addAll(zonesByChunk.get(newChunkHash));
        }

        if (zonesToCheck.isEmpty()) return;

        checkPlayerAgainstZones(player, zonesToCheck);
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        Player player = event.getPlayer();
        for (Zone zone : zonesById.values()) {
            if (zone.isPlayerInside(player)) {
                triggerLeave(zone, player);
            }
        }
    }

    private void checkPlayerAgainstZones(Player player, Collection<Zone> zones) {
        for (Zone zone : zones) {
            boolean isInsideNow = zone.contains(player);
            boolean wasInside = zone.isPlayerInside(player);

            if (isInsideNow && !wasInside) {
                triggerEnter(zone, player);
            } else if (!isInsideNow && wasInside) {
                triggerLeave(zone, player);
            }
        }
    }

    private void triggerEnter(Zone zone, Player player) {
        zone.addPlayer(player);
        Value callback = zone.getOnEnterCallback();
        if (callback != null && callback.canExecute()) {
            engine.getRuntime().executeCallback(callback, new PlayerProxy(player), zone.getId());
        }
    }

    private void triggerLeave(Zone zone, Player player) {
        zone.removePlayer(player);
        Value callback = zone.getOnLeaveCallback();
        if (callback != null && callback.canExecute()) {
            engine.getRuntime().executeCallback(callback, new PlayerProxy(player), zone.getId());
        }
    }

    private long getChunkHash(int chunkX, int chunkZ) {
        return (long) chunkX << 32 | (long) chunkZ & 0xFFFFFFFFL;
    }
}