package com.moud.server.profiler.model;

import java.time.Instant;

public record ProfilerFrame(
        long index,
        Instant timestamp,
        double processCpuLoad,
        double systemCpuLoad,
        long heapUsedBytes,
        long heapCommittedBytes,
        int liveThreads,
        long outboundBytes,
        long inboundBytes,
        long outboundPackets,
        long inboundPackets,
        int sharedStoreCount,
        long sharedValueCount
) {
}

