package com.moud.server.minestom.runtime;

import com.moud.core.scene.Node;
import com.moud.core.scene.PlainNode;
import com.moud.core.util.ParseUtils;
import com.moud.server.minestom.engine.MinestomNodeTypesProvider;
import com.moud.server.minestom.engine.ServerScene;
import net.minestom.server.instance.Weather;

final class RuntimeWorldEnvironmentSystem {
    void ensureWorldEnvironment(ServerScene scene) {
        if (scene == null || scene.engine() == null || scene.engine().sceneTree() == null) {
            return;
        }
        Node root = scene.engine().sceneTree().root();
        if (root == null) {
            return;
        }
        String mode = root.getProperty(MinestomNodeTypesProvider.PROP_SCENE_MODE);
        boolean is2d = mode != null && mode.equalsIgnoreCase("2d");

        Node existing = root.findChild("WorldEnvironment");
        if (is2d) {
            if (existing != null) {
                existing.queueFree();
                scene.engine().bumpSceneRevision();
            }
            return;
        }
        if (existing != null) {
            return;
        }
        PlainNode env = new PlainNode("WorldEnvironment");
        env.setProperty("@type", "WorldEnvironment");
        scene.engine().nodeTypes().applyDefaults(env, "WorldEnvironment");
        root.addChild(env);
        scene.engine().bumpSceneRevision();
    }

    WorldEnvironment readWorldEnvironment(ServerScene scene) {
        Node root = scene.engine().sceneTree().root();
        Node node = root.findChild("WorldEnvironment");
        if (node == null) {
            return new WorldEnvironment(
                    false,
                    0.5f,
                    0.5f,
                    0.5f,
                    0.02f,
                    true,
                    6000,
                    "clear",
                    1.0f
            );
        }
        boolean fogEnabled = ParseUtils.parseBool(node.getProperty("fog_enabled"));
        float fogDensity = ParseUtils.parseFloat(node.getProperty("fog_density"), 0.02f);

        float fogColorR = ParseUtils.parseFloat(node.getProperty("fog_color_r"), Float.NaN);
        float fogColorG = ParseUtils.parseFloat(node.getProperty("fog_color_g"), Float.NaN);
        float fogColorB = ParseUtils.parseFloat(node.getProperty("fog_color_b"), Float.NaN);

        if (Float.isNaN(fogColorR) && Float.isNaN(fogColorG) && Float.isNaN(fogColorB)) {
            float[] legacy = ParseUtils.parseLegacyRgb(node.getProperty("fog_color"), new float[]{0.5f, 0.5f, 0.5f});
            fogColorR = legacy[0];
            fogColorG = legacy[1];
            fogColorB = legacy[2];
        } else {
            fogColorR = ParseUtils.finiteOr(fogColorR, 0.5f);
            fogColorG = ParseUtils.finiteOr(fogColorG, 0.5f);
            fogColorB = ParseUtils.finiteOr(fogColorB, 0.5f);
        }

        boolean timeEnabled = ParseUtils.parseBool(ParseUtils.defaulted(node.getProperty("time_enabled"), "true"));
        int timeTicks = ParseUtils.parseInt(node.getProperty("time_ticks"), 6000);
        String weather = ParseUtils.defaulted(node.getProperty("weather"), "clear");
        float ambientLight = ParseUtils.parseFloat(node.getProperty("ambient_light"), 1.0f);

        return new WorldEnvironment(
                fogEnabled,
                fogColorR,
                fogColorG,
                fogColorB,
                fogDensity,
                timeEnabled,
                timeTicks,
                weather,
                ambientLight
        );
    }

    void applyWorldEnvironment(ServerScene scene, WorldEnvironment env) {
        if (scene == null || env == null) {
            return;
        }

        if (env.timeEnabled()) {
            scene.instance().setTimeRate(0);
            scene.instance().setTime(env.timeTicks());
        } else {
            scene.instance().setTimeRate(1);
        }

        scene.instance().setWeather(toWeather(env.weather()));
    }

    private static Weather toWeather(String value) {
        if (value == null) {
            return Weather.CLEAR;
        }
        return switch (value.trim().toLowerCase()) {
            case "rain" -> Weather.RAIN;
            case "thunder" -> Weather.THUNDER;
            default -> Weather.CLEAR;
        };
    }

    record WorldEnvironment(
            boolean fogEnabled,
            float fogColorR,
            float fogColorG,
            float fogColorB,
            float fogDensity,
            boolean timeEnabled,
            int timeTicks,
            String weather,
            float ambientLight
    ) {
    }
}
