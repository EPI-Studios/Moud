package com.moud.client.fabric.render.scene.math;

public final class NodePoseState {
    public final Pose prevLocal = new Pose();
    public final Pose currLocal = new Pose();
    public final Pose interpLocal = new Pose();
    public long parentId;
    public boolean initialized;
    public long interpFrame = Long.MIN_VALUE;

    public Pose interpolatedLocal(long frameId, float t) {
        if (interpFrame != frameId) {
            Pose.interpolate(prevLocal, currLocal, t, interpLocal);
            interpFrame = frameId;
        }
        return interpLocal;
    }

    public void invalidateInterp() {
        interpFrame = Long.MIN_VALUE;
    }
}
