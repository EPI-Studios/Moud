package com.moud.client.fabric.editor.dialogs;

import com.miry.ui.event.TextInputEvent;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;

import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.event.KeyEvent;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.TextField;
import com.miry.platform.InputConstants;
import com.moud.core.NodeTypeDef;
import com.moud.net.protocol.SceneOp;
import com.moud.net.session.Session;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class CreateNodeDialog {
    private static final int DIALOG_W = 600;
    private static final int DIALOG_H = 500;

    private final EditorRuntime runtime;
    private final TextField searchField = new TextField();
    private final List<String> filteredTypes = new ArrayList<>();
    private long parentNodeId;
    private String parentName = "";
    private boolean justOpened;
    private boolean open;

    public CreateNodeDialog(EditorRuntime runtime) {
        this.runtime = runtime;
    }

    public void open(long parentId) {
        EditorState state = runtime.state();
        if (state == null) {
            return;
        }

        this.parentNodeId = parentId;
        var parent = state.scene.getNode(parentId);
        this.parentName = parent != null ? parent.name() : "";

        searchField.setText("");
        updateFilter();
        justOpened = true;
        open = true;
    }

    public void close() {
        open = false;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean handleKey(UiContext ctx, KeyEvent event) {
        if (!open || event == null) {
            return false;
        }

        if (event.isPress() && event.key() == InputConstants.KEY_ESCAPE) {
            close();
            return true;
        }
        if (event.isPress() && event.key() == InputConstants.KEY_ENTER) {
            createFirstMatch();
            return true;
        }

        if (ctx != null && searchField.isFocused(ctx)) {
            searchField.handleKey(event, ctx.clipboard());
            updateFilter();
            return true;
        }
        return false;
    }

    public void render(UiRenderer r, UiContext ctx, Ui ui, Theme theme, int screenW, int screenH) {
        if (!open) {
            return;
        }

        if (justOpened && ctx != null) {
            justOpened = false;
            searchField.focus(ctx);
        }

        int mx = (int) ui.mouse().x;
        int my = (int) ui.mouse().y;

        int bg = Theme.toArgb(theme.panelBg);
        int overlay = 0x80000000;
        int text = Theme.toArgb(theme.text);
        int btnBg = Theme.toArgb(theme.widgetBg);
        int btnHover = Theme.toArgb(theme.widgetHover);
        int outline = Theme.toArgb(theme.widgetOutline);

        r.drawRect(0, 0, screenW, screenH, overlay);

        int dialogW = Math.min(DIALOG_W, Math.max(260, screenW - theme.design.space_lg * 2));
        int dialogH = Math.min(DIALOG_H, Math.max(200, screenH - theme.design.space_lg * 2));
        int dialogX = (screenW - dialogW) / 2;
        int dialogY = (screenH - dialogH) / 2;

        r.drawRoundedRect(dialogX, dialogY, dialogW, dialogH, theme.design.radius_md, bg, theme.design.border_thin, outline);

        int pad = theme.design.space_lg;
        int headerH = 44;
        r.drawText("Create New Node", dialogX + pad, r.baselineForBox(dialogY, headerH), text);

        int buttonW = 120;
        int buttonH = theme.design.widget_height_md + theme.design.border_thin * 2;
        int buttonY = dialogY + dialogH - pad - buttonH;
        int createX = dialogX + dialogW - pad - buttonW;
        int cancelX = createX - theme.design.space_sm - buttonW;

        boolean pressed = ui.input().mousePressed();
        if (pressed) {
            // close on backdrop click
            if (mx < dialogX || my < dialogY || mx >= dialogX + dialogW || my >= dialogY + dialogH) {
                close();
                return;
            }
        }

        drawButton(r, theme, "Cancel", cancelX, buttonY, buttonW, buttonH, mx, my, btnBg, btnHover, outline, text);
        drawButton(r, theme, "Create", createX, buttonY, buttonW, buttonH, mx, my, btnBg, btnHover, outline, text);

        int contentX = dialogX + pad;
        int contentY = dialogY + headerH;
        int contentW = dialogW - pad * 2;
        int contentH = buttonY - contentY - theme.design.space_md;

        renderContent(r, theme, contentX, contentY, contentW, contentH);

        if (pressed) {
            if (hit(mx, my, cancelX, buttonY, buttonW, buttonH)) {
                close();
                return;
            }
            if (hit(mx, my, createX, buttonY, buttonW, buttonH)) {
                createFirstMatch();
                return;
            }

            handleContentClick(ctx, ui, theme, contentX, contentY, contentW, contentH);
        }
    }

    private void renderContent(UiRenderer r, Theme theme, int x, int y, int width, int height) {
        EditorState state = runtime.state();
        if (state == null) {
            return;
        }

        String parentInfo = "Parent: " + (parentName.isBlank() ? "#" + parentNodeId : parentName);
        r.drawText(parentInfo, x, r.baselineForBox(y, 18), Theme.toArgb(theme.textMuted));
        int cursorY = y + 22;

        int searchH = theme.design.widget_height_sm;
        int searchBg = Theme.toArgb(theme.widgetBg);
        int searchOutline = Theme.toArgb(theme.widgetOutline);
        r.drawRoundedRect(x, cursorY, width, searchH, theme.design.radius_sm, searchBg, theme.design.border_thin, searchOutline);

        String searchText = searchField.text();
        if (searchText != null && !searchText.isEmpty()) {
            r.drawText(searchText, x + theme.design.space_sm, r.baselineForBox(cursorY, searchH), Theme.toArgb(theme.text));
        } else {
            r.drawText("Search...", x + theme.design.space_sm, r.baselineForBox(cursorY, searchH), Theme.toArgb(theme.textMuted));
        }
        cursorY += searchH + theme.design.space_sm;
        int listH = height - (cursorY - y) - theme.design.space_sm;
        int listBg = Theme.darkenArgb(Theme.toArgb(theme.widgetBg), 0.02f);
        r.drawRoundedRect(x, cursorY, width, listH, theme.design.radius_sm, listBg, theme.design.border_thin, searchOutline);

        if (filteredTypes.isEmpty()) {
            r.drawText("No matches", x + theme.design.space_sm, r.baselineForBox(cursorY + theme.design.space_sm, 18), Theme.toArgb(theme.textMuted));
            return;
        }

        int itemH = 20;
        int itemY = cursorY + theme.design.space_xs;
        int maxVisible = (listH - theme.design.space_xs * 2) / itemH;

        for (int i = 0; i < Math.min(filteredTypes.size(), maxVisible); i++) {
            String typeId = filteredTypes.get(i);
            NodeTypeDef def = state.typesById.get(typeId);
            String label = def == null ? typeId : def.uiLabel();

            r.drawText(label, x + theme.design.space_sm, r.baselineForBox(itemY, itemH), Theme.toArgb(theme.text));
            itemY += itemH;
        }
    }

    public void handleTextInput(int codepoint) {
        if (!open) {
            return;
        }
        searchField.handleTextInput(new TextInputEvent(codepoint));
        updateFilter();
    }

    private void updateFilter() {
        EditorState state = runtime.state();
        if (state == null) {
            return;
        }

        String query = searchField.text() == null ? "" : searchField.text().toLowerCase(Locale.ROOT);
        filteredTypes.clear();

        for (String typeId : state.typeIds) {
            if (typeId == null || typeId.isBlank() || "Root".equals(typeId)) {
                continue;
            }
            NodeTypeDef def = state.typesById.get(typeId);
            String label = def == null ? typeId : def.uiLabel();
            if (query.isEmpty() || typeId.toLowerCase(Locale.ROOT).contains(query) || label.toLowerCase(Locale.ROOT).contains(query)) {
                filteredTypes.add(typeId);
            }
        }

        filteredTypes.sort(Comparator.naturalOrder());
    }

    private void createNode(EditorState state, String typeId) {
        Session session = runtime.session();
        if (session == null) {
            return;
        }
        String name = typeId + "_" + (int) (System.nanoTime() % 10_000);
        runtime.net().sendOps(session, state, List.of(new SceneOp.CreateNode(parentNodeId, name, typeId)));
    }

    private void createFirstMatch() {
        EditorState state = runtime.state();
        if (state == null || filteredTypes.isEmpty()) {
            return;
        }
        createNode(state, filteredTypes.get(0));
        close();
    }

    private void handleContentClick(UiContext ctx, Ui ui, Theme theme, int x, int y, int width, int height) {
        EditorState state = runtime.state();
        if (state == null || ui == null) {
            return;
        }

        int cursorY = y + 22;
        int searchH = theme.design.widget_height_sm;
        if (ctx != null && hit((int) ui.mouse().x, (int) ui.mouse().y, x, cursorY, width, searchH)) {
            searchField.focus(ctx);
            return;
        }
        cursorY += searchH + theme.design.space_sm;

        int listH = height - (cursorY - y) - theme.design.space_sm;
        int mx = (int) ui.mouse().x;
        int my = (int) ui.mouse().y;
        if (!hit(mx, my, x, cursorY, width, listH)) {
            return;
        }

        int itemH = 20;
        int idx = (int) ((my - cursorY - theme.design.space_xs) / itemH);
        if (idx >= 0 && idx < filteredTypes.size()) {
            String typeId = filteredTypes.get(idx);
            createNode(state, typeId);
            close();
        }
    }

    private static void drawButton(UiRenderer r,
                                   Theme theme,
                                   String label,
                                   int x,
                                   int y,
                                   int w,
                                   int h,
                                   int mx,
                                   int my,
                                   int bg,
                                   int hoverBg,
                                   int outline,
                                   int text) {
        boolean hovered = hit(mx, my, x, y, w, h);
        int col = hovered ? hoverBg : bg;
        r.drawRoundedRect(x, y, w, h, theme.design.radius_sm, col, theme.design.border_thin, outline);
        r.drawText(label, x + theme.design.space_sm, r.baselineForBox(y, h), text);
    }

    private static boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
