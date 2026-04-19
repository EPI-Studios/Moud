package com.moud.client.fabric.editor.panels;

import com.miry.ui.PanelContext;
import com.miry.ui.UiContext;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Icon;
import com.miry.ui.theme.Theme;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.editor.tools.EditorTool;
import com.moud.client.fabric.editor.util.EditorUiUtil;

class ViewportToolbar {
    private final EditorRuntime runtime;

    ViewportToolbar(EditorRuntime runtime) {
        this.runtime = runtime;
    }

    void renderViewportToolbar(PanelContext ctx, UiRenderer r, UiContext uiContext, Theme theme, int x, int y, int w, int h, boolean interactive) {
        int bg = Theme.toArgb(theme.windowBg);
        r.drawRect(x, y, w, h, bg);
        r.drawRect(x, y + h - 1, w, 1, Theme.toArgb(theme.headerLine));

        var ui = ctx.ui();
        int pad = theme.design.space_sm;
        int cursorX = x + pad;
        int btn = 22;
        int groupPad = 2;

        int groupW = btn * 4 + pad * 3;
        int groupH = btn + groupPad * 2;
        int groupY = y + (h - groupH) / 2;
        int groupBg = Theme.toArgb(theme.headerBg);
        r.drawRoundedRect(cursorX, groupY, groupW, groupH, theme.design.radius_sm, groupBg);

        int bx = cursorX + groupPad;
        int by = groupY + groupPad;
        EditorTool tool = runtime.tool();
        bx = EditorUiUtil.toggleButton(ui, r, theme, bx, by, btn, btn, Icon.SELECT, tool == EditorTool.SELECT, interactive, () -> runtime.setTool(EditorTool.SELECT)) + pad;
        bx = EditorUiUtil.toggleButton(ui, r, theme, bx, by, btn, btn, Icon.MOVE, tool == EditorTool.MOVE, interactive, () -> runtime.setTool(EditorTool.MOVE)) + pad;
        bx = EditorUiUtil.toggleButton(ui, r, theme, bx, by, btn, btn, Icon.ROTATE, tool == EditorTool.ROTATE, interactive, () -> runtime.setTool(EditorTool.ROTATE)) + pad;
        EditorUiUtil.toggleButton(ui, r, theme, bx, by, btn, btn, Icon.SCALE, tool == EditorTool.SCALE, interactive, () -> runtime.setTool(EditorTool.SCALE));

        cursorX += groupW + pad * 2;

        int snapStepW = 52;
        int snapGroupW = groupPad * 2 + btn + pad + snapStepW;
        r.drawRoundedRect(cursorX, groupY, snapGroupW, groupH, theme.design.radius_sm, groupBg);
        int sx = cursorX + groupPad;
        boolean snapEnabled = runtime != null && runtime.gridSnapEnabled();
        sx = EditorUiUtil.toggleButton(ui, r, theme, sx, by, btn, btn, Icon.SNAP, snapEnabled, interactive, () -> {
            if (runtime != null) {
                runtime.setGridSnapEnabled(!runtime.gridSnapEnabled());
            }
        }) + pad;

        float step = runtime == null ? 1.0f : runtime.gridSnapStep();
        String stepLabel = formatSnapStep(step);
        EditorUiUtil.stepButton(ui, r, theme, sx, by, snapStepW, btn, stepLabel, interactive, () -> {
            if (runtime != null) {
                runtime.cycleGridSnapStep();
            }
        });

        cursorX += snapGroupW + pad * 2;

        int rotStepW = 40;
        int rotGroupW = groupPad * 2 + btn + pad + rotStepW;
        r.drawRoundedRect(cursorX, groupY, rotGroupW, groupH, theme.design.radius_sm, groupBg);
        int rx = cursorX + groupPad;
        boolean rotSnapEnabled = runtime != null && runtime.rotationSnapEnabled();
        rx = EditorUiUtil.toggleButton(ui, r, theme, rx, by, btn, btn, Icon.ROTATE, rotSnapEnabled, interactive, () -> {
            if (runtime != null) {
                runtime.setRotationSnapEnabled(!runtime.rotationSnapEnabled());
            }
        }) + pad;
        float rotStep = runtime == null ? 15.0f : runtime.rotationSnapDeg();
        String rotStepLabel = Math.abs(rotStep - 15.0f) < 1e-3f ? "15°"
                : Math.abs(rotStep - 45.0f) < 1e-3f ? "45°" : "90°";
        EditorUiUtil.stepButton(ui, r, theme, rx, by, rotStepW, btn, rotStepLabel, interactive, () -> {
            if (runtime != null) {
                runtime.cycleRotationSnapDeg();
            }
        });

        cursorX += rotGroupW + pad * 2;

        int spaceGroupW = groupPad * 2 + btn;
        r.drawRoundedRect(cursorX, groupY, spaceGroupW, groupH, theme.design.radius_sm, groupBg);
        boolean localSpace = runtime != null && runtime.gizmoLocalSpace();
        EditorUiUtil.toggleButton(ui, r, theme, cursorX + groupPad, by, btn, btn, Icon.ROTATE, localSpace, interactive, () -> {
            if (runtime != null) runtime.setGizmoLocalSpace(!runtime.gizmoLocalSpace());
        });
        int lx = cursorX + groupPad + btn + theme.design.space_sm;
        r.drawText(localSpace ? "Local" : "World", lx, r.baselineForBox(groupY, groupH), Theme.toArgb(localSpace ? theme.text : theme.textMuted));

        cursorX += spaceGroupW + pad * 2;
    }

    static String formatSnapStep(float step) {
        if (!Float.isFinite(step) || step <= 0.0f) {
            return "1m";
        }
        if (Math.abs(step - 1.0f) < 1e-6f) {
            return "1m";
        }
        if (Math.abs(step - 0.5f) < 1e-6f) {
            return "0.5m";
        }
        if (Math.abs(step - 0.1f) < 1e-6f) {
            return "0.1m";
        }
        String s = Float.toString(step);
        if (s.endsWith(".0")) {
            s = s.substring(0, s.length() - 2);
        }
        return s + "m";
    }
}

