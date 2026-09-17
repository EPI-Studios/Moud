package com.meekdev.moud.core.character;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Joint;
import com.meekdev.moud.core.instance.Motor;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.math.CFrame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class Animators {

    private static final PropertyDef TRANSFORM = Classes.JOINT.property("transform");
    private static final PropertyDef PLAYING = Classes.TRACK.property("playing");

    private static Function<String, Clip> files = path -> null;
    private static final Map<String, Clip> LOADED = new HashMap<>();

    private Animators() {}

    public static void files(Function<String, Clip> loader) {
        files = loader;
        LOADED.clear();
    }

    public static void forget() {
        LOADED.clear();
    }

    public static Clip clip(Instance animation) {
        if (animation instanceof KeyframeSequence sequence) return Clip.of(sequence);
        if (!(animation instanceof Animation source)) return Clip.EMPTY;
        for (Instance child : source.children()) {
            if (child instanceof KeyframeSequence sequence) return Clip.of(sequence);
        }
        if (source.animationId.isEmpty()) return Clip.EMPTY;
        Clip known = LOADED.get(source.animationId);
        if (known != null) return known;
        Clip loaded = files.apply(source.animationId);
        Clip result = loaded == null ? Clip.EMPTY : loaded;
        LOADED.put(source.animationId, result);
        return result;
    }

    public static void step(InstanceTree tree, double dt, boolean authoritative) {
        for (Instance instance : tree.ofClass(Classes.TRACK)) {
            if (instance instanceof AnimationTrack track && track.animation != null) advance(track, dt, authoritative);
        }
    }

    static void advance(AnimationTrack track, double dt, boolean authoritative) {
        Clip clip = clip(track.animation);
        track.length = clip.length();
        if (track.playing && !track.wasPlaying()) track.timePosition = Math.clamp(track.timePosition, 0, Math.max(0, clip.length()));
        if (!track.playing && track.wasPlaying()) track.stopped.fire(track);
        track.wasPlaying(track.playing);
        double rate = track.fadeTime <= 0 ? Double.POSITIVE_INFINITY : dt / track.fadeTime;
        track.fade(track.playing ? Math.min(1, track.fade() + rate) : Math.max(0, track.fade() - rate));
        if (!track.playing || clip.length() <= 0) return;
        double before = track.timePosition;
        double after = before + dt * track.speed;
        boolean loops = track.looped || clip.looped();
        if (after >= clip.length() && track.speed > 0) {
            if (loops) {
                markers(track, clip, before, clip.length() + 1e-9);
                after -= clip.length();
                if (clip.length() > 0) after %= clip.length();
                track.timePosition = after;
                track.didLoop.fire(track);
                markers(track, clip, -1e-9, after);
                return;
            }
            markers(track, clip, before, clip.length() + 1e-9);
            track.timePosition = clip.length();
            if (authoritative) Instances.setBool(track, PLAYING, false);
            track.ended.fire(track);
            return;
        }
        if (after < 0) after = loops ? clip.length() + after % clip.length() : 0;
        markers(track, clip, before, after);
        track.timePosition = after;
    }

    private static void markers(AnimationTrack track, Clip clip, double from, double to) {
        for (Clip.Marker marker : clip.markers()) {
            if (marker.time() > from && marker.time() <= to) {
                track.keyframeReached.fire(marker.name());
                track.markers().fire(marker);
            }
        }
    }

    public static void follow(InstanceTree tree) {
        for (Instance instance : tree.ofClass(Classes.CHARACTER)) {
            if (instance instanceof Character character) apply(character);
        }
    }

    public static void apply(Character character) {
        if (!(character.child(Rig.ANIMATOR) instanceof Animator animator)) return;
        if (!(character.child(Rig.JOINTS) instanceof Instance joints)) return;

        List<AnimationTrack> playing = new ArrayList<>();
        for (Instance child : animator.children()) {
            if (child instanceof AnimationTrack track && strength(track) > 0) playing.add(track);
        }
        if (playing.isEmpty()) return;

        for (Instance instance : joints.children()) {
            if (!(instance instanceof Motor joint)) continue;
            blend(joint, playing);
        }
    }

    private static double strength(AnimationTrack track) {
        if (track.animation == null) return track.playing ? track.weight : 0;
        return track.weight * track.fade();
    }

    private static double priority(AnimationTrack track) {
        if (track.animation == null || track.priority != 0) return track.priority;
        return clip(track.animation).priority();
    }

    private static CFrame target(AnimationTrack track, String joint) {
        if (track.animation == null) return track.child(joint) instanceof Spatial target ? target.cframe : null;
        return clip(track.animation).sample(joint, track.timePosition);
    }

    private static void blend(Joint joint, List<AnimationTrack> playing) {
        double top = Double.NEGATIVE_INFINITY;
        for (AnimationTrack track : playing) {
            if (target(track, joint.name()) != null && priority(track) > top) top = priority(track);
        }
        if (top == Double.NEGATIVE_INFINITY) return;

        CFrame blended = joint.transform;
        double totalWeight = 0;
        for (AnimationTrack track : playing) {
            if (priority(track) != top) continue;
            CFrame target = target(track, joint.name());
            if (target == null) continue;
            double weight = strength(track) * (track.animation == null ? 1 : clip(track.animation).weight(joint.name()));
            if (weight <= 0) continue;
            double share = totalWeight == 0 ? Math.min(1, weight) : weight / (totalWeight + weight);
            blended = blended.lerp(target, share);
            totalWeight += weight;
        }
        Instances.setObj(joint, TRANSFORM, blended);
    }
}
