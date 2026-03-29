package com.moud.server.minestom.engine.nodes;


import com.moud.core.scene.Node;
import com.moud.server.minestom.engine.MinestomNodeTypesProvider;

import java.util.concurrent.atomic.AtomicLong;

public final class RootNode extends Node {
    private final AtomicLong processed = new AtomicLong();

    public RootNode(String name) {
        super(name);
    }

    private boolean is2D() {
        String mode = getProperty(MinestomNodeTypesProvider.PROP_SCENE_MODE);
        return mode != null && mode.equalsIgnoreCase("2d");
    }

    @Override
    protected void onReady() {
        if (is2D()) {
            Node ticker = findChild("ticker");
            if (ticker != null) {
                ticker.queueFree();
            }
            return;
        }
        if (findChild("ticker") == null) {
            addChild(new TickerNode("ticker"));
        }
    }

    @Override
    protected void onProcess(double dtSeconds) {
        processed.incrementAndGet();
        boolean is2d = is2D();
        Node ticker = findChild("ticker");
        if (is2d) {
            if (ticker != null) {
                ticker.queueFree();
            }
        } else {
            if (ticker == null) {
                addChild(new TickerNode("ticker"));
            }
        }
    }

    public long processedFrames() {
        return processed.get();
    }
}
