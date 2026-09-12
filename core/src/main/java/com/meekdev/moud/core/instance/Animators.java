package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.math.CFrame;
import java.util.ArrayList;
import java.util.List;

// every track a body is playing, blended into its joints
//
// it runs after the engine's own pose and before the joints settle, so the walk is the floor and
// a track is laid over it. a track that names three joints changes three joints -- which is what
// makes a wave something you can play while walking rather than a whole body pose somebody had to
// author around the walk
//
// two at the same priority blend by weight; a higher priority wins outright. those two rules are
// the whole of it, and between them they cover every case anyone reaches for: layering a lean over
// a walk, and a sit that a walk cannot argue with
public final class Animators {

    private static final PropertyDef TRANSFORM = Classes.JOINT.property("transform");

    private Animators() {}

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
            if (child instanceof AnimationTrack track && track.playing && track.weight > 0) {
                playing.add(track);
            }
        }
        if (playing.isEmpty()) return;

        for (Instance instance : joints.children()) {
            if (!(instance instanceof Joint joint)) continue;
            blend(joint, playing);
        }
    }

    // one joint, across every track that has an opinion about it
    private static void blend(Joint joint, List<AnimationTrack> playing) {
        // the loudest voice first: a track below the highest priority that says anything is not
        // quietened, it is not asked
        double top = Double.NEGATIVE_INFINITY;
        for (AnimationTrack track : playing) {
            if (track.child(joint.name()) instanceof Spatial && track.priority > top) {
                top = track.priority;
            }
        }
        if (top == Double.NEGATIVE_INFINITY) return;

        // and among equals, by weight. the base is what the engine's own pose left, so a track at
        // half weight is half itself and half the walk
        CFrame blended = joint.transform;
        double laid = 0;
        for (AnimationTrack track : playing) {
            if (track.priority != top) continue;
            if (!(track.child(joint.name()) instanceof Spatial target)) continue;
            // each in turn against what the ones before it left, so two at half weight land
            // between them rather than one of them winning by being last
            double share = laid == 0 ? track.weight : track.weight / (laid + track.weight);
            blended = blended.lerp(target.cframe, share);
            laid += track.weight;
        }
        Instances.setObj(joint, TRANSFORM, blended);
    }
}
