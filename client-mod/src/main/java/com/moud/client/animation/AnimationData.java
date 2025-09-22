package com.moud.client.animation;

import java.util.Map;

public record AnimationData(
        float length,
        boolean loop,
        Map<String, BoneAnimation> bones
) {
    public record BoneAnimation(
            Map<String, float[]> rotation,
            Map<String, float[]> position,
            Map<String, float[]> scale
    ) {}
}