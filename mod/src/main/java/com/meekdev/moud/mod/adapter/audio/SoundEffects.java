package com.meekdev.moud.mod.adapter.audio;

import com.meekdev.moud.core.audio.ChorusSoundEffect;
import com.meekdev.moud.core.audio.CompressorSoundEffect;
import com.meekdev.moud.core.audio.DistortionSoundEffect;
import com.meekdev.moud.core.audio.EchoSoundEffect;
import com.meekdev.moud.core.audio.EqualizerSoundEffect;
import com.meekdev.moud.core.audio.FlangeSoundEffect;
import com.meekdev.moud.core.audio.PitchShiftSoundEffect;
import com.meekdev.moud.core.audio.ReverbSoundEffect;
import com.meekdev.moud.core.audio.Sound;
import com.meekdev.moud.core.audio.SoundBus;
import com.meekdev.moud.core.audio.SoundEffect;
import com.meekdev.moud.core.audio.TremoloSoundEffect;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.resona.dsp.AudioEffect;
import com.meekdev.resona.dsp.ChorusEffect;
import com.meekdev.resona.dsp.CompressorEffect;
import com.meekdev.resona.dsp.DistortionEffect;
import com.meekdev.resona.dsp.EchoEffect;
import com.meekdev.resona.dsp.EqualizerEffect;
import com.meekdev.resona.dsp.FlangeEffect;
import com.meekdev.resona.dsp.PitchShiftEffect;
import com.meekdev.resona.dsp.ReverbEffect;
import com.meekdev.resona.dsp.TremoloEffect;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.jspecify.annotations.Nullable;

final class SoundEffects {

    private static final Map<SoundEffect, AudioEffect> UNITS = new WeakHashMap<>();

    private SoundEffects() {}

    static List<SoundEffect> of(Sound sound, InstanceTree tree) {
        List<SoundEffect> found = under(sound);
        SoundBus bus = busOf(sound, tree);
        if (bus != null) found.addAll(under(bus));
        return found;
    }

    static List<AudioEffect> units(List<SoundEffect> effects) {
        List<AudioEffect> units = new ArrayList<>(effects.size());
        for (SoundEffect effect : effects) {
            AudioEffect unit = UNITS.computeIfAbsent(effect, SoundEffects::make);
            if (unit == null) continue;
            apply(effect, unit);
            units.add(unit);
        }
        return units;
    }

    static void forget() {
        UNITS.clear();
    }

    private static List<SoundEffect> under(Instance owner) {
        List<SoundEffect> found = new ArrayList<>();
        for (Instance child : owner.children()) {
            if (child instanceof SoundEffect effect && effect.enabled) found.add(effect);
        }
        found.sort(Comparator.comparingInt(effect -> effect.priority));
        return found;
    }

    private static @Nullable SoundBus busOf(Sound sound, InstanceTree tree) {
        for (SoundBus bus : tree.ofClass(Classes.SOUND_BUS)) {
            if (bus.bus.equals(sound.bus)) return bus;
        }
        return null;
    }

    private static @Nullable AudioEffect make(SoundEffect effect) {
        return switch (effect) {
            case ReverbSoundEffect ignored -> new ReverbEffect();
            case EqualizerSoundEffect ignored -> new EqualizerEffect();
            case DistortionSoundEffect ignored -> new DistortionEffect();
            case EchoSoundEffect ignored -> new EchoEffect();
            case PitchShiftSoundEffect ignored -> new PitchShiftEffect();
            case CompressorSoundEffect ignored -> new CompressorEffect();
            case ChorusSoundEffect ignored -> new ChorusEffect();
            case FlangeSoundEffect ignored -> new FlangeEffect();
            case TremoloSoundEffect ignored -> new TremoloEffect();
            default -> null;
        };
    }

    private static void apply(SoundEffect effect, AudioEffect unit) {
        switch (effect) {
            case ReverbSoundEffect from when unit instanceof ReverbEffect to -> to.decayTime(from.decayTime)
                    .density(from.density).diffusion(from.diffusion).dryLevel(from.dryLevel).wetLevel(from.wetLevel);
            case EqualizerSoundEffect from when unit instanceof EqualizerEffect to -> to.lowGain(from.lowGain)
                    .midGain(from.midGain).highGain(from.highGain).midRange(from.midLow, from.midHigh);
            case DistortionSoundEffect from when unit instanceof DistortionEffect to -> to.level(from.level);
            case EchoSoundEffect from when unit instanceof EchoEffect to -> to.delay(from.delay)
                    .feedback(from.feedback).dryLevel(from.dryLevel).wetLevel(from.wetLevel);
            case PitchShiftSoundEffect from when unit instanceof PitchShiftEffect to -> to.octave(from.octave);
            case CompressorSoundEffect from when unit instanceof CompressorEffect to -> to.threshold(from.threshold)
                    .ratio(from.ratio).attack(from.attack).release(from.release).makeupGain(from.makeupGain);
            case ChorusSoundEffect from when unit instanceof ChorusEffect to -> to.depth(from.depth)
                    .mix(from.mix).rate(from.rate);
            case FlangeSoundEffect from when unit instanceof FlangeEffect to -> to.depth(from.depth)
                    .mix(from.mix).rate(from.rate);
            case TremoloSoundEffect from when unit instanceof TremoloEffect to -> to.depth(from.depth)
                    .duty(from.duty).frequency(from.frequency);
            default -> {
            }
        }
    }
}
