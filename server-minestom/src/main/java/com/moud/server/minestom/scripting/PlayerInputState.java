package com.moud.server.minestom.scripting;

import com.moud.net.protocol.PlayerInput;

record PlayerInputState(
        String playerUuid,
        long clientTick,
        float moveX,
        float moveZ,
        float yawDeg,
        float pitchDeg,
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
                input.jump(),
                input.sprint()
        );
    }
}
