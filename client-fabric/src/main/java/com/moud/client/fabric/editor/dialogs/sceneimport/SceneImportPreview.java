package com.moud.client.fabric.editor.dialogs.sceneimport;

import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Icon;
import com.miry.ui.theme.Theme;
import com.moud.client.fabric.render.MoudIcons;

final class SceneImportPreview {
    private SceneImportPreview() {
    }

    static void draw(UiRenderer r, Theme theme, SceneImportCard card, int x, int y, int w, int h, boolean hovered) {
        String seed = card == null ? "" : card.subtitle();
        int base = color(seed, 0.55f, 0.42f);
        int base2 = color(seed + "/b", 0.55f, 0.32f);
        int accent = color(seed + "/c", 0.72f, 0.58f);
        int line = Theme.mulAlpha(Theme.toArgb(theme.text), hovered ? 0.30f : 0.18f);

        r.drawRoundedRect(x, y, w, h, theme.design.radius_sm, base, theme.design.border_thin, line);
        r.drawRoundedRect(x + 8, y + 8, w - 16, h * 0.44f, theme.design.radius_sm, base2);

        int colGap = 6;
        int blockY = y + (int) (h * 0.58f);
        int leftW = Math.max(18, (w - 22) / 2);
        int rightW = Math.max(18, w - 22 - leftW - colGap);
        int blockH = Math.max(14, h - (blockY - y) - 8);
        r.drawRoundedRect(x + 8, blockY, leftW, blockH, theme.design.radius_sm, Theme.mulAlpha(accent, 0.88f));
        r.drawRoundedRect(x + 8 + leftW + colGap, blockY, rightW, blockH, theme.design.radius_sm, Theme.mulAlpha(base2, 0.92f));

        float iconSize = Math.min(w, h) * 0.22f;
        int iconColor = Theme.mulAlpha(Theme.toArgb(theme.text), hovered ? 0.92f : 0.78f);
        MoudIcons.drawOrFallback(r, theme, Icon.FILE, x + (w - iconSize) * 0.5f, y + 14, iconSize, iconColor);
    }

    private static int color(String seed, float sat, float light) {
        int hash = seed == null ? 0 : seed.hashCode();
        float hue = ((hash & 0x7fffffff) % 360) / 360.0f;
        return hslToArgb(hue, sat, light);
    }

    private static int hslToArgb(float h, float s, float l) {
        float q = l < 0.5f ? l * (1.0f + s) : (l + s - l * s);
        float p = 2.0f * l - q;
        float r = hueToRgb(p, q, h + 1.0f / 3.0f);
        float g = hueToRgb(p, q, h);
        float b = hueToRgb(p, q, h - 1.0f / 3.0f);
        return 0xFF000000
                | ((int) (r * 255.0f) << 16)
                | ((int) (g * 255.0f) << 8)
                | (int) (b * 255.0f);
    }

    private static float hueToRgb(float p, float q, float t) {
        if (t < 0.0f) t += 1.0f;
        if (t > 1.0f) t -= 1.0f;
        if (t < 1.0f / 6.0f) return p + (q - p) * 6.0f * t;
        if (t < 1.0f / 2.0f) return q;
        if (t < 2.0f / 3.0f) return p + (q - p) * (2.0f / 3.0f - t) * 6.0f;
        return p;
    }
}
