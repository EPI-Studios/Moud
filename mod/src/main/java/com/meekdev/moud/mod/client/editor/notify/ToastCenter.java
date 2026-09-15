package com.meekdev.moud.mod.client.editor.notify;

import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ToastCenter implements Notifier {

    private static final long TOAST_DURATION_MILLIS = 2_500L;
    private static final float MARGIN = 16.0f;
    private static final float TOAST_WIDTH = 340.0f;
    private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoDecoration
            | ImGuiWindowFlags.NoInputs
            | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoSavedSettings
            | ImGuiWindowFlags.NoFocusOnAppearing
            | ImGuiWindowFlags.NoDocking
            | ImGuiWindowFlags.AlwaysAutoResize;

    private final List<Toast> toasts = new ArrayList<>();

    @Override
    public synchronized void show(String message) {
        toasts.add(new Toast(message, System.currentTimeMillis() + TOAST_DURATION_MILLIS));
    }

    public synchronized Optional<String> latestMessage() {
        return toasts.isEmpty() ? Optional.empty() : Optional.of(toasts.get(toasts.size() - 1).message());
    }

    public synchronized void render() {
        long now = System.currentTimeMillis();
        toasts.removeIf(toast -> toast.expiresAtMillis() < now);
        if (toasts.isEmpty()) {
            return;
        }
        positionWindow();
        if (ImGui.begin("##toasts", WINDOW_FLAGS)) {
            renderToastRows();
        }
        ImGui.end();
    }

    private void positionWindow() {
        ImGuiViewport viewport = ImGui.getMainViewport();
        float x = viewport.getWorkPosX() + viewport.getWorkSizeX() - EditorScale.of(MARGIN);
        float y = viewport.getWorkPosY() + viewport.getWorkSizeY() - EditorScale.of(MARGIN);
        ImGui.setNextWindowPos(x, y, ImGuiCond.Always, 1.0f, 1.0f);
        ImGui.setNextWindowSizeConstraints(EditorScale.of(TOAST_WIDTH), 0.0f, EditorScale.of(TOAST_WIDTH), Float.MAX_VALUE);
        ImGui.setNextWindowBgAlpha(0.92f);
    }

    private void renderToastRows() {
        for (int i = 0; i < toasts.size(); i++) {
            if (i > 0) {
                ImGui.separator();
            }
            ImGui.pushTextWrapPos(EditorScale.of(TOAST_WIDTH) - EditorStyle.windowPadding());
            ImGui.textUnformatted(toasts.get(i).message());
            ImGui.popTextWrapPos();
        }
    }

    private record Toast(String message, long expiresAtMillis) {
    }
}
