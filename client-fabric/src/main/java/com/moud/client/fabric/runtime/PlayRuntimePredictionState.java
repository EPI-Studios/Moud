package com.moud.client.fabric.runtime;

import com.moud.core.physics.CharacterPhysics;
import com.moud.net.protocol.RuntimeState;

final class PlayRuntimePredictionState {
    private static final float CORRECTION_DECAY = 15.0f;
    private static final float HARD_SNAP_DIST = 2.0f;

    private float predX;
    private float predY;
    private float predZ;
    private float predVelX;
    private float predVelY;
    private float predVelZ;
    private boolean predOnFloor;
    private long lastFrameNs;

    private float corrX;
    private float corrY;
    private float corrZ;

    void reset() {
        predX = predY = predZ = 0.0f;
        predVelX = predVelY = predVelZ = 0.0f;
        predOnFloor = true;
        corrX = corrY = corrZ = 0.0f;
        lastFrameNs = 0L;
    }

    void resetFromServer(RuntimeState state) {
        if (state == null) {
            reset();
            return;
        }
        predX = state.charX();
        predY = state.charY();
        predZ = state.charZ();
        predVelX = state.velX();
        predVelY = state.velY();
        predVelZ = state.velZ();
        predOnFloor = state.onFloor();
        corrX = corrY = corrZ = 0.0f;
        lastFrameNs = 0L;
    }

    void applyServerState(RuntimeState state) {
        if (state == null) {
            return;
        }

        float errX = state.charX() - predX;
        float errY = state.charY() - predY;
        float errZ = state.charZ() - predZ;

        float dist = (float) Math.sqrt(errX * errX + errY * errY + errZ * errZ);
        if (dist > HARD_SNAP_DIST) {
            predX = state.charX();
            predY = state.charY();
            predZ = state.charZ();
            predVelX = state.velX();
            predVelY = state.velY();
            predVelZ = state.velZ();
            predOnFloor = state.onFloor();
            corrX = corrY = corrZ = 0.0f;
        } else {
            predX = state.charX();
            predY = state.charY();
            predZ = state.charZ();
            predVelX = state.velX();
            predVelY = state.velY();
            predVelZ = state.velZ();
            predOnFloor = state.onFloor();
            corrX -= errX;
            corrY -= errY;
            corrZ -= errZ;
        }
    }

    void updatePrediction(float yawDeg,
                          float moveX,
                          float moveZ,
                          boolean jump,
                          boolean sprint,
                          float speed) {
        long now = System.nanoTime();
        if (lastFrameNs == 0L) {
            lastFrameNs = now;
            return;
        }
        float dt = (float) ((now - lastFrameNs) / 1_000_000_000.0);
        lastFrameNs = now;
        dt = Math.min(dt, 0.1f);

        CharacterPhysics.State current = new CharacterPhysics.State(
                predX, predY, predZ, predVelX, predVelY, predVelZ, predOnFloor);
        CharacterPhysics.State next = CharacterPhysics.simulate(
                current, moveX, moveZ, yawDeg, speed, jump, sprint, dt);

        predX = next.x();
        predY = next.y();
        predZ = next.z();
        predVelX = next.velX();
        predVelY = next.velY();
        predVelZ = next.velZ();
        predOnFloor = next.onFloor();

        float decay = (float) Math.exp(-CORRECTION_DECAY * dt);
        corrX *= decay;
        corrY *= decay;
        corrZ *= decay;
    }

    float baseX() {
        return predX + corrX;
    }

    float baseY() {
        return predY + corrY;
    }

    float baseZ() {
        return predZ + corrZ;
    }

    float corrX() {
        return corrX;
    }

    float corrY() {
        return corrY;
    }

    float corrZ() {
        return corrZ;
    }
}

