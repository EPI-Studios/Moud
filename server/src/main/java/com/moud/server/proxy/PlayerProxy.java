package com.moud.server.proxy;

import com.moud.api.math.Vector3;
import com.moud.server.api.exception.APIException;
import com.moud.server.api.validation.APIValidator;
import com.moud.server.logging.MoudLogger;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;

public class PlayerProxy {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(PlayerProxy.class);
    private final Player player;
    private final ClientProxy client;
    private final APIValidator validator;

    public PlayerProxy(Player player) {
        if (player == null) {
            throw new APIException("INVALID_PLAYER", "Player cannot be null");
        }

        this.player = player;
        this.client = new ClientProxy(player);
        this.validator = new APIValidator();

        LOGGER.debug("PlayerProxy created for: {}", player.getUsername());
    }

    public String getName() {
        try {
            return player.getUsername();
        } catch (Exception e) {
            LOGGER.error("Failed to get player name", e);
            throw new APIException("PLAYER_ACCESS_FAILED", "Failed to get player name", e);
        }
    }

    public String getUuid() {
        try {
            return player.getUuid().toString();
        } catch (Exception e) {
            LOGGER.error("Failed to get player UUID", e);
            throw new APIException("PLAYER_ACCESS_FAILED", "Failed to get player UUID", e);
        }
    }

    public void sendMessage(String message) {
        try {
            validator.validateString(message, "message");

            if (message.trim().isEmpty()) {
                LOGGER.warn("Attempted to send empty message to player: {}", getName());
                return;
            }

            player.sendMessage(message);
            LOGGER.debug("Message sent to {}: '{}'", getName(),
                    message.length() > 50 ? message.substring(0, 50) + "..." : message);

        } catch (APIException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to send message to player: {}", getName(), e);
            throw new APIException("MESSAGE_SEND_FAILED", "Failed to send message to player", e);
        }
    }

    public void kick(String reason) {
        try {
            if (reason == null) {
                reason = "Kicked from server";
            }

            validator.validateString(reason, "reason");

            player.kick(reason);
            LOGGER.api("Player {} kicked with reason: {}", getName(), reason);

        } catch (APIException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to kick player: {}", getName(), e);
            throw new APIException("KICK_FAILED", "Failed to kick player", e);
        }
    }

    public boolean isOnline() {
        try {
            return player.isOnline();
        } catch (Exception e) {
            LOGGER.error("Failed to check if player is online: {}", getName(), e);
            return false;
        }
    }

    public ClientProxy getClient() {
        return client;
    }

    public Vector3 getPosition() {
        try {
            Pos pos = player.getPosition();
            return new Vector3((float)pos.x(), (float)pos.y(), (float)pos.z());
        } catch (Exception e) {
            LOGGER.error("Failed to get position for player: {}", getName(), e);
            throw new APIException("POSITION_ACCESS_FAILED", "Failed to get player position", e);
        }
    }

    public Vector3 getDirection() {
        try {
            Vec dir = player.getPosition().direction();
            return new Vector3((float)dir.x(), (float)dir.y(), (float)dir.z());
        } catch (Exception e) {
            LOGGER.error("Failed to get direction for player: {}", getName(), e);
            throw new APIException("DIRECTION_ACCESS_FAILED", "Failed to get player direction", e);
        }
    }

    public void teleport(double x, double y, double z) {
        try {
            validator.validateCoordinates(x, y, z);

            Pos targetPos = new Pos(x, y, z);
            player.teleport(targetPos);

            LOGGER.api("Player {} teleported to: {}, {}, {}", getName(), x, y, z);

        } catch (APIException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to teleport player {} to {}, {}, {}", getName(), x, y, z, e);
            throw new APIException("TELEPORT_FAILED", "Failed to teleport player", e);
        }
    }

    public void setHealth(float health) {
        try {
            if (health < 0 || health > 20) {
                throw new APIException("INVALID_HEALTH", "Health must be between 0 and 20");
            }

            player.setHealth(health);
            LOGGER.debug("Player {} health set to: {}", getName(), health);

        } catch (APIException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to set health for player: {}", getName(), e);
            throw new APIException("HEALTH_SET_FAILED", "Failed to set player health", e);
        }
    }

    public float getHealth() {
        try {
            return player.getHealth();
        } catch (Exception e) {
            LOGGER.error("Failed to get health for player: {}", getName(), e);
            throw new APIException("HEALTH_ACCESS_FAILED", "Failed to get player health", e);
        }
    }

    public void setGameMode(String gameMode) {
        try {
            if (gameMode == null || gameMode.trim().isEmpty()) {
                throw new APIException("INVALID_GAMEMODE", "Game mode cannot be null or empty");
            }

            net.minestom.server.entity.GameMode mode = switch (gameMode.toLowerCase()) {
                case "survival", "s" -> net.minestom.server.entity.GameMode.SURVIVAL;
                case "creative", "c" -> net.minestom.server.entity.GameMode.CREATIVE;
                case "adventure", "a" -> net.minestom.server.entity.GameMode.ADVENTURE;
                case "spectator", "sp" -> net.minestom.server.entity.GameMode.SPECTATOR;
                default -> throw new APIException("INVALID_GAMEMODE", "Unknown game mode: " + gameMode);
            };

            player.setGameMode(mode);
            LOGGER.api("Player {} game mode set to: {}", getName(), mode);

        } catch (APIException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to set game mode for player: {}", getName(), e);
            throw new APIException("GAMEMODE_SET_FAILED", "Failed to set player game mode", e);
        }
    }

    public String getGameMode() {
        try {
            return player.getGameMode().name().toLowerCase();
        } catch (Exception e) {
            LOGGER.error("Failed to get game mode for player: {}", getName(), e);
            throw new APIException("GAMEMODE_ACCESS_FAILED", "Failed to get player game mode", e);
        }
    }

    public boolean hasPermission(String permission) {
        try {
            validator.validateString(permission, "permission");

            // for now, return true for all permissions
            // TODO : PERIMISSIONS SYSTEM
            LOGGER.debug("Permission check for {}: {} (always true for now)", getName(), permission);
            return true;

        } catch (APIException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to check permission for player: {}", getName(), e);
            return false;
        }
    }

    @Override
    public String toString() {
        return String.format("PlayerProxy{name='%s', uuid='%s'}", getName(), getUuid());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PlayerProxy that = (PlayerProxy) obj;
        return player.getUuid().equals(that.player.getUuid());
    }

    @Override
    public int hashCode() {
        return player.getUuid().hashCode();
    }
}