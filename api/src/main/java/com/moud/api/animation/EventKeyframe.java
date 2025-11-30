package com.moud.api.animation;

import java.util.Map;

public record EventKeyframe(
        float time,
        String eventName,
        Map<String, String> payload
) {
}
