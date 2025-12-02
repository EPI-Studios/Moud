package com.moud.server.proxy;

import com.moud.api.particle.Billboarding;
import com.moud.api.particle.CollisionMode;
import com.moud.api.particle.ColorKeyframe;
import com.moud.api.particle.Ease;
import com.moud.api.particle.FrameAnimation;
import com.moud.api.particle.LightSettings;
import com.moud.api.particle.ParticleDescriptor;
import com.moud.api.particle.ParticleEmitterConfig;
import com.moud.api.particle.RenderType;
import com.moud.api.particle.ScalarKeyframe;
import com.moud.api.particle.SortHint;
import com.moud.api.particle.UVRegion;
import com.moud.api.particle.Vector3f;
import com.moud.server.logging.MoudLogger;
import com.moud.server.particle.ParticleBatcher;
import com.moud.server.particle.ParticleEmitterManager;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class ParticleAPIProxy {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(ParticleAPIProxy.class);
    private static final float DEFAULT_LIFETIME = 1.0f;
    private static final float DEFAULT_EMITTER_RATE = 10f;
    private static final int DEFAULT_MAX_PARTICLES = 2048;

    private final ParticleBatcher batcher;
    private final ParticleEmitterManager emitterManager;

    public ParticleAPIProxy(ParticleBatcher batcher, ParticleEmitterManager emitterManager) {
        this.batcher = batcher;
        this.emitterManager = emitterManager;
    }

    @HostAccess.Export
    public void spawn(Object descriptor) {
        if (descriptor == null) {
            return;
        }

        LOGGER.info("ParticleAPI.spawn called with descriptor type {}", descriptor.getClass().getSimpleName());

        if (descriptor instanceof List<?> list) {
            spawnMany(list);
            return;
        }

        if (descriptor instanceof Map<?, ?> map) {
            ParticleDescriptor built = buildDescriptor(map);
            batcher.enqueue(built);
            return;
        }

        if (descriptor instanceof Value value) {
            if (value.hasArrayElements()) {
                List<Object> items = new ArrayList<>();
                long size = value.getArraySize();
                for (long i = 0; i < size; i++) {
                    items.add(value.getArrayElement(i).as(Object.class));
                }
                spawnMany(items);
            } else {
                Map<?, ?> map = value.as(Map.class);
                ParticleDescriptor built = buildDescriptor(map);
                batcher.enqueue(built);
            }
            return;
        }

        LOGGER.warn("Unsupported particle descriptor type: {}", descriptor.getClass().getName());
    }

    @HostAccess.Export
    public void createEmitter(Object config) {
        ParticleEmitterConfig built = buildEmitter(config);
        emitterManager.upsert(built);
    }

    @HostAccess.Export
    public void updateEmitter(Object config) {
        ParticleEmitterConfig built = buildEmitter(config);
        emitterManager.upsert(built);
    }

    @HostAccess.Export
    public void removeEmitter(String id) {
        emitterManager.remove(id);
    }

    @HostAccess.Export
    public void spawnMany(List<?> descriptors) {
        if (descriptors == null) {
            return;
        }
        LOGGER.info("ParticleAPI.spawnMany called with {} descriptors", descriptors.size());
        for (Object raw : descriptors) {
            if (raw instanceof Map<?, ?> map) {
                ParticleDescriptor built = buildDescriptor(map);
                batcher.enqueue(built);
            } else if (raw instanceof Value value) {
                Map<?, ?> map = value.as(Map.class);
                ParticleDescriptor built = buildDescriptor(map);
                batcher.enqueue(built);
            } else {
                LOGGER.warn("Skipping particle descriptor of unsupported type {}", raw != null ? raw.getClass().getName() : "null");
            }
        }
    }

    private ParticleEmitterConfig buildEmitter(Object raw) {
        Map<?, ?> map = toMap(raw);
        if (map == null) {
            throw new IllegalArgumentException("Emitter config must be a map or value");
        }
        String id = string(map.get("id"));
        if (id.isBlank()) {
            throw new IllegalArgumentException("Emitter id is required");
        }

        ParticleEmitterConfig existing = emitterManager.get(id);
        Object descriptorObj = map.containsKey("descriptor") ? map.get("descriptor") : map.get("particle");
        ParticleDescriptor descriptor = null;
        Map<?, ?> descriptorMap = descriptorObj != null ? toMap(descriptorObj) : null;
        if (descriptorMap == null && descriptorObj == null) {
            descriptorMap = toMap(map);
        }
        if (descriptorMap != null) {
            descriptor = buildDescriptor(descriptorMap);
        } else if (existing != null) {
            descriptor = existing.descriptor();
        }

        if (descriptor == null) {
            throw new IllegalArgumentException("Emitter descriptor is required for emitter " + id);
        }

        boolean hasRate = map.containsKey("spawnRate") || map.containsKey("rate");
        float rate = hasRate
                ? number(map.containsKey("spawnRate") ? map.get("spawnRate") : map.get("rate"), DEFAULT_EMITTER_RATE)
                : (existing != null ? existing.ratePerSecond() : DEFAULT_EMITTER_RATE);

        boolean hasEnabled = map.containsKey("enabled");
        boolean enabled = hasEnabled ? bool(map.get("enabled"), true) : existing == null || existing.enabled();

        boolean hasMax = map.containsKey("maxParticles");
        int maxParticles = hasMax
                ? (int) number(map.get("maxParticles"), DEFAULT_MAX_PARTICLES)
                : (existing != null ? existing.maxParticles() : DEFAULT_MAX_PARTICLES);

        Vector3f positionJitter = map.containsKey("positionJitter")
                ? vec(map.get("positionJitter"), false)
                : (existing != null ? existing.positionJitter() : new Vector3f(0f, 0f, 0f));

        Vector3f velocityJitter = map.containsKey("velocityJitter")
                ? vec(map.get("velocityJitter"), false)
                : (existing != null ? existing.velocityJitter() : new Vector3f(0f, 0f, 0f));

        float lifetimeJitter = map.containsKey("lifetimeJitter")
                ? number(map.get("lifetimeJitter"), 0f)
                : (existing != null ? existing.lifetimeJitter() : 0f);

        long seed = map.get("seed") instanceof Number n
                ? n.longValue()
                : (existing != null ? existing.seed() : ThreadLocalRandom.current().nextLong());

        List<String> texturePool = stringList(map.get("textures"));

        return new ParticleEmitterConfig(
                id,
                descriptor,
                rate,
                enabled,
                maxParticles,
                positionJitter,
                velocityJitter,
                lifetimeJitter,
                seed,
                texturePool
        );
    }

    private ParticleDescriptor buildDescriptor(Map<?, ?> raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Particle descriptor is null");
        }

        String texture = string(raw.get("texture"));
        float lifetime = number(raw.get("lifetime"), DEFAULT_LIFETIME);

        Vector3f position = vec(raw.get("position"), true);
        Vector3f velocity = vec(raw.get("velocity"), false);
        Vector3f acceleration = vec(raw.get("acceleration"), false);

        RenderType renderType = enumOrDefault(raw.get("renderType"), RenderType.TRANSLUCENT, RenderType.class);
        Billboarding billboard = enumOrDefault(raw.get("billboarding"), Billboarding.CAMERA_FACING, Billboarding.class);
        CollisionMode collision = enumOrDefault(raw.get("collision"), CollisionMode.NONE, CollisionMode.class);
        boolean collideWithPlayers = bool(raw.get("collideWithPlayers"), false);
        SortHint sortHint = enumOrDefault(raw.get("sortHint"), SortHint.NONE, SortHint.class);

        float drag = number(raw.get("drag"), 0f);
        float gravity = number(raw.get("gravityMultiplier"), 1f);

        List<ScalarKeyframe> size = scalarKeyframes(raw.get("sizeOverLife"));
        List<ScalarKeyframe> rotation = scalarKeyframes(raw.get("rotationOverLife"));
        List<ScalarKeyframe> alpha = scalarKeyframes(raw.get("alphaOverLife"));
        List<ColorKeyframe> colors = colorKeyframes(raw.get("colorOverLife"));

        UVRegion uv = uvRegion(raw.get("uvRegion"));
        FrameAnimation frameAnimation = frameAnimation(raw.get("frameAnimation"));

        int impostorSlices = (int) number(raw.get("impostorSlices"), 1f);

        List<String> behaviors = stringList(raw.get("behaviors"));
        Map<String, Object> behaviorPayload = rawPayload(raw.get("behaviorPayload"));
        LightSettings light = light(raw.get("light"));

        return new ParticleDescriptor(
                texture,
                renderType,
                billboard,
                collision,
                collideWithPlayers,
                position,
                velocity,
                acceleration,
                drag,
                gravity,
                lifetime,
                size,
                rotation,
                colors,
                alpha,
                uv,
                frameAnimation,
                behaviors,
                behaviorPayload,
                light,
                sortHint,
                impostorSlices
        );
    }

    private Vector3f vec(Object raw, boolean required) {
        if (raw instanceof Map<?, ?> map) {
            float x = number(map.get("x"), 0f);
            float y = number(map.get("y"), 0f);
            float z = number(map.get("z"), 0f);
            return new Vector3f(x, y, z);
        }
        if (required) {
            throw new IllegalArgumentException("Particle descriptor missing required position vector");
        }
        return new Vector3f(0f, 0f, 0f);
    }

    private List<ScalarKeyframe> scalarKeyframes(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return Collections.emptyList();
        }
        List<ScalarKeyframe> frames = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> map) {
                float t = number(map.get("t"), 0f);
                float value = number(map.get("value"), 0f);
                Ease ease = enumOrDefault(map.get("ease"), null, Ease.class);
                frames.add(new ScalarKeyframe(t, value, ease));
            }
        }
        return frames;
    }

    private List<ColorKeyframe> colorKeyframes(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return Collections.emptyList();
        }
        List<ColorKeyframe> frames = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> map) {
                float t = number(map.get("t"), 0f);
                float r = number(map.get("r"), 1f);
                float g = number(map.get("g"), 1f);
                float b = number(map.get("b"), 1f);
                float a = number(map.get("a"), 1f);
                Ease ease = enumOrDefault(map.get("ease"), null, Ease.class);
                frames.add(new ColorKeyframe(t, r, g, b, a, ease));
            }
        }
        return frames;
    }

    private UVRegion uvRegion(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            float u0 = number(map.get("u0"), 0f);
            float v0 = number(map.get("v0"), 0f);
            float u1 = number(map.get("u1"), 1f);
            float v1 = number(map.get("v1"), 1f);
            return new UVRegion(u0, v0, u1, v1);
        }
        return null;
    }

    private FrameAnimation frameAnimation(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            int frames = (int) number(map.get("frames"), 1);
            float fps = number(map.get("fps"), 0f);
            boolean loop = bool(map.get("loop"), true);
            boolean pingPong = bool(map.get("pingPong"), false);
            int startFrame = (int) number(map.get("startFrame"), 0);
            return new FrameAnimation(frames, fps, loop, pingPong, startFrame);
        }
        return null;
    }

    private LightSettings light(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            int block = (int) number(map.get("block"), 0);
            int sky = (int) number(map.get("sky"), 0);
            boolean emissive = bool(map.get("emissive"), false);
            return new LightSettings(block, sky, emissive);
        }
        return new LightSettings(0, 0, false);
    }

    private Map<String, Object> rawPayload(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private Map<?, ?> toMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return map;
        }
        if (raw instanceof Value value) {
            return value.as(Map.class);
        }
        return null;
    }

    private List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o != null) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    private String string(Object raw) {
        return raw != null ? Objects.toString(raw) : "";
    }

    private float number(Object raw, float fallback) {
        if (raw instanceof Number n) {
            return n.floatValue();
        }
        return fallback;
    }

    private boolean bool(Object raw, boolean fallback) {
        if (raw instanceof Boolean b) {
            return b;
        }
        return fallback;
    }

    private <T extends Enum<T>> T enumOrDefault(Object raw, T fallback, Class<T> type) {
        if (raw instanceof String s) {
            for (T constant : type.getEnumConstants()) {
                if (constant.name().equalsIgnoreCase(s) || constant.name().replace("_", "").equalsIgnoreCase(s.replace("_", ""))) {
                    return constant;
                }
            }
        } else if (type.isInstance(raw)) {
            return type.cast(raw);
        }
        return fallback;
    }
}
