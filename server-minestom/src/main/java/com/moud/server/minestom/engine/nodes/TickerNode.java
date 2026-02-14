package com.moud.server.minestom.engine.nodes;

import com.moud.core.scene.Node;

import java.util.concurrent.atomic.AtomicLong;

public final class TickerNode extends Node {
    private final AtomicLong ticks = new AtomicLong();

    public TickerNode(String name) {
        super(name);
    }

    @Override
    protected void onProcess(double dtSeconds) {
        ticks.incrementAndGet();
    }

    public long ticks() {
        return ticks.get();
    }
}

