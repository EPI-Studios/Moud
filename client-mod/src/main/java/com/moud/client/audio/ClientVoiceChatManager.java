package com.moud.client.audio;

import com.moud.client.audio.voice.*;
import com.moud.client.network.ClientNetworkManager;
import com.moud.client.runtime.ClientScriptingRuntime;
import com.moud.network.MoudPackets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class ClientVoiceChatManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientVoiceChatManager.class);

    private static final int MAX_INPUT_QUEUE_FRAMES = 64;
    private static final int MAX_OUTPUT_QUEUE_FRAMES = 128;
    private static final int MAX_DRAIN_FRAMES = 16;
    private final ConcurrentMap<String, Value> processorFactories = new ConcurrentHashMap<>();
    private final ArrayBlockingQueue<VoiceCapturedFrame> inputQueue = new ArrayBlockingQueue<>(MAX_INPUT_QUEUE_FRAMES);
    private final ArrayBlockingQueue<VoiceIncomingFrame> outputQueue = new ArrayBlockingQueue<>(MAX_OUTPUT_QUEUE_FRAMES);
    private final AtomicBoolean inputDrainScheduled = new AtomicBoolean(false);
    private final AtomicBoolean outputDrainScheduled = new AtomicBoolean(false);
    private final VoiceVad voiceVad = new VoiceVad();
    private final AtomicInteger microphoneSequence = new AtomicInteger();
    private final ConcurrentMap<UUID, VoiceStreamState> streamStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, VoiceProcessingSpec> localOutputProcessing = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, VoiceSpeakerPlayback> speakerPlayback = new ConcurrentHashMap<>();
    private volatile boolean enabled = true;
    private volatile ClientScriptingRuntime runtime;
    private volatile Context jsContext;
    private volatile float voiceVolume = 1.0f;
    private volatile VoiceProcessingSpec microphoneProcessing = VoiceProcessingSpec.empty();
    private volatile VoiceProcessingSpec microphoneLastProcessing = VoiceProcessingSpec.empty();
    private volatile VoiceProcessorChain microphoneChain = VoiceProcessorChain.empty();
    private volatile String microphoneSessionId = "";
    private volatile int microphoneFrameSizeMs = 20;

    private static <T> boolean offerDropOldest(ArrayBlockingQueue<T> queue, T value) {
        if (queue.offer(value)) {
            return true;
        }
        queue.poll();
        return queue.offer(value);
    }

    public void setRuntime(@Nullable ClientScriptingRuntime runtime) {
        this.runtime = runtime;
    }

    public void setContext(@Nullable Context jsContext) {
        this.jsContext = jsContext;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            inputQueue.clear();
            outputQueue.clear();
            speakerPlayback.forEach((uuid, stream) -> stream.close());
            speakerPlayback.clear();
            streamStates.clear();
        }
    }

    public void registerProcessor(String id, Value factory) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Processor id cannot be null/blank");
        }
        if (factory == null || !factory.canExecute()) {
            throw new IllegalArgumentException("Processor factory must be executable");
        }
        processorFactories.put(id, factory);
    }

    public void setMicrophoneConfig(Map<String, Object> options) {
        if (options == null) {
            microphoneProcessing = VoiceProcessingSpec.empty();
            microphoneLastProcessing = VoiceProcessingSpec.empty();
            microphoneChain = VoiceProcessorChain.empty();
            voiceVad.configure(null);
            return;
        }

        if (options.get("sessionId") instanceof String sessionId && !sessionId.isBlank()) {
            if (!Objects.equals(this.microphoneSessionId, sessionId)) {
                this.microphoneSessionId = sessionId;
                microphoneSequence.set(0);
            }
        }

        if (options.get("frameSizeMs") instanceof Number number) {
            microphoneFrameSizeMs = Math.max(5, Math.min(number.intValue(), 60));
        }

        if (options.get("inputProcessing") instanceof Map<?, ?> map) {
            microphoneProcessing = VoiceProcessingSpec.fromMap(VoiceMapUtil.toStringObjectMap(map));
        } else if (options.get("inputProcessors") instanceof List<?> list) {
            microphoneProcessing = VoiceProcessingSpec.fromChain(list);
        }

        if (options.get("vad") instanceof Map<?, ?> vadMap) {
            voiceVad.configure(VoiceMapUtil.toStringObjectMap(vadMap));
        } else {
            voiceVad.configure(null);
        }
    }

    public void onMicrophoneStopped() {
        microphoneSessionId = "";
        microphoneSequence.set(0);
        voiceVad.reset();
    }

    public void onMicrophoneFrame(String sessionId, long timestampMs, AudioFormat format, byte[] data) {
        if (!enabled || data == null || data.length == 0) {
            return;
        }
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        if (!Objects.equals(microphoneSessionId, sessionId)) {
            microphoneSessionId = sessionId;
            microphoneSequence.set(0);
        }

        VoiceCapturedFrame frame = new VoiceCapturedFrame(
                sessionId,
                timestampMs,
                (int) format.getSampleRate(),
                format.getChannels(),
                microphoneFrameSizeMs,
                data
        );

        if (!offerDropOldest(inputQueue, frame)) {
            return;
        }

        if (runtime == null || jsContext == null) {
            sendMicrophoneFrameRaw(frame);
            return;
        }

        scheduleInputDrain();
    }

    public void handleVoiceStreamChunk(MoudPackets.VoiceStreamChunkPacket packet) {
        if (!enabled || packet == null || packet.data() == null || packet.data().length == 0) {
            return;
        }

        VoiceIncomingFrame frame = VoiceIncomingFrame.fromPacket(packet);

        if (!offerDropOldest(outputQueue, frame)) {
            return;
        }

        if (runtime == null || jsContext == null) {
            playIncomingRaw(frame);
            return;
        }

        scheduleOutputDrain();
    }

    public void setLocalOutputProcessing(UUID speakerId, @Nullable Map<String, Object> processing) {
        if (speakerId == null) {
            return;
        }
        if (processing == null || processing.isEmpty()) {
            localOutputProcessing.remove(speakerId);
            return;
        }
        localOutputProcessing.put(speakerId, VoiceProcessingSpec.fromMap(processing));
    }

    public void clearLocalOutputProcessing(UUID speakerId) {
        if (speakerId == null) {
            return;
        }
        localOutputProcessing.remove(speakerId);
    }

    public void tick() {
        voiceVolume = VoiceClientVolume.readVoiceVolume();
        long nowMs = System.currentTimeMillis();
        speakerPlayback.forEach((speakerId, playback) -> {
            if (nowMs - playback.getLastReceivedAtMs() > 5_000L) {
                speakerPlayback.remove(speakerId, playback);
                playback.close();
                streamStates.remove(speakerId);
            } else {
                playback.applySpatialization();
            }
        });
    }

    private void scheduleInputDrain() {
        if (!inputDrainScheduled.compareAndSet(false, true)) {
            return;
        }
        ClientScriptingRuntime currentRuntime = runtime;
        if (currentRuntime == null) {
            inputDrainScheduled.set(false);
            return;
        }
        currentRuntime.executePriority(this::drainInputQueue);
    }

    private void scheduleOutputDrain() {
        if (!outputDrainScheduled.compareAndSet(false, true)) {
            return;
        }
        ClientScriptingRuntime currentRuntime = runtime;
        if (currentRuntime == null) {
            outputDrainScheduled.set(false);
            return;
        }
        currentRuntime.executePriority(this::drainOutputQueue);
    }

    private void drainInputQueue() {
        try {
            if (!enabled) {
                inputQueue.clear();
                return;
            }
            Context context = jsContext;
            if (context == null) {
                return;
            }

            context.enter();
            try {
                int processed = 0;
                while (processed < MAX_DRAIN_FRAMES) {
                    VoiceCapturedFrame frame = inputQueue.poll();
                    if (frame == null) {
                        break;
                    }
                    processAndSendMicrophoneFrame(frame);
                    processed++;
                }
            } finally {
                context.leave();
            }
        } catch (Exception e) {
            LOGGER.warn("Error draining microphone frames", e);
        } finally {
            inputDrainScheduled.set(false);
            if (!inputQueue.isEmpty()) {
                scheduleInputDrain();
            }
        }
    }

    private void drainOutputQueue() {
        try {
            if (!enabled) {
                outputQueue.clear();
                return;
            }
            Context context = jsContext;
            if (context == null) {
                return;
            }

            context.enter();
            try {
                int processed = 0;
                while (processed < MAX_DRAIN_FRAMES) {
                    VoiceIncomingFrame frame = outputQueue.poll();
                    if (frame == null) {
                        break;
                    }
                    processAndPlayIncomingFrame(frame);
                    processed++;
                }
            } finally {
                context.leave();
            }
        } catch (Exception e) {
            LOGGER.warn("Error draining incoming voice frames", e);
        } finally {
            outputDrainScheduled.set(false);
            if (!outputQueue.isEmpty()) {
                scheduleOutputDrain();
            }
        }
    }

    private void processAndSendMicrophoneFrame(VoiceCapturedFrame frame) {
        if (!VoiceCodecs.PCM_S16LE.equals(frame.codec())) {
            return;
        }

        short[] samples = VoiceAudioUtil.decodePcmS16Le(frame.data());
        VoiceProcessingSpec spec = microphoneProcessing;
        if (!spec.chain().isEmpty()) {
            if (!Objects.equals(microphoneLastProcessing, spec)) {
                microphoneLastProcessing = spec;
                microphoneChain = VoiceProcessorChain.build(processorFactories, spec, null, true);
            }
            microphoneChain.process(samples, voiceProcessContext("input", null, frame, 0.0f, true));
        } else if (!microphoneChain.isEmpty()) {
            microphoneChain = VoiceProcessorChain.empty();
        }

        VoiceAudioUtil.applyGainWithLimiter(samples, spec.gain() * VoiceTuning.INPUT_GAIN, VoiceTuning.LIMITER_PEAK);

        float level = VoiceAudioUtil.computeRmsLevel(samples);
        boolean speaking = voiceVad.isSpeaking(level, frame.timestampMs());

        if (!speaking && voiceVad.dropSilence()) {
            return;
        }

        byte[] pcm = VoiceAudioUtil.encodePcmS16Le(samples);
        int sequence = microphoneSequence.getAndIncrement();

        MoudPackets.VoiceMicrophoneChunkPacket packet = new MoudPackets.VoiceMicrophoneChunkPacket(
                frame.sessionId(),
                sequence,
                frame.timestampMs(),
                VoiceCodecs.PCM_S16LE,
                frame.sampleRate(),
                frame.channels(),
                frame.frameSizeMs(),
                level,
                speaking,
                pcm
        );
        ClientNetworkManager.send(packet);
    }

    private void sendMicrophoneFrameRaw(VoiceCapturedFrame frame) {
        if (!VoiceCodecs.PCM_S16LE.equals(frame.codec())) {
            return;
        }
        short[] samples = VoiceAudioUtil.decodePcmS16Le(frame.data());
        VoiceProcessingSpec spec = microphoneProcessing;
        VoiceAudioUtil.applyGainWithLimiter(samples, spec.gain() * VoiceTuning.INPUT_GAIN, VoiceTuning.LIMITER_PEAK);
        float level = VoiceAudioUtil.computeRmsLevel(samples);
        boolean speaking = voiceVad.isSpeaking(level, frame.timestampMs());
        if (!speaking && voiceVad.dropSilence()) {
            return;
        }
        byte[] pcm = VoiceAudioUtil.encodePcmS16Le(samples);
        int sequence = microphoneSequence.getAndIncrement();
        MoudPackets.VoiceMicrophoneChunkPacket packet = new MoudPackets.VoiceMicrophoneChunkPacket(
                frame.sessionId(),
                sequence,
                frame.timestampMs(),
                VoiceCodecs.PCM_S16LE,
                frame.sampleRate(),
                frame.channels(),
                frame.frameSizeMs(),
                level,
                speaking,
                pcm
        );
        ClientNetworkManager.send(packet);
    }

    private void processAndPlayIncomingFrame(VoiceIncomingFrame frame) {
        if (!VoiceCodecs.PCM_S16LE.equals(frame.codec())) {
            return;
        }

        VoiceProcessingSpec serverSpec = VoiceProcessingSpec.fromMap(frame.outputProcessing());
        VoiceProcessingSpec localSpec = localOutputProcessing.get(frame.speakerId());
        VoiceProcessingSpec combined = VoiceProcessingSpec.combine(serverSpec, localSpec);

        short[] samples = VoiceAudioUtil.decodePcmS16Le(frame.data());
        if (!combined.chain().isEmpty()) {
            VoiceStreamState state = streamStates.computeIfAbsent(frame.speakerId(), uuid -> new VoiceStreamState());
            VoiceProcessorChain chain = state.getOrBuildChain(processorFactories, combined, frame.speakerId(), false);
            chain.process(samples, voiceProcessContext("output", frame, null, frame.level(), frame.speaking()));
        } else {
            VoiceStreamState state = streamStates.remove(frame.speakerId());
            if (state != null) {
                state.clear();
            }
        }

        float mixGain = voiceVolume;
        float overallGain = combined.gain() * VoiceTuning.OUTPUT_GAIN * mixGain;
        VoiceAudioUtil.applyGainWithLimiter(samples, overallGain, VoiceTuning.LIMITER_PEAK);

        byte[] pcm = VoiceAudioUtil.encodePcmS16Le(samples);
        VoiceSpeakerPlayback playback = getOrCreatePlayback(frame.speakerId(), frame.sampleRate(), frame.channels());
        if (playback == null) {
            return;
        }
        playback.markReceived(frame.position());
        playback.enqueue(pcm);
    }

    private void playIncomingRaw(VoiceIncomingFrame frame) {
        if (!VoiceCodecs.PCM_S16LE.equals(frame.codec())) {
            return;
        }
        short[] samples = VoiceAudioUtil.decodePcmS16Le(frame.data());
        float mixGain = voiceVolume;
        VoiceAudioUtil.applyGainWithLimiter(
                samples,
                VoiceTuning.OUTPUT_GAIN * mixGain,
                VoiceTuning.LIMITER_PEAK
        );
        byte[] pcm = VoiceAudioUtil.encodePcmS16Le(samples);
        VoiceSpeakerPlayback playback = getOrCreatePlayback(frame.speakerId(), frame.sampleRate(), frame.channels());
        if (playback == null) {
            return;
        }
        playback.markReceived(frame.position());
        playback.enqueue(pcm);
    }

    private Map<String, Object> voiceProcessContext(String direction,
                                                    @Nullable VoiceIncomingFrame output,
                                                    @Nullable VoiceCapturedFrame input,
                                                    float level,
                                                    boolean speaking) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("direction", direction);
        ctx.put("nowMs", System.currentTimeMillis());
        ctx.put("level", level);
        ctx.put("speaking", speaking);

        if (output != null) {
            ctx.put("speakerId", output.speakerId().toString());
            ctx.put("sessionId", output.sessionId());
            ctx.put("sampleRate", output.sampleRate());
            ctx.put("channels", output.channels());
            ctx.put("frameSizeMs", output.frameSizeMs());
            ctx.put("sequence", output.sequence());
            ctx.put("position", output.position());
        }

        if (input != null) {
            ctx.put("sessionId", input.sessionId());
            ctx.put("sampleRate", input.sampleRate());
            ctx.put("channels", input.channels());
            ctx.put("frameSizeMs", input.frameSizeMs());
        }
        return ctx;
    }

    private @Nullable VoiceSpeakerPlayback getOrCreatePlayback(UUID speakerId, int sampleRate, int channels) {
        VoiceSpeakerPlayback playback = speakerPlayback.get(speakerId);
        if (playback != null) {
            return playback;
        }

        VoiceSpeakerPlayback created = VoiceSpeakerPlayback.open(speakerId, sampleRate, channels);
        if (created == null) {
            return null;
        }

        VoiceSpeakerPlayback existing = speakerPlayback.putIfAbsent(speakerId, created);
        if (existing != null) {
            created.close();
            return existing;
        }
        return created;
    }
}
