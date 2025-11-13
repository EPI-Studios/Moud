package com.moud.client.audio;

import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Map;


public final class ManagedSoundOptions {

    private final @Nullable Identifier soundEventId;
    private final @Nullable SoundCategory category;
    private final float baseVolume;
    private final float basePitch;
    private final @Nullable Boolean loop;
    private final long fadeInMs;
    private final long fadeOutMs;
    private final @Nullable Boolean positional;
    private final @Nullable Vector3f position;
    private final @Nullable Float maxDistance;
    private final @Nullable PitchRamp pitchRamp;
    private final @Nullable String crossFadeGroup;
    private final long crossFadeDurationMs;

    public ManagedSoundOptions(@Nullable Identifier soundEventId,
                               @Nullable SoundCategory category,
                               float baseVolume,
                               float basePitch,
                               @Nullable Boolean loop,
                               long fadeInMs,
                               long fadeOutMs,
                               @Nullable Boolean positional,
                               @Nullable Vector3f position,
                               @Nullable Float maxDistance,
                               @Nullable PitchRamp pitchRamp,
                               @Nullable String crossFadeGroup,
                               long crossFadeDurationMs) {
        this.soundEventId = soundEventId;
        this.category = category;
        this.baseVolume = baseVolume;
        this.basePitch = basePitch;
        this.loop = loop;
        this.fadeInMs = fadeInMs;
        this.fadeOutMs = fadeOutMs;
        this.positional = positional;
        this.position = position != null ? new Vector3f(position) : null;
        this.maxDistance = maxDistance;
        this.pitchRamp = pitchRamp;
        this.crossFadeGroup = crossFadeGroup;
        this.crossFadeDurationMs = crossFadeDurationMs;
    }

    public @Nullable Identifier soundEventIdRaw() {
        return soundEventId;
    }

    public Identifier soundEventIdOrThrow() {
        if (soundEventId == null) {
            throw new IllegalStateException("Sound event id requested but not set");
        }
        return soundEventId;
    }

    public @Nullable Identifier soundEventId() {
        return soundEventId;
    }

    public SoundCategory category() {
        return category != null ? category : SoundCategory.MASTER;
    }

    public @Nullable SoundCategory categoryRaw() {
        return category;
    }

    public float baseVolume() {
        return baseVolume;
    }

    public float basePitch() {
        return basePitch;
    }

    public boolean loop() {
        return Boolean.TRUE.equals(loop);
    }

    public @Nullable Boolean loopRaw() {
        return loop;
    }

    public long fadeInMs() {
        return fadeInMs;
    }

    public long fadeOutMs() {
        return fadeOutMs;
    }

    public boolean positional() {
        return Boolean.TRUE.equals(positional);
    }

    public @Nullable Boolean positionalRaw() {
        return positional;
    }

    public Vector3f positionOrDefault() {
        return position != null ? new Vector3f(position) : new Vector3f();
    }

    public @Nullable Vector3f positionRaw() {
        return position != null ? new Vector3f(position) : null;
    }

    public @Nullable Float maxDistance() {
        return maxDistance;
    }

    public @Nullable PitchRamp pitchRamp() {
        return pitchRamp;
    }

    public @Nullable String crossFadeGroup() {
        return crossFadeGroup;
    }

    public long crossFadeDurationMs() {
        return crossFadeDurationMs;
    }

    public ManagedSoundOptions merge(ManagedSoundOptions update) {
        Identifier mergedId = update.soundEventId != null ? update.soundEventId : this.soundEventId;
        SoundCategory mergedCategory = update.category != null ? update.category : this.category;
        float mergedVolume = !Float.isNaN(update.baseVolume) ? update.baseVolume : this.baseVolume;
        float mergedPitch = !Float.isNaN(update.basePitch) ? update.basePitch : this.basePitch;
        Boolean mergedLoop = update.loop != null ? update.loop : this.loop;
        long mergedFadeIn = update.fadeInMs != Long.MIN_VALUE ? update.fadeInMs : this.fadeInMs;
        long mergedFadeOut = update.fadeOutMs != Long.MIN_VALUE ? update.fadeOutMs : this.fadeOutMs;
        Boolean mergedPositional = update.positional != null ? update.positional : this.positional;
        Vector3f mergedPosition = update.position != null ? new Vector3f(update.position) : (this.position != null ? new Vector3f(this.position) : null);
        Float mergedDistance = update.maxDistance != null ? update.maxDistance : this.maxDistance;
        PitchRamp mergedRamp = update.pitchRamp != null ? update.pitchRamp : this.pitchRamp;
        String mergedGroup = update.crossFadeGroup != null ? update.crossFadeGroup : this.crossFadeGroup;
        long mergedCrossFade = update.crossFadeDurationMs != Long.MIN_VALUE ? update.crossFadeDurationMs : this.crossFadeDurationMs;

        return new ManagedSoundOptions(
                mergedId,
                mergedCategory,
                mergedVolume,
                mergedPitch,
                mergedLoop,
                mergedFadeIn,
                mergedFadeOut,
                mergedPositional,
                mergedPosition,
                mergedDistance,
                mergedRamp,
                mergedGroup,
                mergedCrossFade
        );
    }

