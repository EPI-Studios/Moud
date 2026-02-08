package com.moud.client.audio.voice;

import java.util.HashMap;
import java.util.Map;

public final class VoiceMapUtil {

    private VoiceMapUtil() {
    }

    public static Map<String, Object> toStringObjectMap(Map<?, ?> raw) {
        Map<String, Object> map = new HashMap<>();
        raw.forEach((key, value) -> map.put(String.valueOf(key), value));
        return map;
    }
}

