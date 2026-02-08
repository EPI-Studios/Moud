package com.moud.client.util;

import com.moud.api.particle.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ParticleDescriptorMapper {
    private ParticleDescriptorMapper() {}

    public static ParticleDescriptor fromMap(String id, Map<String, Object> map) {
        if (map == null) return null;
        String texture = string(map.get("texture"), "minecraft:particle/generic_0");
        RenderType renderType = enumOrDefault(map.get("renderType"), RenderType.TRANSLUCENT, RenderType.class);
        Billboarding billboarding = enumOrDefault(map.get("billboarding"), Billboarding.CAMERA_FACING, Billboarding.class);
        CollisionMode collision = enumOrDefault(map.get("collision"), CollisionMode.NONE, CollisionMode.class);
        boolean collidePlayers = bool(map.get("collideWithPlayers"), false);
        int impostorSlices = (int) number(map.get("impostorSlices"), 1f);

        Vector3f position = vec3f(map.get("position"), new Vector3f(0f, 0f, 0f));
        Vector3f velocity = vec3f(map.get("velocity"), new Vector3f(0f, 0f, 0f));
        Vector3f acceleration = vec3f(map.get("acceleration"), new Vector3f(0f, 0f, 0f));
        float drag = (float) number(map.get("drag"), 0f);
        float gravity = (float) number(map.get("gravityMultiplier"), 1f);
        float lifetime = (float) number(map.get("lifetime"), 1f);

        List<ScalarKeyframe> size = scalarKeyframes(map.get("sizeOverLife"));
        List<ScalarKeyframe> rotation = scalarKeyframes(map.get("rotationOverLife"));
        List<ColorKeyframe> colors = colorKeyframes(map.get("colorOverLife"));
        List<ScalarKeyframe> alpha = scalarKeyframes(map.get("alphaOverLife"));

        UVRegion uv = uvRegion(map.get("uvRegion"));
        FrameAnimation frame = frameAnimation(map.get("frameAnimation"));
        List<String> behaviors = stringList(map.get("behaviors"));
        Map<String, Object> payload = rawPayload(map.get("behaviorPayload"));
        LightSettings light = light(map.get("light"));
        SortHint sortHint = enumOrDefault(map.get("sortHint"), SortHint.NONE, SortHint.class);

        return new ParticleDescriptor(
                texture,
                renderType,
                billboarding,
                collision,
                collidePlayers,
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
                frame,
                behaviors,
                payload,
                light,
                sortHint,
                impostorSlices
        );
    }

    public static Vector3f vec3f(Object o, Vector3f def) {
        if (o instanceof Map<?, ?> map) {
            float x = (float) number(map.get("x"), def.x());
            float y = (float) number(map.get("y"), def.y());
            float z = (float) number(map.get("z"), def.z());
            return new Vector3f(x, y, z);
        }
        return def;
    }

    public static List<String> stringList(Object o) {
        if (o instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object e : list) {
                if (e != null) out.add(String.valueOf(e));
            }
            return out;
        }
        return List.of();
    }

    private static UVRegion uvRegion(Object o) {
        if (o instanceof Map<?, ?> map) {
            float u0 = (float) number(map.get("u0"), 0f);
            float v0 = (float) number(map.get("v0"), 0f);
            float u1 = (float) number(map.get("u1"), 1f);
            float v1 = (float) number(map.get("v1"), 1f);
            return new UVRegion(u0, v0, u1, v1);
        }
        return null;
    }

    private static FrameAnimation frameAnimation(Object o) {
        if (o instanceof Map<?, ?> map) {
            int frames = (int) number(map.get("frames"), 1f);
            float fps = (float) number(map.get("fps"), 0f);
            boolean loop = bool(map.get("loop"), false);
            boolean pingPong = bool(map.get("pingPong"), false);
            int start = (int) number(map.get("startFrame"), 0f);
            return new FrameAnimation(frames, fps, loop, pingPong, start);
        }
        return null;
    }

    private static Map<String, Object> rawPayload(Object o) {
        if (o instanceof Map<?, ?> map) {
            java.util.HashMap<String, Object> out = new java.util.HashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return Map.of();
    }

    private static LightSettings light(Object o) {
        if (o instanceof Map<?, ?> map) {
            int block = (int) number(map.get("block"), 0f);
            int sky = (int) number(map.get("sky"), 0f);
            boolean emissive = bool(map.get("emissive"), false);
            return new LightSettings(block, sky, emissive);
        }
        return new LightSettings(0, 0, false);
    }

    private static List<ScalarKeyframe> scalarKeyframes(Object o) {
        if (o instanceof List<?> list) {
            List<ScalarKeyframe> out = new ArrayList<>();
            for (Object e : list) {
                if (e instanceof Map<?, ?> m) {
                    float t = (float) number(m.get("t"), 0f);
                    float v = (float) number(m.get("value"), 0f);
                    Ease ease = enumOrDefault(m.get("ease"), Ease.LINEAR, Ease.class);
                    out.add(new ScalarKeyframe(t, v, ease));
                }
            }
            return out;
        }
        return List.of();
    }

    private static List<ColorKeyframe> colorKeyframes(Object o) {
        if (o instanceof List<?> list) {
            List<ColorKeyframe> out = new ArrayList<>();
            for (Object e : list) {
                if (e instanceof Map<?, ?> m) {
                    float t = (float) number(m.get("t"), 0f);
                    float r = (float) number(m.get("r"), 1f);
                    float g = (float) number(m.get("g"), 1f);
                    float b = (float) number(m.get("b"), 1f);
                    float a = (float) number(m.get("a"), 1f);
                    Ease ease = enumOrDefault(m.get("ease"), Ease.LINEAR, Ease.class);
                    out.add(new ColorKeyframe(t, r, g, b, a, ease));
                }
            }
            return out;
        }
        return List.of();
    }

    private static String string(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    private static double number(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        try {
            return o != null ? Double.parseDouble(String.valueOf(o)) : def;
        } catch (Exception ignored) {
            return def;
        }
    }

    private static boolean bool(Object o, boolean def) {
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.intValue() != 0;
        if (o != null) return Boolean.parseBoolean(String.valueOf(o));
        return def;
    }

    private static <E extends Enum<E>> E enumOrDefault(Object raw, E def, Class<E> type) {
        if (raw == null) return def;
        try {
            if (raw instanceof String s) {
                return Enum.valueOf(type, s.toUpperCase());
            }
            if (raw instanceof Number n) {
                E[] values = type.getEnumConstants();
                int idx = n.intValue();
                if (idx >= 0 && idx < values.length) {
                    return values[idx];
                }
            }
        } catch (Exception ignored) {
        }
        return def;
    }
}
