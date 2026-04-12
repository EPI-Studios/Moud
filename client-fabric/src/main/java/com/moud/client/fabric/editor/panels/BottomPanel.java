package com.moud.client.fabric.editor.panels;

import com.miry.ui.PanelContext;
import com.miry.ui.Ui;
import com.miry.ui.input.UiInput;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.StripTabs;
import com.moud.client.fabric.editor.diagnostics.EditorDiagnostics;
import com.moud.client.fabric.editor.state.EditorRuntime;

import java.util.ArrayList;
import java.util.List;

public final class BottomPanel extends Panel {
    private final EditorRuntime runtime;
    private final StripTabs tabs = new StripTabs();
    private final StripTabs.Style tabStyle = new StripTabs.Style();
    private int activeTab;
    private Filter activeFilter = Filter.ALL;
    private float scrollY;
    private boolean collapseDuplicates = true;

    public BottomPanel(EditorRuntime runtime) {
        super("");
        this.runtime = runtime;
    }

    @Override
    public void render(PanelContext ctx) {
        Ui ui = ctx.ui();
        UiRenderer r = ctx.renderer();
        Theme theme = ui.theme();
        boolean interactive = runtime != null && !runtime.uiBlocked();

        int x = ctx.x();
        int y = ctx.y();
        int w = ctx.width();
        int h = ctx.height();

        ui.beginPanel(x, y, w, h);

        int minBarH = theme.design.widget_height_sm - theme.design.space_xs;
        int barH = Math.max(minBarH, theme.design.tab_height_md);
        barH = Math.min(barH, Math.max(minBarH, h));
        int barY = y + h - barH;

        int contentH = Math.max(0, barY - y);
        if (contentH > 8 && activeTab == 0) {
            renderDiagnostics(ui, r, theme, x, y, w, contentH, interactive);
        }

        renderTabBar(ctx, r, theme, x, barY, w, barH, interactive);

        ui.endPanel();
    }

    private void renderTabBar(PanelContext ctx, UiRenderer r, Theme theme, int x, int y, int w, int h, boolean interactive) {
        tabStyle.containerBg = Theme.toArgb(theme.windowBg);
        tabStyle.tabActiveBg = Theme.toArgb(theme.widgetHover);
        tabStyle.tabInactiveBg = 0;
        tabStyle.tabHoverBg = Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.85f);
        tabStyle.borderColor = Theme.toArgb(theme.headerLine);
        tabStyle.highlightColor = Theme.toArgb(theme.accent);
        tabStyle.textActive = Theme.toArgb(theme.text);
        tabStyle.textInactive = Theme.toArgb(theme.textMuted);
        tabStyle.equalWidth = false;
        tabStyle.highlightTop = false;
        tabStyle.highlightThickness = 0;

