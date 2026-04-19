package com.moud.client.fabric.editor.panels;

import com.miry.ui.PanelContext;
import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.event.KeyEvent;
import com.miry.ui.event.TextInputEvent;
import com.miry.ui.input.UiInput;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.SearchBox;
import com.miry.ui.widgets.StripTabs;
import com.moud.client.fabric.editor.diagnostics.ClientFrameProfiler;
import com.moud.client.fabric.editor.diagnostics.ClientNetworkLog;
import com.moud.client.fabric.editor.diagnostics.ClientOutput;
import com.moud.client.fabric.editor.diagnostics.EditorDiagnostics;
import com.moud.client.fabric.editor.state.EditorRuntime;

import java.util.ArrayList;
import java.util.List;

public final class BottomPanel extends Panel {
    private static final String[] TAB_LABELS = {"Diagnostics", "Output", "Search", "Profiler", "Network"};

    private final EditorRuntime runtime;
    private final StripTabs tabs = new StripTabs();
    private final StripTabs.Style tabStyle = new StripTabs.Style();
    private final SearchBox searchBox = new SearchBox();

    private int activeTab;

    private float diagScrollY;
    private float outputScrollY;
    private float profilerScrollY;
    private float networkScrollY;

    public BottomPanel(EditorRuntime runtime) {
        super("");
        this.runtime = runtime;
        searchBox.setPlaceholder("Search diagnostics and output…");
    }

    public void handleKey(UiContext uiContext, KeyEvent e) {
        searchBox.handleKey(uiContext, e, uiContext.clipboard());
    }

    public void handleTextInput(UiContext uiContext, TextInputEvent e) {
        searchBox.handleTextInput(uiContext, e);
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
        if (contentH > 8) {
            switch (activeTab) {
                case 0 -> renderDiagnostics(ui, r, theme, x, y, w, contentH, interactive);
                case 1 -> renderOutput(r, theme, x, y, w, contentH, interactive, ui.input());
                case 2 -> renderSearch(ui, r, theme, x, y, w, contentH, interactive, ctx.uiContext());
                case 3 -> renderProfiler(r, theme, x, y, w, contentH, interactive, ui.input());
                case 4 -> renderNetwork(r, theme, x, y, w, contentH, interactive, ui.input());
            }
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

        var input = interactive ? ctx.ui().input() : null;
        activeTab = tabs.render(r, ctx.uiContext(), input, theme, x, y, w, h, TAB_LABELS, activeTab, true, tabStyle);
    }


    private void renderDiagnostics(Ui ui, UiRenderer r, Theme theme, int x, int y, int w, int h, boolean interactive) {
        r.drawRect(x, y, w, h, Theme.toArgb(theme.panelBg));

        int toolbarH = Math.round(26.0f * runtime.editorUiScale());
        UiInput input = interactive ? ui.input() : null;
        renderSimpleToolbar(r, theme, x, y, w, toolbarH, interactive, input, "Diagnostics", () -> {
            EditorDiagnostics.clear();
            diagScrollY = 0.0f;
        });

        int listY = y + toolbarH + 1;
        int listH = Math.max(1, h - toolbarH - 1);
        List<EditorDiagnostics.Entry> entries = EditorDiagnostics.snapshot().entries();
        if (entries.isEmpty()) {
            r.drawText("No diagnostics - shader, script, asset, and scene errors will appear here.",
                    x + theme.design.space_md,
                    r.baselineForBox(listY + theme.design.space_sm, theme.design.widget_height_sm),
                    Theme.toArgb(theme.textMuted));
            return;
        }

        int rowH = Math.round(20.0f * runtime.editorUiScale());
        int contentH = entries.size() * rowH;
        int maxScroll = Math.max(0, contentH - listH);
        boolean hoveredList = input != null
                && input.mousePos().x >= x && input.mousePos().x < x + w
                && input.mousePos().y >= listY && input.mousePos().y < listY + listH;
        if (hoveredList && interactive) {
            diagScrollY = clamp(diagScrollY - input.consumeMouseScrollDelta() * 30.0f, 0.0f, maxScroll);
        } else {
            diagScrollY = clamp(diagScrollY, 0.0f, maxScroll);
        }

        r.pushClip(x, listY, w, listH);
        float rowY = listY - diagScrollY;
        // Render newest-first so the most recent error is at the top.
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (rowY + rowH < listY) { rowY += rowH; continue; }
            if (rowY > listY + listH) break;
            EditorDiagnostics.Entry e = entries.get(i);
            if (e == null) { rowY += rowH; continue; }
            int accent = severityColor(theme, e.severity());
            String prefix = switch (e.severity()) {
                case ERROR -> "[ERR] ";
                case WARN  -> "[WRN] ";
                case INFO  -> "[INF] ";
            };
            String source = e.source().isBlank() ? "" : e.source() + ": ";
            String line = prefix + source + e.message();
            r.drawText(line, x + theme.design.space_md, r.baselineForBox(Math.round(rowY), rowH), accent);
            r.drawText(e.formattedTime(), x + w - 58, r.baselineForBox(Math.round(rowY), rowH),
                    Theme.toArgb(theme.textMuted));
            rowY += rowH;
        }
        r.popClip();
    }


