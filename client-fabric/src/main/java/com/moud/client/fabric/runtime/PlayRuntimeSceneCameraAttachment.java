package com.moud.client.fabric.runtime;

import com.moud.net.protocol.RuntimeState;

final class PlayRuntimeSceneCameraAttachment {
    private static final float SCENE_CAM_ATTACH_EPS = 1e-3f;

    private boolean sceneCameraAttached;
    private boolean sceneCameraLocalOffsetValid;
    private float sceneCameraLocalOffX;
    private float sceneCameraLocalOffY;
    private float sceneCameraLocalOffZ;
    private float sceneCameraLocalYawOffDeg;
    private float sceneCameraLocalPitchOffDeg;
    private float sceneCameraLocalRollOffDeg;

    void clear() {
        sceneCameraAttached = false;
        sceneCameraLocalOffsetValid = false;
        sceneCameraLocalOffX = sceneCameraLocalOffY = sceneCameraLocalOffZ = 0.0f;
        sceneCameraLocalYawOffDeg = 0.0f;
        sceneCameraLocalPitchOffDeg = 0.0f;
        sceneCameraLocalRollOffDeg = 0.0f;
    }

    void updateFromServerState(RuntimeState state) {
        if (state == null || !state.useSceneCamera()) {
            clear();
            return;
        }

        float wx = state.sceneCamX() - state.charX();
        float wy = state.sceneCamY() - state.charY();
        float wz = state.sceneCamZ() - state.charZ();
        float distSq = wx * wx + wy * wy + wz * wz;
        boolean attached = Float.isFinite(distSq) && distSq > (SCENE_CAM_ATTACH_EPS * SCENE_CAM_ATTACH_EPS);
        sceneCameraAttached = attached;
        if (!attached) {
            sceneCameraLocalOffsetValid = false;
            sceneCameraLocalOffX = sceneCameraLocalOffY = sceneCameraLocalOffZ = 0.0f;
            sceneCameraLocalYawOffDeg = 0.0f;
            sceneCameraLocalPitchOffDeg = 0.0f;
            sceneCameraLocalRollOffDeg = 0.0f;
            return;
        }

        float yawRad = (float) Math.toRadians(PlayRuntimeAngles.normalizeYaw(state.camYawDeg()));
        float cos = (float) Math.cos(yawRad);
        float sin = (float) Math.sin(yawRad);

        float lx = wx * cos - wz * sin;
        float lz = wx * sin + wz * cos;
        sceneCameraLocalOffX = lx;
        sceneCameraLocalOffY = wy;
        sceneCameraLocalOffZ = lz;
        sceneCameraLocalOffsetValid = Float.isFinite(lx) && Float.isFinite(wy) && Float.isFinite(lz);

        sceneCameraLocalYawOffDeg = PlayRuntimeAngles.normalizeYaw(state.sceneCamYawDeg() - state.camYawDeg());
        sceneCameraLocalPitchOffDeg = state.sceneCamPitchDeg() - state.camPitchDeg();
        sceneCameraLocalRollOffDeg = state.sceneCamRollDeg();
    }

    boolean isAttached() {
        return sceneCameraAttached;
    }

    boolean isLocalOffsetValid() {
        return sceneCameraLocalOffsetValid;
    }

    float localOffX() {
        return sceneCameraLocalOffX;
    }

    float localOffY() {
        return sceneCameraLocalOffY;
    }

    float localOffZ() {
        return sceneCameraLocalOffZ;
    }

    float localYawOffDeg() {
        return sceneCameraLocalYawOffDeg;
    }

    float localPitchOffDeg() {
        return sceneCameraLocalPitchOffDeg;
    }

    float localRollOffDeg() {
        return sceneCameraLocalRollOffDeg;
    }
}

