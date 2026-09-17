package com.meekdev.moud.core.effect;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

public final class EffectRuns<S extends Instance, R> {

    private final Map<S, R> runs = new IdentityHashMap<>();
    private final Function<S, R> start;
    private InstanceTree tree;

    public EffectRuns(Function<S, R> start) {
        this.start = start;
    }

    public void begin(InstanceTree tree) {
        if (tree == this.tree) return;
        runs.clear();
        this.tree = tree;
    }

    public R run(S source) {
        return runs.computeIfAbsent(source, start);
    }

    public void end() {
        runs.keySet().removeIf(source -> !source.isAlive() || source.tree() != tree);
    }

    public Map<S, R> runs() {
        return runs;
    }
}
