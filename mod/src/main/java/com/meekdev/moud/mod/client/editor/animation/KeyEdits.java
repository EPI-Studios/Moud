package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class KeyEdits {

    public record Copied(String joint, Channel channel, double offset, AnimKey key) {}

    private KeyEdits() {}

    public static KeyRef set(AnimClip clip, String joint, Channel channel, double time, Vector3 value) {
        AnimKey existing = clip.keyAt(joint, channel, time);
        if (existing != null) {
            clip.put(joint, channel, existing.with(value));
        } else {
            clip.put(joint, channel, new AnimKey(time, value, interpBefore(clip.keys(joint, channel), time)));
        }
        return new KeyRef(joint, channel, time);
    }

    public static void interp(AnimClip clip, Collection<KeyRef> refs, Interp interp) {
        for (KeyRef ref : refs) {
            AnimKey key = clip.keyAt(ref.joint(), ref.channel(), ref.time());
            if (key != null) clip.put(ref.joint(), ref.channel(), key.with(interp));
        }
    }

    public static int delete(AnimClip clip, Collection<KeyRef> refs) {
        int removed = 0;
        for (KeyRef ref : refs) {
            if (clip.remove(ref.joint(), ref.channel(), ref.time())) removed++;
        }
        return removed;
    }

    public static Set<KeyRef> move(AnimClip clip, Collection<KeyRef> refs, double delta) {
        List<KeyRef> sources = new ArrayList<>();
        List<AnimKey> moving = new ArrayList<>();
        for (KeyRef ref : refs) {
            AnimKey key = clip.keyAt(ref.joint(), ref.channel(), ref.time());
            if (key == null) continue;
            sources.add(ref);
            moving.add(key);
        }
        for (KeyRef ref : sources) clip.remove(ref.joint(), ref.channel(), ref.time());
        Set<KeyRef> moved = new LinkedHashSet<>();
        for (int n = 0; n < sources.size(); n++) {
            KeyRef ref = sources.get(n);
            double time = KeyRef.clean(Math.max(0, moving.get(n).time() + delta));
            clip.put(ref.joint(), ref.channel(), moving.get(n).at(time));
            moved.add(new KeyRef(ref.joint(), ref.channel(), time));
        }
        return moved;
    }

    public static double earliest(AnimClip clip, Collection<KeyRef> refs) {
        double earliest = Double.POSITIVE_INFINITY;
        for (KeyRef ref : refs) {
            if (clip.keyAt(ref.joint(), ref.channel(), ref.time()) != null) earliest = Math.min(earliest, ref.time());
        }
        return earliest;
    }

    public static List<Copied> copy(AnimClip clip, Collection<KeyRef> refs) {
        double start = earliest(clip, refs);
        List<Copied> copied = new ArrayList<>();
        for (KeyRef ref : refs) {
            AnimKey key = clip.keyAt(ref.joint(), ref.channel(), ref.time());
            if (key != null) copied.add(new Copied(ref.joint(), ref.channel(), ref.time() - start, key));
        }
        return copied;
    }

    public static Set<KeyRef> paste(AnimClip clip, List<Copied> copied, double at) {
        Set<KeyRef> pasted = new LinkedHashSet<>();
        for (Copied one : copied) {
            double time = KeyRef.clean(at + one.offset());
            clip.put(one.joint(), one.channel(), one.key().at(time));
            pasted.add(new KeyRef(one.joint(), one.channel(), time));
        }
        return pasted;
    }

    public static Set<KeyRef> mirror(AnimClip clip, Collection<KeyRef> refs) {
        List<KeyRef> sources = new ArrayList<>();
        List<AnimKey> keys = new ArrayList<>();
        for (KeyRef ref : refs) {
            AnimKey key = clip.keyAt(ref.joint(), ref.channel(), ref.time());
            if (key == null) continue;
            sources.add(ref);
            keys.add(key);
        }
        Set<KeyRef> placed = new LinkedHashSet<>();
        for (int n = 0; n < sources.size(); n++) {
            KeyRef ref = sources.get(n);
            AnimKey key = keys.get(n);
            String target = opposite(ref.joint());
            clip.put(target, ref.channel(), new AnimKey(key.time(), mirrored(ref.channel(), key.value()), key.interp(),
                    mirrored(ref.channel(), key.in()), mirrored(ref.channel(), key.out())));
            placed.add(new KeyRef(target, ref.channel(), ref.time()));
        }
        return placed;
    }

    public static Set<KeyRef> mirrorPose(AnimClip clip, String joint, double time) {
        Set<KeyRef> placed = new LinkedHashSet<>();
        String target = opposite(joint);
        for (Channel channel : Channel.values()) {
            List<AnimKey> keys = clip.keys(joint, channel);
            if (keys.isEmpty()) continue;
            Vector3 value = Curves.sample(keys, time, channel.rest());
            placed.add(set(clip, target, channel, time, mirrored(channel, value)));
        }
        return placed;
    }

    public static String opposite(String joint) {
        if (joint.startsWith("right")) return "left" + joint.substring("right".length());
        if (joint.startsWith("left")) return "right" + joint.substring("left".length());
        return joint;
    }

    public static Vector3 mirrored(Channel channel, Vector3 value) {
        return switch (channel) {
            case ROTATION -> new Vector3(value.x(), -value.y(), -value.z());
            case POSITION -> new Vector3(-value.x(), value.y(), value.z());
            case SCALE -> value;
        };
    }

    private static AnimKey.Handle mirrored(Channel channel, AnimKey.Handle handle) {
        if (handle == null || channel == Channel.SCALE) return handle;
        return new AnimKey.Handle(handle.dt(), mirrored(channel, handle.dv()));
    }

    private static Interp interpBefore(List<AnimKey> keys, double time) {
        Interp found = Interp.LINEAR;
        for (AnimKey key : keys) {
            if (key.time() > time) break;
            found = key.interp();
        }
        return found;
    }
}