    private void renderOutput(UiRenderer r, Theme theme, int x, int y, int w, int h, boolean interactive, UiInput input) {
        r.drawRect(x, y, w, h, Theme.toArgb(theme.panelBg));

        int toolbarH = 26;
        renderSimpleToolbar(r, theme, x, y, w, toolbarH, interactive, input, "Output", () -> {
            ClientOutput.clear();
            outputScrollY = 0;
        });

        int listY = y + toolbarH + 1;
        int listH = Math.max(1, h - toolbarH - 1);
        ClientOutput.Snapshot snap = ClientOutput.snapshot();

        if (snap.entries().isEmpty()) {
            r.drawRect(x, listY, w, listH, Theme.mulAlpha(Theme.toArgb(theme.windowBg), 0.45f));
            r.drawText("No output - script print() calls will appear here.",
                    x + theme.design.space_md,
                    r.baselineForBox(listY + theme.design.space_sm, theme.design.widget_height_sm),
                    Theme.toArgb(theme.textMuted));
            return;
        }

        r.drawRect(x, listY, w, listH, Theme.mulAlpha(Theme.toArgb(theme.windowBg), 0.45f));

        int rowH = 22;
        boolean hovered = isHoveredRect(input, x, listY, w, listH);
        int contentH = snap.entries().size() * rowH;
        int maxScroll = Math.max(0, contentH - listH);
        if (hovered && interactive && input != null) {
            outputScrollY = clamp(outputScrollY - input.consumeMouseScrollDelta() * 30.0f, 0.0f, maxScroll);
        } else {
            outputScrollY = clamp(outputScrollY, 0.0f, maxScroll);
        }

        r.pushClip(x, listY, w, listH);
        float rowY = listY - outputScrollY;
        for (ClientOutput.Entry entry : snap.entries()) {
            if (rowY + rowH >= listY && rowY < listY + listH) {
                int rowBg = Theme.mulAlpha(Theme.toArgb(theme.widgetBg), ((int) rowY / Math.max(1, rowH)) % 2 == 0 ? 0.18f : 0.08f);
                r.drawRect(x, Math.round(rowY), w, rowH, rowBg);
                r.drawRect(x, Math.round(rowY) + rowH - 1, w, 1, Theme.toArgb(theme.headerLine));
                r.drawText(entry.source(), x + theme.design.space_md, r.baselineForBox(Math.round(rowY), rowH), Theme.toArgb(theme.textMuted));
                r.drawText(ellipsize(r, entry.message(), Math.max(40, w - 120)),
                        x + 90, r.baselineForBox(Math.round(rowY), rowH), Theme.toArgb(theme.text));
                r.drawText(entry.formattedTime(), x + w - 58, r.baselineForBox(Math.round(rowY), rowH), Theme.toArgb(theme.textMuted));
            }
            rowY += rowH;
            if (rowY > listY + listH) break;
        }
        r.popClip();
    }


