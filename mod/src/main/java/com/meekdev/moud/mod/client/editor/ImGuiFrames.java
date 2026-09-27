package com.meekdev.moud.mod.client.editor;

import foundry.imgui.impl.ImGuiHandler;
import foundry.imgui.impl.ImGuiMCImpl;
import org.jspecify.annotations.Nullable;

public final class ImGuiFrames {

    private static @Nullable ImGuiHandler seen;
    private static long lastFrame = -1;

    private ImGuiFrames() {}

    public static void renderFrame() {
        if (!Editor.available()) return;
        ImGuiHandler handler = ImGuiMCImpl.handler;
        if (handler == null) return;
        if (handler == seen && handler.getFrame() == lastFrame) handler.beginFrame();
        seen = handler;
        lastFrame = handler.getFrame();
    }
}
