package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Signal;

// a sound that plays on every client that holds it. inside something that has a position it plays
// from there and follows it; anywhere else it plays everywhere at once
public final class Sound extends Instance {

    // a res:// file in the place, or a sound in a resource pack
    @Prop(asset = true) public String soundId = "";

    // a named event instead of one file: its variations are picked at random each play
    public String event = "";

    @Prop(min = 0) public double volume = 1;
    @Prop(min = 0.01) public double pitch = 1;
    public boolean looped;

    // what a client should be doing with it. play() and stop() are the usual way to change it
    public boolean playing;

    // counts play() calls, so playing something already playing starts it again on every client
    @Prop(min = 0) public int plays;

    public String bus = "sfx";
    public int priority;

    // full volume inside minDistance, silent past maxDistance, rollOff is how steeply it falls between
    @Prop(min = 0) public double minDistance = 8;
    @Prop(min = 0) public double maxDistance = 48;
    @Prop(min = 0) public double rollOff = 1;

    @Prop(min = 0) public double fadeIn;

    // read from the file as it plays rather than all at once, for music and anything long
    public boolean stream;

    // fire on the client that is playing it
    public final Signal<Instance> played = new Signal<>();
    public final Signal<Instance> ended = new Signal<>();
}
