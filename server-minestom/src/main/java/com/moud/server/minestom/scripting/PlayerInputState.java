package com.moud.server.minestom.scripting;


import com.moud.net.protocol.PlayerInput;

record PlayerInputState(
        String playerUuid,
        long clientTick,
        float moveX,
        float moveZ,
        float yawDeg,
        float pitchDeg,
        float cursorX,
        float cursorY,
        boolean jump,
        boolean sprint
) {
    PlayerInputState(String playerUuid, PlayerInput input) {
        this(
                playerUuid,
                input.clientTick(),
                input.moveX(),
                input.moveZ(),
                input.yawDeg(),
                input.pitchDeg(),
                input.cursorX(),
                input.cursorY(),
                input.jump(),
                input.sprint()
        );
    }
}
