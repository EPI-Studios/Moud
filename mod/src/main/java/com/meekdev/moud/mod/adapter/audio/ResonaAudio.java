package com.meekdev.moud.mod.adapter.audio;

import com.meekdev.moud.core.instance.Hits;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.script.api.AudioRef;
import com.meekdev.resona.api.Bus;
import com.meekdev.resona.api.PlaySettings;
import com.meekdev.resona.api.Quantize;
import com.meekdev.resona.api.Resona;
import com.meekdev.resona.api.SoundHandle;
import com.meekdev.resona.bank.EventDefinition;
import com.meekdev.resona.bank.SoundBank;
import com.meekdev.resona.param.Curve;
import com.meekdev.resona.param.Lfo;
import com.meekdev.resona.voice.Biquad;
import com.meekdev.resona.voice.VoiceChain;
import com.meekdev.resona.voice.VoiceFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.LongConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.phys.Vec3;

public final class ResonaAudio implements AudioRef {

    public static final ResonaAudio INSTANCE = new ResonaAudio();

    private static final int VOICE_RATE = 48000;

    final PlaceSounds sounds = new PlaceSounds();

    private final Queue<Long> beats = new ConcurrentLinkedQueue<>();
    private final Queue<Long> bars = new ConcurrentLinkedQueue<>();
    private volatile boolean occluding = true;

    private ResonaAudio() {}

    public void install() {
        Resona.setSoundSource(sounds);
        Resona.setOcclusionTester(this::occlusion);
        Resona.voice().setOcclusionTester(this::occlusion);
        Resona.conductor().onBeat(beats::add);
        Resona.conductor().onBar(bars::add);
    }

    public void reset() {
        Resona.stopAll();
        Resona.resetMix();
        SoundBank.clearDefined();
        Resona.clearSnapshot(0);
        for (Bus bus : Resona.engine().buses()) {
            bus.setVolume(1);
            bus.setMuted(false);
            bus.setLowPass(1);
        }
        Resona.setReverb(1.8f, 0);
        Resona.setHrtf(true);
        Resona.voice().setFilter(null);
        Resona.voice().setEnabled(true);
        Resona.voice().setStrength(1);
        occluding = true;
        beats.clear();
        bars.clear();
        sounds.forget();
    }

