package com.moud.client.fabric.editor.dialogs;


import com.moud.net.protocol.ScriptFileReadResponse;
import com.moud.net.protocol.ScriptFileWriteAck;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;

public final class ScriptEditorDialog {
    private static final int DIALOG_W = 980;
    private static final int DIALOG_H = 680;
    private static final long CONFIRM_TIMEOUT_MS = 3500L;

    private enum ConfirmAction {
        CLOSE,
        RELOAD
    }

    private final EditorRuntime runtime;
    private final CodeEditor editor = new CodeEditor();

    private boolean open;
    private boolean justOpened;
    private long nodeId;
    private String scriptPath = "";

    private long pendingReadId;
    private long pendingWriteId;
    private boolean loading;
    private boolean saving;
    private String lastLoadedText = "";
    private String error;
    private ConfirmAction confirmAction;
    private long confirmUntilMs;

    public ScriptEditorDialog(EditorRuntime runtime) {
        this.runtime = runtime;
        editor.setLanguage(CodeEditor.Language.NONE);
    }

    public void open(long nodeId, String scriptPath) {
        this.nodeId = nodeId;
        this.scriptPath = scriptPath == null ? "" : scriptPath.trim();
        this.error = null;
        this.confirmAction = null;
        this.confirmUntilMs = 0L;
        this.justOpened = true;
        this.open = true;
        this.lastLoadedText = "";
        editor.setText("");
        requestReload();
    }

    public void close() {
        open = false;
        loading = false;
        saving = false;
        pendingReadId = 0L;
        pendingWriteId = 0L;
        error = null;
        confirmAction = null;
        confirmUntilMs = 0L;
    }

    public boolean isOpen() {
        return open;
    }

    public void onReadResponse(ScriptFileReadResponse response) {
        if (!open || response == null) {
            return;
        }
        if (pendingReadId != 0L && response.requestId() != pendingReadId) {
            return;
        }
        pendingReadId = 0L;
        loading = false;

        if (!response.success()) {
            error = response.error() == null ? "Read failed" : response.error();
            lastLoadedText = "";
            if (editor.getText() == null || editor.getText().isEmpty()) {
                editor.setText("");
            }
            ClientDebugLog.error("Script read failed path=" + response.path() + " error=" + error);
            return;
        }

        String content = response.content() == null ? "" : response.content();
        lastLoadedText = content;
        editor.setText(content);
        error = null;
    }

    public void onWriteAck(ScriptFileWriteAck ack) {
        if (!open || ack == null) {
            return;
        }
        if (pendingWriteId != 0L && ack.requestId() != pendingWriteId) {
            return;
        }
        pendingWriteId = 0L;
        saving = false;
        if (!ack.success()) {
            error = ack.error() == null ? "Save failed" : ack.error();
            ClientDebugLog.error("Script save failed path=" + ack.path() + " error=" + error);
            return;
        }
        lastLoadedText = editor.getText();
        error = null;
    }

    public boolean handleKey(UiContext ctx, KeyEvent event) {
        if (!open || ctx == null || event == null) {
            return false;
        }

        if (event.isPress() && event.key() == InputConstants.KEY_ESCAPE) {
            requestClose();
            return true;
        }

        boolean ctrl = event.hasCtrl() || event.hasSuper();
        if (ctrl && event.isPressOrRepeat() && event.key() == InputConstants.KEY_S) {
            confirmAction = null;
            confirmUntilMs = 0L;
            save();
            return true;
        }

        if (editor.isFocused()) {
            editor.handleKey(event, ctx.clipboard());
            return true;
        }
        return false;
    }

    public void handleTextInput(UiContext ctx, int codepoint) {
        if (!open || ctx == null) {
            return;
        }
        if (editor.isFocused()) {
            editor.handleTextInput(ctx, new TextInputEvent(codepoint));
        }
    }

