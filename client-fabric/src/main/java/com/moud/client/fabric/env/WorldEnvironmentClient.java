package com.moud.client.fabric.env;


import com.moud.client.fabric.scene.ClientSceneBus;
import com.moud.core.util.ParseUtils;
import com.moud.net.protocol.SceneSnapshot;
import java.util.List;
import java.util.Locale;

public final class WorldEnvironmentClient {
    private static final Object LOCK = new Object();
    private static long cachedSceneVersion = Long.MIN_VALUE;
    private static EnvSettings cachedEnv = EnvSettings.defaults();

    private WorldEnvironmentClient() {
    }

    public static EnvSettings current() {
        long v = ClientSceneBus.version();
        synchronized (LOCK) {
            if (v == cachedSceneVersion) {
                return cachedEnv;
            }
            cachedSceneVersion = v;
            cachedEnv = readEnvFromScene(ClientSceneBus.copyNodes());
            return cachedEnv;
        }
    }

    private static EnvSettings readEnvFromScene(List<SceneSnapshot.NodeSnapshot> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return EnvSettings.defaults();
        }
        SceneSnapshot.NodeSnapshot env = null;
        for (SceneSnapshot.NodeSnapshot n : nodes) {
            if (n == null) {
                continue;
            }
            if ("WorldEnvironment".equals(n.type()) || "WorldEnvironment".equals(n.name())) {
                env = n;
                break;
            }
        }
        if (env == null) {
            return EnvSettings.defaults();
        }

        String skyMode = defaulted(stringProp(env, "sky_mode"), "vanilla");
        String skyShader = defaulted(stringProp(env, "sky_shader"), "");
        String skyMaterial = defaulted(stringProp(env, "sky_material"), "");

        float skyTopR = Math.max(0.0f, Math.min(1.0f, ParseUtils.parseFloat(stringProp(env, "sky_color_top_r"), 0.2f)));
        float skyTopG = Math.max(0.0f, Math.min(1.0f, ParseUtils.parseFloat(stringProp(env, "sky_color_top_g"), 0.4f)));
        float skyTopB = Math.max(0.0f, Math.min(1.0f, ParseUtils.parseFloat(stringProp(env, "sky_color_top_b"), 0.9f)));
        float skyHorizonR = Math.max(0.0f, Math.min(1.0f, ParseUtils.parseFloat(stringProp(env, "sky_color_horizon_r"), 0.9f)));
        float skyHorizonG = Math.max(0.0f, Math.min(1.0f, ParseUtils.parseFloat(stringProp(env, "sky_color_horizon_g"), 0.9f)));
        float skyHorizonB = Math.max(0.0f, Math.min(1.0f, ParseUtils.parseFloat(stringProp(env, "sky_color_horizon_b"), 1.0f)));
        float skySunriseR = Math.max(0.0f, Math.min(1.0f, ParseUtils.parseFloat(stringProp(env, "sky_color_sunrise_r"), 1.0f)));
        float skySunriseG = Math.max(0.0f, Math.min(1.0f, ParseUtils.parseFloat(stringProp(env, "sky_color_sunrise_g"), 0.4f)));
        float skySunriseB = Math.max(0.0f, Math.min(1.0f, ParseUtils.parseFloat(stringProp(env, "sky_color_sunrise_b"), 0.2f)));
        float skySunriseStrength = Math.max(0.0f, Math.min(1.0f, ParseUtils.parseFloat(stringProp(env, "sky_color_sunrise_strength"), 1.0f)));

        String cloudsMode = defaulted(stringProp(env, "clouds_mode"), "vanilla");
        String cloudsShader = defaulted(stringProp(env, "clouds_shader"), "");
        String cloudsMaterial = defaulted(stringProp(env, "clouds_material"), "");
        float cloudHeight = ParseUtils.parseFloat(stringProp(env, "cloud_height"), 128.0f);
        float cloudSpeed = ParseUtils.parseFloat(stringProp(env, "cloud_speed"), 1.0f);
        float cloudOffsetX = ParseUtils.parseFloat(stringProp(env, "cloud_offset_x"), 0.0f);
        float cloudOffsetZ = ParseUtils.parseFloat(stringProp(env, "cloud_offset_z"), 0.0f);
        float cloudScale = ParseUtils.parseFloat(stringProp(env, "cloud_scale"), 1.0f);
        if (!Float.isFinite(cloudScale) || cloudScale <= 0.0f) {
            cloudScale = 1.0f;
        }

        return new EnvSettings(
                Mode.parse(skyMode),
                skyShader,
                skyMaterial,
                skyTopR,
                skyTopG,
                skyTopB,
                skyHorizonR,
                skyHorizonG,
                skyHorizonB,
                skySunriseR,
                skySunriseG,
                skySunriseB,
                skySunriseStrength,
                Mode.parse(cloudsMode),
                cloudsShader,
                cloudsMaterial,
                cloudHeight,
                cloudSpeed,
                cloudOffsetX,
                cloudOffsetZ,
                cloudScale
        );
    }

    private static String stringProp(SceneSnapshot.NodeSnapshot node, String key) {
        if (node == null || key == null || key.isBlank() || node.properties() == null) {
            return null;
        }
        for (SceneSnapshot.Property p : node.properties()) {
            if (p != null && key.equals(p.key())) {
                return p.value();
            }
        }
        return null;
    }

    private static String defaulted(String v, String fallback) {
        if (v == null || v.isBlank()) {
            return fallback;
        }
        return v;
    }

    public enum Mode {
        VANILLA,
        CUSTOM,
        OFF;

        public static Mode parse(String s) {
            if (s == null) {
                return VANILLA;
            }
            return switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "custom" -> CUSTOM;
                case "off", "none", "disabled" -> OFF;
                default -> VANILLA;
            };
        }
    }

    public record EnvSettings(
            Mode skyMode,
            String skyShader,
            String skyMaterial,
            float skyTopR,
            float skyTopG,
            float skyTopB,
            float skyHorizonR,
            float skyHorizonG,
            float skyHorizonB,
            float skySunriseR,
            float skySunriseG,
            float skySunriseB,
            float skySunriseStrength,
            Mode cloudsMode,
            String cloudsShader,
            String cloudsMaterial,
            float cloudHeight,
            float cloudSpeed,
            float cloudOffsetX,
            float cloudOffsetZ,
            float cloudScale
    ) {
        public static EnvSettings defaults() {
            return new EnvSettings(
                    Mode.VANILLA,
                    "",
                    "",
                    0.2f,
                    0.4f,
                    0.9f,
                    0.9f,
                    0.9f,
                    1.0f,
                    1.0f,
                    0.4f,
                    0.2f,
                    1.0f,
                    Mode.VANILLA,
                    "",
                    "",
                    128.0f,
                    1.0f,
                    0.0f,
                    0.0f,
                    1.0f
            );
        }
    }
}
