package com.moud.client.fabric.editor.dialogs;


import com.miry.platform.InputConstants;
import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.event.KeyEvent;
import com.miry.ui.event.TextInputEvent;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Icon;
import com.moud.client.fabric.render.MoudIcons;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.TextField;
import com.moud.client.fabric.editor.state.EditorHistory;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.core.NodeTypeDef;
import com.moud.core.scene.Node;
import com.moud.net.protocol.SceneOp;
import com.moud.net.session.Session;
import com.moud.net.protocol.SceneSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public final class CreateNodeDialog {
    private static final int DIALOG_W = 600;
    private static final int DIALOG_H = 500;
    private static final int MAX_RECENT = 5;
    private static final List<String> recentTypes = new ArrayList<>();

    private final EditorRuntime runtime;
    private final TextField searchField = new TextField();
    private final List<ListItem> filteredItems = new ArrayList<>();
    private long parentNodeId;
    private String parentName = "";
    private boolean justOpened;
    private boolean open;
    private int typeListScrollY;
    private int highlightedIndex = -1;
    private Consumer<String> onTypeSelected;

    private record ListItem(boolean header, String label, String typeId, String category) {
        static ListItem header(String label) {
            return new ListItem(true, label == null ? "" : label, null, null);
        }

        static ListItem type(String label, String typeId, String category) {
            return new ListItem(false, label == null ? "" : label, typeId, category == null ? "" : category);
        }
    }

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
        typeListScrollY = 0;
        highlightedIndex = firstSelectableIndex();
        justOpened = true;
        open = true;
        onTypeSelected = null;
    }

    public void setOnTypeSelected(Consumer<String> callback) {
        onTypeSelected = callback;
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
        if (event.isPressOrRepeat() && event.key() == InputConstants.KEY_UP) {
            moveHighlight(-1);
            return true;
        }
        if (event.isPressOrRepeat() && event.key() == InputConstants.KEY_DOWN) {
            moveHighlight(1);
            return true;
        }
        if (event.isPress() && event.key() == InputConstants.KEY_ENTER) {
            createHighlightedOrFirst();
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

        int buttonW = 140;
        int buttonH = theme.design.widget_height_md + theme.design.border_thin * 2;
        int buttonY = dialogY + dialogH - pad - buttonH;
        int createX = dialogX + dialogW - pad - buttonW;
        int cancelX = createX - theme.design.space_sm - buttonW;

        boolean pressed = ui.input() != null && ui.input().mousePressed();
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

        renderContent(r, ctx, ui, theme, contentX, contentY, contentW, contentH);

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

    private void renderContent(UiRenderer r, UiContext uiContext, Ui ui, Theme theme, int x, int y, int width, int height) {
        EditorState state = runtime.state();
        if (state == null) {
            return;
        }

        var input = ui != null ? ui.input() : null;
        boolean canInteract = input != null;
        float mx = canInteract ? input.mousePos().x : -1;
        float my = canInteract ? input.mousePos().y : -1;

        String parentInfo = "Parent: " + (parentName.isBlank() ? "#" + parentNodeId : parentName);
        r.drawText(parentInfo, x, r.baselineForBox(y, 18), Theme.toArgb(theme.textMuted));
        int cursorY = y + 22;

        int searchH = theme.design.widget_height_md;
        searchField.render(r, uiContext, input, theme, x, cursorY, width, searchH, true);
        if ((searchField.text() == null || searchField.text().isEmpty()) && (uiContext == null || !searchField.isFocused(uiContext))) {
            int hint = Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.70f);
            float iconSize = Math.min(theme.design.icon_sm, searchH - 6);
            if (theme.icons != null) {
                MoudIcons.drawOrFallback(r, theme, Icon.SEARCH, x + 6, cursorY + (searchH - iconSize) * 0.5f, iconSize, hint);
            }
            int hintX = x + 6 + (int) Math.ceil(iconSize) + 6;
            r.drawText("Search...", hintX, r.baselineForBox(cursorY, searchH), hint);
        }
        cursorY += searchH + theme.design.space_sm;
        int listH = Math.max(0, height - (cursorY - y) - theme.design.space_sm);
        int listBg = Theme.darkenArgb(Theme.toArgb(theme.widgetBg), 0.02f);
        int listOutline = Theme.toArgb(theme.widgetOutline);
        r.drawRoundedRect(x, cursorY, width, listH, theme.design.radius_sm, listBg, theme.design.border_thin, listOutline);

        if (filteredItems.isEmpty()) {
            r.drawText("No matches", x + theme.design.space_sm, r.baselineForBox(cursorY + theme.design.space_sm, 18), Theme.toArgb(theme.textMuted));
            return;
        }

        int itemH = Math.max(18, theme.tokens.itemHeight);
        int listX = x;
        int listY = cursorY;
        int listW = width;
        int contentHeight = filteredItems.size() * itemH + theme.design.space_xs * 2;

        Ui.ScrollArea area = ui.beginScrollArea(r, "createNodeTypesScroll", listX, listY, listW, listH, contentHeight);
        int scrollY = (int) area.scrollY();
        typeListScrollY = scrollY;

        int first = Math.max(0, scrollY / Math.max(1, itemH));
        int visible = Math.max(1, (listH / Math.max(1, itemH)) + 2);
        int last = Math.min(filteredItems.size(), first + visible);

        int itemY = listY + theme.design.space_xs - scrollY;
        for (int i = first; i < last; i++) {
            int rowY = itemY + i * itemH;
            boolean hovered = canInteract && mx >= listX && my >= rowY && mx < listX + listW && my < rowY + itemH;
            boolean highlighted = i == highlightedIndex;

            if (hovered || highlighted) {
                int fill = Theme.mulAlpha(Theme.toArgb(hovered ? theme.widgetHover : theme.widgetActive), 0.30f);
                r.drawRect(listX + 1, rowY, Math.max(0, listW - 2), itemH, fill);
            }

            ListItem item = filteredItems.get(i);
            if (item.header) {
                int col = Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.90f);
                String label = item.label == null ? "" : item.label;
                r.drawText(label, listX + theme.design.space_sm, r.baselineForBox(rowY, itemH), col);
                int lineY = rowY + itemH - 1;
                r.drawRect(listX + theme.design.space_sm, lineY, Math.max(0, listW - theme.design.space_sm * 2), 1, Theme.mulAlpha(col, 0.45f));
                continue;
            }

            String label = item.label == null ? "" : item.label;
            int iconBox = itemH;
            float iconSize = Math.min(theme.design.icon_sm, iconBox - 6);
            float iconX = listX + theme.design.space_sm;
            float iconY = rowY + (iconBox - iconSize) * 0.5f;
            drawTypeIcon(r, theme, item.typeId, iconX, iconY, iconSize, Theme.toArgb(theme.textMuted));

            int textX = listX + theme.design.space_sm + iconBox;
            r.drawText(label, textX, r.baselineForBox(rowY, itemH), Theme.toArgb(theme.text));

            String typeId = item.typeId == null ? "" : item.typeId;
            int muted = Theme.toArgb(theme.textMuted);
            float typeW = r.measureText(typeId);
            int rightPad = theme.design.space_sm;
            int typeX = (int) (listX + listW - rightPad - typeW);
            r.drawText(typeId, typeX, r.baselineForBox(rowY, itemH), Theme.mulAlpha(muted, 0.90f));
        }
        ui.endScrollArea(area);

        int hintY = listY + listH + theme.design.space_xs;
        if (hintY + 18 < y + height) {
            int col = Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.80f);
            r.drawText("↑/↓ navigate   Enter create   Esc close", x, r.baselineForBox(hintY, 18), col);
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
        filteredItems.clear();

        Set<String> added = new HashSet<>();

        record TypeRow(String typeId, String label, String category, int order) {}

        ArrayList<TypeRow> matches = new ArrayList<>();
        for (String typeId : state.typeIds) {
            if (typeId == null || typeId.isBlank() || "Root".equals(typeId)) {
                continue;
            }
            NodeTypeDef def = state.typesById.get(typeId);
            String label = def == null ? typeId : def.uiLabel();
            String cat = def == null ? "" : def.category();
            int order = def == null ? 0 : def.order();

            if (query.isEmpty()
                    || typeId.toLowerCase(Locale.ROOT).contains(query)
                    || label.toLowerCase(Locale.ROOT).contains(query)
                    || (!cat.isBlank() && cat.toLowerCase(Locale.ROOT).contains(query))) {
                matches.add(new TypeRow(typeId, label, cat, order));
            }
        }

        if (query.isEmpty() && !recentTypes.isEmpty()) {
            ArrayList<TypeRow> recents = new ArrayList<>();
            for (String recent : recentTypes) {
                if (recent == null || !state.typesById.containsKey(recent)) {
                    continue;
                }
                NodeTypeDef def = state.typesById.get(recent);
                recents.add(new TypeRow(recent, def == null ? recent : def.uiLabel(), def == null ? "" : def.category(), def == null ? 0 : def.order()));
                added.add(recent);
            }
            if (!recents.isEmpty()) {
                filteredItems.add(ListItem.header("Recent"));
                recents.sort(Comparator.comparing(TypeRow::label, String.CASE_INSENSITIVE_ORDER));
                for (TypeRow row : recents) {
                    filteredItems.add(ListItem.type(row.label, row.typeId, row.category));
                }
                filteredItems.add(ListItem.header("All Nodes"));
            }
        }

        matches.removeIf(r -> added.contains(r.typeId));
        matches.sort(Comparator
                .comparingInt(TypeRow::order)
                .thenComparing(TypeRow::category, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(TypeRow::label, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(TypeRow::typeId, String.CASE_INSENSITIVE_ORDER));

        String lastCat = null;
        for (TypeRow row : matches) {
            String cat = row.category == null ? "" : row.category.trim();
            if (cat.isBlank()) {
                cat = "Uncategorized";
            }
            if (!cat.equals(lastCat)) {
                filteredItems.add(ListItem.header(cat));
                lastCat = cat;
            }
            filteredItems.add(ListItem.type(row.label, row.typeId, row.category));
        }

        highlightedIndex = firstSelectableIndex();
    }

    private void createNode(EditorState state, String typeId) {
        addRecentType(typeId);
        if (onTypeSelected != null) {
            onTypeSelected.accept(typeId);
            return;
        }
        EditorHistory.CreateNodeEntry entry = new EditorHistory.CreateNodeEntry(parentNodeId, typeId, typeId, List.of(), true);
        runtime.history().pushEntry(entry);
        entry.redo(runtime);
    }

    private static void addRecentType(String typeId) {
        if (typeId == null || typeId.isBlank()) return;
        recentTypes.remove(typeId);
        recentTypes.add(0, typeId);
        while (recentTypes.size() > MAX_RECENT) recentTypes.remove(recentTypes.size() - 1);
    }

    private static String uniqueChildName(EditorState state, long parentId, String typeId) {
        if (state == null || state.scene == null) return typeId;
        Set<String> existing = new HashSet<>();
        for (SceneSnapshot.NodeSnapshot s : state.scene.childrenOf(parentId)) {
            if (s != null && s.name() != null) existing.add(s.name());
        }
        if (!existing.contains(typeId)) return typeId;
        int n = 2;
        while (existing.contains(typeId + n)) n++;
        return typeId + n;
    }

    private void createFirstMatch() {
        EditorState state = runtime.state();
        if (state == null) {
            return;
        }
        int idx = firstSelectableIndex();
        if (idx < 0) {
            return;
        }
        String typeId = filteredItems.get(idx).typeId;
        if (typeId == null || typeId.isBlank()) {
            return;
        }
        createNode(state, typeId);
        close();
    }

    private void createHighlightedOrFirst() {
        EditorState state = runtime.state();
        if (state == null) {
            return;
        }
        int idx = highlightedIndex;
        if (idx < 0 || idx >= filteredItems.size() || filteredItems.get(idx).header) {
            idx = firstSelectableIndex();
        }
        if (idx < 0) {
            return;
        }
        String typeId = filteredItems.get(idx).typeId;
        if (typeId == null || typeId.isBlank()) {
            return;
        }
        createNode(state, typeId);
        close();
    }

    private int firstSelectableIndex() {
        for (int i = 0; i < filteredItems.size(); i++) {
            ListItem item = filteredItems.get(i);
            if (item != null && !item.header && item.typeId != null && !item.typeId.isBlank()) {
                return i;
            }
        }
        return -1;
    }

    private void moveHighlight(int dir) {
        if (filteredItems.isEmpty()) {
            highlightedIndex = -1;
            return;
        }
        int i = highlightedIndex;
        if (i < 0 || i >= filteredItems.size()) {
            i = firstSelectableIndex();
        }
        if (i < 0) {
            highlightedIndex = -1;
            return;
        }
        int next = i;
        while (true) {
            next += dir;
            if (next < 0 || next >= filteredItems.size()) {
                break;
            }
            ListItem item = filteredItems.get(next);
            if (item != null && !item.header) {
                highlightedIndex = next;
                break;
            }
        }
    }

    private void handleContentClick(UiContext ctx, Ui ui, Theme theme, int x, int y, int width, int height) {
        EditorState state = runtime.state();
        if (state == null || ui == null) {
            return;
        }

        int cursorY = y + 22;
        int searchH = theme.design.widget_height_md;
        if (ctx != null && hit((int) ui.mouse().x, (int) ui.mouse().y, x, cursorY, width, searchH)) {
            searchField.focus(ctx);
            return;
        }
        cursorY += searchH + theme.design.space_sm;

        int listH = Math.max(0, height - (cursorY - y) - theme.design.space_sm);
        int mx = (int) ui.mouse().x;
        int my = (int) ui.mouse().y;
        if (!hit(mx, my, x, cursorY, width, listH)) {
            return;
        }

        int itemH = Math.max(18, theme.tokens.itemHeight);
        int offsetY = my - (cursorY + theme.design.space_xs);
        int contentY = offsetY + typeListScrollY;
        if (contentY < 0) {
            return;
        }
        int idx = contentY / Math.max(1, itemH);
        if (idx < 0 || idx >= filteredItems.size()) {
            return;
        }

        ListItem item = filteredItems.get(idx);
        if (item == null) {
            return;
        }
        highlightedIndex = idx;
        if (!item.header && item.typeId != null && !item.typeId.isBlank()) {
            createNode(state, item.typeId);
            close();
        }
    }

    private static void drawTypeIcon(UiRenderer r, Theme theme, String typeId, float x, float y, float size, int color) {
        if (r == null) {
            return;
        }
        if (typeId != null && MoudIcons.has(typeId)) {
            MoudIcons.draw(r, typeId, x, y, size, color);
            return;
        }
        if (theme != null && theme.icons != null) {
            theme.icons.draw(r, Icon.FILE, x, y, size, color);
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
