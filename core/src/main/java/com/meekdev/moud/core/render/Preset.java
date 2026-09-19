package com.meekdev.moud.core.render;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.Classes;
import java.util.Map;

public record Preset(String name, Map<String, Object> lighting, Map<String, Object> atmosphere,
                     Map<String, Object> clouds, Map<String, Object> weather) {

    public Map<String, Object> of(ClassDef<?> def) {
        if (def == Classes.LIGHTING) return lighting;
        if (def == Classes.ATMOSPHERE) return atmosphere;
        if (def == Classes.CLOUDS) return clouds;
        if (def == Classes.WEATHER) return weather;
        return Map.of();
    }
}