    private void renderSearch(Ui ui, UiRenderer r, Theme theme, int x, int y, int w, int h,
                              boolean interactive, UiContext uiContext) {
        r.drawRect(x, y, w, h, Theme.toArgb(theme.panelBg));

        int fieldH = 26;
        int fieldPad = theme.design.space_sm;
        int fieldY = y + fieldPad;
        int line = Theme.toArgb(theme.headerLine);
        int bg = Theme.toArgb(theme.windowBg);
        r.drawRect(x, y, w, fieldH + fieldPad * 2, bg);
        r.drawRect(x, y + fieldH + fieldPad * 2 - 1, w, 1, line);

        UiInput input = interactive ? ui.input() : null;
        searchBox.setEnabled(interactive);
        searchBox.render(uiContext, input, r, theme, x + fieldPad, fieldY, w - fieldPad * 2, fieldH);

        String query = searchBox.field().text().trim().toLowerCase();
        int listY = y + fieldH + fieldPad * 2 + 1;
        int listH = Math.max(1, h - (listY - y));

        if (query.isEmpty()) {
            r.drawRect(x, listY, w, listH, Theme.mulAlpha(Theme.toArgb(theme.windowBg), 0.45f));
            r.drawText("Type to search diagnostics and output…",
                    x + theme.design.space_md,
                    r.baselineForBox(listY + theme.design.space_sm, theme.design.widget_height_sm),
                    Theme.toArgb(theme.textMuted));
            return;
        }

        List<SearchResult> results = buildSearchResults(query);

        r.drawRect(x, listY, w, listH, Theme.mulAlpha(Theme.toArgb(theme.windowBg), 0.45f));

        if (results.isEmpty()) {
            r.drawText("No results for \"" + query + "\"",
                    x + theme.design.space_md,
                    r.baselineForBox(listY + theme.design.space_sm, theme.design.widget_height_sm),
                    Theme.toArgb(theme.textMuted));
            return;
        }

        int rowH = 22;
        r.pushClip(x, listY, w, listH);
        float rowY = listY;
        for (SearchResult sr : results) {
            if (rowY + rowH >= listY && rowY < listY + listH) {
                int accent = sr.isOutput ? Theme.toArgb(theme.accent) : severityColor(theme, sr.severity);
                int rowBg = Theme.mulAlpha(Theme.toArgb(theme.widgetBg), ((int) rowY / Math.max(1, rowH)) % 2 == 0 ? 0.18f : 0.08f);
                r.drawRect(x, Math.round(rowY), w, rowH, rowBg);
                r.drawRect(x, Math.round(rowY), 2, rowH, accent);
                r.drawRect(x, Math.round(rowY) + rowH - 1, w, 1, Theme.toArgb(theme.headerLine));
                String tag = sr.isOutput ? "OUT" : switch (sr.severity) {
                    case ERROR -> "ERR";
                    case WARN -> "WRN";
                    case INFO -> "INF";
                };
                r.drawText(tag, x + theme.design.space_md, r.baselineForBox(Math.round(rowY), rowH), accent);
                r.drawText(ellipsize(r, sr.source, 96), x + 50, r.baselineForBox(Math.round(rowY), rowH), Theme.toArgb(theme.textMuted));
                r.drawText(ellipsize(r, sr.message, Math.max(40, w - 180)), x + 160, r.baselineForBox(Math.round(rowY), rowH), Theme.toArgb(theme.text));
                r.drawText(sr.time, x + w - 58, r.baselineForBox(Math.round(rowY), rowH), Theme.toArgb(theme.textMuted));
            }
            rowY += rowH;
            if (rowY > listY + listH) break;
        }
        r.popClip();
    }

    private List<SearchResult> buildSearchResults(String query) {
        List<SearchResult> out = new ArrayList<>();
        for (EditorDiagnostics.Entry e : EditorDiagnostics.snapshot().entries()) {
            if (e == null) continue;
            if (e.message().toLowerCase().contains(query) || e.source().toLowerCase().contains(query)) {
                out.add(new SearchResult(false, e.severity(), e.source(), e.message(), e.formattedTime()));
            }
        }
        for (ClientOutput.Entry e : ClientOutput.snapshot().entries()) {
            if (e.message().toLowerCase().contains(query) || e.source().toLowerCase().contains(query)) {
                out.add(new SearchResult(true, null, e.source(), e.message(), e.formattedTime()));
            }
        }
        return out;
    }

