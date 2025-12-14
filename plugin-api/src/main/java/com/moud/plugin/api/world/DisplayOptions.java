package com.moud.plugin.api.world;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;

public final class DisplayOptions {
    private Vector3 position = Vector3.zero();
    private Quaternion rotation = Quaternion.identity();
    private Vector3 scale = Vector3.one();
    private DisplayBillboardMode billboard = DisplayBillboardMode.NONE;
    private boolean renderThroughBlocks = false;
    private DisplayAnchor anchor;
    private DisplayContent content;
    private DisplayPlayback playback;

    public Vector3 position() {
        return position;
    }

    public DisplayOptions position(Vector3 position) {
        this.position = position;
        return this;
    }

    public Quaternion rotation() {
        return rotation;
    }

    public DisplayOptions rotation(Quaternion rotation) {
        this.rotation = rotation;
        return this;
    }

    public Vector3 scale() {
        return scale;
    }

    public DisplayOptions scale(Vector3 scale) {
        this.scale = scale;
        return this;
    }

    public DisplayBillboardMode billboard() {
        return billboard;
    }

    public DisplayOptions billboard(DisplayBillboardMode billboard) {
        this.billboard = billboard;
        return this;
    }

    public boolean renderThroughBlocks() {
        return renderThroughBlocks;
    }

    public DisplayOptions renderThroughBlocks(boolean renderThroughBlocks) {
        this.renderThroughBlocks = renderThroughBlocks;
        return this;
    }

    public DisplayAnchor anchor() {
        return anchor;
    }

    public DisplayOptions anchor(DisplayAnchor anchor) {
        this.anchor = anchor;
        return this;
    }

    public DisplayContent content() {
        return content;
    }

    public DisplayOptions content(DisplayContent content) {
        this.content = content;
        return this;
    }

    public DisplayPlayback playback() {
        return playback;
    }

    public DisplayOptions playback(DisplayPlayback playback) {
        this.playback = playback;
        return this;
    }
}
