package com.meekdev.moud.core.render;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.scene.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Presets {

    public static final List<ClassDef<?>> PARTS = List.of(Classes.LIGHTING, Classes.ATMOSPHERE, Classes.CLOUDS, Classes.WEATHER);

    public static final List<Preset> ALL = load();

    private Presets() {}

    public static Preset find(String name) {
        if (name == null) return null;
        for (Preset preset : ALL) {
            if (preset.name().equalsIgnoreCase(name.strip())) return preset;
        }
        return null;
    }

    public static List<Instance> parts(Lighting lighting) {
        List<Instance> parts = new ArrayList<>(PARTS.size());
        parts.add(lighting);
        for (ClassDef<?> def : PARTS) {
            if (def == Classes.LIGHTING) continue;
            Instance found = Environments.under(lighting, def);
            if (found != null) parts.add(found);
        }
        return parts;
    }

    public static List<ClassDef<?>> missing(Lighting lighting) {
        List<ClassDef<?>> missing = new ArrayList<>();
        for (ClassDef<?> def : PARTS) {
            if (def != Classes.LIGHTING && Environments.under(lighting, def) == null) missing.add(def);
        }
        return missing;
    }

    public static List<String> names() {
        List<String> names = new ArrayList<>(ALL.size());
        for (Preset preset : ALL) names.add(preset.name());
        return names;
    }

    private static List<Preset> load() {
        List<Preset> presets = new ArrayList<>();
        for (Object entry : (List<?>) Json.resource(Presets.class, "presets.json")) {
            Map<?, ?> data = (Map<?, ?>) entry;
            presets.add(new Preset((String) data.get("name"), lighting(table(data, "lighting")), air(table(data, "atmosphere")),
                    clouds(table(data, "clouds")), weather(table(data, "weather"))));
        }
        return List.copyOf(presets);
    }

    private static Map<String, Object> lighting(Map<?, ?> data) {
        return Map.of("clockTime", number(data, "clockTime"), "brightness", number(data, "brightness"), "dayCycle", true,
                "ambient", color(data, "ambient"), "outdoorAmbient", color(data, "outdoorAmbient"),
                "exposureCompensation", number(data, "exposureCompensation"));
    }

    private static Map<String, Object> air(Map<?, ?> data) {
        return Map.of("density", number(data, "density"), "offset", number(data, "offset"), "color", color(data, "color"),
                "decay", color(data, "decay"), "glare", number(data, "glare"), "haze", number(data, "haze"));
    }

    private static Map<String, Object> clouds(Map<?, ?> data) {
        return Map.of("enabled", true, "cover", number(data, "cover"), "density", number(data, "density"), "color", color(data, "color"));
    }

    private static Map<String, Object> weather(Map<?, ?> data) {
        return Map.of("kind", WeatherKind.valueOf((String) data.get("kind")), "intensity", number(data, "intensity"),
                "wind", vector(data, "wind"));
    }

    private static Map<?, ?> table(Map<?, ?> data, String key) {
        return (Map<?, ?>) data.get(key);
    }

    private static double number(Map<?, ?> data, String key) {
        return ((Number) data.get(key)).doubleValue();
    }

    private static double[] triple(Map<?, ?> data, String key) {
        List<?> values = (List<?>) data.get(key);
        return new double[] {((Number) values.get(0)).doubleValue(), ((Number) values.get(1)).doubleValue(),
                ((Number) values.get(2)).doubleValue()};
    }

    private static Color color(Map<?, ?> data, String key) {
        double[] rgb = triple(data, key);
        return new Color((float) rgb[0], (float) rgb[1], (float) rgb[2]);
    }

    private static Vector3 vector(Map<?, ?> data, String key) {
        double[] xyz = triple(data, key);
        return new Vector3(xyz[0], xyz[1], xyz[2]);
    }
}