    private record SearchResult(boolean isOutput, EditorDiagnostics.Severity severity, String source, String message, String time) {}


    private void renderProfiler(UiRenderer r, Theme theme, int x, int y, int w, int h, boolean interactive, UiInput input) {
        r.drawRect(x, y, w, h, Theme.toArgb(theme.panelBg));

        int toolbarH = Math.round(26.0f * runtime.editorUiScale());
        renderSimpleToolbar(r, theme, x, y, w, toolbarH, interactive, input, "Profiler", null);

        int contentY = y + toolbarH + 1;
        int contentH = Math.max(1, h - toolbarH - 1);
        int text = Theme.toArgb(theme.text);
        int muted = Theme.toArgb(theme.textMuted);
        int line = Theme.toArgb(theme.headerLine);

        ClientFrameProfiler.Snapshot snap = ClientFrameProfiler.snapshot();
        float[] ft = snap.frameTimes();
        List<ClientFrameProfiler.ScopeStat> scopes = snap.scopes();

        int summaryH = 20;
        if (ft.length == 0) {
            r.drawText("Waiting for frame data…", x + theme.design.space_md,
                    r.baselineForBox(contentY + theme.design.space_sm, summaryH), muted);
            return;
        }

        // One-line summary: fps · avg · p95 · max · frame count
        String summary = String.format("%.0f fps  ·  avg %.2f ms  ·  p95 %.2f ms  ·  max %.2f ms  ·  %d frames",
                snap.fps(), snap.avg(), snap.p95(), snap.max(), ft.length);
        r.drawText(summary, x + theme.design.space_md, r.baselineForBox(contentY, summaryH), text);

        // Tiny frame-time histogram above the scope list.
        int graphY = contentY + summaryH + 2;
        int graphH = Math.min(36, contentH / 4);
        r.drawRect(x, graphY, w, graphH, Theme.mulAlpha(Theme.toArgb(theme.windowBg), 0.45f));
        float target60 = 1000.0f / 60.0f;
        float displayMax = Math.max(snap.max() * 1.1f, target60 * 2.0f);
        float barWf = (float) w / ft.length;
        for (int i = 0; i < ft.length; i++) {
            float ms = ft[i];
            int bx = x + Math.round(i * barWf);
            int bxn = x + Math.round((i + 1) * barWf);
            int bw = Math.max(1, bxn - bx);
            int bh = Math.max(1, Math.round((ms / displayMax) * graphH));
            int color = ms < target60 ? 0xFF4f8ef7 : ms < target60 * 2 ? 0xFFE3B341 : 0xFFE16A5A;
            r.drawRect(bx, graphY + graphH - bh, bw - (bw > 2 ? 1 : 0), bh, Theme.mulAlpha(color, 0.6f));
        }

        // Scope list below.
        int listY = graphY + graphH + 4;
        int listH = Math.max(1, contentH - summaryH - graphH - 6);
        if (scopes.isEmpty()) {
            r.drawText("No scoped timings captured yet.", x + theme.design.space_md,
                    r.baselineForBox(listY + theme.design.space_sm, 16), muted);
            return;
        }

        int rowH = 18;
        int maxScroll = Math.max(0, scopes.size() * rowH - listH);
        boolean hoveredList = input != null
                && input.mousePos().x >= x && input.mousePos().x < x + w
                && input.mousePos().y >= listY && input.mousePos().y < listY + listH;
        if (hoveredList && interactive) {
            profilerScrollY = clamp(profilerScrollY - input.consumeMouseScrollDelta() * 30.0f, 0.0f, maxScroll);
        } else {
            profilerScrollY = clamp(profilerScrollY, 0.0f, maxScroll);
        }

        r.pushClip(x, listY, w, listH);
        float rowY = listY - profilerScrollY;
        for (ClientFrameProfiler.ScopeStat scope : scopes) {
            if (rowY + rowH < listY) { rowY += rowH; continue; }
            if (rowY > listY + listH) break;
            int rowYi = Math.round(rowY);
            String row = String.format("%-44s  last %6s  avg %6s  p95 %6s  max %6s",
                    truncate(scope.name(), 44),
                    formatMs(scope.lastMs()),
                    formatMs(scope.avgMs()),
                    formatMs(scope.p95Ms()),
                    formatMs(scope.maxMs()));
            r.drawText(row, x + theme.design.space_md, r.baselineForBox(rowYi, rowH), text);
            rowY += rowH;
        }
        r.popClip();
    }

