package com.moud.server.minestom.scripting.player;

import com.moud.net.protocol.PlayerInput;

public record PlayerInputState(
        String playerUuid,
        long clientTick,
        float moveX,
        float moveZ,
        float yawDeg,
        float pitchDeg,
        float cursorX,
        float cursorY,
        String stateKey,
        String stateValue,
        boolean jump,
        boolean sprint,
        boolean sneak
) {
    public PlayerInputState(String playerUuid, PlayerInput input) {
        this(
                playerUuid,
                input.clientTick(),
                input.moveX(),
                input.moveZ(),
                input.yawDeg(),
                input.pitchDeg(),
                input.cursorX(),
                input.cursorY(),
                input.stateKey(),
                input.stateValue(),
                input.jump(),
                input.sprint(),
                input.sneak()
        );
    }
}
