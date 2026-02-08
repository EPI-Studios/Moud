package com.moud.server.profiler.model;

import java.time.Instant;
import java.util.List;

public record ProfilerCapture(
        String name,
        Instant startedAt,
        Instant finishedAt,
        List<ProfilerFrame> frames,
        List<ScriptSample> scriptSamples
) {
}