    private static String truncate(String s, int len) {
        if (s == null) return "";
        return s.length() <= len ? s : s.substring(0, len - 1) + "…";
    }

    private void renderNetwork(UiRenderer r, Theme theme, int x, int y, int w, int h, boolean interactive, UiInput input) {
        r.drawRect(x, y, w, h, Theme.toArgb(theme.panelBg));

        int toolbarH = 26;
        int bg = Theme.toArgb(theme.windowBg);
        int line = Theme.toArgb(theme.headerLine);
        r.drawRect(x, y, w, toolbarH, bg);
        r.drawRect(x, y + toolbarH - 1, w, 1, line);

        ClientNetworkLog.Snapshot snap = ClientNetworkLog.snapshot();

        int btnH = Math.max(16, toolbarH - 6);
        int btnY = y + (toolbarH - btnH) / 2;
        String clearLabel = "Clear";
        int clearW = Math.round(r.measureText(clearLabel) + 16.0f);
        int clearX = x + w - theme.design.space_sm - clearW;
        boolean clearHovered = isHovered(input, clearX, btnY, clearW, btnH);
        if (clearHovered) r.drawRoundedRect(clearX, btnY, clearW, btnH, theme.design.radius_sm, Theme.toArgb(theme.widgetHover), 0, 0);
        r.drawText(clearLabel, clearX + 8, r.baselineForBox(btnY, btnH), Theme.toArgb(theme.textMuted));
        if (interactive && clearHovered && input != null && input.mousePressed()) {
            ClientNetworkLog.clear();
            networkScrollY = 0;
        }

        String sent = "↑ " + humanBytes(snap.totalSentBytes());
        String recv = "↓ " + humanBytes(snap.totalRecvBytes());
        r.drawText(sent, x + theme.design.space_md, r.baselineForBox(y, toolbarH), 0xFF4f8ef7);
        r.drawText(recv, x + theme.design.space_md + 100, r.baselineForBox(y, toolbarH), 0xFF5CB86C);

        int hdrY = y + toolbarH;
        int hdrH = 20;
        r.drawRect(x, hdrY, w, hdrH, Theme.mulAlpha(Theme.toArgb(theme.widgetBg), 0.40f));
        r.drawRect(x, hdrY + hdrH, w, 1, line);
        int muted = Theme.toArgb(theme.textMuted);
        r.drawText("Dir",     x + theme.design.space_md, r.baselineForBox(hdrY, hdrH), muted);
        r.drawText("Lane",    x + 54,    r.baselineForBox(hdrY, hdrH), muted);
        r.drawText("Size",    x + 160,   r.baselineForBox(hdrY, hdrH), muted);
        r.drawText("Time",    x + w - 60, r.baselineForBox(hdrY, hdrH), muted);

        int listY = hdrY + hdrH + 1;
        int listH = Math.max(1, h - (listY - y));

        if (snap.entries().isEmpty()) {
            r.drawRect(x, listY, w, listH, Theme.mulAlpha(Theme.toArgb(theme.windowBg), 0.45f));
            r.drawText("No packets - network traffic will appear here during play mode.",
                    x + theme.design.space_md,
                    r.baselineForBox(listY + theme.design.space_sm, theme.design.widget_height_sm),
                    Theme.toArgb(theme.textMuted));
            return;
        }

        r.drawRect(x, listY, w, listH, Theme.mulAlpha(Theme.toArgb(theme.windowBg), 0.45f));

        int rowH = 22;
        boolean hoveredList = isHoveredRect(input, x, listY, w, listH);
        List<ClientNetworkLog.Entry> entries = snap.entries();
        int contentH = entries.size() * rowH;
        int maxScroll = Math.max(0, contentH - listH);
        if (hoveredList && interactive && input != null) {
            networkScrollY = clamp(networkScrollY - input.consumeMouseScrollDelta() * 30.0f, 0.0f, maxScroll);
        } else {
            networkScrollY = clamp(networkScrollY, 0.0f, maxScroll);
        }

        r.pushClip(x, listY, w, listH);
        float rowY = listY - networkScrollY;
        for (ClientNetworkLog.Entry entry : entries) {
            if (rowY + rowH >= listY && rowY < listY + listH) {
                boolean isSend = entry.direction() == ClientNetworkLog.Direction.SEND;
                int rowBg = Theme.mulAlpha(Theme.toArgb(theme.widgetBg), ((int) rowY / Math.max(1, rowH)) % 2 == 0 ? 0.18f : 0.08f);
                int dirColor = isSend ? 0xFF4f8ef7 : 0xFF5CB86C;
                r.drawRect(x, Math.round(rowY), w, rowH, rowBg);
                r.drawRect(x, Math.round(rowY), 2, rowH, dirColor);
                r.drawRect(x, Math.round(rowY) + rowH - 1, w, 1, line);
                r.drawText(isSend ? "SND" : "RCV", x + theme.design.space_md, r.baselineForBox(Math.round(rowY), rowH), dirColor);
                r.drawText(ellipsize(r, entry.lane(), 92), x + 54, r.baselineForBox(Math.round(rowY), rowH), Theme.toArgb(theme.text));
                r.drawText(entry.sizeLabel(), x + 160, r.baselineForBox(Math.round(rowY), rowH), Theme.toArgb(theme.textMuted));
                r.drawText(entry.formattedTime(), x + w - 58, r.baselineForBox(Math.round(rowY), rowH), Theme.toArgb(theme.textMuted));
            }
            rowY += rowH;
            if (rowY > listY + listH) break;
        }
        r.popClip();
    }


