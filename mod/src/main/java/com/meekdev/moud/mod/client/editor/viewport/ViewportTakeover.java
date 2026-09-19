package com.meekdev.moud.mod.client.editor.viewport;

import imgui.ImDrawList;

public interface ViewportTakeover {

    void toolbar();

    void draw(ImDrawList drawList, SceneView view, boolean hovered);

    boolean holdsLeftDrag();

    void frameRequested();
}
