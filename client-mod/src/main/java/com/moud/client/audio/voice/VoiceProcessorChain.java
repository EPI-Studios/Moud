package com.moud.client.audio.voice;

import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

public final class VoiceProcessorChain {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoiceProcessorChain.class);
    private static final VoiceProcessorChain EMPTY = new VoiceProcessorChain(List.of());

    private final List<ProcessorInstance> instances;

    private VoiceProcessorChain(List<ProcessorInstance> instances) {
        this.instances = instances != null ? List.copyOf(instances) : List.of();
    }

    public static VoiceProcessorChain empty() {
        return EMPTY;
    }

    public static VoiceProcessorChain build(ConcurrentMap<String, Value> factories,
                                            VoiceProcessingSpec spec,
                                            @Nullable UUID speakerId,
                                            boolean input) {
        if (spec == null || spec.chain().isEmpty()) {
            return empty();
        }

        List<ProcessorInstance> instances = new ArrayList<>(spec.chain().size());
        for (VoiceProcessorRef ref : spec.chain()) {
            if (ref == null || ref.id() == null || ref.id().isBlank()) {
                continue;
            }

            Value factory = factories.get(ref.id());
            if (factory == null) {
                continue;
            }

            Value created;
            try {
                created = ref.options() != null ? factory.execute(ref.options()) : factory.execute();
            } catch (Exception e) {
                LOGGER.warn("Voice processor '{}' factory threw", ref.id(), e);
                continue;
            }

            if (created == null || created.isNull()) {
                continue;
            }

            Value processFn;
            if (created.canExecute()) {
                processFn = created;
            } else if (created.hasMember("process") && created.getMember("process").canExecute()) {
                processFn = created.getMember("process");
            } else {
                LOGGER.warn("Voice processor '{}' instance is not callable and has no process()", ref.id());
                continue;
            }

            instances.add(new ProcessorInstance(ref.id(), created, processFn, ref.options(), input, speakerId));
        }

        if (instances.isEmpty()) {
            return empty();
        }
        return new VoiceProcessorChain(instances);
    }

    public boolean isEmpty() {
        return instances.isEmpty();
    }

    public void process(short[] samples, Map<String, Object> ctx) {
        if (instances.isEmpty()) {
            return;
        }
        for (ProcessorInstance instance : instances) {
            try {
                instance.processFn.execute(samples, ctx);
            } catch (Exception e) {
                LOGGER.warn("Voice processor '{}' threw during process()", instance.id, e);
            }
        }
    }

    private record ProcessorInstance(String id,
                                     Value instance,
                                     Value processFn,
                                     @Nullable Map<String, Object> options,
                                     boolean input,
                                     @Nullable UUID speakerId) {
    }
}

