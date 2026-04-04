package com.moud.client.fabric.editor.theme;

import com.miry.ui.theme.Theme;

public final class EditorTheme {
    private EditorTheme() {}

    public static void apply(Theme theme) {
        apply(theme, 1.0f);
    }

    public static void apply(Theme theme, float uiScale) {
        float scale = Math.max(0.75f, Math.min(1.75f, uiScale));

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

        theme.design.font_xs = scaledInt(11, scale);
        theme.design.font_sm = scaledInt(12, scale);
        theme.design.font_base = scaledInt(13, scale);
        theme.design.font_md = scaledInt(16, scale);
        theme.design.font_lg = scaledInt(20, scale);
        theme.design.font_xl = scaledInt(24, scale);

        theme.design.space_xs = scaledInt(2, scale);
        theme.design.space_sm = scaledInt(5, scale);
        theme.design.space_md = scaledInt(8, scale);
        theme.design.space_lg = scaledInt(12, scale);
        theme.design.space_xl = scaledInt(18, scale);
        theme.design.space_2xl = scaledInt(24, scale);

        theme.design.radius_sm = scaledInt(6, scale);
        theme.design.radius_md = scaledInt(8, scale);
        theme.design.radius_lg = scaledInt(10, scale);
        theme.design.radius_xl = scaledInt(12, scale);
        theme.design.radius_input = scaledFloat(4.0f, scale);
        theme.design.radius_tab = scaledFloat(4.0f, scale);
        theme.design.radius_popup = scaledFloat(8.0f, scale);
        theme.design.border_thin = Math.max(1, scaledInt(1, scale));
        theme.design.border_medium = Math.max(theme.design.border_thin, scaledInt(2, scale));
        theme.design.border_thick = Math.max(theme.design.border_medium, scaledInt(3, scale));
        theme.design.widget_height_sm = scaledInt(20, scale);
        theme.design.widget_height_md = scaledInt(24, scale);
        theme.design.widget_height_lg = scaledInt(28, scale);
        theme.design.widget_height_xl = scaledInt(36, scale);
        theme.design.tab_height_sm = scaledInt(20, scale);
        theme.design.tab_height_md = scaledInt(24, scale);
        theme.design.toolbar_height = scaledInt(28, scale);
        theme.design.menu_item_height = scaledInt(24, scale);
        theme.design.tab_underline_thickness = Math.max(1, scaledInt(2, scale));
        theme.design.input_padding_x = scaledInt(7, scale);
        theme.design.input_padding_y = scaledInt(2, scale);
        theme.design.flat_inputs = true;
        theme.design.flat_surfaces = true;
        theme.design.icon_xs = scaledInt(12, scale);
        theme.design.icon_sm = scaledInt(16, scale);
        theme.design.icon_md = scaledInt(20, scale);
        theme.design.icon_lg = scaledInt(24, scale);
        theme.design.icon_xl = scaledInt(28, scale);

        theme.tokens.padding = scaledInt(8, scale);
        theme.tokens.itemHeight = scaledInt(24, scale);
        theme.tokens.itemSpacing = scaledInt(3, scale);
        theme.tokens.cornerRadius = scaledInt(6, scale);
        theme.tokens.animSpeed = 14.0f;
    }

    private static int scaledInt(int base, float scale) {
        return Math.max(1, Math.round(base * scale));
    }

    private static float scaledFloat(float base, float scale) {
        return Math.max(1.0f, base * scale);
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
