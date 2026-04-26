package com.moud.client.fabric.editor.widgets;

import com.miry.graphics.Texture;
import com.miry.ui.input.UiInput;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;

public final class AssetPickerRow {
    private String typeFilter = "";
    private String path = "";
    private Texture thumbnail;
    private AssetPickerListener listener;
    private boolean browseHovered;
    private boolean clearHovered;

    public void setTypeFilter(String filter) { this.typeFilter = filter == null ? "" : filter; }
    public void setPath(String p) { this.path = p == null ? "" : p; }
    public String path() { return path; }
    public void setThumbnail(Texture t) { this.thumbnail = t; }
    public void setListener(AssetPickerListener l) { this.listener = l; }

    public int render(UiRenderer r, UiInput input, Theme theme,
                      int x, int y, int width, int height, boolean interactive) {
        int thumb = height;
        int btnW = 56;
        int clearW = path.isEmpty() ? 0 : 22;
        int gap = 4;
        int nameX = x + thumb + gap;
        int nameW = width - thumb - btnW - clearW - gap * (clearW > 0 ? 4 : 3);

        int thumbBg = Theme.toArgb(theme.widgetBg);
        r.drawRoundedRect(x, y, thumb, thumb, theme.design.radius_sm, thumbBg);
        if (thumbnail != null) {
            int pad = 2;
            r.drawTexturedRect(thumbnail, x + pad, y + pad, thumb - pad * 2, thumb - pad * 2, 0xFFFFFFFF);
        }

        int nameBg = Theme.toArgb(theme.widgetBg);
        r.drawRoundedRect(nameX, y, nameW, height, theme.design.radius_sm, nameBg);
        String label = path.isEmpty() ? "(none)" : path;
        String clipped = r.clipText(label, nameW - 8);
        int textColor = path.isEmpty() ? Theme.toArgb(theme.textMuted) : Theme.toArgb(theme.text);
        r.drawText(clipped, nameX + 6, r.baselineForBox(y, height), textColor);

        int browseX = nameX + nameW + gap;
        boolean canInteract = interactive && input != null;
        float mx = input != null ? input.mousePos().x : -1;
        float my = input != null ? input.mousePos().y : -1;
        browseHovered = canInteract && mx >= browseX && mx < browseX + btnW && my >= y && my < y + height;
        int browseBg = browseHovered ? Theme.toArgb(theme.widgetHover) : Theme.toArgb(theme.widgetBg);
        r.drawRoundedRect(browseX, y, btnW, height, theme.design.radius_sm, browseBg);
        r.drawText("Browse", browseX + 8, r.baselineForBox(y, height), Theme.toArgb(theme.text));

        if (clearW > 0) {
            int clearX = browseX + btnW + gap;
            clearHovered = canInteract && mx >= clearX && mx < clearX + clearW && my >= y && my < y + height;
            int clearBg = clearHovered ? Theme.toArgb(theme.widgetHover) : Theme.toArgb(theme.widgetBg);
            r.drawRoundedRect(clearX, y, clearW, height, theme.design.radius_sm, clearBg);
            r.drawText("X", clearX + 8, r.baselineForBox(y, height), Theme.toArgb(theme.textMuted));
            if (canInteract && clearHovered && input.mousePressed()) {
                path = "";
                thumbnail = null;
                if (listener != null) listener.onBrowse(typeFilter, path);
            }
        }

        if (canInteract && browseHovered && input.mousePressed()) {
            if (listener != null) listener.onBrowse(typeFilter, path);
        }

        return y + height;
    }
}
