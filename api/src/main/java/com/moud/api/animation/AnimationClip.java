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

    public AnimationClip withEventTrack(java.util.List<EventKeyframe> events) {
        return new AnimationClip(this.id, this.name, this.duration, this.frameRate, this.objectTracks, events, this.metadata);
    }
}
