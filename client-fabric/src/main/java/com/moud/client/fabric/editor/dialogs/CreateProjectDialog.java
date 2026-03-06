package com.moud.client.fabric.editor.dialogs;

import com.miry.platform.InputConstants;
import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.event.KeyEvent;
import com.miry.ui.event.TextInputEvent;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.TextField;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.net.session.Session;

public final class CreateProjectDialog {
    private static final int DIALOG_W = 560;
    private static final int DIALOG_H = 320;

    private final EditorRuntime runtime;
    private final TextField nameField = new TextField();
    private final TextField authorField = new TextField();
    private boolean open;
    private boolean justOpened;

    public CreateProjectDialog(EditorRuntime runtime) {
        this.runtime = runtime;
    }

    public void open() {
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
        if (!open || ctx == null || event == null) {
            return false;
        }

        if (event.isPress() && event.key() == InputConstants.KEY_ESCAPE) {
            close();
            return true;
        }

        if (event.isPressOrRepeat() && event.key() == InputConstants.KEY_TAB) {
            if (nameField.isFocused(ctx)) {
                authorField.focus(ctx);
            } else {
                nameField.focus(ctx);
            }
            return true;
        }

        if (nameField.isFocused(ctx)) {
            nameField.handleKey(event, ctx.clipboard());
            if (event.isPressOrRepeat() && event.key() == InputConstants.KEY_ENTER) {
                create();
            }
            return true;
        }
        if (authorField.isFocused(ctx)) {
            authorField.handleKey(event, ctx.clipboard());
            if (event.isPressOrRepeat() && event.key() == InputConstants.KEY_ENTER) {
                create();
            }
            return true;
        }

        return false;
    }

    public void handleTextInput(UiContext ctx, int codepoint) {
        if (!open || ctx == null) {
            return;
        }
        TextInputEvent event = new TextInputEvent(codepoint);
        if (nameField.isFocused(ctx)) {
            nameField.handleTextInput(event);
        } else if (authorField.isFocused(ctx)) {
            authorField.handleTextInput(event);
        }
    }

    public void render(UiRenderer r, UiContext ctx, Ui ui, Theme theme, int screenW, int screenH) {
        if (!open) {
            return;
        }

        if (justOpened && ctx != null) {
            justOpened = false;
            if (nameField.text() == null || nameField.text().isBlank()) {
                nameField.setText("My Game");
            }
            if (authorField.text() == null) {
                authorField.setText("");
            }
            nameField.focus(ctx);
        }

        int mx = (int) ui.mouse().x;
        int my = (int) ui.mouse().y;
        boolean pressed = ui.input() != null && ui.input().mousePressed();

        int overlay = 0x80000000;
        r.drawRect(0, 0, screenW, screenH, overlay);

        int dialogW = Math.min(DIALOG_W, Math.max(300, screenW - theme.design.space_lg * 2));
        int dialogH = Math.min(DIALOG_H, Math.max(220, screenH - theme.design.space_lg * 2));
        int dialogX = (screenW - dialogW) / 2;
        int dialogY = (screenH - dialogH) / 2;

        int bg = Theme.toArgb(theme.panelBg);
        int outline = Theme.toArgb(theme.widgetOutline);
        int text = Theme.toArgb(theme.text);
        int muted = Theme.toArgb(theme.textMuted);
        int btnBg = Theme.toArgb(theme.widgetBg);
        int btnHover = Theme.toArgb(theme.widgetHover);

        r.drawRoundedRect(dialogX, dialogY, dialogW, dialogH, theme.design.radius_md, bg, theme.design.border_thin, outline);

        int pad = theme.design.space_lg;
        int headerH = 44;
        r.drawText("Create Project", dialogX + pad, r.baselineForBox(dialogY, headerH), text);
        r.drawText("No project found in server folder.", dialogX + pad, r.baselineForBox(dialogY + 22, headerH), muted);

        int buttonW = 140;
        int buttonH = theme.design.widget_height_md + theme.design.border_thin * 2;
        int buttonY = dialogY + dialogH - pad - buttonH;
        int createX = dialogX + dialogW - pad - buttonW;
        int cancelX = createX - theme.design.space_sm - buttonW;

        if (pressed) {
            if (mx < dialogX || my < dialogY || mx >= dialogX + dialogW || my >= dialogY + dialogH) {
                close();
                return;
            }
        }

        drawButton(r, theme, "Cancel", cancelX, buttonY, buttonW, buttonH, mx, my, btnBg, btnHover, outline, text);
        drawButton(r, theme, "Create", createX, buttonY, buttonW, buttonH, mx, my, btnBg, btnHover, outline, text);

        int fieldW = Math.max(160, dialogW - pad * 2);
        int fieldH = theme.design.widget_height_md;
        int fieldX = dialogX + pad;
        int rowY = dialogY + headerH + pad;

        r.drawText("Name", fieldX, r.baselineForBox(rowY, 18), muted);
        nameField.render(r, ctx, ui.input(), theme, fieldX, rowY + 18, fieldW, fieldH, true);
        rowY += 18 + fieldH + theme.design.space_md;

        r.drawText("Author", fieldX, r.baselineForBox(rowY, 18), muted);
        authorField.render(r, ctx, ui.input(), theme, fieldX, rowY + 18, fieldW, fieldH, true);

        if (pressed) {
            if (hit(mx, my, cancelX, buttonY, buttonW, buttonH)) {
                close();
                return;
            }
            if (hit(mx, my, createX, buttonY, buttonW, buttonH)) {
                create();
            }
        }
    }

    private void create() {
        EditorState state = runtime.state();
        if (state == null) {
            return;
        }
        Session session = runtime.session();
        if (session == null) {
            return;
        }
        String name = nameField.text() == null ? "" : nameField.text().trim();
        String author = authorField.text() == null ? "" : authorField.text().trim();
        runtime.net().createProject(session, state, name, author);
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
                                   int hover,
                                   int outline,
                                   int text) {
        boolean hovered = hit(mx, my, x, y, w, h);
        r.drawRoundedRect(x, y, w, h, theme.design.radius_sm, hovered ? hover : bg, theme.design.border_thin, outline);
        r.drawText(label, x + theme.design.space_md, r.baselineForBox(y, h), text);
    }

    private static boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
