package com.meekdev.moud.mod.adapter.render;

import java.util.Map;
import org.joml.Vector4f;

// where each part of a body sits on a 64x64 skin, in texels
//
// this is a fixed layout, not a guess: it is the one every skin made in the last decade is drawn
// against, so a body wearing one has to read it exactly. the numbers are the origin of the box
// and its width, height and depth
public final class SkinLayout {

    public static final float SIZE = 64.0f;

    // origin u, origin v, then the box as width, height, depth
    public record Box(float u, float v, float w, float h, float d) {}

    private static final Map<String, Box> WIDE = Map.of(
            "head", new Box(0, 0, 8, 8, 8),
            "torso", new Box(16, 16, 8, 12, 4),
            "rightArm", new Box(40, 16, 4, 12, 4),
            "leftArm", new Box(32, 48, 4, 12, 4),
            "rightLeg", new Box(0, 16, 4, 12, 4),
            "leftLeg", new Box(16, 48, 4, 12, 4));

    // a slim skin narrows the arms by a texel, and nothing else about it changes
    private static final Map<String, Box> SLIM = Map.of(
            "rightArm", new Box(40, 16, 3, 12, 4),
            "leftArm", new Box(32, 48, 3, 12, 4));

    private static final Map<String, Box> SHELL = Map.of(
            "head", new Box(32, 0, 8, 8, 8),
            "torso", new Box(16, 32, 8, 12, 4),
            "rightArm", new Box(40, 32, 4, 12, 4),
            "leftArm", new Box(48, 48, 4, 12, 4),
            "rightLeg", new Box(0, 32, 4, 12, 4),
            "leftLeg", new Box(0, 48, 4, 12, 4));

    private static final Map<String, Box> SHELL_SLIM = Map.of(
            "rightArm", new Box(40, 32, 3, 12, 4),
            "leftArm", new Box(48, 48, 3, 12, 4));

    private SkinLayout() {}

    public static Box of(String part, boolean shell, boolean slim) {
        if (shell) {
            Box narrow = slim ? SHELL_SLIM.get(part) : null;
            return narrow != null ? narrow : SHELL.get(part);
        }
        Box narrow = slim ? SLIM.get(part) : null;
        return narrow != null ? narrow : WIDE.get(part);
    }

    public static Vector4f uv(Box box, Vector4f out) {
        return out.set(box.u(), box.v(), box.w(), box.h());
    }
}