        String[] labels = new String[]{"Diagnostics"};
        var input = interactive ? ctx.ui().input() : null;
        activeTab = tabs.render(r, ctx.uiContext(), input, theme, x, y, w, h, labels, 0, true, tabStyle);
    }

    private void renderDiagnostics(Ui ui, UiRenderer r, Theme theme, int x, int y, int w, int h, boolean interactive) {
        int panelBg = Theme.toArgb(theme.panelBg);
        int outline = Theme.toArgb(theme.headerLine);
        int muted = Theme.toArgb(theme.textMuted);

        r.drawRect(x, y, w, h, panelBg);

        EditorDiagnostics.Snapshot snapshot = EditorDiagnostics.snapshot();
        List<DisplayEntry> filtered = filteredEntries(snapshot.entries(), activeFilter, collapseDuplicates);

        int toolbarH = Math.round(26.0f * runtime.editorUiScale());
        int listY = y + toolbarH + 1;
        int listH = Math.max(1, h - toolbarH - 1);
        UiInput input = interactive ? ui.input() : null;

        renderToolbar(r, theme, snapshot.entries(), x, y, w, toolbarH, interactive, input);

        if (filtered.isEmpty()) {
            r.drawRect(x, listY, w, listH, Theme.mulAlpha(Theme.toArgb(theme.windowBg), 0.45f));
            r.drawText("No diagnostics — shader, script, asset, and scene errors will appear here.",
                    x + theme.design.space_md,
                    r.baselineForBox(listY + theme.design.space_sm, theme.design.widget_height_sm),
                    muted);
            return;
        }

        boolean hoveredList = input != null
                && input.mousePos().x >= x
                && input.mousePos().x < x + w
                && input.mousePos().y >= listY
                && input.mousePos().y < listY + listH;
        int rowH = Math.round(22.0f * runtime.editorUiScale());
        int rowGap = 1;
        int contentH = filtered.size() * (rowH + rowGap);
        int maxScroll = Math.max(0, contentH - listH);
        if (hoveredList && interactive && input != null) {
            scrollY = clamp(scrollY - input.consumeMouseScrollDelta() * 30.0f, 0.0f, maxScroll);
        } else {
            scrollY = clamp(scrollY, 0.0f, maxScroll);
        }

        r.drawRect(x, listY, w, listH, Theme.mulAlpha(Theme.toArgb(theme.windowBg), 0.45f));
        renderListHeader(r, theme, x, listY, w);
        r.pushClip(x, listY, w, listH);
        float rowY = listY + 21 - scrollY;
        for (DisplayEntry entry : filtered) {
            if (rowY + rowH < listY) {
                rowY += rowH + rowGap;
                continue;
            }
            if (rowY > listY + listH) {
                break;
            }
            renderEntry(r, theme, entry, x + theme.design.space_sm, Math.round(rowY), w - theme.design.space_sm * 2, rowH);
            rowY += rowH + rowGap;
        }
        r.popClip();
    }

    private void renderToolbar(UiRenderer r, Theme theme, List<EditorDiagnostics.Entry> entries, int x, int y, int w, int h, boolean interactive, UiInput input) {
        int bg = Theme.toArgb(theme.windowBg);
        int line = Theme.toArgb(theme.headerLine);
        r.drawRect(x, y, w, h, bg);
        r.drawRect(x, y + h - 1, w, 1, line);

        int btnH = Math.max(16, h - 6);
        int btnY = y + (h - btnH) / 2;
        int cursor = x + w - theme.design.space_sm;

        String clearLabel = "Clear";
        int clearW = Math.round(r.measureText(clearLabel) + 16.0f);
        cursor -= clearW;
        boolean clearHovered = isHovered(input, cursor, btnY, clearW, btnH);
        int clearFill = clearHovered ? Theme.toArgb(theme.widgetHover) : 0;
        if (clearFill != 0) r.drawRoundedRect(cursor, btnY, clearW, btnH, theme.design.radius_sm, clearFill, 0, 0);
        r.drawText(clearLabel, cursor + 8, r.baselineForBox(btnY, btnH), Theme.toArgb(theme.textMuted));
        if (interactive && clearHovered && input != null && input.mousePressed()) {
            EditorDiagnostics.clear();
            scrollY = 0.0f;
        }

        cursor -= theme.design.space_sm;
        r.drawRect(cursor, y + 4, 1, h - 8, line);
        cursor -= theme.design.space_sm;

        String collapseLabel = collapseDuplicates ? "Collapse: on" : "Collapse: off";
        int collapseW = Math.round(r.measureText(collapseLabel) + 16.0f);
        cursor -= collapseW;
        boolean collapseHovered = isHovered(input, cursor, btnY, collapseW, btnH);
        int collapseFill = collapseHovered ? Theme.toArgb(theme.widgetHover)
                : (collapseDuplicates ? Theme.mulAlpha(Theme.toArgb(theme.accent), 0.14f) : 0);
        if (collapseFill != 0) r.drawRoundedRect(cursor, btnY, collapseW, btnH, theme.design.radius_sm, collapseFill, 0, 0);
        int collapseText = collapseDuplicates ? Theme.toArgb(theme.text) : Theme.toArgb(theme.textMuted);
        r.drawText(collapseLabel, cursor + 8, r.baselineForBox(btnY, btnH), collapseText);
        if (interactive && collapseHovered && input != null && input.mousePressed()) {
            collapseDuplicates = !collapseDuplicates;
            scrollY = 0.0f;
        }

        cursor -= theme.design.space_sm;
        r.drawRect(cursor, y + 4, 1, h - 8, line);

        int chipCursor = x + theme.design.space_sm;
        for (Filter filter : Filter.values()) {
            int count = countFor(entries, filter);
            String label = filter.label + " " + count;
            int chipW = Math.round(r.measureText(label) + 16.0f);
            if (chipCursor + chipW > cursor - theme.design.space_sm) break;
            boolean active = activeFilter == filter;
            boolean hovered = isHovered(input, chipCursor, btnY, chipW, btnH);
            if (active) {
                r.drawRoundedRect(chipCursor, btnY, chipW, btnH, theme.design.radius_sm,
                        Theme.mulAlpha(Theme.toArgb(theme.accent), 0.18f), 0, 0);
            } else if (hovered) {
                r.drawRoundedRect(chipCursor, btnY, chipW, btnH, theme.design.radius_sm,
                        Theme.toArgb(theme.widgetHover), 0, 0);
            }
            int chipText = active ? Theme.toArgb(theme.text) : Theme.toArgb(theme.textMuted);
            r.drawText(label, chipCursor + 8, r.baselineForBox(btnY, btnH), chipText);
            if (active) {
                r.drawRect(chipCursor + 4, btnY + btnH - 2, chipW - 8, 2, Theme.toArgb(theme.accent));
            }
            if (interactive && hovered && input != null && input.mousePressed()) {
                activeFilter = filter;
                scrollY = 0.0f;
            }
            chipCursor += chipW + 2;
        }
    }

    private void renderListHeader(UiRenderer r, Theme theme, int x, int y, int w) {
        int headerH = 20;
        int bg = Theme.mulAlpha(Theme.toArgb(theme.widgetBg), 0.40f);
        int line = Theme.toArgb(theme.headerLine);
        int muted = Theme.toArgb(theme.textMuted);
        r.drawRect(x, y, w, headerH, bg);
        r.drawRect(x, y + headerH, w, 1, line);
        r.drawText("Type", x + theme.design.space_md, r.baselineForBox(y, headerH), muted);
        r.drawText("Source", x + 100, r.baselineForBox(y, headerH), muted);
        r.drawText("Message", x + 210, r.baselineForBox(y, headerH), muted);
        r.drawText("Time", x + w - 60, r.baselineForBox(y, headerH), muted);
    }

    private void renderEntry(UiRenderer r, Theme theme, DisplayEntry entry, int x, int y, int w, int h) {
        int line = Theme.toArgb(theme.headerLine);
        int accent = severityColor(theme, entry.entry.severity());
        int rowBg = Theme.mulAlpha(Theme.toArgb(theme.widgetBg), (y / Math.max(1, h)) % 2 == 0 ? 0.18f : 0.08f);
        r.drawRect(x, y, w, h, rowBg);
        r.drawRect(x, y + h - 1, w, 1, line);
        r.drawRect(x, y, 2, h, accent);

        String severity = switch (entry.entry.severity()) {
            case ERROR -> "ERR";
            case WARN -> "WRN";
            case INFO -> "INF";
        };
        String source = entry.entry.source().isBlank() ? "General" : entry.entry.source();
        String time = entry.entry.formattedTime();
        String repeat = entry.repeatCount > 1 ? " ×" + entry.repeatCount : "";

        r.drawText(severity + repeat, x + theme.design.space_md, r.baselineForBox(y, h), accent);
        r.drawText(ellipsize(r, source, 96), x + 100, r.baselineForBox(y, h), Theme.toArgb(theme.textMuted));
        r.drawText(ellipsize(r, entry.entry.message(), Math.max(40, w - 280)), x + 210, r.baselineForBox(y, h), Theme.toArgb(theme.text));
        r.drawText(time, x + w - 58, r.baselineForBox(y, h), Theme.toArgb(theme.textMuted));
    }

    private static int severityColor(Theme theme, EditorDiagnostics.Severity severity) {
        return switch (severity) {
            case ERROR -> 0xFFE16A5A;
            case WARN -> 0xFFE3B341;
            case INFO -> Theme.toArgb(theme.accent);
        };
    }

    private static boolean isHovered(UiInput input, int x, int y, int w, int h) {
        return input != null
                && input.mousePos().x >= x
                && input.mousePos().x < x + w
                && input.mousePos().y >= y
                && input.mousePos().y < y + h;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static List<DisplayEntry> filteredEntries(List<EditorDiagnostics.Entry> entries, Filter filter, boolean collapse) {
        ArrayList<DisplayEntry> out = new ArrayList<>();
        if (entries == null || entries.isEmpty()) {
            return out;
        }
        for (int i = entries.size() - 1; i >= 0; i--) {
            EditorDiagnostics.Entry entry = entries.get(i);
            if (entry == null) {
                continue;
            }
            if (filter.matches(entry)) {
                if (collapse && !out.isEmpty() && out.get(out.size() - 1).isSame(entry)) {
                    out.get(out.size() - 1).repeatCount++;
                } else {
                    out.add(new DisplayEntry(entry));
                }
            }
        }
        return out;
    }

    private static int countFor(List<EditorDiagnostics.Entry> entries, Filter filter) {
        int count = 0;
        if (entries == null) {
            return 0;
        }
        for (EditorDiagnostics.Entry entry : entries) {
            if (entry != null && filter.matches(entry)) {
                count++;
            }
        }
        return count;
    }

    private static String ellipsize(UiRenderer r, String text, float maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (r.measureText(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int end = text.length();
        while (end > 0) {
            String candidate = text.substring(0, end) + ellipsis;
            if (r.measureText(candidate) <= maxWidth) {
                return candidate;
            }
            end--;
        }
        return ellipsis;
    }

    private enum Filter {
        ALL("All") {
            @Override
            boolean matches(EditorDiagnostics.Entry entry) {
                return true;
            }
        },
        ERRORS("Errors") {
            @Override
            boolean matches(EditorDiagnostics.Entry entry) {
                return entry.severity() == EditorDiagnostics.Severity.ERROR;
            }
        },
        WARNINGS("Warnings") {
            @Override
            boolean matches(EditorDiagnostics.Entry entry) {
                return entry.severity() == EditorDiagnostics.Severity.WARN;
            }
        },
        SHADERS("Shaders") {
            @Override
            boolean matches(EditorDiagnostics.Entry entry) {
                return "Shaders".equals(entry.source());
            }
        },
        SCRIPTS("Scripts") {
            @Override
            boolean matches(EditorDiagnostics.Entry entry) {
                return "Scripts".equals(entry.source());
            }
        },
        ASSETS("Assets") {
            @Override
            boolean matches(EditorDiagnostics.Entry entry) {
                return "Assets".equals(entry.source());
            }
        };

        private final String label;

        Filter(String label) {
            this.label = label;
        }

        abstract boolean matches(EditorDiagnostics.Entry entry);
    }

    private static final class DisplayEntry {
        private final EditorDiagnostics.Entry entry;
        private int repeatCount = 1;

        private DisplayEntry(EditorDiagnostics.Entry entry) {
            this.entry = entry;
        }

        private boolean isSame(EditorDiagnostics.Entry other) {
            return other != null
                    && entry.severity() == other.severity()
                    && entry.source().equals(other.source())
                    && entry.message().equals(other.message());
        }
    }
}
