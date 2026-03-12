package com.moud.client.fabric.model;

import java.util.Map;

public record AnimationClip(
        String name,
        float duration,
        String loopMode,
        Map<String, BoneTrack> tracks
) {
    public AnimationClip {
        if (loopMode == null) loopMode = "once";
        tracks = Map.copyOf(tracks);
    }
}
