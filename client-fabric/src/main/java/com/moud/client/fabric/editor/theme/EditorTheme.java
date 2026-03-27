package com.moud.client.fabric.editor.theme;

import com.miry.ui.theme.Theme;

public final class EditorTheme {
    private EditorTheme() {}

    public static void apply(Theme theme) {
        theme.windowBg.set(Theme.rgba(15, 17, 20, 255));
        theme.panelBg.set(Theme.rgba(22, 25, 30, 255));
        theme.headerBg.set(Theme.rgba(18, 20, 24, 255));
        theme.headerLine.set(Theme.rgba(35, 40, 48, 255));

        theme.widgetBg.set(Theme.rgba(28, 32, 40, 255));
        theme.widgetHover.set(Theme.rgba(36, 42, 54, 255));
        theme.widgetActive.set(Theme.rgba(71, 114, 179, 255));
        theme.widgetOutline.set(Theme.rgba(48, 56, 70, 255));

        theme.text.set(Theme.rgba(232, 236, 245, 255));
        theme.textMuted.set(Theme.rgba(150, 160, 178, 255));

        theme.shadow.set(Theme.rgba(0, 0, 0, 70));
        theme.focusRing.set(Theme.rgba(71, 114, 179, 210));
        theme.accent.set(Theme.rgba(71, 114, 179, 255));
        theme.danger.set(Theme.rgba(239, 68, 68, 255));

        theme.disabledFg.set(Theme.rgba(112, 118, 132, 255));
        theme.disabledBg.set(Theme.rgba(18, 20, 24, 255));

        theme.design.font_sm = 12;
        theme.design.font_base = 13;
        theme.design.space_xs = 2;
        theme.design.space_sm = 5;
        theme.design.space_md = 8;
        theme.design.radius_sm = 6;
        theme.design.radius_md = 8;
        theme.design.radius_input = 4;
        theme.design.radius_tab = 4;
        theme.design.border_thin = 1;
        theme.design.widget_height_sm = 20;
        theme.design.widget_height_md = 24;
        theme.design.tab_height_sm = 20;
        theme.design.tab_height_md = 24;
        theme.design.tab_underline_thickness = 2;
        theme.design.input_padding_x = 7;
        theme.design.input_padding_y = 2;
        theme.design.flat_inputs = true;
        theme.design.flat_surfaces = true;
        theme.design.radius_popup = 8;
        theme.design.menu_item_height = 24;
        theme.design.icon_sm = 16;
        theme.design.icon_md = 20;
        theme.design.icon_lg = 24;

        theme.tokens.padding = 8;
        theme.tokens.itemHeight = 24;
        theme.tokens.itemSpacing = 3;
        theme.tokens.cornerRadius = 6;
        theme.tokens.animSpeed = 14.0f;
    }

    public static int separator(Theme theme) {
        return Theme.toArgb(theme.headerLine);
    }

    public static int accent(Theme theme) {
        return Theme.toArgb(theme.accent);
    }

    public static int textColor(Theme theme) {
        return Theme.toArgb(theme.text);
    }

    public static int textMuted(Theme theme) {
        return Theme.toArgb(theme.textMuted);
    }

    public static final int WARNING_BG = 0xFF704020;
    public static final int WARNING_TEXT = 0xFFFFCC66;
    public static final int ERROR_TEXT = 0xFFEF4444;
    public static final int SUCCESS_TEXT = 0xFF5CB85C;

    public static final int NODE_COLOR_DEFAULT = 0xFF607080;
    public static final int NODE_COLOR_CAMERA = 0xFF4A9EE0;
    public static final int NODE_COLOR_PLAYER = 0xFF5CB85C;
    public static final int NODE_COLOR_ENVIRONMENT = 0xFF9B6EC8;
    public static final int NODE_COLOR_CSG = 0xFF8A9BA8;
    public static final int NODE_COLOR_MESH = 0xFF6EA8D4;
    public static final int NODE_COLOR_SCENE_INSTANCE = 0xFF5BA0A0;
    public static final int NODE_COLOR_LIGHT = 0xFFD4A017;
}
