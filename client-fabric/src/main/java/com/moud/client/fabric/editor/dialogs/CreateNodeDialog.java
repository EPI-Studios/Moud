package com.moud.client.fabric.editor.dialogs;

import com.miry.ui.event.TextInputEvent;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.editor.theme.EditorTheme;

import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.event.KeyEvent;
import com.miry.ui.input.UiInput;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.Modal;
import com.miry.ui.widgets.TextField;
import com.moud.core.NodeTypeDef;
import com.moud.net.protocol.SceneOp;
import com.moud.net.session.Session;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;


public final class CreateNodeDialog {
    private final EditorRuntime runtime;
    private final Modal modal;
    private final TextField searchField = new TextField();
    private final List<String> filteredTypes = new ArrayList<>();
    private long parentNodeId;
    private String parentName = "";
    private boolean justOpened;

    public CreateNodeDialog(EditorRuntime runtime) {
        this.runtime = runtime;

        this.modal = new Modal(Modal.Type.INPUT, "Create New Node", "");
        this.modal.setSize(600, 500);
        this.modal.setContentRenderer(this::renderContent);

        this.modal.addButton("Cancel", this::close);
        this.modal.addButton("Create", this::createFirstMatch);
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
        modal.open();
    }

    public void close() {
        modal.close();
    }

    public boolean isOpen() {
        return modal.isOpen();
    }

    public boolean handleKey(UiContext ctx, KeyEvent event) {
        if (!modal.isOpen() || event == null) {
            return false;
        }

        if (modal.handleKey(event)) {
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
        if (!modal.isOpen()) {
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

        modal.render(r, theme, screenW, screenH, mx, my, bg, overlay, text, btnBg, btnHover, outline);
        if (ui.input().mousePressed()) {
            modal.handleClick(screenW, screenH, mx, my);
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
        if (modal.isOpen()) {
            searchField.handleTextInput(new TextInputEvent(codepoint));
            updateFilter();
        }
    }

    public void handleInput(UiContext ctx, UiInput input, Theme theme, int contentX, int contentY, int contentW, int contentH) {
        if (!modal.isOpen()) {
            return;
        }

        EditorState state = runtime.state();
        if (state == null) {
            return;
        }

        int cursorY = contentY + 22;
        int searchH = theme.design.widget_height_sm;

        searchField.render(null, ctx, input, theme, contentX, cursorY, contentW, searchH, true);
        cursorY += searchH + theme.design.space_sm;

        int listH = contentH - (cursorY - contentY) - theme.design.space_sm;
        float mx = input.mousePos().x;
        float my = input.mousePos().y;

        if (input.mousePressed() && mx >= contentX && mx < contentX + contentW && my >= cursorY && my < cursorY + listH) {
            int itemH = 20;
            int idx = (int) ((my - cursorY - theme.design.space_xs) / itemH);
            if (idx >= 0 && idx < filteredTypes.size()) {
                String typeId = filteredTypes.get(idx);
                createNode(state, typeId);
                close();
            }
        }
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
}
