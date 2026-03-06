package com.moud.client.fabric.editor.util;


public final class EditorUiUtil {
    private EditorUiUtil() {
    }

    public static void openMenuClamped(ContextMenu menu, EditorRuntime runtime, int x, int y) {
        if (menu == null) {
            return;
        }
        int screenW = runtime != null ? runtime.uiWidth() : 0;
        int screenH = runtime != null ? runtime.uiHeight() : 0;
        if (screenW <= 0 || screenH <= 0) {
            menu.open(x, y);
            return;
        }
        int mw = Math.max(1, menu.lastWidth());
        int mh = Math.max(1, menu.lastHeight());
        int nx = clamp(x, 0, Math.max(0, screenW - mw));
        int ny = clamp(y, 0, Math.max(0, screenH - mh));
        menu.open(nx, ny);
    }

    public static void clampOpenMenuToScreen(ContextMenu menu, EditorRuntime runtime) {
        if (menu == null || runtime == null || !menu.isOpen()) {
            return;
        }
        int screenW = runtime.uiWidth();
        int screenH = runtime.uiHeight();
        if (screenW <= 0 || screenH <= 0) {
            return;
        }
        int mw = Math.max(1, menu.lastWidth());
        int mh = Math.max(1, menu.lastHeight());
        int x = menu.x();
        int y = menu.y();
        int nx = clamp(x, 0, Math.max(0, screenW - mw));
        int ny = clamp(y, 0, Math.max(0, screenH - mh));
        if (nx != x || ny != y) {
            menu.open(nx, ny);
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}

