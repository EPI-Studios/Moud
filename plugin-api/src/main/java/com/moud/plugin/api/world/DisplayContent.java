package com.moud.plugin.api.world;

import java.util.List;

public final class DisplayContent {
    public enum Type {
        IMAGE,
        URL,
        FRAME_SEQUENCE
    }

    private Type type = Type.IMAGE;
    private String source;
    private List<String> frames = List.of();
    private double fps = 30.0;
    private boolean loop = false;

    public Type type() {
        return type;
    }

    public DisplayContent type(Type type) {
        this.type = type;
        return this;
    }

    public String source() {
        return source;
    }

    public DisplayContent source(String source) {
        this.source = source;
        return this;
    }

    public List<String> frames() {
        return frames;
    }

    public DisplayContent frames(List<String> frames) {
        if (frames != null) {
            this.frames = List.copyOf(frames);
        }
        return this;
    }

    public double fps() {
        return fps;
    }

    public DisplayContent fps(double fps) {
        this.fps = fps;
        return this;
    }

    public boolean loop() {
        return loop;
    }

    public DisplayContent loop(boolean loop) {
        this.loop = loop;
        return this;
    }
}
