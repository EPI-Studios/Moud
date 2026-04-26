package com.moud.client.fabric.editor.widgets;

import com.miry.ui.UiContext;
import com.miry.ui.input.UiInput;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.CollapsibleHeader;

public final class CollapsibleSection {
    private final CollapsibleHeader header = new CollapsibleHeader();
    private String title = "";
    private int headerHeight = 22;

    public CollapsibleSection() { }

    public CollapsibleSection(String title) { this.title = title == null ? "" : title; }

    public void setTitle(String title) { this.title = title == null ? "" : title; }
    public void setHeaderHeight(int h) { this.headerHeight = Math.max(14, h); }
    public void setExpanded(boolean expanded) { header.setExpanded(expanded); }
    public boolean expanded() { return header.expanded(); }

    public int render(UiRenderer r, UiContext ctx, UiInput input, Theme theme,
                      int x, int y, int width, int contentTargetH, boolean interactive,
                      SectionContent content) {
        CollapsibleHeader.Content adapter = (renderer, cx, cy, cw, ch) -> {
            if (content != null) content.render(renderer, cx, cy, cw, ch);
        };
        return header.render(r, ctx, input, theme, x, y, width, headerHeight, contentTargetH, title, interactive, adapter);
    }
}
