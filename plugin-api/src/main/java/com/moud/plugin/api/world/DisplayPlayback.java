package com.moud.plugin.api.world;

public final class DisplayPlayback {
    private double speed = 1.0;
    private double offsetSeconds = 0.0;
    private boolean playing = false;
    private Boolean loop;
    private Double fps;

    public double speed() {
        return speed;
    }

    public DisplayPlayback speed(double speed) {
        this.speed = speed;
        return this;
    }

    public double offsetSeconds() {
        return offsetSeconds;
    }

    public DisplayPlayback offsetSeconds(double offsetSeconds) {
        this.offsetSeconds = offsetSeconds;
        return this;
    }

    public boolean playing() {
        return playing;
    }

    public DisplayPlayback playing(boolean playing) {
        this.playing = playing;
        return this;
    }

    public Boolean loop() {
        return loop;
    }

    public DisplayPlayback loop(Boolean loop) {
        this.loop = loop;
        return this;
    }

    public Double fps() {
        return fps;
    }

    public DisplayPlayback fps(Double fps) {
        this.fps = fps;
        return this;
    }
}
