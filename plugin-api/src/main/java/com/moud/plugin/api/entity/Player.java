package com.moud.plugin.api.entity;

import com.moud.api.math.Vector3;
import com.moud.plugin.api.PluginContext;
import com.moud.plugin.api.network.ClientMessageBuilder;
import com.moud.plugin.api.player.PlayerContext;
import com.moud.plugin.api.ui.PlayerOverlay;
import net.minestom.server.entity.GameMode;
import net.minestom.server.instance.Instance;

import java.util.UUID;

/**
 * Player helpers.
 */
public interface Player {
    /**
     * In-game username.
     * @return the player's name.
     */
    String name();

    /**
     * Current world position.
     * @return the player's position.
     */
    Vector3 position();
    /**
     * Current instance.
     *
     * @return the player's instance.
     */

    Instance instance();

    /**
     * Send a chat message to the player.
     *
     * @param message the message to send.
     * @return this player instance for chaining.
     */
    Player sendMessage(String message);

    /**
     * Show a toast notification. (doesn't work proprely)
     *
     * @param title the toast title.
     * @param body  the toast body.
     */
    Player toast(String title, String body);

    /**
     * Teleport the player to the provided position.
     *
     * @param position the target position.
     * @return this player instance for chaining.
     */
    Player teleport(Vector3 position);

    /**
     * Teleport the player to the provided position in the specified instance.
     *
     * @param position   the target position.
     * @param instanceId the target instance UUID.
     * @return this player instance for chaining.
     */
    Player teleportInstance(Vector3 position, UUID instanceId);

    /**
     * Build a client event payload to send to this player.
     *
     * @param eventName the event name.
     * @return a client message builder.
     */
    ClientMessageBuilder send(String eventName);

    /**
     * Server-driven overlay UI controls for this player.
     *
     * @return the player's UI overlay.
     */
    PlayerOverlay uiOverlay();


    /**
     * Unique player identifier.
     *
     * @return the player's UUID.
     */
    UUID uuid();

    /**
     * Underlying player context.
     *
     * @return the player context.
     */
    PlayerContext context();

    /**
     * Current game mode.
     *
     * @return the player's game mode.
     */
    GameMode gameMode();

    /**
     * Set the player's game mode.
     *
     * @param gameMode the new game mode.
     * @return this player instance for chaining.
     */
    Player setGameMode(GameMode gameMode);

    static Player wrap(PluginContext context, PlayerContext playerContext) {
        return new PlayerImpl(context, playerContext);
    }
}
