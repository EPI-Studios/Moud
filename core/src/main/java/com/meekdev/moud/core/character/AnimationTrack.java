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

    private double weightTarget = -1;

    private double weightRate;

    public void fadeWeight(double target, double seconds) {
        if (seconds <= 0) {
            weight = target;
            weightTarget = -1;
            return;
        }
        weightTarget = target;
        weightRate = Math.abs(target - weight) / seconds;
    }

    void stepWeight(double dt) {
        if (weightTarget < 0) return;
        double step = weightRate * dt;
        if (Math.abs(weightTarget - weight) <= step) {
            weight = weightTarget;
            weightTarget = -1;
        } else {
            weight += Math.signum(weightTarget - weight) * step;
        }
    }

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
