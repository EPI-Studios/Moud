package com.moud.server.state;

import com.moud.api.math.Vector3;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerState {

    private final UUID playerId;
    private final Map<String, BodyPart> parts = new HashMap<>();

    public PlayerState(UUID playerId) {
        this.playerId = playerId;
        initializeDefaultParts();
    }

    private void initializeDefaultParts() {
        parts.put("head", new BodyPart("head"));
        parts.put("body", new BodyPart("body"));
        parts.put("right_arm", new BodyPart("right_arm"));
        parts.put("left_arm", new BodyPart("left_arm"));
        parts.put("right_leg", new BodyPart("right_leg"));
        parts.put("left_leg", new BodyPart("left_leg"));
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public BodyPart getPart(String name) {
        return parts.get(name);
    }

    public Map<String, BodyPart> getParts() {
        return parts;
    }
}