    public static @Nullable ManagedSoundOptions fromPayload(Map<String, Object> payload) {
        Identifier sound = identifier(payload.get("sound"));
        if (sound == null) {
            return null;
        }

        SoundCategory category = category(payload.get("category"));
        float volume = floatValue(payload.get("volume"), 1.0f);
        float pitch = floatValue(payload.get("pitch"), 1.0f);
        Boolean loop = booleanObject(payload.get("loop"));
        long fadeIn = longValue(payload.get("fadeInMs"), 0L);
        long fadeOut = longValue(payload.get("fadeOutMs"), 0L);
        Boolean positional = booleanObject(payload.get("positional"));
        Vector3f position = vector(payload.get("position"));
        Float maxDistance = floatNullable(payload.get("maxDistance"));
        PitchRamp ramp = PitchRamp.from(payload.get("pitchRamp"));
        String crossFadeGroup = string(payload.get("crossFadeGroup"));
        long crossFade = longValue(payload.get("crossFadeMs"), fadeOut);

        return new ManagedSoundOptions(
                sound,
                category,
                volume,
                pitch,
                loop != null ? loop : Boolean.FALSE,
                fadeIn,
                fadeOut,
                positional != null ? positional : Boolean.FALSE,
                position,
                maxDistance,
                ramp,
                crossFadeGroup,
                crossFade
        );
    }

    public static ManagedSoundOptions partialFromPayload(Map<String, Object> payload) {
        Identifier sound = identifier(payload.get("sound"));
        SoundCategory category = payload.containsKey("category") ? category(payload.get("category")) : null;
        float volume = payload.containsKey("volume") ? floatValue(payload.get("volume"), Float.NaN) : Float.NaN;
        float pitch = payload.containsKey("pitch") ? floatValue(payload.get("pitch"), Float.NaN) : Float.NaN;
        Boolean loop = payload.containsKey("loop") ? booleanObject(payload.get("loop")) : null;
        long fadeIn = payload.containsKey("fadeInMs") ? longValue(payload.get("fadeInMs"), 0L) : Long.MIN_VALUE;
        long fadeOut = payload.containsKey("fadeOutMs") ? longValue(payload.get("fadeOutMs"), 0L) : Long.MIN_VALUE;
        Boolean positional = payload.containsKey("positional") ? booleanObject(payload.get("positional")) : null;
        Vector3f position = payload.containsKey("position") ? vector(payload.get("position")) : null;
        Float maxDistance = payload.containsKey("maxDistance") ? floatNullable(payload.get("maxDistance")) : null;
        PitchRamp ramp = PitchRamp.from(payload.get("pitchRamp"));
        String crossFadeGroup = string(payload.get("crossFadeGroup"));
        long crossFade = payload.containsKey("crossFadeMs") ? longValue(payload.get("crossFadeMs"), 0L) : Long.MIN_VALUE;

        return new ManagedSoundOptions(
                sound,
                category,
                volume,
                pitch,
                loop,
                fadeIn,
                fadeOut,
                positional,
                position,
                maxDistance,
                ramp,
                crossFadeGroup,
                crossFade
        );
    }

    private static @Nullable Identifier identifier(Object value) {
        if (value instanceof String string && !string.isEmpty()) {
            return Identifier.tryParse(string);
        }
        return null;
    }

    private static SoundCategory category(Object value) {
        if (value instanceof String string) {
            try {
                return SoundCategory.valueOf(string.toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return SoundCategory.MASTER;
    }

    private static float floatValue(Object value, float fallback) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return fallback;
    }

    private static @Nullable Float floatNullable(Object value) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return null;
    }

    private static long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return fallback;
    }

    private static @Nullable Boolean booleanObject(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return null;
    }

    private static String string(Object value) {
        return value instanceof String string && !string.isEmpty() ? string : null;
    }

    private static Vector3f vector(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object ox = map.get("x");
            Object oy = map.get("y");
            Object oz = map.get("z");
            float x = floatValue(ox != null ? ox : 0.0f, 0.0f);
            float y = floatValue(oy != null ? oy : 0.0f, 0.0f);
            float z = floatValue(oz != null ? oz : 0.0f, 0.0f);
            return new Vector3f(x, y, z);
        }
        if (value instanceof Iterable<?> iterable) {
            float[] components = new float[3];
            int index = 0;
            for (Object element : iterable) {
                if (element instanceof Number number && index < components.length) {
                    components[index++] = number.floatValue();
                }
            }
            return new Vector3f(components[0], components[1], components[2]);
        }
        return new Vector3f();
    }


    public record PitchRamp(float targetPitch, long durationMs, String easing) {
        public static @Nullable PitchRamp from(Object value) {
            if (value instanceof Map<?, ?> map) {
                Object pitchObj = map.get("pitch");
                if (!(pitchObj instanceof Number pitchNumber)) {
                    return null;
                }
                float targetPitch = pitchNumber.floatValue();
                long duration = longValue(map.get("durationMs"), 0L);
                String easing = string(map.get("easing"));
                return new PitchRamp(targetPitch, duration, easing != null ? easing : "linear");
            }
            return null;
        }
    }
}
