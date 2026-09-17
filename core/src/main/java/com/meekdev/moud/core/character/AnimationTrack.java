package com.meekdev.moud.core.character;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;

public final class AnimationTrack extends Instance {

    public boolean playing;

    @Prop(min = 0, max = 1) public double weight = 1.0;

    public double priority;

    public Instance animation;

    public double speed = 1;

    public boolean looped;

    @Prop(min = 0) public double fadeTime = 0.1;

    @Prop(replicated = false) public double timePosition;

    @Prop(replicated = false) public double length;

    public final Signal<Instance> stopped = new Signal<>();

    public final Signal<Instance> ended = new Signal<>();

    public final Signal<Instance> didLoop = new Signal<>();

    public final Signal<String> keyframeReached = new Signal<>();

    private final Signal<Clip.Marker> markers = new Signal<>();

    private double fade;

    private boolean wasPlaying;

    public Signal<Clip.Marker> markers() {
        return markers;
    }

    double fade() {
        return fade;
    }

    void fade(double value) {
        fade = value;
    }

    boolean wasPlaying() {
        return wasPlaying;
    }

    void wasPlaying(boolean value) {
        wasPlaying = value;
    }
}
