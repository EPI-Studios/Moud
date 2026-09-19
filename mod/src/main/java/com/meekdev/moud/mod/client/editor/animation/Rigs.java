package com.meekdev.moud.mod.client.editor.animation;

import java.util.ArrayList;
import java.util.List;

public final class Rigs {

    public record Joint(String name, String parent, int depth) {}

    public static final List<Joint> PLAYER = List.of(
            new Joint("root", "", 0),
            new Joint("torso", "root", 1),
            new Joint("head", "torso", 2),
            new Joint("rightArm", "torso", 2),
            new Joint("leftArm", "torso", 2),
            new Joint("cape", "torso", 2),
            new Joint("rightLeg", "root", 1),
            new Joint("leftLeg", "root", 1));

    public static final List<Joint> VIEW = List.of(
            new Joint("camera", "", 0),
            new Joint("rightArm", "camera", 1),
            new Joint("rightItem", "rightArm", 2),
            new Joint("leftArm", "camera", 1),
            new Joint("leftItem", "leftArm", 2));

    private Rigs() {}

    public static List<Joint> of(AnimClip clip) {
        List<Joint> base = clip.space == AnimClip.Space.VIEW ? VIEW : PLAYER;
        List<Joint> joints = new ArrayList<>(base);
        for (String name : clip.channels.keySet()) {
            if (joints.stream().noneMatch(joint -> joint.name().equals(name))) joints.add(new Joint(name, "", 0));
        }
        return joints;
    }

    public static List<String> names(AnimClip clip) {
        List<String> names = new ArrayList<>();
        for (Joint joint : of(clip)) names.add(joint.name());
        return names;
    }
}
