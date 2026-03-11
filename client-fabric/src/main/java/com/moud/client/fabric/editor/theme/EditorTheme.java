package com.moud.client.fabric.editor.theme;

import com.miry.ui.theme.Theme;

/**
 * Flat dark theme inspired by Godot 4's editor aesthetic.
 *
 * Three-level background hierarchy:
 *   Level 0 (darkest) : windowBg / headerBg  — outer chrome, toolbars
 *   Level 1 (mid)     : panelBg              — panel content areas
 *   Level 2 (lighter) : widgetBg             — interactive inputs, fields
 *
 * Border radius: 0 for structural containers, 2px for interactive widgets only.
 */
public final class EditorTheme {
    private EditorTheme() {}

    public static void apply(Theme theme) {
        // === Backgrounds (strict 3-level hierarchy) ===
        theme.windowBg.set(Theme.rgba(26, 29, 35, 255));      // #1A1D23 – darkest, window chrome
        theme.panelBg.set(Theme.rgba(37, 41, 51, 255));       // #252933 – panel content areas
        theme.headerBg.set(Theme.rgba(22, 25, 30, 255));      // #16191E – toolbars, slightly darker
        theme.headerLine.set(Theme.rgba(15, 17, 21, 255));    // #0F1115 – separator lines

        // === Widgets (inputs / interactive) ===
        theme.widgetBg.set(Theme.rgba(44, 49, 62, 255));      // #2C313E – input field background
        theme.widgetHover.set(Theme.rgba(60, 67, 86, 255));   // #3C4356 – hover state
        theme.widgetActive.set(Theme.rgba(66, 133, 244, 255)); // #4285F4 – active / selection
        theme.widgetOutline.set(Theme.rgba(55, 61, 78, 255)); // #373D4E – subtle border

        // === Text ===
        theme.text.set(Theme.rgba(220, 224, 235, 255));        // #DCE0EB – primary text
        theme.textMuted.set(Theme.rgba(130, 140, 160, 255));   // #828CA0 – secondary / hints

        // === States ===
        theme.shadow.set(Theme.rgba(0, 0, 0, 130));
        theme.focusRing.set(Theme.rgba(66, 133, 244, 200));    // blue focus ring with alpha
        theme.accent.set(Theme.rgba(66, 133, 244, 255));       // #4285F4
        theme.danger.set(Theme.rgba(220, 80, 80, 255));        // #DC5050

        theme.disabledFg.set(Theme.rgba(95, 102, 118, 255));  // #5F6676
        theme.disabledBg.set(Theme.rgba(32, 36, 44, 255));    // #20242C

        // === Design tokens ===
        theme.design.font_sm = 12;
        theme.design.font_base = 13;
        theme.design.space_xs = 2;
        theme.design.space_sm = 4;
        theme.design.space_md = 8;
        theme.design.radius_sm = 4;   // rounded widgets (buttons, inputs)
        theme.design.radius_md = 6;   // panels, larger elements
        theme.design.border_thin = 1;
        theme.design.widget_height_sm = 20;
        theme.design.widget_height_md = 24;
        theme.design.icon_sm = 16;
        theme.design.icon_md = 20;
        theme.design.icon_lg = 24;

        theme.tokens.padding = 8;
        theme.tokens.itemHeight = 24;
        theme.tokens.itemSpacing = 2;
        theme.tokens.cornerRadius = 2;
        theme.tokens.animSpeed = 16.0f;
    }

    // ── Convenience accessors ─────────────────────────────────────────

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
}
