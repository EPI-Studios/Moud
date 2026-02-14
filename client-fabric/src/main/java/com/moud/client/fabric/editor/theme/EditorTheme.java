package com.moud.client.fabric.editor.theme;

import com.miry.ui.theme.Theme;

public final class EditorTheme {
    private EditorTheme() {
    }

    public static void apply(Theme theme) {
        theme.windowBg.set(Theme.rgba(30, 30, 30, 255));
        theme.panelBg.set(Theme.rgba(37, 37, 37, 255));
        theme.headerBg.set(Theme.rgba(42, 42, 42, 255));
        theme.headerLine.set(Theme.rgba(50, 50, 50, 255));

        theme.widgetBg.set(Theme.rgba(42, 42, 42, 255));
        theme.widgetHover.set(Theme.rgba(52, 52, 52, 255));
        theme.widgetActive.set(Theme.rgba(66, 133, 244, 255));
        theme.widgetOutline.set(Theme.rgba(60, 60, 60, 255));

        theme.text.set(Theme.rgba(230, 230, 230, 255));
        theme.textMuted.set(Theme.rgba(150, 150, 150, 255));

        theme.shadow.set(Theme.rgba(0, 0, 0, 100));
        theme.focusRing.set(Theme.rgba(66, 133, 244, 255));

        theme.disabledFg.set(Theme.rgba(120, 120, 120, 255));
        theme.disabledBg.set(Theme.rgba(35, 35, 35, 255));

        theme.design.font_sm = 12;
        theme.design.font_base = 13;
        theme.design.space_xs = 2;
        theme.design.space_sm = 4;
        theme.design.space_md = 8;
        theme.design.radius_sm = 2;
        theme.design.radius_md = 3;
        theme.design.border_thin = 1;
        theme.design.widget_height_sm = 18;
        theme.design.widget_height_md = 22;
        theme.design.icon_sm = 16;
        theme.design.icon_md = 20;
        theme.design.icon_lg = 24;

        theme.tokens.padding = 4;
        theme.tokens.itemHeight = 18;
        theme.tokens.itemSpacing = 2;
        theme.tokens.cornerRadius = 2;
        theme.tokens.animSpeed = 12.0f;
    }

    public static int separator(Theme theme) {
        return Theme.toArgb(Theme.rgba(60, 60, 63, 255));
    }

    public static int accent(Theme theme) {
        return Theme.toArgb(theme.widgetActive);
    }

    public static int textColor(Theme theme) {
        return Theme.toArgb(theme.text);
    }

    public static int textMuted(Theme theme) {
        return Theme.toArgb(theme.textMuted);
    }
}