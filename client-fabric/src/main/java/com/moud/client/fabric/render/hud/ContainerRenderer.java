package com.moud.client.fabric.render.hud;

import com.moud.net.protocol.SceneSnapshot;

public final class ContainerRenderer implements ControlRenderer {

    public static final ContainerRenderer LAYOUT  = new ContainerRenderer(ControlRenderContext.BG_DIM);
    public static final ContainerRenderer WRAPPER = new ContainerRenderer(ControlRenderContext.BG_GHOST);

    private final int background;

    private ContainerRenderer(int background) {
        this.background = background;
    }

    @Override
    public void render(ControlRenderContext ctx, SceneSnapshot.NodeSnapshot node, int x, int y, int w, int h) {
        ctx.fill(x, y, w, h, background);
        ctx.border(x, y, w, h, ControlRenderContext.BORDER);
    }
}
