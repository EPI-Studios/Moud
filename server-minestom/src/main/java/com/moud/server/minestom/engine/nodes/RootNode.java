package com.moud.server.minestom.engine.nodes;

import com.moud.core.scene.Node;

import java.util.concurrent.atomic.AtomicLong;

public final class RootNode extends Node {
    private final AtomicLong processed = new AtomicLong();

    public RootNode(String name) {
        super(name);
    }

    @Override
    protected void onReady() {
        addChild(new TickerNode("ticker"));
    }

    @Override
    protected void onProcess(double dtSeconds) {
        processed.incrementAndGet();
    }

    public long processedFrames() {
        return processed.get();
    }
}

