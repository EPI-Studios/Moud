package com.moud.api.animation;

import java.util.List;
import java.util.Map;


public record AnimationClip(
        String id,
        String name,
        float duration,
        float frameRate,
        List<ObjectTrack> objectTracks,
        List<EventKeyframe> eventTrack,
        Map<String, Object> metadata
) {

    public AnimationClip withName(String newName) {
        return new AnimationClip(this.id, newName, this.duration, this.frameRate, this.objectTracks, this.eventTrack, this.metadata);
    }
}
