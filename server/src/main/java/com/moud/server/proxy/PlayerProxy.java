package com.moud.server.proxy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moud.api.math.Vector3; // Assuming you will create this math class in the 'api' module
import com.moud.server.network.ServerNetworkManager;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerProxy {
    private final Player player;
    private final ClientProxy client;

    public PlayerProxy(Player player) {
        this.player = player;
        this.client = new ClientProxy(player);
    }

    /**
     * Gets the player's username.
     * @return The username of the player.
     */
    public String getName() {
        return player.getUsername();
    }

    /**
     * Gets the player's unique identifier (UUID).
     * @return The UUID of the player as a string.
     */
    public String getUuid() {
        return player.getUuid().toString();
    }

    /**
     * Sends a chat message to this player.
     * @param message The message to send.
     */
    public void sendMessage(String message) {
        player.sendMessage(message);
    }

    /**
     * Kicks this player from the server.
     * @param reason The reason for being kicked, displayed to the player.
     */
    public void kick(String reason) {
        player.kick(reason);
    }

    /**
     * Checks if the player is currently online.
     * @return true if the player is online, false otherwise.
     */
    public boolean isOnline() {
        return player.isOnline();
    }

    /**
     * Gets the client proxy for this player, used for sending client-specific events.
     * @return The ClientProxy instance for this player.
     */
    public ClientProxy getClient() {
        return client;
    }

    /**
     * Gets the player's current position in the world.
     * @return A Vector3 object containing the x, y, z coordinates.
     */
    public Vector3 getPosition() {
        Pos pos = player.getPosition();
        return new Vector3((float)pos.x(), (float)pos.y(), (float)pos.z());
    }

    /**
     * Gets the player's normalized look direction vector.
     * @return A Vector3 object representing the direction.
     */
    public Vector3 getDirection() {
        Vec dir = player.getPosition().direction();
        return new Vector3((float)dir.x(), (float)dir.y(), (float)dir.z());
    }

    /**
     * Teleports the player to a new position in their current instance.
     * @param x The target X coordinate.
     * @param y The target Y coordinate.
     * @param z The target Z coordinate.
     */
    public void teleport(double x, double y, double z) {
        player.teleport(new Pos(x, y, z));
    }

}