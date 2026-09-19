package com.meekdev.moud.core.character;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class Animators {

    private static final PropertyDef PLAYING = Classes.TRACK.property("playing");

    private static Function<String, Clip> files = path -> null;
    private static final Map<String, Clip> LOADED = new HashMap<>();

    private record Layer(AnimationTrack track, Clip clip, double priority, double strength, boolean additive,
                         Set<String> mask, Map<String, Clip.Sample> composed) {}

    private static final class Blend {
        Quat rotation;
        Vector3 position;
        Vector3 scale;
        boolean moved;
        boolean scaled;
    }

    private Animators() {}

    public static void files(Function<String, Clip> loader) {
        files = loader;
        LOADED.clear();
    }

    public static void forget() {
        LOADED.clear();
    }

    public static void forget(String animationId) {
        LOADED.remove(animationId);
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

    public static void step(InstanceTree tree, double dt, boolean server) {
        for (Instance instance : tree.ofClass(Classes.TRACK)) {
            if (instance instanceof AnimationTrack track && track.animation != null && (server || !ViewModels.inside(track))) {
                advance(track, dt, server);
            }
        }
    }

    static void advance(AnimationTrack track, double dt, boolean server) {
        Clip clip = clip(track.animation);
        track.length = clip.length();
        boolean authoritative = server || track.id() < 0;
        if (track.playing && !track.wasPlaying()) {
            track.timePosition = track.speed < 0 ? clip.length() : 0;
            track.held(false);
            if (track.parent() instanceof Animator animator) animator.animationPlayed.fire(track);
        }
        track.stepWeight(dt);
        if (!track.playing && track.wasPlaying()) track.stopped.fire(track);
        track.wasPlaying(track.playing);
        double rate = track.fadeTime <= 0 ? Double.POSITIVE_INFINITY : dt / track.fadeTime;
        track.fade(track.playing ? Math.min(1, track.fade() + rate) : Math.max(0, track.fade() - rate));
        if (!track.playing || clip.length() <= 0) return;
        double before = track.timePosition;
        double after = before + dt * track.speed;
        boolean loops = track.looped || clip.loop() == Clip.Loop.LOOP;
        boolean holds = !loops && clip.loop() == Clip.Loop.HOLD;
        if (after >= clip.length() && track.speed > 0) {
            if (loops) {
                markers(track, clip, before, clip.length() + 1e-9, server);
                after -= clip.length();
                if (clip.length() > 0) after %= clip.length();
                track.timePosition = after;
                track.didLoop.fire(track);
                markers(track, clip, -1e-9, after, server);
                return;
            }
            if (track.held()) return;
            markers(track, clip, before, clip.length() + 1e-9, server);
            track.timePosition = clip.length();
            if (holds) {
                track.held(true);
            } else if (authoritative) {
                Instances.setBool(track, PLAYING, false);
            }
            track.ended.fire(track);
            return;
        }
        if (after <= 0 && track.speed < 0) {
            if (loops) {
                after = clip.length() + after % clip.length();
                track.didLoop.fire(track);
            } else {
                track.timePosition = 0;
                if (!holds && authoritative) Instances.setBool(track, PLAYING, false);
                track.ended.fire(track);
                return;
            }
        }
        markers(track, clip, before, after, server);
        track.timePosition = after;
    }

    private static void markers(AnimationTrack track, Clip clip, double from, double to, boolean server) {
        for (Clip.Marker marker : clip.markers()) {
            if (marker.time() > from && marker.time() <= to && marker.firesOn(server)) {
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

    public static void controllers(InstanceTree tree) {
        for (Instance instance : tree.ofClass(Classes.ANIMATION_CONTROLLER)) {
            if (instance instanceof AnimationController controller) apply(controller);
        }
    }

    public static void apply(Character character) {
        if (character.child(Rig.ANIMATOR) instanceof Animator animator) pose(animator, false, true);
    }

    public static void apply(AnimationController controller) {
        for (Instance child : controller.children()) {
            if (child instanceof Animator animator) pose(animator, true, false);
        }
    }

    static void pose(Animator animator, boolean fresh, boolean retarget) {
        List<Layer> layers = new ArrayList<>();
        for (Instance child : animator.children()) {
            if (!(child instanceof AnimationTrack track)) continue;
            double strength = strength(track);
            if (strength <= 0) continue;
            Clip clip = track.animation == null ? null : clip(track.animation);
            Set<String> mask = !track.mask.isEmpty() ? Clip.maskOf(track.mask) : clip == null ? Set.of() : clip.mask();
            boolean additive = track.blend == AnimationBlend.ADDITIVE || clip != null && clip.blend() == AnimationBlend.ADDITIVE;
            Map<String, Clip.Sample> composed = retarget && clip != null && !clip.skeleton().isEmpty()
                    ? clip.composed(track.timePosition) : Map.of();
            layers.add(new Layer(track, clip, priority(track, clip), strength, additive, mask, composed));
        }
        if (layers.isEmpty()) return;
        layers.sort(Comparator.comparingDouble(Layer::priority));
        for (Map.Entry<String, Instance> joint : Rigs.joints(animator).entrySet()) {
            blend(joint.getKey(), joint.getValue(), layers, fresh, retarget);
        }
    }

    static boolean moving(Animator animator) {
        for (Instance child : animator.children()) {
            if (child instanceof AnimationTrack track && strength(track) > 0) return true;
        }
        return false;
    }

    private static double strength(AnimationTrack track) {
        if (track.animation == null) return track.playing ? track.weight : 0;
        return track.weight * track.fade();
    }

    private static double priority(AnimationTrack track, Clip clip) {
        if (clip == null || track.priority != 0) return track.priority;
        return clip.priority();
    }

    private record Weighted(Clip.Sample sample, double weight) {}

    private static Weighted sample(Layer layer, String joint, boolean retarget) {
        if (layer.clip() == null) {
            if (!(layer.track().child(joint) instanceof Spatial still)) return null;
            return new Weighted(new Clip.Sample(still.cframe.rotation(), still.cframe.position(), null), layer.strength());
        }
        String channel = layer.clip().channelFor(joint, retarget);
        if (channel == null) return null;
        if (!layer.mask().isEmpty() && !layer.mask().contains(joint) && !layer.mask().contains(channel)) return null;
        Clip.Sample sample = layer.clip().sample(joint, layer.track().timePosition, retarget, layer.composed());
        if (sample == null) return null;
        double weight = layer.strength() * layer.clip().weight(channel);
        return weight <= 0 ? null : new Weighted(sample, weight);
    }

    private static void blend(String name, Instance joint, List<Layer> layers, boolean fresh, boolean retarget) {
        CFrame start = fresh ? CFrame.IDENTITY : Rigs.transform(joint);
        Blend out = new Blend();
        out.rotation = start.rotation();
        out.position = start.position();
        out.scale = fresh ? Vector3.ONE : Rigs.scale(joint);

        int n = 0;
        while (n < layers.size()) {
            double priority = layers.get(n).priority();
            Blend level = new Blend();
            double rotationWeight = 0;
            double positionWeight = 0;
            double scaleWeight = 0;
            for (; n < layers.size() && layers.get(n).priority() == priority; n++) {
                Layer layer = layers.get(n);
                if (layer.additive()) continue;
                Weighted weighted = sample(layer, name, retarget);
                if (weighted == null) continue;
                Clip.Sample s = weighted.sample();
                double w = weighted.weight();
                if (s.rotation() != null) {
                    level.rotation = level.rotation == null ? s.rotation() : level.rotation.slerp(s.rotation(), w / (rotationWeight + w));
                    rotationWeight += w;
                }
                if (s.position() != null) {
                    level.position = level.position == null ? s.position() : level.position.lerp(s.position(), w / (positionWeight + w));
                    positionWeight += w;
                }
                if (s.scale() != null) {
                    level.scale = level.scale == null ? s.scale() : level.scale.lerp(s.scale(), w / (scaleWeight + w));
                    scaleWeight += w;
                }
            }
            if (level.rotation != null) {
                out.rotation = out.rotation.slerp(level.rotation, Math.min(1, rotationWeight));
                out.moved = true;
            }
            if (level.position != null) {
                out.position = out.position.lerp(level.position, Math.min(1, positionWeight));
                out.moved = true;
            }
            if (level.scale != null) {
                out.scale = out.scale.lerp(level.scale, Math.min(1, scaleWeight));
                out.scaled = true;
            }
        }

        for (Layer layer : layers) {
            if (!layer.additive()) continue;
            Weighted weighted = sample(layer, name, retarget);
            if (weighted == null) continue;
            Clip.Sample s = weighted.sample();
            double w = Math.min(1, weighted.weight());
            if (s.rotation() != null) {
                out.rotation = out.rotation.mul(Quat.IDENTITY.slerp(s.rotation(), w)).normalize();
                out.moved = true;
            }
            if (s.position() != null) {
                out.position = out.position.add(s.position().mul(w));
                out.moved = true;
            }
            if (s.scale() != null) {
                out.scale = out.scale.mul(Vector3.ONE.lerp(s.scale(), w));
                out.scaled = true;
            }
        }

        if (out.moved) Rigs.transform(joint, new CFrame(out.position, out.rotation.normalize()));
        if (out.scaled) Rigs.scale(joint, out.scale);
    }
}
