package com.moud.api.animation;

public record AnimatableProperty(
        String path,
        String displayName,
        String category,
        float minValue,
        float maxValue,
        float defaultValue,
        PropertyTrack.PropertyType type
) {
}
