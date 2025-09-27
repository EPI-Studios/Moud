package com.moud.client.animation;

import com.moud.api.math.Vector3;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerPartConfigManager {
    private static final PlayerPartConfigManager INSTANCE = new PlayerPartConfigManager();
    private final Map<UUID, Map<String, PartConfig>> playerConfigs = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerInterpolationSettings> playerSettings = new ConcurrentHashMap<>();
    private static InterpolationSettings globalSettings = new InterpolationSettings();

    private PlayerPartConfigManager() {}

    public static PlayerPartConfigManager getInstance() {
        return INSTANCE;
    }

    public void updatePartConfig(UUID playerId, String partName, Map<String, Object> properties) {
        PartConfig config = playerConfigs.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(partName, k -> new PartConfig());

        Vector3 oldRotation = config.rotation;
        Vector3 oldPosition = config.position;
        Vector3 oldScale = config.scale;

        config.update(properties);

        InterpolationSettings settings = getEffectiveSettings(playerId);
        if (settings.enabled) {
            config.setupInterpolation(oldRotation, oldPosition, oldScale, settings);
        }
    }

    public PartConfig getPartConfig(UUID playerId, String partName) {
        Map<String, PartConfig> parts = playerConfigs.get(playerId);
        return parts != null ? parts.get(partName) : null;
    }

    public void setPlayerInterpolationSettings(UUID playerId, InterpolationSettings settings) {
        playerSettings.computeIfAbsent(playerId, k -> new PlayerInterpolationSettings()).update(settings);
    }

    public static void setGlobalInterpolationSettings(InterpolationSettings settings) {
        globalSettings = settings;
    }

    private InterpolationSettings getEffectiveSettings(UUID playerId) {
        PlayerInterpolationSettings playerSetting = playerSettings.get(playerId);
        if (playerSetting != null && playerSetting.hasOverrides()) {
            return playerSetting.getEffectiveSettings(globalSettings);
        }
        return globalSettings;
    }

    public void clearConfig(UUID playerId) {
        playerConfigs.remove(playerId);
        playerSettings.remove(playerId);
    }

    public static class PartConfig {
        public Vector3 position;
        public Vector3 rotation;
        public Vector3 scale;
        public Boolean visible;
        public boolean overrideAnimation = false;

        private Vector3 fromRotation;
        private Vector3 fromPosition;
        private Vector3 fromScale;
        private Vector3 targetRotation;
        private Vector3 targetPosition;
        private Vector3 targetScale;
        private long interpolationStart;
        private InterpolationSettings interpolationSettings;
        private boolean isInterpolating = false;

        public void update(Map<String, Object> props) {
            if (props.containsKey("position")) this.position = parseVector(props.get("position"));
            if (props.containsKey("rotation")) this.rotation = parseVector(props.get("rotation"));
            if (props.containsKey("scale")) this.scale = parseVector(props.get("scale"));
            if (props.containsKey("visible")) {
                Object visibleProp = props.get("visible");
                if (visibleProp instanceof Boolean) {
                    this.visible = (Boolean) visibleProp;
                }
            }
            if (props.containsKey("overrideAnimation")) {
                Object overrideProp = props.get("overrideAnimation");
                if (overrideProp instanceof Boolean) {
                    this.overrideAnimation = (Boolean) overrideProp;
                }
            }
        }

        public void setupInterpolation(Vector3 oldRotation, Vector3 oldPosition, Vector3 oldScale, InterpolationSettings settings) {
            this.interpolationSettings = settings;
            this.fromRotation = oldRotation != null ? oldRotation : Vector3.zero();
            this.fromPosition = oldPosition != null ? oldPosition : Vector3.zero();
            this.fromScale = oldScale != null ? oldScale : new Vector3(1, 1, 1);
            this.targetRotation = this.rotation != null ? this.rotation : Vector3.zero();
            this.targetPosition = this.position != null ? this.position : Vector3.zero();
            this.targetScale = this.scale != null ? this.scale : new Vector3(1, 1, 1);
            this.interpolationStart = System.currentTimeMillis();
            this.isInterpolating = true;
        }

        public Vector3 getInterpolatedRotation() {
            if (!isInterpolating || interpolationSettings == null) return rotation;
            return interpolateVector(fromRotation, targetRotation);
        }

        public Vector3 getInterpolatedPosition() {
            if (!isInterpolating || interpolationSettings == null) return position;
            return interpolateVector(fromPosition, targetPosition);
        }

        public Vector3 getInterpolatedScale() {
            if (!isInterpolating || interpolationSettings == null) return scale;
            return interpolateVector(fromScale, targetScale);
        }

        private Vector3 interpolateVector(Vector3 from, Vector3 to) {
            long elapsed = System.currentTimeMillis() - interpolationStart;
            float progress = Math.min(1.0f, elapsed / (float) interpolationSettings.duration);

            if (progress >= 1.0f) {
                isInterpolating = false;
                return to;
            }

            float easedProgress = applyEasing(progress, interpolationSettings.easing);
            return Vector3.lerp(from, to, easedProgress);
        }

        private float applyEasing(float t, EasingType easing) {
            return switch (easing) {
                case LINEAR -> t;
                case EASE_IN -> t * t;
                case EASE_OUT -> 1 - (1 - t) * (1 - t);
                case EASE_IN_OUT -> t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
                case BOUNCE -> {
                    if (t < 1 / 2.75f) {
                        yield 7.5625f * t * t;
                    } else if (t < 2 / 2.75f) {
                        yield 7.5625f * (t -= 1.5f / 2.75f) * t + 0.75f;
                    } else if (t < 2.5 / 2.75) {
                        yield 7.5625f * (t -= 2.25f / 2.75f) * t + 0.9375f;
                    } else {
                        yield 7.5625f * (t -= 2.625f / 2.75f) * t + 0.984375f;
                    }
                }
            };
        }

        private Vector3 parseVector(Object obj) {
            if (obj instanceof Vector3) {
                return (Vector3) obj;
            }
            if (obj instanceof Map<?,?> map) {
                Number xVal = (Number) map.get("x");
                Number yVal = (Number) map.get("y");
                Number zVal = (Number) map.get("z");

                double x = (xVal != null) ? xVal.doubleValue() : 0.0;
                double y = (yVal != null) ? yVal.doubleValue() : 0.0;
                double z = (zVal != null) ? zVal.doubleValue() : 0.0;

                return new Vector3(x, y, z);
            }
            return null;
        }
    }

    public static class InterpolationSettings {
        public boolean enabled = true;
        public long duration = 150;
        public EasingType easing = EasingType.EASE_OUT;
        public float speed = 1.0f;

        public InterpolationSettings() {}

        public InterpolationSettings(boolean enabled, long duration, EasingType easing, float speed) {
            this.enabled = enabled;
            this.duration = duration;
            this.easing = easing;
            this.speed = speed;
        }
    }

    public static class PlayerInterpolationSettings {
        private Boolean enabledOverride;
        private Long durationOverride;
        private EasingType easingOverride;
        private Float speedOverride;

        public void update(InterpolationSettings settings) {
            this.enabledOverride = settings.enabled;
            this.durationOverride = settings.duration;
            this.easingOverride = settings.easing;
            this.speedOverride = settings.speed;
        }

        public boolean hasOverrides() {
            return enabledOverride != null || durationOverride != null ||
                    easingOverride != null || speedOverride != null;
        }

        public InterpolationSettings getEffectiveSettings(InterpolationSettings global) {
            return new InterpolationSettings(
                    enabledOverride != null ? enabledOverride : global.enabled,
                    durationOverride != null ? durationOverride : global.duration,
                    easingOverride != null ? easingOverride : global.easing,
                    speedOverride != null ? speedOverride : global.speed
            );
        }
    }

    public enum EasingType {
        LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, BOUNCE
    }
}