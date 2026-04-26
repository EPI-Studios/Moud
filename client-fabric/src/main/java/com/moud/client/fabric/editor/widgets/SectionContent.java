package com.moud.client.fabric.editor.widgets;

import com.miry.ui.render.UiRenderer;

@FunctionalInterface
public interface SectionContent {
    void render(UiRenderer r, int x, int y, int width, int height);
}
