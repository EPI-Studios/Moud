package com.moud.server.movement;

import com.moud.server.MoudEngine;
import com.moud.server.events.EventDispatcher;
import net.minestom.server.entity.Player;
import com.moud.network.MoudPackets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServerMovementHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerMovementHandler.class);
    private static ServerMovementHandler instance;

    private final Map<UUID, PlayerMovementState> playerStates = new ConcurrentHashMap<>();

    private ServerMovementHandler() {}

    public static ServerMovementHandler getInstance() {
        if (instance == null) {
            instance = new ServerMovementHandler();
        }
        return instance;
    }

    public void handleMovementState(Player player, MoudPackets.MovementStatePacket packet) {
        UUID playerId = player.getUuid();
        PlayerMovementState oldState = playerStates.get(playerId);

        PlayerMovementState newState = new PlayerMovementState(
                packet.forward(), packet.backward(), packet.left(), packet.right(),
                packet.jumping(), packet.sneaking(), packet.sprinting(),
                packet.onGround(), packet.speed()
        );

        playerStates.put(playerId, newState);

        if (oldState == null) return;

        if (oldState.jumping() != newState.jumping() && newState.jumping()) {
            triggerMovementEvent(player, "jump");
        }
        if (oldState.sneaking() != newState.sneaking()) {
            triggerMovementEvent(player, newState.sneaking() ? "sneak_start" : "sneak_stop");
        }
        if (oldState.sprinting() != newState.sprinting()) {
            triggerMovementEvent(player, newState.sprinting() ? "sprint_start" : "sprint_stop");
        }
        if (oldState.onGround() != newState.onGround()) {
            triggerMovementEvent(player, newState.onGround() ? "land" : "airborne");
        }

        boolean wasMoving = oldState.forward() || oldState.backward() || oldState.left() || oldState.right();
        boolean isMoving = newState.forward() || newState.backward() || newState.left() || newState.right();

        if (!wasMoving && isMoving) {
            triggerMovementEvent(player, "movement_start");
        } else if (wasMoving && !isMoving) {
            triggerMovementEvent(player, "movement_stop");
        }
    }

    private void triggerMovementEvent(Player player, String eventType) {
        LOGGER.debug("Player {} triggered movement event: {}", player.getUsername(), eventType);

        EventDispatcher eventDispatcher = MoudEngine.getInstance().getEventDispatcher();
        if (eventDispatcher != null) {
            switch (eventType) {
                case "jump" -> eventDispatcher.dispatchMovementEventType(player, "player.jump");
                case "sneak_start" -> eventDispatcher.dispatchMovementEventType(player, "player.sneak.start");
                case "sneak_stop" -> eventDispatcher.dispatchMovementEventType(player, "player.sneak.stop");
                case "sprint_start" -> eventDispatcher.dispatchMovementEventType(player, "player.sprint.start");
                case "sprint_stop" -> eventDispatcher.dispatchMovementEventType(player, "player.sprint.stop");
                case "movement_start" -> eventDispatcher.dispatchMovementEventType(player, "player.movement.start");
                case "land" -> eventDispatcher.dispatchMovementEventType(player, "player.land");
                case "airborne" -> eventDispatcher.dispatchMovementEventType(player, "player.airborne");
            }
        }
    }

    public PlayerMovementState getPlayerState(Player player) {
        return playerStates.get(player.getUuid());
    }

    public void removePlayer(Player player) {
        playerStates.remove(player.getUuid());
    }

    public record PlayerMovementState(
            boolean forward, boolean backward, boolean left, boolean right,
            boolean jumping, boolean sneaking, boolean sprinting,
            boolean onGround, float speed
    ) {
        public boolean isMoving() {
            return forward || backward || left || right;
        }

        public String getMovementDirection() {
            if (forward && right) return "northeast";
            if (forward && left) return "northwest";
            if (backward && right) return "southeast";
            if (backward && left) return "southwest";
            if (forward) return "north";
            if (backward) return "south";
            if (right) return "east";
            if (left) return "west";
            return "none";
        }

        public String getMovementType() {
            if (sprinting) return "sprinting";
            if (sneaking) return "sneaking";
            if (isMoving()) return "walking";
            return "standing";
        }
    }
}