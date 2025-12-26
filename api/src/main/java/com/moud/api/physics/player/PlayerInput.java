package com.moud.api.physics.player;

public record PlayerInput(
        long sequenceId,
        boolean forward,
        boolean backward,
        boolean left,
        boolean right,
        boolean jump,
        boolean sprint,
        boolean sneak,
        float yaw,
        float pitch
) {
    public boolean hasMovementInput() {
        return forward || backward || left || right;
    }
}

