package com.moud.client.animation;

import java.util.Map;
import java.util.TreeMap;

public class AnimationData {
    private final float length;
    private final boolean loop;
    private final Map<String, BoneAnimation> bones;

    public AnimationData(float length, boolean loop, Map<String, BoneAnimation> bones) {
        this.length = length;
        this.loop = loop;
        this.bones = bones;
    }

    public float getLength() {
        return length;
    }

    public boolean isLooping() {
        return loop;
    }

    public Map<String, BoneAnimation> getBones() {
        return bones;
    }

    public static class BoneAnimation {
        private final TreeMap<Float, float[]> rotation;
        private final TreeMap<Float, float[]> position;
        private final TreeMap<Float, float[]> scale;

        public BoneAnimation(TreeMap<Float, float[]> rotation, TreeMap<Float, float[]> position, TreeMap<Float, float[]> scale) {
            this.rotation = rotation;
            this.position = position;
            this.scale = scale;
        }

        public TreeMap<Float, float[]> getRotation() {
            return rotation;
        }

        public TreeMap<Float, float[]> getPosition() {
            return position;
        }

        public TreeMap<Float, float[]> getScale() {
            return scale;
        }
    }
}