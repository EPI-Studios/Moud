package com.moud.client.fabric.player;

import com.moud.net.protocol.PlayerClientState;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RemotePlayerStateCache {
    private static final Map<String, Map<String, String>> stateByPlayer = new ConcurrentHashMap<>();

    private RemotePlayerStateCache() {
    }

    public static void apply(PlayerClientState state) {
        if (state == null || state.playerUuid() == null || state.playerUuid().isBlank()
                || state.key() == null || state.key().isBlank()) {
            return;
        }
        Map<String, String> playerState = stateByPlayer.computeIfAbsent(state.playerUuid(), ignored -> new ConcurrentHashMap<>());
        String value = state.value() == null ? "" : state.value();
        if (value.isEmpty()) {
            playerState.remove(state.key());
            if (playerState.isEmpty()) {
                stateByPlayer.remove(state.playerUuid());
            }
            return;
        }
        playerState.put(state.key(), value);
    }

    public static String get(String playerUuid, String key) {
        if (playerUuid == null || key == null) {
            return "";
        }
        Map<String, String> playerState = stateByPlayer.get(playerUuid);
        return playerState == null ? "" : playerState.getOrDefault(key, "");
    }

    /**
     * Returns an unmodifiable snapshot of all state keys currently set for the given player UUID.
     * Callers may filter by prefix to discover dynamic state (e.g. keys starting with "anim.").
     */
    public static java.util.Set<String> getPlayerKeys(String playerUuid) {
        if (playerUuid == null) {
            return java.util.Set.of();
        }
        Map<String, String> playerState = stateByPlayer.get(playerUuid);
        if (playerState == null) {
            return java.util.Set.of();
        }
        return Collections.unmodifiableSet(playerState.keySet());
    }

    public static void clear() {
        stateByPlayer.clear();
    }
}
