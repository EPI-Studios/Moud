package com.moud.server.profiler;

import com.moud.server.profiler.model.ProfilerFrame;
import com.moud.server.profiler.model.ProfilerSnapshot;
import com.moud.server.profiler.model.ScriptSample;

public interface ProfilerListener {
    default void onFrame(ProfilerFrame frame) {}

    default void onScriptSample(ScriptSample sample) {}

    default void onSnapshot(ProfilerSnapshot snapshot) {}
}