    public void render(UiRenderer r, UiContext ctx, Ui ui, Theme theme, int screenW, int screenH) {
        if (!open) {
            return;
        }

        long now = System.currentTimeMillis();
        if (confirmAction != null && now >= confirmUntilMs) {
            confirmAction = null;
            confirmUntilMs = 0L;
        }

        if (justOpened && ctx != null) {
            justOpened = false;
            editor.focus(ctx);
        }

        int mx = (int) ui.mouse().x;
        int my = (int) ui.mouse().y;
        boolean pressed = ui.input() != null && ui.input().mousePressed();

        r.drawRect(0, 0, screenW, screenH, 0x80000000);

        int dialogW = Math.min(DIALOG_W, Math.max(540, screenW - theme.design.space_lg * 2));
        int dialogH = Math.min(DIALOG_H, Math.max(360, screenH - theme.design.space_lg * 2));
        int dialogX = (screenW - dialogW) / 2;
        int dialogY = (screenH - dialogH) / 2;

        int bg = Theme.toArgb(theme.panelBg);
        int outline = Theme.toArgb(theme.widgetOutline);
        int text = Theme.toArgb(theme.text);
        int muted = Theme.toArgb(theme.textMuted);
        int danger = Theme.toArgb(theme.danger);

        r.drawRoundedRect(dialogX, dialogY, dialogW, dialogH, theme.design.radius_md, bg, theme.design.border_thin, outline);

        int pad = theme.design.space_lg;
        int headerH = 54;
        int headerX = dialogX + pad;
        int headerY = dialogY;

        String title = "Script";
        r.drawText(title, headerX, r.baselineForBox(headerY, headerH), text);

        String subtitle = (scriptPath == null || scriptPath.isBlank()) ? "(no script)" : scriptPath;
        r.drawText(subtitle, headerX, r.baselineForBox(headerY + 22, headerH), muted);

        int btnH = theme.design.widget_height_md + theme.design.border_thin * 2;
        int btnW = 120;
        int btnY = dialogY + dialogH - pad - btnH;
        int closeW = 110;
        int closeX = dialogX + dialogW - pad - closeW;
        int saveX = closeX - theme.design.space_sm - btnW;
        int reloadX = saveX - theme.design.space_sm - btnW;

        String currentText = editor.getText() == null ? "" : editor.getText();
        boolean dirty = !currentText.equals(lastLoadedText == null ? "" : lastLoadedText);
        boolean canInteract = ui.input() != null;
        boolean canSave = dirty && !saving && !loading && hasSession();
        boolean canReload = !loading && !saving && hasSession();

        int reloadText = (dirty && confirmAction == ConfirmAction.RELOAD) ? danger : text;
        int closeText = (dirty && confirmAction == ConfirmAction.CLOSE) ? danger : text;
        drawButton(r, theme, "Reload", reloadX, btnY, btnW, btnH, mx, my, canReload, reloadText);
        drawButton(r, theme, "Save", saveX, btnY, btnW, btnH, mx, my, canSave, text);
        drawButton(r, theme, "Close", closeX, btnY, closeW, btnH, mx, my, true, closeText);

        String status;
        int statusColor = muted;
        if (dirty && confirmAction != null) {
            statusColor = danger;
            status = confirmAction == ConfirmAction.CLOSE
                    ? "Unsaved changes — click Close again to discard"
                    : "Unsaved changes — click Reload again to discard";
        } else if (loading) {
            status = "Loading…";
        } else if (saving) {
            status = "Saving…";
        } else if (error != null && !error.isBlank()) {
            status = error;
            statusColor = danger;
        } else if (dirty) {
            status = "Modified";
        } else {
            status = "Saved";
        }
        r.drawText(status, dialogX + pad, r.baselineForBox(btnY, btnH), statusColor);

        int editorX = dialogX + pad;
        int editorY = dialogY + headerH + pad;
        int editorW = Math.max(1, dialogW - pad * 2);
        int editorH = Math.max(1, btnY - editorY - pad);

        // Small code icon in the gutter to reinforce "this is a script".
        float iconSize = Math.min(theme.design.icon_sm, 18);
        theme.icons.draw(r, Icon.CODE, editorX, editorY - 26, iconSize, Theme.toArgb(theme.textMuted));

        editor.render(r, ctx, ui.input(), theme, editorX, editorY, editorW, editorH, true);

        if (!canInteract || !pressed) {
            return;
        }

        if (mx < dialogX || my < dialogY || mx >= dialogX + dialogW || my >= dialogY + dialogH) {
            requestClose();
            return;
        }

        if (hit(mx, my, closeX, btnY, closeW, btnH)) {
            requestClose();
            return;
        }
        if (hit(mx, my, reloadX, btnY, btnW, btnH) && canReload) {
            requestReloadWithConfirm();
            return;
        }
        if (hit(mx, my, saveX, btnY, btnW, btnH) && canSave) {
            confirmAction = null;
            confirmUntilMs = 0L;
            save();
        }
    }

