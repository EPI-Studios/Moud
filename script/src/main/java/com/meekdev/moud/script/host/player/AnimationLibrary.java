package com.meekdev.moud.script.host.player;

import com.meekdev.moud.core.character.Animation;
import com.meekdev.moud.core.character.AnimationTrack;
import com.meekdev.moud.core.character.Animator;
import com.meekdev.moud.core.character.Animators;
import com.meekdev.moud.core.character.KeyframeSequence;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostSignal;
import com.meekdev.moud.script.host.Members;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class AnimationLibrary {

    private static final PropertyDef PLAYING = Classes.TRACK.property("playing");
    private static final PropertyDef WEIGHT = Classes.TRACK.property("weight");
    private static final PropertyDef SPEED = Classes.TRACK.property("speed");
    private static final PropertyDef FADE = Classes.TRACK.property("fadeTime");
    private static final PropertyDef ANIMATION = Classes.TRACK.property("animation");

    private AnimationLibrary() {}

    static void install(Host host) {
        host.api().declare(HostSignal.decl("StringSignal", "(value: string) -> ()"));
        host.api().declare(HostSignal.decl("MarkerSignal", "(value: any) -> ()"));
        Map<AnimationTrack, Map<String, HostSignal>> reached = new WeakHashMap<>();

        Members animators = host.instances().of(Classes.ANIMATOR);
        animators.method("loadAnimation", "(animation: Instance) -> AnimationTrack", a -> {
            Animator animator = a.self(Animator.class);
            Instance animation = a.instance(1);
            if (!(animation instanceof Animation) && !(animation instanceof KeyframeSequence)) {
                throw new HostError("loadAnimation expects an Animation or a KeyframeSequence, got %s", animation.def().name());
            }
            AnimationTrack track = Instances.create(Classes.TRACK, animator, animation.name());
            Instances.setObj(track, ANIMATION, animation);
            track.length = Animators.clip(animation).length();
            return track;
        });
        animators.method("getPlayingAnimationTracks", "() -> { AnimationTrack }", a -> {
            List<Object> playing = new ArrayList<>();
            for (Instance child : a.self(Animator.class).children()) {
                if (child instanceof AnimationTrack track && track.playing) playing.add(track);
            }
            return playing;
        });

        Members tracks = host.instances().of(Classes.TRACK);
        tracks.method("play", "(fadeTime: number?, weight: number?, speed: number?) -> ()", a -> {
            AnimationTrack track = a.self(AnimationTrack.class);
            if (a.has(1)) host.instances().write(track, FADE, a.number(1));
            host.instances().write(track, WEIGHT, a.number(2, track.weight));
            host.instances().write(track, SPEED, a.number(3, track.speed));
            if (!track.playing) track.timePosition = track.speed < 0 ? track.length : 0;
            host.instances().write(track, PLAYING, true);
            return null;
        });
        tracks.method("stop", "(fadeTime: number?) -> ()", a -> {
            AnimationTrack track = a.self(AnimationTrack.class);
            if (a.has(1)) host.instances().write(track, FADE, a.number(1));
            host.instances().write(track, PLAYING, false);
            return null;
        });
        tracks.method("adjustSpeed", "(speed: number) -> ()", a -> {
            host.instances().write(a.self(AnimationTrack.class), SPEED, a.number(1));
            return null;
        });
        tracks.method("adjustWeight", "(weight: number, fadeTime: number?) -> ()", a -> {
            AnimationTrack track = a.self(AnimationTrack.class);
            double target = Math.clamp(a.number(1), 0, 1);
            host.instances().checkWrite(track, WEIGHT);
            if (a.has(2) && a.number(2) > 0) track.fadeWeight(target, a.number(2));
            else host.instances().write(track, WEIGHT, target);
            return null;
        });
        tracks.method("getMarkerReachedSignal", "(name: string) -> MarkerSignal", a -> {
            AnimationTrack track = a.self(AnimationTrack.class);
            String name = a.string(1);
            Map<String, HostSignal> byName = reached.computeIfAbsent(track, key -> new HashMap<>());
            HostSignal known = byName.get(name);
            if (known != null) return known;
            HostSignal signal = new HostSignal(host, "MarkerSignal", "markerReached");
            track.markers().connect(marker -> {
                if (marker.name().equals(name)) signal.fire(marker.value());
            });
            byName.put(name, signal);
            return signal;
        });
    }
}
