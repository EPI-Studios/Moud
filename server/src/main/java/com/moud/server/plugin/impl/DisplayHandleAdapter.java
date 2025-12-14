package com.moud.server.plugin.impl;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.plugin.api.world.DisplayBillboardMode;
import com.moud.plugin.api.world.DisplayHandle;
import com.moud.server.proxy.MediaDisplayProxy;

public final class DisplayHandleAdapter implements DisplayHandle {
    private final MediaDisplayProxy proxy;

    public DisplayHandleAdapter(MediaDisplayProxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public long id() {
        return proxy.getId();
    }

    @Override
    public Vector3 position() {
        return proxy.getPosition();
    }

    @Override
    public Quaternion rotation() {
        return proxy.getRotation();
    }

    @Override
    public Vector3 scale() {
        return proxy.getScale();
    }

    @Override
    public void setPosition(Vector3 position) {
        proxy.setPosition(position);
    }

    @Override
    public void setRotation(Quaternion rotation) {
        proxy.setRotation(rotation);
    }

    @Override
    public void setRotationFromEuler(double pitch, double yaw, double roll) {
        proxy.setRotationFromEuler(pitch, yaw, roll);
    }

    @Override
    public void setTransform(Vector3 position, Quaternion rotation, Vector3 scale) {
        proxy.setTransform(position, rotation, scale);
    }

    @Override
    public void setScale(Vector3 scale) {
        proxy.setScale(scale);
    }

    @Override
    public void setBillboard(DisplayBillboardMode mode) {
        if (mode == null) return;
        switch (mode) {
            case CAMERA -> proxy.setBillboard("camera");
            case VERTICAL -> proxy.setBillboard("vertical");
            default -> proxy.setBillboard("none");
        }
    }

    @Override
    public void setRenderThroughBlocks(boolean enabled) {
        proxy.setRenderThroughBlocks(enabled);
    }

    @Override
    public void setAnchorToBlock(int x, int y, int z, Vector3 offset) {
        proxy.setAnchorToBlock(x, y, z, offset);
    }

    @Override
    public void setAnchorToEntity(String uuid, Vector3 offset) {
        proxy.setAnchorToEntity(java.util.UUID.fromString(uuid), offset);
    }

    @Override
    public void clearAnchor() {
        proxy.clearAnchor();
    }

    @Override
    public void setImage(String source) {
        proxy.setImage(source);
    }

    @Override
    public void setVideo(String url, double fps, boolean loop) {
        proxy.setVideo(url, fps, loop);
    }

    @Override
    public void setFrameSequence(String[] frames, double fps, boolean loop) {
        proxy.setFrameSequence(frames, fps, loop);
    }

    @Override
    public void setFrameRate(double fps) {
        proxy.setFrameRate(fps);
    }

    @Override
    public void setLoop(boolean loop) {
        proxy.setLoop(loop);
    }

    @Override
    public void play() {
        proxy.play();
    }

    @Override
    public void pause() {
        proxy.pause();
    }

    @Override
    public void setPlaybackSpeed(double speed) {
        proxy.setPlaybackSpeed(speed);
    }

    @Override
    public void seek(double seconds) {
        proxy.seek(seconds);
    }

    @Override
    public void remove() {
        proxy.remove();
    }
}