    private void requestReload() {
        if (!hasSession()) {
            return;
        }
        if (scriptPath == null || scriptPath.isBlank()) {
            error = "No script path set on node";
            return;
        }
        EditorState state = runtime.state();
        EditorNet net = runtime.net();
        Session session = runtime.session();
        if (state == null || net == null || session == null) {
            return;
        }
        loading = true;
        saving = false;
        error = null;
        pendingReadId = net.requestScriptFile(session, state, scriptPath);
    }

    private void requestReloadWithConfirm() {
        String current = editor.getText() == null ? "" : editor.getText();
        boolean dirty = !current.equals(lastLoadedText == null ? "" : lastLoadedText);
        if (!dirty) {
            confirmAction = null;
            confirmUntilMs = 0L;
            requestReload();
            return;
        }
        if (confirmAction == ConfirmAction.RELOAD && System.currentTimeMillis() < confirmUntilMs) {
            confirmAction = null;
            confirmUntilMs = 0L;
            requestReload();
            return;
        }
        confirmAction = ConfirmAction.RELOAD;
        confirmUntilMs = System.currentTimeMillis() + CONFIRM_TIMEOUT_MS;
    }

    private void requestClose() {
        String current = editor.getText() == null ? "" : editor.getText();
        boolean dirty = !current.equals(lastLoadedText == null ? "" : lastLoadedText);
        if (!dirty) {
            close();
            return;
        }
        if (confirmAction == ConfirmAction.CLOSE && System.currentTimeMillis() < confirmUntilMs) {
            close();
            return;
        }
        confirmAction = ConfirmAction.CLOSE;
        confirmUntilMs = System.currentTimeMillis() + CONFIRM_TIMEOUT_MS;
    }

    private void save() {
        if (!hasSession()) {
            return;
        }
        if (scriptPath == null || scriptPath.isBlank()) {
            error = "No script path set on node";
            return;
        }
        EditorState state = runtime.state();
        EditorNet net = runtime.net();
        Session session = runtime.session();
        if (state == null || net == null || session == null) {
            return;
        }
        saving = true;
        loading = false;
        error = null;
        pendingWriteId = net.writeScriptFile(session, state, scriptPath, editor.getText());
    }

    private boolean hasSession() {
        Session session = runtime.session();
        return session != null && session.state() == SessionState.CONNECTED;
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
                                   boolean enabled,
                                   int textColor) {
        int bg = Theme.toArgb(theme.widgetBg);
        int hover = Theme.toArgb(theme.widgetHover);
        int outline = Theme.toArgb(theme.widgetOutline);
        int muted = Theme.mulAlpha(textColor, 0.65f);
        boolean hovered = enabled && hit(mx, my, x, y, w, h);
        int fg = enabled ? textColor : muted;
        r.drawRoundedRect(x, y, w, h, theme.design.radius_sm, hovered ? hover : bg, theme.design.border_thin, outline);
        r.drawText(label, x + theme.design.space_md, r.baselineForBox(y, h), fg);
    }

    private static boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