    private float occlusion(Vec3 listener, Vec3 source) {
        if (!occluding) return 0;
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && client.player != null) {
            BlockHitResult hit = client.level.clip(new ClipContext(listener, source,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
            if (hit.getType() != HitResult.Type.MISS) return 1;
        }
        InstanceTree tree = ClientScene.tree();
        if (tree == null) return 0;
        Vector3 from = vec(listener);
        Vector3 way = vec(source).sub(from);
        double range = way.length() - 0.5;
        Hits.Hit hit = Hits.cast(tree.root(), from, way, range, part -> part.collides && part.transparency < 0.5);
        return hit == null ? 0 : 1;
    }

    static Vector3 vec(Vec3 v) {
        return new Vector3(v.x, v.y, v.z);
    }

    static Vec3 vec(Vector3 v) {
        return new Vec3(v.x(), v.y(), v.z());
    }

    private static @Nullable Voice voice(@Nullable SoundHandle handle) {
        return handle == null ? null : new ResonaVoice(handle);
    }

    @Override
    public @Nullable Voice play(String soundId, Options o) {
        Identifier id = sounds.id(soundId);
        if (id == null) return null;
        PlaySettings settings = PlaySettings.of(id).volume((float) o.volume()).pitch((float) o.pitch())
                .loop(o.looped()).bus(o.bus()).priority(o.priority())
                .distance((float) o.minDistance(), (float) o.maxDistance(), (float) o.rollOff())
                .fadeIn((float) o.fadeIn()).stream(o.stream());
        if (o.at() != null) settings.at(vec(o.at()));
        return voice(Resona.play(settings));
    }

    @Override
    public @Nullable Voice playEvent(String name, @Nullable Vector3 at) {
        return voice(Resona.playEvent(name, at == null ? null : vec(at)));
    }

    @Override
    public @Nullable Voice stinger(String name, String quantize) {
        return voice(Resona.stinger(name, quantize(quantize)));
    }

    @Override
    public void defineEvent(String name, List<String> files, String bus, double volumeMin, double volumeMax,
                            double pitchMin, double pitchMax, boolean looped) {
        List<Identifier> ids = new ArrayList<>();
        for (String file : files) {
            Identifier id = sounds.id(file);
            if (id != null) ids.add(id);
        }
        if (ids.isEmpty()) return;
        SoundBank.define(name, new EventDefinition(bus, ids, (float) volumeMin, (float) volumeMax,
                (float) pitchMin, (float) pitchMax, looped));
    }

    @Override
    public void tempo(double bpm, int beatsPerBar) {
        Resona.tempo(bpm, Math.max(1, beatsPerBar));
    }

    @Override
    public void stopTempo() {
        Resona.conductor().stop();
    }

    @Override
    public double beats() {
        return Resona.conductor().positionBeats();
    }

    @Override
    public void drainBeats(LongConsumer beat, LongConsumer bar) {
        for (Long n = beats.poll(); n != null; n = beats.poll()) beat.accept(n);
        for (Long n = bars.poll(); n != null; n = bars.poll()) bar.accept(n);
    }

    @Override
    public void parameter(String name, double value) {
        Resona.setParameter(name, (float) value);
    }

    @Override
    public double parameter(String name) {
        return Resona.getParameter(name);
    }

    @Override
    public void bindBusVolume(String parameter, String bus, double[][] points) {
        Curve curve = new Curve();
        for (double[] point : points) curve.point((float) point[0], (float) point[1]);
        Resona.bindBusVolume(parameter, curve, bus);
    }

    @Override
    public void switchTo(String group, String value) {
        Resona.setSwitch(group, value);
    }

    @Override
    public @Nullable String switchOf(String group) {
        return Resona.getSwitch(group);
    }

    @Override
    public void lfo(String parameter, String shape, double hertz, double min, double max) {
        Resona.addLfo(new Lfo(parameter, Lfo.Shape.valueOf(shape.toUpperCase(Locale.ROOT)),
                (float) hertz, (float) min, (float) max));
    }

    @Override
    public void snapshot(Map<String, Double> volumes, double seconds) {
        Map<String, Float> levels = new HashMap<>();
        volumes.forEach((bus, volume) -> levels.put(bus, volume.floatValue()));
        Resona.applySnapshot(levels, seconds);
    }

    @Override
    public void clearSnapshot(double seconds) {
        Resona.clearSnapshot(seconds);
    }

    @Override
    public void sidechain(String sourceBus, String targetBus, double amount) {
        Resona.sidechain(sourceBus, targetBus, (float) amount);
    }

    @Override
    public void reverb(double decaySeconds, double wet) {
        Resona.setReverb((float) decaySeconds, (float) wet);
    }

    @Override
    public void hrtf(boolean enabled) {
        Resona.setHrtf(enabled);
    }

    @Override
    public void occlusion(boolean enabled) {
        occluding = enabled;
    }

    @Override
    public Music music(String bus, List<String> layers, Map<String, double[]> states) {
        return ResonaMusic.of(sounds, bus, layers, states);
    }

    @Override
    public void voiceChat(boolean enabled, double strength, List<Filter> filters) {
        Resona.voice().setEnabled(enabled);
        Resona.voice().setStrength((float) strength);
        if (filters.isEmpty()) {
            Resona.voice().setFilter(null);
            return;
        }
        Resona.voice().setFilter(() -> {
            VoiceFilter[] chain = new VoiceFilter[filters.size()];
            for (int n = 0; n < chain.length; n++) {
                Filter f = filters.get(n);
                chain[n] = switch (f.kind()) {
                    case "highpass" -> Biquad.highPass(f.frequency(), f.q(), VOICE_RATE);
                    case "bandpass" -> Biquad.bandPass(f.frequency(), f.q(), VOICE_RATE);
                    case "peaking" -> Biquad.peaking(f.frequency(), f.q(), f.gainDb(), VOICE_RATE);
                    default -> Biquad.lowPass(f.frequency(), f.q(), VOICE_RATE);
                };
            }
            return new VoiceChain(chain);
        });
    }

    @Override
    public int voiceCount() {
        return Resona.voiceCount();
    }

    static Quantize quantize(String name) {
        return switch (name) {
            case "beat" -> Quantize.BEAT;
            case "bar" -> Quantize.BAR;
            default -> Quantize.IMMEDIATE;
        };
    }

    private record ResonaVoice(SoundHandle handle) implements Voice {
        @Override
        public void stop() {
            handle.stop();
        }

        @Override
        public void volume(double value) {
            handle.setVolume((float) value);
        }

        @Override
        public void pitch(double value) {
            handle.setPitch((float) value);
        }

        @Override
        public void fade(double to, double seconds) {
            handle.fade((float) to, seconds);
        }

        @Override
        public void fadeOut(double seconds) {
            handle.fadeOutAndStop(seconds);
        }

        @Override
        public void position(Vector3 at) {
            handle.setPosition(vec(at));
        }

        @Override
        public boolean playing() {
            return handle.isPlaying();
        }
    }
}
