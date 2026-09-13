package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.math.CFrame;
import java.util.ArrayList;
import java.util.List;

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
            if (!(instance instanceof Motor joint)) continue;
            blend(joint, playing);
        }
    }

    private static void blend(Joint joint, List<AnimationTrack> playing) {
        double top = Double.NEGATIVE_INFINITY;
        for (AnimationTrack track : playing) {
            if (track.child(joint.name()) instanceof Spatial && track.priority > top) {
                top = track.priority;
            }
        }
        if (top == Double.NEGATIVE_INFINITY) return;

        CFrame blended = joint.transform;
        double laid = 0;
        for (AnimationTrack track : playing) {
            if (track.priority != top) continue;
            if (!(track.child(joint.name()) instanceof Spatial target)) continue;
            double share = laid == 0 ? track.weight : track.weight / (laid + track.weight);
            blended = blended.lerp(target.cframe, share);
            laid += track.weight;
        }
        Instances.setObj(joint, TRANSFORM, blended);
    }
}
