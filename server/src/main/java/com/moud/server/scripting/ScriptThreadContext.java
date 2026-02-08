package com.moud.server.scripting;

import java.util.UUID;


public final class ScriptThreadContext {
    private static final ThreadLocal<UUID> CURRENT_PLAYER_ID = new ThreadLocal<>();
    private static final ThreadLocal<ScriptPlayerContextProvider> CURRENT_PLAYER_CONTEXT = new ThreadLocal<>();

    private ScriptThreadContext() {
    }

    public static void setPlayer(UUID playerId, ScriptPlayerContextProvider contextProvider) {
        CURRENT_PLAYER_ID.set(playerId);
        CURRENT_PLAYER_CONTEXT.set(contextProvider);
    }

    public static UUID getPlayerId() {
        return CURRENT_PLAYER_ID.get();
    }

    public static ScriptPlayerContextProvider getPlayerContext() {
        return CURRENT_PLAYER_CONTEXT.get();
    }

    public static void clear() {
        CURRENT_PLAYER_ID.remove();
        CURRENT_PLAYER_CONTEXT.remove();
    }
}

