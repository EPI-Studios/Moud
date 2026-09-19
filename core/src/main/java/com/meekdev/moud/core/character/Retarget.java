package com.meekdev.moud.core.character;

import com.meekdev.moud.core.scene.Json;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class Retarget {

    private static final Map<String, String> NAMES = load();

    private Retarget() {}

    public static String joint(String name) {
        String key = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (key.startsWith("biped")) key = key.substring("biped".length());
        return NAMES.get(key);
    }

    public static Map<String, String> names() {
        return NAMES;
    }

    private static Map<String, String> load() {
        Map<String, String> names = new HashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) Json.resource(Retarget.class, "retarget.json")).entrySet()) {
            names.put((String) entry.getKey(), (String) entry.getValue());
        }
        return Map.copyOf(names);
    }
}