    private void renderSimpleToolbar(UiRenderer r, Theme theme, int x, int y, int w, int h,
                                     boolean interactive, UiInput input, String title, Runnable onClear) {
        r.drawRect(x, y, w, h, Theme.toArgb(theme.windowBg));
        r.drawRect(x, y + h - 1, w, 1, Theme.toArgb(theme.headerLine));
        r.drawText(title, x + theme.design.space_md, r.baselineForBox(y, h), Theme.toArgb(theme.textMuted));

        String clearLabel = "Clear";
        int clearW = Math.round(r.measureText(clearLabel) + 16.0f);
        int btnH = Math.max(16, h - 6);
        int btnY = y + (h - btnH) / 2;
        int clearX = x + w - theme.design.space_sm - clearW;
        boolean hovered = isHovered(input, clearX, btnY, clearW, btnH);
        if (hovered) r.drawRoundedRect(clearX, btnY, clearW, btnH, theme.design.radius_sm, Theme.toArgb(theme.widgetHover), 0, 0);
        r.drawText(clearLabel, clearX + 8, r.baselineForBox(btnY, btnH), Theme.toArgb(theme.textMuted));
        if (interactive && hovered && input != null && input.mousePressed()) {
            onClear.run();
        }
    }

    private static String formatMs(float value) {
        return String.format("%.2f", value);
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
                && input.mousePos().x >= x && input.mousePos().x < x + w
                && input.mousePos().y >= y && input.mousePos().y < y + h;
    }

    private static boolean isHoveredRect(UiInput input, int x, int y, int w, int h) {
        return isHovered(input, x, y, w, h);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String ellipsize(UiRenderer r, String text, float maxWidth) {
        if (text == null || text.isEmpty()) return "";
        if (r.measureText(text) <= maxWidth) return text;
        String ellipsis = "...";
        int end = text.length();
        while (end > 0) {
            String candidate = text.substring(0, end) + ellipsis;
            if (r.measureText(candidate) <= maxWidth) return candidate;
            end--;
        }
        return ellipsis;
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

}