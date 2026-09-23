package com.meekdev.moud.core.tween;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

public final class Timeline extends Instance {

    public record Marker(double time, String name) {}

    private record Cue(double time, Runnable run) {}

    @Prop(min = 0) public double length;

    public boolean looped;

    public double speed = 1;

    public boolean playing;

    @Prop(min = 0) public double timePosition;

    public String markers = "";

    public final Signal<Instance> ended = new Signal<>();

    public final Signal<Instance> didLoop = new Signal<>();

    public final Signal<String> reached = new Signal<>();

    private final List<Cue> cues = new ArrayList<>();
    private boolean seen;
    private boolean wasPlaying;
    private boolean done;
    private double applied;

    private static PropertyDef property(String name) {
        return Classes.TIMELINE.property(name);
    }

    public void play() {
        if (speed >= 0 && timePosition >= length) Instances.setNum(this, property("timePosition"), 0);
        if (speed < 0 && timePosition <= 0) Instances.setNum(this, property("timePosition"), length);
        Instances.setBool(this, property("playing"), true);
    }

    public void pause() {
        Instances.setBool(this, property("playing"), false);
    }

    public void stop() {
        Instances.setBool(this, property("playing"), false);
        Instances.setNum(this, property("timePosition"), 0);
    }

    public void fit(double time) {
        if (time > length) Instances.setNum(this, property("length"), time);
    }

    public Runnable at(double time, Runnable run) {
        Cue cue = new Cue(time, run);
        cues.add(cue);
        fit(time);
        return () -> cues.remove(cue);
    }

    public List<Marker> markerList() {
        List<Marker> out = new ArrayList<>();
        for (String entry : markers.split(",")) {
            String[] words = entry.trim().split("\\s+", 2);
            if (words.length < 2) continue;
            try {
                out.add(new Marker(Double.parseDouble(words[0]), words[1].trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    public void addMarker(double time, String name) {
        String entry = String.format(Locale.ROOT, "%s %s", Double.toString(time).replaceAll("\\.0$", ""), name);
        Instances.setObj(this, property("markers"), markers.isBlank() ? entry : markers + ", " + entry);
        fit(time);
    }

    public List<PropertyTrack> tracks() {
        List<PropertyTrack> out = new ArrayList<>();
        for (Instance child : children()) {
            if (child instanceof PropertyTrack track) out.add(track);
        }
        return out;
    }

    public void step(double dt, boolean authoritative, Predicate<Instance> writes) {
        if (!seen) {
            seen = true;
            applied = playing ? Double.NaN : timePosition;
        }
        List<Runnable> fired = new ArrayList<>();
        boolean start = playing && !wasPlaying;
        wasPlaying = playing;
        if (start || done && timePosition != applied) {
            done = false;
            for (PropertyTrack track : tracks()) track.rewind();
        }
        if (playing && !done) advance(dt * speed, start, authoritative, fired);
        if (playing || timePosition != applied) {
            for (PropertyTrack track : tracks()) track.apply(timePosition, length, looped, writes);
            applied = timePosition;
        }
        for (Runnable run : fired) run.run();
    }

    private void advance(double by, boolean start, boolean authoritative, List<Runnable> fired) {
        double from = timePosition;
        double to = from + by;
        boolean forward = by >= 0;
        if (forward ? to < length : to > 0) {
            reach(from, to, start, fired);
            timePosition = to;
            return;
        }
        double edge = forward ? length : 0;
        reach(from, edge, start, fired);
        if (looped && length > 0) {
            double rest = forward ? (to - length) % length : length + to % length;
            timePosition = rest;
            for (PropertyTrack track : tracks()) track.rewind();
            fired.add(() -> didLoop.fire(this));
            reach(forward ? 0 : length, rest, true, fired);
            return;
        }
        timePosition = edge;
        done = true;
        if (authoritative) Instances.setBool(this, property("playing"), false);
        fired.add(() -> ended.fire(this));
    }

    private void reach(double from, double to, boolean inclusive, List<Runnable> fired) {
        for (Marker marker : markerList()) {
            if (crossed(marker.time(), from, to, inclusive)) fired.add(() -> reached.fire(marker.name()));
        }
        for (Cue cue : List.copyOf(cues)) {
            if (crossed(cue.time(), from, to, inclusive)) fired.add(cue.run());
        }
    }

    private static boolean crossed(double time, double from, double to, boolean inclusive) {
        if (to >= from) return (inclusive ? time >= from : time > from) && time <= to;
        return (inclusive ? time <= from : time < from) && time >= to;
    }
}
