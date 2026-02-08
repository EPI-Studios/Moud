package com.moud.api.animation;

import java.util.Map;


public record ObjectTrack(
        String targetObjectId,
        String targetObjectName,
        Map<String, PropertyTrack> propertyTracks
) {
}
