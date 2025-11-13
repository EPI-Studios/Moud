package com.moud.client.audio;

import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ManagedSound {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManagedSound.class);

    private final String id;
    private ManagedSoundOptions options;

    private ManagedSoundInstance activeInstance;

    private long startedAtNanos;
    private long stopAtNanos = Long.MIN_VALUE;
    private long fadeOutDurationMs;

    ManagedSound(String id, ManagedSoundOptions options) {
        this.id = id;
        this.options = options;
    }

    public String getId() {
        return id;
    }

    public ManagedSoundOptions getOptions() {
        return options;
    }

    public SoundInstance createInstance() {
        SoundEvent event = Registries.SOUND_EVENT.get(options.soundEventIdOrThrow());

        if (event == null) {
            LOGGER.warn("Managed sound '{}' references missing event {}", id, options.soundEventIdOrThrow());
            return null;
        }

        activeInstance = new ManagedSoundInstance(event, options.category(), options);
        activeInstance.setManagedSound(this);

        activeInstance.setPosition(options.positionOrDefault());
        activeInstance.setEnvelopeVolume(computeVolume(0));
        activeInstance.setPitchPublic(options.basePitch());
        activeInstance.setLoopAndRelative(options.loop(), options.positional());

        startedAtNanos = System.nanoTime();
        fadeOutDurationMs = options.fadeOutMs();
        return activeInstance;
    }


    public ManagedSoundOptions update(ManagedSoundOptions update) {
        ManagedSoundOptions previous = this.options;
        this.options = this.options.merge(update);

        if (activeInstance != null) {
            activeInstance.setPosition(options.positionOrDefault());
            activeInstance.setLoopAndRelative(options.loop(), options.positional());
            // activeInstance.setEnvelopeVolume(computeVolume(0));
            // activeInstance.setPitchPublic(options.basePitch());
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
            return false;
        }

        long elapsedMs = (nowNanos - startedAtNanos) / 1_000_000L;
        if (stopAtNanos != Long.MIN_VALUE) {
            long stopElapsed = (nowNanos - stopAtNanos) / 1_000_000L;
            if (stopElapsed >= fadeOutDurationMs) {
                activeInstance.markDone();
                return false;
            }
        }

        float envelope = computeVolume(elapsedMs);
        activeInstance.setEnvelopeVolume(envelope);

        ManagedSoundOptions.PitchRamp ramp = options.pitchRamp();
        if (ramp != null && ramp.durationMs() > 0) {
            float t = Math.min(1.0f, elapsedMs / (float) ramp.durationMs());
            float eased = applyEasing(ramp.easing(), t);
            float interpolated = options.basePitch() + (ramp.targetPitch() - options.basePitch()) * eased;
            activeInstance.setPitchPublic(interpolated);
        }

        if (options.positional() && activeInstance != null) {
            activeInstance.setPosition(options.positionOrDefault());
        }

        return true;
    }

    private float computeVolume(long elapsedMs) {
        float volume = options.baseVolume();

        long fadeIn = options.fadeInMs();
        if (fadeIn > 0) {
            float fadeInFactor = Math.min(1.0f, elapsedMs / (float) fadeIn);
            volume *= fadeInFactor;
        }

        if (stopAtNanos != Long.MIN_VALUE && fadeOutDurationMs > 0) {
            long stopElapsedMs = (System.nanoTime() - stopAtNanos) / 1_000_000L;
            float fadeOutFactor = 1.0f - Math.min(1.0f, stopElapsedMs / (float) fadeOutDurationMs);
            volume *= fadeOutFactor;
        }

        return volume;
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
            this.attenuationType = opts.positional() ? AttenuationType.LINEAR : AttenuationType.NONE;
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
        public void setLoopAndRelative(boolean loop, boolean positional) {
            this.repeat = loop;
            this.relative = !positional;
            this.attenuationType = positional ? AttenuationType.LINEAR : AttenuationType.NONE;
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
