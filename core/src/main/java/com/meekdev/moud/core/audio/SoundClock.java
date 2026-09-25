package com.meekdev.moud.core.audio;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;

public final class SoundClock {

    private SoundClock() {}

    public static void step(InstanceTree tree, double dt) {
        for (Sound sound : tree.ofClass(Classes.SOUND)) {
            if (!sound.playing || sound.paused || Instance.outOfWorld(sound)) continue;
            sound.timePosition += dt * sound.pitch;
        }
    }

    public static double startAt(Sound sound) {
        double from = Math.max(0, sound.timePosition);
        double length = sound.timeLength;
        if (from == 0 || length <= 0) return from;
        if (sound.looped) return from % length;
        return from;
    }

    public static boolean over(Sound sound) {
        return !sound.looped && sound.timeLength > 0 && sound.timePosition >= sound.timeLength;
    }
}
