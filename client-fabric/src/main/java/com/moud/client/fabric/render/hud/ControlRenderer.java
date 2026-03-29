package com.moud.client.fabric.render.hud;

import com.moud.net.protocol.SceneSnapshot;

@FunctionalInterface
public interface ControlRenderer {
    void render(ControlRenderContext ctx,
                SceneSnapshot.NodeSnapshot node,
                int x, int y, int w, int h);
}
