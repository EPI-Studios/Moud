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
    private final long startDelayMs;
    private final long fadeInMs;
    private final @Nullable String fadeInEasing;
    private final long fadeOutMs;
    private final @Nullable String fadeOutEasing;
    private final @Nullable Boolean positional;
    private final @Nullable Vector3f position;
    private final @Nullable Float minDistance;
    private final @Nullable Float maxDistance;
    private final @Nullable String distanceModel;
    private final @Nullable Float rolloff;
    private final @Nullable PitchRamp pitchRamp;
    private final @Nullable VolumeLfo volumeLfo;
    private final @Nullable PitchLfo pitchLfo;
    private final @Nullable String mixGroup;
    private final @Nullable Ducking ducking;
    private final @Nullable String crossFadeGroup;
    private final long crossFadeDurationMs;

    public ManagedSoundOptions(@Nullable Identifier soundEventId,
                               @Nullable SoundCategory category,
                               float baseVolume,
                               float basePitch,
                               @Nullable Boolean loop,
                               long startDelayMs,
                               long fadeInMs,
                               @Nullable String fadeInEasing,
                               long fadeOutMs,
                               @Nullable String fadeOutEasing,
                               @Nullable Boolean positional,
                               @Nullable Vector3f position,
                               @Nullable Float minDistance,
                               @Nullable Float maxDistance,
                               @Nullable String distanceModel,
                               @Nullable Float rolloff,
                               @Nullable PitchRamp pitchRamp,
                               @Nullable VolumeLfo volumeLfo,
                               @Nullable PitchLfo pitchLfo,
                               @Nullable String mixGroup,
                               @Nullable Ducking ducking,
                               @Nullable String crossFadeGroup,
                               long crossFadeDurationMs) {
        this.soundEventId = soundEventId;
        this.category = category;
        this.baseVolume = baseVolume;
        this.basePitch = basePitch;
        this.loop = loop;
        this.startDelayMs = startDelayMs;
        this.fadeInMs = fadeInMs;
        this.fadeInEasing = fadeInEasing;
        this.fadeOutMs = fadeOutMs;
        this.fadeOutEasing = fadeOutEasing;
        this.positional = positional;
        this.position = position != null ? new Vector3f(position) : null;
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.distanceModel = distanceModel;
        this.rolloff = rolloff;
        this.pitchRamp = pitchRamp;
        this.volumeLfo = volumeLfo;
        this.pitchLfo = pitchLfo;
        this.mixGroup = mixGroup;
        this.ducking = ducking;
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

    public long startDelayMs() {
        return startDelayMs;
    }

    public long fadeInMs() {
        return fadeInMs;
    }

    public @Nullable String fadeInEasing() {
        return fadeInEasing;
    }

    public long fadeOutMs() {
        return fadeOutMs;
    }

    public @Nullable String fadeOutEasing() {
        return fadeOutEasing;
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

    public @Nullable Float minDistance() {
        return minDistance;
    }

    public @Nullable Float maxDistance() {
        return maxDistance;
    }

    public @Nullable String distanceModel() {
        return distanceModel;
    }

    public @Nullable Float rolloff() {
        return rolloff;
    }

    public @Nullable PitchRamp pitchRamp() {
        return pitchRamp;
    }

    public @Nullable VolumeLfo volumeLfo() {
        return volumeLfo;
    }

    public @Nullable PitchLfo pitchLfo() {
        return pitchLfo;
    }

    public @Nullable String mixGroup() {
        return mixGroup;
    }

    public @Nullable Ducking ducking() {
        return ducking;
    }

    public @Nullable String crossFadeGroup() {
        return crossFadeGroup;
    }

    public long crossFadeDurationMs() {
        return crossFadeDurationMs;
    }

    public boolean useCustomDistanceAttenuation() {
        return positional() && maxDistance != null;
    }

    public ManagedSoundOptions merge(ManagedSoundOptions update) {
        Identifier mergedId = update.soundEventId != null ? update.soundEventId : this.soundEventId;
        SoundCategory mergedCategory = update.category != null ? update.category : this.category;
        float mergedVolume = !Float.isNaN(update.baseVolume) ? update.baseVolume : this.baseVolume;
        float mergedPitch = !Float.isNaN(update.basePitch) ? update.basePitch : this.basePitch;
        Boolean mergedLoop = update.loop != null ? update.loop : this.loop;
        long mergedStartDelay = update.startDelayMs != Long.MIN_VALUE ? update.startDelayMs : this.startDelayMs;
        long mergedFadeIn = update.fadeInMs != Long.MIN_VALUE ? update.fadeInMs : this.fadeInMs;
        String mergedFadeInEasing = update.fadeInEasing != null ? update.fadeInEasing : this.fadeInEasing;
        long mergedFadeOut = update.fadeOutMs != Long.MIN_VALUE ? update.fadeOutMs : this.fadeOutMs;
        String mergedFadeOutEasing = update.fadeOutEasing != null ? update.fadeOutEasing : this.fadeOutEasing;
        Boolean mergedPositional = update.positional != null ? update.positional : this.positional;
        Vector3f mergedPosition = update.position != null
                ? new Vector3f(update.position)
                : (this.position != null ? new Vector3f(this.position) : null);
        Float mergedMinDistance = update.minDistance != null ? update.minDistance : this.minDistance;
        Float mergedDistance = update.maxDistance != null ? update.maxDistance : this.maxDistance;
        String mergedDistanceModel = update.distanceModel != null ? update.distanceModel : this.distanceModel;
        Float mergedRolloff = update.rolloff != null ? update.rolloff : this.rolloff;
        PitchRamp mergedRamp = update.pitchRamp != null ? update.pitchRamp : this.pitchRamp;
        VolumeLfo mergedVolumeLfo = update.volumeLfo != null ? update.volumeLfo : this.volumeLfo;
        PitchLfo mergedPitchLfo = update.pitchLfo != null ? update.pitchLfo : this.pitchLfo;
        String mergedMixGroup = update.mixGroup != null ? update.mixGroup : this.mixGroup;
        Ducking mergedDucking = update.ducking != null ? update.ducking : this.ducking;
        String mergedGroup = update.crossFadeGroup != null ? update.crossFadeGroup : this.crossFadeGroup;
        long mergedCrossFade = update.crossFadeDurationMs != Long.MIN_VALUE
                ? update.crossFadeDurationMs
                : this.crossFadeDurationMs;

        return new ManagedSoundOptions(
                mergedId,
                mergedCategory,
                mergedVolume,
                mergedPitch,
                mergedLoop,
                mergedStartDelay,
                mergedFadeIn,
                mergedFadeInEasing,
                mergedFadeOut,
                mergedFadeOutEasing,
                mergedPositional,
                mergedPosition,
                mergedMinDistance,
                mergedDistance,
                mergedDistanceModel,
                mergedRolloff,
                mergedRamp,
                mergedVolumeLfo,
                mergedPitchLfo,
                mergedMixGroup,
                mergedDucking,
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
        long startDelayMs = longValue(payload.get("startDelayMs"), 0L);
        long fadeIn = longValue(payload.get("fadeInMs"), 0L);
        String fadeInEasing = string(payload.get("fadeInEasing"));
        long fadeOut = longValue(payload.get("fadeOutMs"), 0L);
        String fadeOutEasing = string(payload.get("fadeOutEasing"));
        Boolean positional = booleanObject(payload.get("positional"));
        Vector3f position = vector(payload.get("position"));
        Float minDistance = floatNullable(payload.get("minDistance"));
        Float maxDistance = floatNullable(payload.get("maxDistance"));
        String distanceModel = string(payload.get("distanceModel"));
        Float rolloff = floatNullable(payload.get("rolloff"));
        PitchRamp ramp = PitchRamp.from(payload.get("pitchRamp"));
        VolumeLfo volumeLfo = VolumeLfo.from(payload.get("volumeLfo"));
        PitchLfo pitchLfo = PitchLfo.from(payload.get("pitchLfo"));
        String mixGroup = string(payload.get("mixGroup"));
        Ducking ducking = Ducking.from(payload.get("duck"));
        String crossFadeGroup = string(payload.get("crossFadeGroup"));
        long crossFade = longValue(payload.get("crossFadeMs"), fadeOut);

        return new ManagedSoundOptions(
                sound,
                category,
                volume,
                pitch,
                loop != null ? loop : Boolean.FALSE,
                startDelayMs,
                fadeIn,
                fadeInEasing,
                fadeOut,
                fadeOutEasing,
                positional != null ? positional : Boolean.FALSE,
                position,
                minDistance,
                maxDistance,
                distanceModel,
                rolloff,
                ramp,
                volumeLfo,
                pitchLfo,
                mixGroup,
                ducking,
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
        long startDelayMs = payload.containsKey("startDelayMs")
                ? longValue(payload.get("startDelayMs"), 0L)
                : Long.MIN_VALUE;
        long fadeIn = payload.containsKey("fadeInMs") ? longValue(payload.get("fadeInMs"), 0L) : Long.MIN_VALUE;
        String fadeInEasing = payload.containsKey("fadeInEasing") ? string(payload.get("fadeInEasing")) : null;
        long fadeOut = payload.containsKey("fadeOutMs") ? longValue(payload.get("fadeOutMs"), 0L) : Long.MIN_VALUE;
        String fadeOutEasing = payload.containsKey("fadeOutEasing") ? string(payload.get("fadeOutEasing")) : null;
        Boolean positional = payload.containsKey("positional") ? booleanObject(payload.get("positional")) : null;
        Vector3f position = payload.containsKey("position") ? vector(payload.get("position")) : null;
        Float minDistance = payload.containsKey("minDistance") ? floatNullable(payload.get("minDistance")) : null;
        Float maxDistance = payload.containsKey("maxDistance") ? floatNullable(payload.get("maxDistance")) : null;
        String distanceModel = payload.containsKey("distanceModel") ? string(payload.get("distanceModel")) : null;
        Float rolloff = payload.containsKey("rolloff") ? floatNullable(payload.get("rolloff")) : null;
        PitchRamp ramp = PitchRamp.from(payload.get("pitchRamp"));
        VolumeLfo volumeLfo = VolumeLfo.from(payload.get("volumeLfo"));
        PitchLfo pitchLfo = PitchLfo.from(payload.get("pitchLfo"));
        String mixGroup = payload.containsKey("mixGroup") ? string(payload.get("mixGroup")) : null;
        Ducking ducking = Ducking.from(payload.get("duck"));
        String crossFadeGroup = string(payload.get("crossFadeGroup"));
        long crossFade = payload.containsKey("crossFadeMs")
                ? longValue(payload.get("crossFadeMs"), 0L)
                : Long.MIN_VALUE;

        return new ManagedSoundOptions(
                sound,
                category,
                volume,
                pitch,
                loop,
                startDelayMs,
                fadeIn,
                fadeInEasing,
                fadeOut,
                fadeOutEasing,
                positional,
                position,
                minDistance,
                maxDistance,
                distanceModel,
                rolloff,
                ramp,
                volumeLfo,
                pitchLfo,
                mixGroup,
                ducking,
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

    public record VolumeLfo(float frequencyHz, float depth, String waveform) {
        public static @Nullable VolumeLfo from(Object value) {
            if (value instanceof Map<?, ?> map) {
                Object frequencyObj = map.get("frequencyHz");
                if (!(frequencyObj instanceof Number frequencyNumber)) {
                    return null;
                }

                float frequencyHz = frequencyNumber.floatValue();
                float depth = floatValue(map.get("depth"), 0.0f);
                String waveform = string(map.get("waveform"));
                return new VolumeLfo(
                        frequencyHz,
                        clamp(depth, 0.0f, 1.0f),
                        waveform != null ? waveform : "sine"
                );
            }
            return null;
        }
    }

    public record PitchLfo(float frequencyHz, float depthSemitones, String waveform) {
        public static @Nullable PitchLfo from(Object value) {
            if (value instanceof Map<?, ?> map) {
                Object frequencyObj = map.get("frequencyHz");
                if (!(frequencyObj instanceof Number frequencyNumber)) {
                    return null;
                }

                float frequencyHz = frequencyNumber.floatValue();
                float depthSemitones = floatValue(map.get("depthSemitones"), 0.0f);
                String waveform = string(map.get("waveform"));
                return new PitchLfo(
                        frequencyHz,
                        Math.max(0.0f, depthSemitones),
                        waveform != null ? waveform : "sine"
                );
            }
            return null;
        }
    }

    public record Ducking(String group, float amount, long attackMs, long releaseMs) {
        public static @Nullable Ducking from(Object value) {
            if (value instanceof Map<?, ?> map) {
                String group = string(map.get("group"));
                if (group == null) {
                    return null;
                }

                float amount = floatValue(map.get("amount"), 0.0f);
                long attackMs = longValue(map.get("attackMs"), 50L);
                long releaseMs = longValue(map.get("releaseMs"), 250L);
                return new Ducking(group, clamp(amount, 0.0f, 1.0f), Math.max(0L, attackMs), Math.max(0L, releaseMs));
            }
            return null;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
