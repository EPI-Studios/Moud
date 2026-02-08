package com.moud.client.editor.ui.panel.timeline;

import com.moud.api.animation.Keyframe;
import com.moud.api.animation.PropertyTrack;

import java.util.List;
import java.util.Map;

public record TrackView(String objectLabel, String label, String propertyPath, Map<String, PropertyTrack> propertyMap,
                 PropertyTrack propertyTrack) {
    public List<Keyframe> keyframes() {
        return propertyTrack.keyframes();
    }
}
