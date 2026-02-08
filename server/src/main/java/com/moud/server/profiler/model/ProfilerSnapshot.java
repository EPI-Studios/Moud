package com.moud.server.profiler.model;

import com.moud.server.network.diagnostics.NetworkProbe;
import com.moud.server.shared.diagnostics.SharedStoreSnapshot;

import java.util.List;

public record ProfilerSnapshot(
        ProfilerFrame frame,
        List<ScriptAggregate> scriptAggregates,
        List<ScriptSample> recentScriptSamples,
        NetworkProbe.NetworkSnapshot networkSnapshot,
        List<SharedStoreSnapshot> sharedStores
) {
}

