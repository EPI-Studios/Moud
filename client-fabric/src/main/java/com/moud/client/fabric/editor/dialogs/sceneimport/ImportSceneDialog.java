package com.moud.client.fabric.editor.dialogs.sceneimport;

import com.miry.platform.InputConstants;
import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.event.KeyEvent;
import com.miry.ui.event.TextInputEvent;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Icon;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.TextField;
import com.moud.client.fabric.editor.state.EditorHistory;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.render.MoudIcons;
import com.moud.net.protocol.SceneInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ImportSceneDialog {
    private static final int DIALOG_W = 920;
    private static final int DIALOG_H = 620;
    private static final int CARD_W = 180;
    private static final int CARD_H = 166;

    private final EditorRuntime runtime;
    private final TextField searchField = new TextField();
    private final ArrayList<SceneImportCard> filteredCards = new ArrayList<>();

    private boolean open;
    private boolean justOpened;
    private boolean resetScrollPending;
    private long parentNodeId;
    private String parentName = "";

    public ImportSceneDialog(EditorRuntime runtime) {
        this.runtime = runtime;
    }

    public void open(long parentNodeId) {
        EditorState state = runtime.state();
        this.parentNodeId = parentNodeId;
        var parent = state != null && state.scene != null ? state.scene.getNode(parentNodeId) : null;
        this.parentName = parent != null ? parent.name() : "";
        searchField.setText("");
        rebuildCards();
        justOpened = true;
        resetScrollPending = true;
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
        if (ctx != null && searchField.isFocused(ctx)) {
            searchField.handleKey(event, ctx.clipboard());
            rebuildCards();
            return true;
        }
        if (event.isPressOrRepeat() && event.key() == InputConstants.KEY_ENTER) {
            importFirst();
            return true;
        }
        return false;
    }

    public void handleTextInput(int codepoint) {
        if (!open) {
            return;
        }
        searchField.handleTextInput(new TextInputEvent(codepoint));
        rebuildCards();
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
        boolean pressed = ui.input() != null && ui.input().mousePressed();

        int overlay = 0x80000000;
        int bg = Theme.toArgb(theme.panelBg);
        int outline = Theme.toArgb(theme.widgetOutline);
        int text = Theme.toArgb(theme.text);
        int muted = Theme.toArgb(theme.textMuted);
        int widgetBg = Theme.toArgb(theme.widgetBg);
        int widgetHover = Theme.toArgb(theme.widgetHover);

        r.drawRect(0, 0, screenW, screenH, overlay);

        int dialogW = Math.min(DIALOG_W, Math.max(360, screenW - theme.design.space_lg * 2));
        int dialogH = Math.min(DIALOG_H, Math.max(280, screenH - theme.design.space_lg * 2));
        int dialogX = (screenW - dialogW) / 2;
        int dialogY = (screenH - dialogH) / 2;
        r.drawRoundedRect(dialogX, dialogY, dialogW, dialogH, theme.design.radius_md, bg, theme.design.border_thin, outline);

        int pad = theme.design.space_lg;
        int headerH = 52;
        r.drawText("Import Scene", dialogX + pad, r.baselineForBox(dialogY, headerH), text);
        String parentInfo = "As child of " + (parentName == null || parentName.isBlank() ? "#" + parentNodeId : parentName);
        r.drawText(parentInfo, dialogX + pad + 180, r.baselineForBox(dialogY, headerH), muted);

        int buttonW = 140;
        int buttonH = theme.design.widget_height_md + theme.design.border_thin * 2;
        int buttonY = dialogY + dialogH - pad - buttonH;
        int importX = dialogX + dialogW - pad - buttonW;
        int cancelX = importX - theme.design.space_sm - buttonW;

        drawButton(r, theme, "Cancel", cancelX, buttonY, buttonW, buttonH, mx, my, widgetBg, widgetHover, outline, text);
        drawButton(r, theme, "Import", importX, buttonY, buttonW, buttonH, mx, my, widgetBg, widgetHover, outline, text);

        int contentX = dialogX + pad;
        int contentY = dialogY + headerH;
        int contentW = dialogW - pad * 2;
        int contentH = buttonY - contentY - theme.design.space_md;

        int searchH = theme.design.widget_height_md;
        searchField.render(r, ctx, ui.input(), theme, contentX, contentY, contentW, searchH, true);
        if ((searchField.text() == null || searchField.text().isEmpty()) && (ctx == null || !searchField.isFocused(ctx))) {
            int hint = Theme.mulAlpha(muted, 0.70f);
            float iconSize = Math.min(theme.design.icon_sm, searchH - 6);
            MoudIcons.drawOrFallback(r, theme, Icon.SEARCH, contentX + 6, contentY + (searchH - iconSize) * 0.5f, iconSize, hint);
            r.drawText("Search scenes...", contentX + 6 + (int) Math.ceil(iconSize) + 6, r.baselineForBox(contentY, searchH), hint);
        }

        int gridY = contentY + searchH + theme.design.space_sm;
        int gridH = Math.max(0, contentH - searchH - theme.design.space_sm);
        r.drawRoundedRect(contentX, gridY, contentW, gridH, theme.design.radius_sm, Theme.darkenArgb(widgetBg, 0.02f), theme.design.border_thin, outline);

        if (filteredCards.isEmpty()) {
            r.drawText("No scenes found", contentX + theme.design.space_md, r.baselineForBox(gridY + theme.design.space_md, 20), muted);
        } else {
            int innerPad = theme.design.space_sm;
            int cols = Math.max(1, (contentW - innerPad * 2 + theme.design.space_sm) / (CARD_W + theme.design.space_sm));
            int actualCardW = Math.max(140, (contentW - innerPad * 2 - theme.design.space_sm * (cols - 1)) / cols);
            int rowGap = theme.design.space_sm;
            int rows = (filteredCards.size() + cols - 1) / cols;
            int contentHeight = innerPad * 2 + rows * CARD_H + Math.max(0, rows - 1) * rowGap;

            if (resetScrollPending) {
                resetScrollPending = false;
                ui.setScrollY("sceneImportGridScroll", 0f);
            }
            Ui.ScrollArea area = ui.beginScrollArea(r, "sceneImportGridScroll", contentX, gridY, contentW, gridH, contentHeight);
            int scrollY = (int) area.scrollY();
            for (int i = 0; i < filteredCards.size(); i++) {
                SceneImportCard card = filteredCards.get(i);
                int col = i % cols;
                int row = i / cols;
                int cardX = contentX + innerPad + col * (actualCardW + theme.design.space_sm);
                int cardY = gridY + innerPad + row * (CARD_H + rowGap) - scrollY;
                if (cardY + CARD_H < gridY || cardY > gridY + gridH) {
                    continue;
                }
                boolean hovered = hit(mx, my, cardX, cardY, actualCardW, CARD_H);
                int cardBg = hovered ? widgetHover : widgetBg;
                r.drawRoundedRect(cardX, cardY, actualCardW, CARD_H, theme.design.radius_md, cardBg, theme.design.border_thin, outline);

                SceneImportPreview.draw(r, theme, card, cardX + 8, cardY + 8, actualCardW - 16, 88, hovered);

                int titleY = cardY + 104;
                r.drawText(card.title(), cardX + 10, r.baselineForBox(titleY, 22), text);
                r.drawText(card.subtitle(), cardX + 10, r.baselineForBox(titleY + 22, 18), muted);

                if (hovered && pressed) {
                    importScene(card.scene());
                    close();
                    ui.endScrollArea(area);
                    return;
                }
            }
            ui.endScrollArea(area);
        }

        if (!pressed) {
            return;
        }
        if (mx < dialogX || my < dialogY || mx >= dialogX + dialogW || my >= dialogY + dialogH) {
            close();
            return;
        }
        if (hit(mx, my, cancelX, buttonY, buttonW, buttonH)) {
            close();
            return;
        }
        if (hit(mx, my, importX, buttonY, buttonW, buttonH)) {
            importFirst();
        }
    }

    private void rebuildCards() {
        filteredCards.clear();
        EditorState state = runtime.state();
        if (state == null || state.scenes == null) {
            return;
        }
        String query = searchField.text() == null ? "" : searchField.text().trim().toLowerCase(Locale.ROOT);
        List<SceneImportCard> cards = new ArrayList<>();
        for (SceneInfo scene : state.scenes) {
            if (scene == null || scene.sceneId() == null || scene.sceneId().isBlank()) {
                continue;
            }
            SceneImportCard card = SceneImportCard.of(scene);
            if (card.matches(query)) {
                cards.add(card);
            }
        }
        cards.sort(Comparator.comparing(SceneImportCard::title, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(SceneImportCard::subtitle, String.CASE_INSENSITIVE_ORDER));
        filteredCards.addAll(cards);
    }

    private void importFirst() {
        if (filteredCards.isEmpty()) {
            return;
        }
        importScene(filteredCards.getFirst().scene());
        close();
    }

    private void importScene(SceneInfo scene) {
        if (scene == null || scene.sceneId() == null || scene.sceneId().isBlank()) {
            return;
        }
        EditorState state = runtime.state();
        if (state == null || runtime.session() == null) {
            runtime.requestToast("Cannot import scene: not connected", true, 3500);
            return;
        }
        String nameHint = scene.displayName() != null && !scene.displayName().isBlank()
                ? scene.displayName()
                : scene.sceneId();
        EditorHistory.CreateNodeEntry entry = new EditorHistory.CreateNodeEntry(
                parentNodeId,
                nameHint,
                "SceneInstance3D",
                List.of(Map.entry("scene_id", scene.sceneId().trim())),
                true
        );
        runtime.history().pushEntry(entry);
        entry.redo(runtime);
        runtime.requestToast("Imported scene: " + scene.sceneId(), false, 2500);
    }

    private static void drawButton(UiRenderer r, Theme theme, String label,
                                   int x, int y, int w, int h,
                                   int mx, int my,
                                   int bg, int hover, int outline, int textColor) {
        boolean hovered = hit(mx, my, x, y, w, h);
        r.drawRoundedRect(x, y, w, h, theme.design.radius_sm, hovered ? hover : bg, theme.design.border_thin, outline);
        r.drawText(label, x + theme.design.space_md, r.baselineForBox(y, h), textColor);
    }

    private static boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
