package com.moud.plugin.api.entity;

import com.moud.api.math.Vector3;
import com.moud.plugin.api.PluginContext;
import com.moud.plugin.api.network.ClientMessageBuilder;
import com.moud.plugin.api.player.PlayerContext;
import com.moud.plugin.api.ui.PlayerOverlay;

/**
 * Player helpers.
 */
public interface Player {
    /**
     * In-game username.
     */
    String name();
    /**
     * Current world position.
     */
    Vector3 position();
    /**
     * Send a chat message to the player.
     */
    Player sendMessage(String message);
    /**
     * Show a toast notification. (doesn't work proprely)
     */
    Player toast(String title, String body);
    /**
     * Teleport the player to the provided position.
     */
    Player teleport(Vector3 position);
    /**
     * Build a client event payload to send to this player.
     */
    ClientMessageBuilder send(String eventName);
    /**
     * Server-driven overlay UI controls for this player.
     */
    PlayerOverlay uiOverlay();

    static Player wrap(PluginContext context, PlayerContext playerContext) {
        return new PlayerImpl(context, playerContext);
    }
}
