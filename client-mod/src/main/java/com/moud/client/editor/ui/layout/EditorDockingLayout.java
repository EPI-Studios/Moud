package com.moud.client.editor.ui.layout;

import imgui.ImGui;
import imgui.flag.ImGuiCond;


public final class EditorDockingLayout {

    public enum Region {
        RIBBON,
        EXPLORER,
        INSPECTOR,
        SCRIPT_VIEWER,
        ASSET_BROWSER,
        ANIMATION_TIMELINE,
        DIAGNOSTICS
    }

    private static final float MIN_CENTER_WIDTH = 420f;
    private static final float MIN_EXPLORER_WIDTH = 280f;
    private static final float MIN_INSPECTOR_WIDTH = 320f;
    private static final float MIN_BOTTOM_HEIGHT = 220f;
    private static final float RIBBON_HEIGHT = 96f;

    private Rect ribbon;
    private Rect explorer;
    private Rect inspector;
    private Rect scriptViewer;
    private Rect assetBrowser;
    private Rect animationTimeline;
    private Rect diagnostics;
    private Rect viewport;
    private float lastWidth;
    private float lastHeight;
    private boolean layoutDirty;

    public void begin(float width, float height) {
        layoutDirty = width != lastWidth || height != lastHeight;
        lastWidth = width;
        lastHeight = height;

        float ribbonHeight = Math.min(Math.max(RIBBON_HEIGHT, height * 0.08f), 128f);
        float bottomHeight = Math.max(MIN_BOTTOM_HEIGHT, height * 0.26f);
        float contentHeight = Math.max(180f, height - ribbonHeight - bottomHeight);

        float inspectorWidth = Math.max(MIN_INSPECTOR_WIDTH, width * 0.28f);
        float explorerWidth = Math.max(MIN_EXPLORER_WIDTH, width * 0.20f);
        float centerWidth = width - inspectorWidth - explorerWidth;
        if (centerWidth < MIN_CENTER_WIDTH) {
            float deficit = MIN_CENTER_WIDTH - centerWidth;
            float reduceExplorer = Math.min(deficit / 2f, explorerWidth - MIN_EXPLORER_WIDTH);
            explorerWidth -= reduceExplorer;
            deficit -= reduceExplorer;
            if (deficit > 0f) {
                float reduceInspector = Math.min(deficit, inspectorWidth - MIN_INSPECTOR_WIDTH);
                inspectorWidth -= reduceInspector;
            }
            centerWidth = Math.max(MIN_CENTER_WIDTH, width - inspectorWidth - explorerWidth);
        }

        float inspectorHeight = contentHeight * 0.55f;
        float scriptHeight = contentHeight - inspectorHeight;

        ribbon = new Rect(0f, 0f, width, ribbonHeight);
        explorer = new Rect(0f, ribbonHeight, explorerWidth, contentHeight);
        inspector = new Rect(width - inspectorWidth, ribbonHeight, inspectorWidth, inspectorHeight);
        scriptViewer = new Rect(width - inspectorWidth, ribbonHeight + inspectorHeight, inspectorWidth, scriptHeight);
        viewport = new Rect(explorerWidth, ribbonHeight, centerWidth, contentHeight);

        float diagnosticsWidth = Math.max(320f, width * 0.28f);
        diagnosticsWidth = Math.min(diagnosticsWidth, width * 0.5f);
        float assetWidth = Math.max(320f, width - diagnosticsWidth);

        float bottomY = height - bottomHeight;
        assetBrowser = new Rect(0f, bottomY, assetWidth, bottomHeight);
        animationTimeline = assetBrowser; // timeline shares dock area as tab with asset browser
        diagnostics = new Rect(width - diagnosticsWidth, bottomY, diagnosticsWidth, bottomHeight);
    }

    public void apply(Region region) {
        Rect rect = switch (region) {
            case RIBBON -> ribbon;
            case EXPLORER -> explorer;
            case INSPECTOR -> inspector;
            case SCRIPT_VIEWER -> scriptViewer;
            case ASSET_BROWSER -> assetBrowser;
            case ANIMATION_TIMELINE -> animationTimeline;
            case DIAGNOSTICS -> diagnostics;
        };
        if (rect == null) {
            return;
        }
        int cond = layoutDirty ? ImGuiCond.Always : ImGuiCond.FirstUseEver;
        ImGui.setNextWindowPos(rect.x, rect.y, cond);
        ImGui.setNextWindowSize(rect.width, rect.height, cond);
    }

    public Rect getViewportRect() {
        return viewport;
    }

    public static final class Rect {
        public final float x;
        public final float y;
        public final float width;
        public final float height;

        private Rect(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
