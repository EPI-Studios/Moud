package com.moud.plugin.api.world;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;

public interface DisplayHandle extends AutoCloseable {
    long id();
    Vector3 position();
    Quaternion rotation();
    Vector3 scale();

    void setPosition(Vector3 position);
    void setRotation(Quaternion rotation);
    void setRotationFromEuler(double pitch, double yaw, double roll);
    void setTransform(Vector3 position, Quaternion rotation, Vector3 scale);
    void setScale(Vector3 scale);
    void setBillboard(DisplayBillboardMode mode);
    void setRenderThroughBlocks(boolean enabled);
    void setAnchorToBlock(int x, int y, int z, Vector3 offset);
    void setAnchorToEntity(String uuid, Vector3 offset);
    default void setAnchorToEntity(String uuid, Vector3 offset, boolean local, boolean inheritRotation, boolean inheritScale, boolean includePitch) {
        setAnchorToEntity(uuid, offset);
    }
    default void setAnchorToModel(long modelId, Vector3 offset) {
        throw new UnsupportedOperationException("Model anchoring is not supported by this runtime.");
    }
    default void setAnchorToModel(long modelId, Vector3 offset, boolean local, boolean inheritRotation, boolean inheritScale, boolean includePitch) {
        setAnchorToModel(modelId, offset);
    }
    void clearAnchor();
    void setImage(String source);
    void setVideo(String url, double fps, boolean loop);
    void setFrameSequence(String[] frames, double fps, boolean loop);
    void setFrameRate(double fps);
    void setLoop(boolean loop);
    void play();
    void pause();
    void setPlaybackSpeed(double speed);
    void seek(double seconds);
    void remove();

    @Override
    default void close() {
        remove();
    }
}
