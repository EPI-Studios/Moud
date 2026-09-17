package com.meekdev.moud.core.audio;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;

public final class Sound extends Instance {

    @Prop(asset = true) public String soundId = "";

    public String event = "";

    @Prop(min = 0) public double volume = 1;
    @Prop(min = 0.01) public double pitch = 1;
    public boolean looped;

    public boolean playing;

    public boolean paused;

    @Prop(min = 0) public int plays;

    @Prop(min = 0) public double timePosition;

    @Prop(readOnly = true, min = 0) public double timeLength;

    @Prop(readOnly = true) public boolean isLoaded;

    @Prop(readOnly = true, min = 0, max = 1000) public double playbackLoudness;

    public String bus = "sfx";
    public int priority;

    @Prop(min = 0) public double minDistance = 8;
    @Prop(min = 0) public double maxDistance = 48;
    @Prop(min = 0) public double rollOff = 1;

    @Prop(min = 0) public double fadeIn;

    public boolean stream;

    public final Signal<Instance> played = new Signal<>();
    public final Signal<Instance> ended = new Signal<>();
    public final Signal<Instance> loaded = new Signal<>();
}
