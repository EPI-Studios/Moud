package com.moud.client.fabric.editor.theme;

import com.miry.ui.theme.Theme;

public final class EditorTheme {
    private EditorTheme() {
    }

    public static void apply(Theme theme) {
        theme.windowBg.set(Theme.rgba(33, 37, 41, 255));
        theme.panelBg.set(Theme.rgba(33, 37, 41, 255));
        theme.headerBg.set(Theme.rgba(25, 28, 32, 255));
        theme.headerLine.set(Theme.rgba(17, 19, 22, 255));

        theme.widgetBg.set(Theme.rgba(25, 28, 32, 255));
        theme.widgetHover.set(Theme.rgba(64, 69, 83, 255));
        theme.widgetActive.set(Theme.rgba(77, 144, 254, 255));
        theme.widgetOutline.set(Theme.rgba(50, 56, 66, 255));

        theme.text.set(Theme.rgba(224, 224, 224, 255));
        theme.textMuted.set(Theme.rgba(179, 179, 179, 255));

        theme.shadow.set(Theme.rgba(0, 0, 0, 120));
        theme.focusRing.set(Theme.rgba(77, 144, 254, 255));
        theme.accent.set(Theme.rgba(77, 144, 254, 255));
        theme.danger.set(Theme.rgba(252, 127, 127, 255));

        theme.disabledFg.set(Theme.rgba(111, 116, 125, 255));
        theme.disabledBg.set(Theme.rgba(25, 28, 32, 255));

        theme.design.font_sm = 12;
        theme.design.font_base = 13;
        theme.design.space_xs = 2;
        theme.design.space_sm = 4;
        theme.design.space_md = 8;
        theme.design.radius_sm = 4;
        theme.design.radius_md = 4;
        theme.design.border_thin = 1;
        theme.design.widget_height_sm = 18;
        theme.design.widget_height_md = 22;
        theme.design.icon_sm = 16;
        theme.design.icon_md = 20;
        theme.design.icon_lg = 24;

        theme.tokens.padding = 8;
        theme.tokens.itemHeight = 22;
        theme.tokens.itemSpacing = 4;
        theme.tokens.cornerRadius = 4;
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