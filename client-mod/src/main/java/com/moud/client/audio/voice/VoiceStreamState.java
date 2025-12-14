package com.moud.client.audio.voice;

import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

public final class VoiceStreamState {

    private VoiceProcessingSpec lastProcessing = VoiceProcessingSpec.empty();
    private VoiceProcessorChain chain = VoiceProcessorChain.empty();

    public VoiceProcessorChain getOrBuildChain(ConcurrentMap<String, Value> factories,
                                               VoiceProcessingSpec spec,
                                               @Nullable UUID speakerId,
                                               boolean input) {
        if (spec == null || spec.chain().isEmpty()) {
            clear();
            return chain;
        }

        if (!Objects.equals(lastProcessing, spec)) {
            lastProcessing = spec;
            chain = VoiceProcessorChain.build(factories, spec, speakerId, input);
        }
        return chain;
    }

    public void clear() {
        lastProcessing = VoiceProcessingSpec.empty();
        chain = VoiceProcessorChain.empty();
    }
}

