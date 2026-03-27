package com.moud.client.fabric.editor.util;

import com.miry.ui.Ui;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Icon;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.ContextMenu;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.render.MoudIcons;

public final class EditorUiUtil {

    private EditorUiUtil() {}

    public static void openMenuClamped(ContextMenu menu, EditorRuntime runtime, int x, int y) {
        if (menu == null) return;

        if (runtime == null || runtime.uiWidth() <= 0 || runtime.uiHeight() <= 0) {
            menu.open(x, y);
            return;
        }

        int maxWidth = Math.max(0, runtime.uiWidth() - Math.max(1, menu.lastWidth()));
        int maxHeight = Math.max(0, runtime.uiHeight() - Math.max(1, menu.lastHeight()));

        menu.open(clamp(x, 0, maxWidth), clamp(y, 0, maxHeight));
    }

    public static void clampOpenMenuToScreen(ContextMenu menu, EditorRuntime runtime) {
        if (menu == null || !menu.isOpen() || runtime == null || runtime.uiWidth() <= 0 || runtime.uiHeight() <= 0) {
            return;
        }

        int maxWidth = Math.max(0, runtime.uiWidth() - Math.max(1, menu.lastWidth()));
        int maxHeight = Math.max(0, runtime.uiHeight() - Math.max(1, menu.lastHeight()));

        int targetX = clamp(menu.x(), 0, maxWidth);
        int targetY = clamp(menu.y(), 0, maxHeight);

        if (targetX != menu.x() || targetY != menu.y()) {
            menu.open(targetX, targetY);
        }
    }

    public static boolean iconButton(Ui ui, UiRenderer renderer, Theme theme,
                                     int x, int y, int w, int h,
                                     Icon icon, boolean interactive, Runnable action) {
        boolean hovered = isHovered(ui, interactive, x, y, w, h);

        if (hovered) {
            int fillColor = Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.65f);
            renderer.drawRoundedRect(x, y, w, h, theme.design.radius_sm, fillColor);
        }

        drawCenteredIcon(renderer, theme, icon, x, y, w, h, Theme.toArgb(theme.text));

        boolean clicked = isClicked(ui, hovered);
        if (clicked && action != null) {
            action.run();
        }

        return clicked;
    }

    public static boolean iconButtonOutlined(Ui ui, UiRenderer renderer, Theme theme,
                                             int x, int y, int w, int h,
                                             Icon icon, boolean interactive, Runnable action) {
        boolean hovered = isHovered(ui, interactive, x, y, w, h);

        int backgroundColor = hovered ? Theme.toArgb(theme.widgetHover) : Theme.toArgb(theme.widgetBg);
        int outlineColor = Theme.toArgb(theme.widgetOutline);

        renderer.drawRoundedRect(x, y, w, h, theme.design.radius_sm, backgroundColor, theme.design.border_thin, outlineColor);
        drawCenteredIcon(renderer, theme, icon, x, y, w, h, Theme.toArgb(theme.textMuted));

        boolean clicked = isClicked(ui, hovered);
        if (clicked && action != null) {
            action.run();
        }

        return clicked;
    }

    public static int toggleButton(Ui ui, UiRenderer renderer, Theme theme,
                                   int x, int y, int w, int h,
                                   Icon icon, boolean active, boolean interactive,
                                   Runnable action) {
        boolean hovered = isHovered(ui, interactive, x, y, w, h);
        int fillColor = 0;

        if (active) {
            fillColor = Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.95f);
        } else if (hovered) {
            fillColor = Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.60f);
        }

        if (fillColor != 0) {
            renderer.drawRoundedRect(x, y, w, h, theme.design.radius_sm, fillColor);
        }

        int iconColor = active
                ? Theme.toArgb(theme.accent)
                : (hovered ? Theme.toArgb(theme.text) : Theme.toArgb(theme.textMuted));

        drawCenteredIcon(renderer, theme, icon, x, y, w, h, iconColor);

        if (isClicked(ui, hovered) && action != null) {
            action.run();
        }

        return x + w;
    }

    public static boolean textButton(Ui ui, UiRenderer renderer, Theme theme,
                                     int x, int y, int w, int h,
                                     String label, boolean interactive, Runnable action) {
        boolean hovered = isHovered(ui, interactive, x, y, w, h);

        int fillColor = Theme.mulAlpha(Theme.toArgb(theme.headerBg), 0.90f);
        if (hovered) {
            fillColor = Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.75f);
        }

        renderer.drawRoundedRect(x, y, w, h, theme.design.radius_sm, fillColor);

        int textColor = interactive ? Theme.toArgb(theme.text) : Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.60f);
        renderer.drawText(label == null ? "" : label, x + theme.design.space_sm, renderer.baselineForBox(y, h), textColor);

        boolean clicked = isClicked(ui, hovered);
        if (clicked && action != null) {
            action.run();
        }

        return clicked;
    }

    public static boolean buttonOutlined(Ui ui, UiRenderer renderer, Theme theme,
                                         int x, int y, int w, int h,
                                         String label, boolean interactive) {
        boolean hovered = isHovered(ui, interactive, x, y, w, h);

        int backgroundColor = hovered ? Theme.toArgb(theme.widgetHover) : Theme.toArgb(theme.widgetBg);
        int outlineColor = Theme.toArgb(theme.widgetOutline);

        renderer.drawRoundedRect(x, y + 2, w, h - 4, theme.design.radius_sm, backgroundColor, theme.design.border_thin, outlineColor);
        renderer.drawText(label == null ? "" : label, x + theme.design.space_sm, renderer.baselineForBox(y, h), Theme.toArgb(theme.text));

        return isPressed(ui, hovered);
    }

    public static boolean stepButton(Ui ui, UiRenderer renderer, Theme theme,
                                     int x, int y, int w, int h,
                                     String label, boolean interactive, Runnable action) {
        boolean hovered = isHovered(ui, interactive, x, y, w, h);

        if (hovered) {
            int fillColor = Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.60f);
            renderer.drawRoundedRect(x, y, w, h, theme.design.radius_sm, fillColor);
        }

        String safeLabel = label == null ? "" : label;
        int textColor = hovered ? Theme.toArgb(theme.text) : Theme.toArgb(theme.textMuted);

        int textWidth = Math.round(renderer.measureText(safeLabel));
        int textX = x + Math.max(0, (w - textWidth) / 2);

        renderer.drawText(safeLabel, textX, renderer.baselineForBox(y, h), textColor);

        boolean clicked = isClicked(ui, hovered);
        if (clicked && action != null) {
            action.run();
        }

        return clicked;
    }

    private static boolean isHovered(Ui ui, boolean interactive, int x, int y, int w, int h) {
        if (!interactive || ui == null || ui.input() == null) return false;

        float mouseX = ui.input().mousePos().x;
        float mouseY = ui.input().mousePos().y;

        return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
    }

    private static boolean isClicked(Ui ui, boolean hovered) {
        return hovered && ui != null && ui.input() != null && ui.input().mouseReleased();
    }

    private static boolean isPressed(Ui ui, boolean hovered) {
        return hovered && ui != null && ui.input() != null && ui.input().mousePressed();
    }

    private static void drawCenteredIcon(UiRenderer renderer, Theme theme, Icon icon, int x, int y, int w, int h, int color) {
        float iconSize = Math.min(theme.design.icon_sm, h - 6);
        float iconX = x + (w - iconSize) * 0.5f;
        float iconY = y + (h - iconSize) * 0.5f;
        MoudIcons.drawOrFallback(renderer, theme, icon, iconX, iconY, iconSize, color);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}