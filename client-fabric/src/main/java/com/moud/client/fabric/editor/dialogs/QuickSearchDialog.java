package com.moud.client.fabric.editor.dialogs;

import com.miry.platform.InputConstants;
import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.event.KeyEvent;
import com.miry.ui.event.TextInputEvent;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Icon;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.TextField;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.render.MoudIcons;
import com.moud.net.protocol.AssetManifestResponse;
import com.moud.core.assets.AssetType;
import java.util.ArrayList;
import java.util.Locale;

public final class QuickSearchDialog {
    private static final int MAX_RESULTS = 12;
    private static final int DIALOG_W = 560;
    private static final int ROW_H = 28;

    private final EditorRuntime runtime;
    private final TextField field = new TextField();

    private boolean open;
    private boolean focusRequested;
    private final ArrayList<AssetManifestResponse.Entry> results = new ArrayList<>();

    public QuickSearchDialog(EditorRuntime runtime) {
        this.runtime = runtime;
    }

    public void open() {
        open = true;
        focusRequested = true;
        field.setText("");
        results.clear();
    }

    public void close() {
        open = false;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean handleKey(UiContext ctx, KeyEvent event) {
        if (!open || event == null) return false;
        if (event.isPress() && event.key() == InputConstants.KEY_ESCAPE) {
            close();
            return true;
        }
        if (event.isPressOrRepeat() && event.key() == InputConstants.KEY_ENTER) {
            if (!results.isEmpty()) {
                openEntry(results.get(0));
            }
            close();
            return true;
        }
        field.handleKey(event, ctx != null ? ctx.clipboard() : null);
        return true;
    }

    public void handleTextInput(int codepoint) {
        if (!open) return;
        field.handleTextInput(new TextInputEvent(codepoint));
    }

    public void render(UiRenderer r, UiContext ctx, Ui ui, Theme theme, int screenW, int screenH) {
        if (!open) return;

        if (focusRequested && ctx != null) {
            focusRequested = false;
            field.focus(ctx);
        }

        String query = field.text() == null ? "" : field.text().trim().toLowerCase(Locale.ROOT);
        rebuildResults(query);

        int resultRows = results.size();
        int dialogH = 48 + resultRows * ROW_H + (resultRows > 0 ? 8 : 0);
        int dialogX = (screenW - DIALOG_W) / 2;
        int dialogY = screenH / 5;

        int bg = Theme.toArgb(theme.panelBg);
        int outline = Theme.toArgb(theme.widgetOutline);
        int text = Theme.toArgb(theme.text);
        int muted = Theme.toArgb(theme.textMuted);
        int accent = Theme.toArgb(theme.accent);

        r.drawRoundedRect(dialogX, dialogY, DIALOG_W, dialogH, theme.design.radius_md, bg, theme.design.border_thin, outline);

        int pad = theme.design.space_md;
        int fieldH = 30;
        int fieldY = dialogY + (48 - fieldH) / 2;

        float iconSize = Math.min(theme.design.icon_sm, fieldH - 8);
        MoudIcons.drawOrFallback(r, theme, Icon.SEARCH, dialogX + pad, dialogY + (48 - iconSize) * 0.5f, iconSize, muted);
        field.render(r, ctx, ui.input(), theme, dialogX + pad + (int) iconSize + 6, fieldY, DIALOG_W - pad * 2 - (int) iconSize - 6, fieldH, true);

        float mx = ui.mouse().x;
        float my = ui.mouse().y;
        boolean pressed = ui.input() != null && ui.input().mousePressed();

        int rowY = dialogY + 48;
        for (int i = 0; i < results.size(); i++) {
            AssetManifestResponse.Entry entry = results.get(i);
            String path = entry.path() != null ? entry.path().value() : "";
            boolean hovered = mx >= dialogX && my >= rowY && mx < dialogX + DIALOG_W && my < rowY + ROW_H;
            if (hovered) {
                r.drawRect(dialogX, rowY, DIALOG_W, ROW_H, Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.55f));
            }
            AssetType assetType = entry.meta() != null ? entry.meta().type() : null;
            Icon fileIcon = assetType == AssetType.IMAGE ? Icon.IMAGE : assetType == AssetType.TEXT ? Icon.TEXT : Icon.FILE;
            float fi = Math.min(14f, ROW_H - 6f);
            MoudIcons.drawOrFallback(r, theme, fileIcon, dialogX + pad, rowY + (ROW_H - fi) * 0.5f, fi, muted);
            String filename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            int labelX = dialogX + pad + (int) Math.ceil(fi) + 6;
            r.drawText(filename, labelX, r.baselineForBox(rowY, ROW_H), text);
            int pathMaxW = Math.max(0, dialogX + DIALOG_W - pad - labelX - 160);
            if (pathMaxW > 24) {
                r.drawText(path, dialogX + DIALOG_W - pad - Math.min(200, r.measureText(path)), r.baselineForBox(rowY, ROW_H), muted);
            }
            if (hovered && pressed) {
                openEntry(entry);
                close();
                return;
            }
            rowY += ROW_H;
        }

        if (pressed && (mx < dialogX || my < dialogY || mx >= dialogX + DIALOG_W || my >= dialogY + dialogH)) {
            close();
        }
    }

    private void rebuildResults(String query) {
        results.clear();
        EditorState state = runtime.state();
        if (state == null) return;
        for (AssetManifestResponse.Entry entry : state.manifestEntries) {
            if (entry == null || entry.path() == null) continue;
            String path = entry.path().value();
            if (path == null) continue;
            if (!query.isEmpty() && !path.toLowerCase(Locale.ROOT).contains(query)) continue;
            results.add(entry);
            if (results.size() >= MAX_RESULTS) break;
        }
    }

    private void openEntry(AssetManifestResponse.Entry entry) {
        if (entry == null || entry.path() == null) return;
        String path = entry.path().value();
        if (path == null || path.isBlank()) return;
        AssetType type = entry.meta() != null ? entry.meta().type() : null;
        if (type == AssetType.TEXT
                && path.startsWith("res://scripts/")
                && (path.endsWith(".js") || path.endsWith(".mjs") || path.endsWith(".cjs") || path.endsWith(".luau"))) {
            runtime.openScriptEditor(0L, path);
        } else if (type == AssetType.TEXT) {
            runtime.openTextAssetEditor(path, entry.meta() == null ? null : entry.meta().hash());
        }
    }
}
