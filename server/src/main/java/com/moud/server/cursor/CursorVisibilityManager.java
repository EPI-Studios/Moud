package com.moud.server.cursor;

import net.minestom.server.entity.Player;
import net.minestom.server.MinecraftServer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CursorVisibilityManager {
    private static CursorVisibilityManager instance;

    private final Map<UUID, Set<UUID>> cursorViewers = new ConcurrentHashMap<>();

    private CursorVisibilityManager() {}

    public static synchronized CursorVisibilityManager getInstance() {
        if (instance == null) {
            instance = new CursorVisibilityManager();
        }
        return instance;
    }

    public void setVisibleTo(Player cursorOwner, List<Player> viewers) {
        Set<UUID> viewerIds = new HashSet<>();
        for (Player viewer : viewers) {
            if (viewer != null) {
                viewerIds.add(viewer.getUuid());
            }
        }
        cursorViewers.put(cursorOwner.getUuid(), viewerIds);
    }

    public void setVisibleToAll(Player cursorOwner) {
        Set<UUID> allPlayers = new HashSet<>();
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            allPlayers.add(player.getUuid());
        }
        cursorViewers.put(cursorOwner.getUuid(), allPlayers);
    }

    public void setVisibleToNone(Player cursorOwner) {
        cursorViewers.put(cursorOwner.getUuid(), new HashSet<>());
    }

    public void addViewer(Player cursorOwner, Player viewer) {
        cursorViewers.computeIfAbsent(cursorOwner.getUuid(), k -> new HashSet<>())
                .add(viewer.getUuid());
    }

    public void removeViewer(Player cursorOwner, Player viewer) {
        Set<UUID> viewers = cursorViewers.get(cursorOwner.getUuid());
        if (viewers != null) {
            viewers.remove(viewer.getUuid());
        }
    }

    public boolean canSee(Player cursorOwner, Player viewer) {
        Set<UUID> viewers = cursorViewers.get(cursorOwner.getUuid());
        return viewers != null && viewers.contains(viewer.getUuid());
    }

    public Set<Player> getViewers(Player cursorOwner) {
        Set<UUID> viewerIds = cursorViewers.get(cursorOwner.getUuid());
        if (viewerIds == null) {
            return Collections.emptySet();
        }

        Set<Player> viewers = new HashSet<>();
        for (UUID viewerId : viewerIds) {
            Player viewer = MinecraftServer.getConnectionManager()
                    .getOnlinePlayers()
                    .stream()
                    .filter(p -> p.getUuid().equals(viewerId))
                    .findFirst()
                    .orElse(null);
            if (viewer != null) {
                viewers.add(viewer);
            }
        }
        return viewers;
    }

    public void removePlayer(Player player) {
        cursorViewers.remove(player.getUuid());

        for (Set<UUID> viewers : cursorViewers.values()) {
            viewers.remove(player.getUuid());
        }
    }
}