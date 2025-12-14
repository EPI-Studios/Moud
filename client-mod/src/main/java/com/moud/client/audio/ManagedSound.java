package com.moud.client.audio;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ManagedSound {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManagedSound.class);

    private final String id;
    private ManagedSoundOptions options;

    private ManagedSoundInstance activeInstance;

    private long scheduledStartNanos;
    private long startedAtNanos;
    private long stopAtNanos = Long.MIN_VALUE;
    private long fadeOutDurationMs;
    private volatile float mixVolumeMultiplier = 1.0f;

    ManagedSound(String id, ManagedSoundOptions options) {
        this.id = id;
        this.options = options;
        long delayMs = Math.max(0L, options.startDelayMs());
        this.scheduledStartNanos = System.nanoTime() + delayMs * 1_000_000L;
    }

    public String getId() {
        return id;
    }

    public ManagedSoundOptions getOptions() {
        return options;
    }

    public boolean isStarted() {
        return activeInstance != null;
    }

    public void setMixVolumeMultiplier(float multiplier) {
        this.mixVolumeMultiplier = Math.max(0.0f, multiplier);
    }

    public SoundInstance createInstance(long startTimeNanos) {
        SoundEvent event = ClientAudioService.getInstance()
                .getSoundEvent(options.soundEventIdOrThrow());

        if (event == null) {
            LOGGER.warn("Managed sound '{}' references missing event {}", id, options.soundEventIdOrThrow());
            return null;
        }
        activeInstance = new ManagedSoundInstance(event, options.category(), options);
        activeInstance.setManagedSound(this);

        activeInstance.setPosition(options.positionOrDefault());
        activeInstance.setEnvelopeVolume(computeVolume(0, startTimeNanos));
        activeInstance.setPitchPublic(computePitch(0));
        activeInstance.applyLoopAndAttenuation(options);

        startedAtNanos = startTimeNanos;
        fadeOutDurationMs = options.fadeOutMs();
        return activeInstance;
    }


    public ManagedSoundOptions update(ManagedSoundOptions update) {
        ManagedSoundOptions previous = this.options;
        this.options = this.options.merge(update);

        if (activeInstance == null) {
            if (update.startDelayMs() != Long.MIN_VALUE) {
                long delayMs = Math.max(0L, options.startDelayMs());
                scheduledStartNanos = System.nanoTime() + delayMs * 1_000_000L;
            }
        }

        if (activeInstance != null) {
            activeInstance.setPosition(options.positionOrDefault());
            activeInstance.applyLoopAndAttenuation(options);
        }

        return previous;
    }

    public void requestStop(Long customFadeOutMs, boolean immediate) {
        if (immediate) {
            stopAtNanos = System.nanoTime();
            fadeOutDurationMs = 0;
            return;
        }

        fadeOutDurationMs = customFadeOutMs != null ? customFadeOutMs : options.fadeOutMs();
        stopAtNanos = System.nanoTime();
    }


    public boolean tick(long nowNanos) {
        if (activeInstance == null) {
            if (stopAtNanos != Long.MIN_VALUE) {
                return false;
            }
            if (nowNanos < scheduledStartNanos) {
                return true;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return false;
            }

            SoundInstance instance = createInstance(nowNanos);
            if (instance == null) {
                return false;
            }

            SoundManager manager = client.getSoundManager();
            client.execute(() -> manager.play(instance));
            return true;
        }

        long elapsedMs = (nowNanos - startedAtNanos) / 1_000_000L;
        if (stopAtNanos != Long.MIN_VALUE) {
            long stopElapsed = (nowNanos - stopAtNanos) / 1_000_000L;
            if (stopElapsed >= fadeOutDurationMs) {
                activeInstance.markDone();
                return false;
            }
        }

        float envelope = computeVolume(elapsedMs, nowNanos);
        activeInstance.setEnvelopeVolume(envelope);

        activeInstance.setPitchPublic(computePitch(elapsedMs));
        activeInstance.setPosition(options.positionOrDefault());
        activeInstance.applyLoopAndAttenuation(options);

        return true;
    }

    private float computeVolume(long elapsedMs, long nowNanos) {
        float volume = options.baseVolume();
        volume *= mixVolumeMultiplier;

        long fadeIn = options.fadeInMs();
        if (fadeIn > 0) {
            float fadeInFactor = Math.min(1.0f, elapsedMs / (float) fadeIn);
            fadeInFactor = applyEasing(options.fadeInEasing(), fadeInFactor);
            volume *= fadeInFactor;
        }

        if (stopAtNanos != Long.MIN_VALUE && fadeOutDurationMs > 0) {
            long stopElapsedMs = (nowNanos - stopAtNanos) / 1_000_000L;
            float eased = Math.min(1.0f, stopElapsedMs / (float) fadeOutDurationMs);
            eased = applyEasing(options.fadeOutEasing(), eased);
            float fadeOutFactor = 1.0f - eased;
            volume *= fadeOutFactor;
        }

        ManagedSoundOptions.VolumeLfo lfo = options.volumeLfo();
        if (lfo != null && lfo.frequencyHz() > 0.0f && lfo.depth() > 0.0f) {
            float wave = wave01(lfo.waveform(), elapsedMs / 1000.0f, lfo.frequencyHz());
            float depth = clamp(lfo.depth(), 0.0f, 1.0f);
            float tremolo = (1.0f - depth) + depth * wave;
            volume *= tremolo;
        }

        volume *= computeDistanceAttenuation();
        return volume;
    }

    private float computePitch(long elapsedMs) {
        float pitch = options.basePitch();

        ManagedSoundOptions.PitchRamp ramp = options.pitchRamp();
        if (ramp != null && ramp.durationMs() > 0) {
            float t = Math.min(1.0f, elapsedMs / (float) ramp.durationMs());
            float eased = applyEasing(ramp.easing(), t);
            pitch = pitch + (ramp.targetPitch() - pitch) * eased;
        }

        ManagedSoundOptions.PitchLfo lfo = options.pitchLfo();
        if (lfo != null && lfo.frequencyHz() > 0.0f && lfo.depthSemitones() > 0.0f) {
            float wave = wave11(lfo.waveform(), elapsedMs / 1000.0f, lfo.frequencyHz());
            double semitones = wave * lfo.depthSemitones();
            pitch *= (float) Math.pow(2.0, semitones / 12.0);
        }

        return clamp(pitch, 0.01f, 4.0f);
    }

    private float computeDistanceAttenuation() {
        if (!options.useCustomDistanceAttenuation()) {
            return 1.0f;
        }

        Float maxDistanceObj = options.maxDistance();
        if (maxDistanceObj == null) {
            return 1.0f;
        }

        float maxDistance = maxDistanceObj;
        if (maxDistance <= 0.0f) {
            return 0.0f;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null) {
            return 1.0f;
        }

        Vec3d listener = client.gameRenderer.getCamera().getPos();
        Vector3f soundPos = options.positionOrDefault();

        float dx = soundPos.x - (float) listener.x;
        float dy = soundPos.y - (float) listener.y;
        float dz = soundPos.z - (float) listener.z;
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distance >= maxDistance) {
            return 0.0f;
        }

        String model = options.distanceModel();
        float rolloff = options.rolloff() != null ? options.rolloff() : 1.0f;
        rolloff = Math.max(0.0f, rolloff);

        float minDistance = options.minDistance() != null ? options.minDistance() : 0.0f;
        minDistance = clamp(minDistance, 0.0f, maxDistance);

        float attenuation = switch (model == null ? "linear" : model.toLowerCase()) {
            case "inverse" -> {
                float ref = Math.max(0.001f, minDistance > 0.0f ? minDistance : 1.0f);
                float d = Math.max(ref, distance);
                yield ref / (ref + rolloff * (d - ref));
            }
            case "exponential" -> {
                float ref = Math.max(0.001f, minDistance > 0.0f ? minDistance : 1.0f);
                float d = Math.max(ref, distance);
                yield (float) Math.pow(d / ref, -rolloff);
            }
            default -> {
                if (distance <= minDistance) {
                    yield 1.0f;
                }
                float denom = Math.max(0.001f, maxDistance - minDistance);
                yield 1.0f - ((distance - minDistance) / denom);
            }
        };

        return clamp(attenuation, 0.0f, 1.0f);
    }

    private static float applyEasing(String easing, float value) {
        return switch (easing == null ? "linear" : easing.toLowerCase()) {
            case "ease_in" -> value * value;
            case "ease_out" -> 1.0f - (1.0f - value) * (1.0f - value);
            case "ease_in_out" -> value < 0.5f
                    ? 2.0f * value * value
                    : 1.0f - (float) Math.pow(-2.0f * value + 2.0f, 2) / 2.0f;
            default -> value;
        };
    }

    private static float wave01(String waveform, float timeSeconds, float frequencyHz) {
        float w = wave11(waveform, timeSeconds, frequencyHz);
        return (w + 1.0f) * 0.5f;
    }

    private static float wave11(String waveform, float timeSeconds, float frequencyHz) {
        float phase = (timeSeconds * frequencyHz) % 1.0f;
        return switch (waveform == null ? "sine" : waveform.toLowerCase()) {
            case "triangle" -> 1.0f - 4.0f * Math.abs(phase - 0.5f);
            case "square" -> phase < 0.5f ? 1.0f : -1.0f;
            case "saw" -> 2.0f * phase - 1.0f;
            default -> (float) Math.sin(phase * Math.PI * 2.0);
        };
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class ManagedSoundInstance extends MovingSoundInstance {

        private ManagedSound managedSound;

        protected ManagedSoundInstance(SoundEvent soundEvent,
                                       SoundCategory soundCategory,
                                       ManagedSoundOptions opts) {
            super(soundEvent, soundCategory, net.minecraft.util.math.random.Random.create());
            applyOptions(opts);
        }

        public void applyOptions(ManagedSoundOptions opts) {
            this.volume = opts.baseVolume();
            this.pitch = opts.basePitch();
            Vector3f p = opts.positionOrDefault();
            this.x = p.x;
            this.y = p.y;
            this.z = p.z;
            this.repeat = opts.loop();
            this.repeatDelay = 0;
            this.relative = !opts.positional();
            if (!opts.positional()) {
                this.attenuationType = AttenuationType.NONE;
            } else {
                this.attenuationType = opts.useCustomDistanceAttenuation()
                        ? AttenuationType.NONE
                        : AttenuationType.LINEAR;
            }
        }

        public void applyLoopAndAttenuation(ManagedSoundOptions opts) {
            this.repeat = opts.loop();
            this.relative = !opts.positional();
            if (!opts.positional()) {
                this.attenuationType = AttenuationType.NONE;
            } else {
                this.attenuationType = opts.useCustomDistanceAttenuation()
                        ? AttenuationType.NONE
                        : AttenuationType.LINEAR;
            }
        }

        public void setPosition(Vector3f p) {
            this.x = p.x;
            this.y = p.y;
            this.z = p.z;
        }
        public void setEnvelopeVolume(float v) {
            this.volume = v;
        }
        public void setPitchPublic(float p) {
            this.pitch = p;
        }

        public void markDone() {
            this.setDone();
        }

        @Override
        public void tick() {
            if (managedSound == null) {
                setDone();
                return;
            }
            boolean stillPlaying = managedSound.tick(System.nanoTime());
            if (!stillPlaying) {
                setDone();
            }
        }

        private void setManagedSound(ManagedSound managedSound) {
            this.managedSound = managedSound;
        }
    }
}
