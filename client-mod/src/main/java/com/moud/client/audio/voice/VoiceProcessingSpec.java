package com.moud.client.audio.voice;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record VoiceProcessingSpec(List<VoiceProcessorRef> chain, float gain, boolean replace) {

    public VoiceProcessingSpec {
        chain = chain != null ? List.copyOf(chain) : List.of();
    }

    public static VoiceProcessingSpec empty() {
        return new VoiceProcessingSpec(List.of(), 1.0f, false);
    }

    public static VoiceProcessingSpec fromMap(@Nullable Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return empty();
        }

        float gain = map.get("gain") instanceof Number number ? number.floatValue() : 1.0f;
        boolean replace = map.get("replace") instanceof Boolean bool && bool;

        Object chainRaw = map.get("chain");
        if (chainRaw instanceof List<?> list) {
            return new VoiceProcessingSpec(parseChain(list), gain, replace);
        }
        if (map.get("id") instanceof String id && !id.isBlank()) {
            Map<String, Object> options = map.get("options") instanceof Map<?, ?> optionsMap
                    ? VoiceMapUtil.toStringObjectMap(optionsMap)
                    : null;
            return new VoiceProcessingSpec(List.of(new VoiceProcessorRef(id, options)), gain, replace);
        }
        return empty();
    }

    public static VoiceProcessingSpec fromChain(List<?> chain) {
        return new VoiceProcessingSpec(parseChain(chain), 1.0f, false);
    }

    public static VoiceProcessingSpec combine(VoiceProcessingSpec serverSpec, @Nullable VoiceProcessingSpec localSpec) {
        if (localSpec == null) {
            return serverSpec != null ? serverSpec : empty();
        }
        if (serverSpec == null) {
            return localSpec;
        }
        if (localSpec.replace) {
            return localSpec;
        }

        List<VoiceProcessorRef> combined = new ArrayList<>(serverSpec.chain.size() + localSpec.chain.size());
        combined.addAll(serverSpec.chain);
        combined.addAll(localSpec.chain);
        return new VoiceProcessingSpec(combined, serverSpec.gain * localSpec.gain, false);
    }

    private static List<VoiceProcessorRef> parseChain(List<?> chain) {
        List<VoiceProcessorRef> refs = new ArrayList<>();
        for (Object entry : chain) {
            if (entry instanceof String id && !id.isBlank()) {
                refs.add(new VoiceProcessorRef(id, null));
            } else if (entry instanceof Map<?, ?> map) {
                Object idObj = map.get("id");
                if (!(idObj instanceof String id) || id.isBlank()) {
                    continue;
                }
                Map<String, Object> options = map.get("options") instanceof Map<?, ?> optionsMap
                        ? VoiceMapUtil.toStringObjectMap(optionsMap)
                        : null;
                refs.add(new VoiceProcessorRef(id, options));
            }
        }
        return refs;
    }
}

