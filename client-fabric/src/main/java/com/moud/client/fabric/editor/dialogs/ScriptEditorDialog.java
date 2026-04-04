package com.moud.client.fabric.editor.dialogs;

import com.miry.platform.InputConstants;
import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.event.KeyEvent;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Icon;
import com.moud.client.fabric.render.MoudIcons;
import com.miry.ui.theme.Theme;
import com.moud.client.fabric.editor.net.EditorNet;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.util.ClientDebugLog;
import com.miry.ui.widgets.editor.language.LanguageProvider;
import com.miry.ui.widgets.editor.language.impl.GLSLLanguageProvider;
import com.miry.ui.widgets.editor.language.impl.JSLanguageProvider;
import com.miry.ui.widgets.editor.language.impl.JavaLanguageProvider;
import com.miry.ui.widgets.editor.language.impl.LuauLanguageProvider;
import com.miry.ui.widgets.editor.language.impl.TypeScriptLanguageProvider;
import com.miry.ui.widgets.editor.view.CodeEditor;
import com.miry.ui.widgets.editor.view.FindBarWidget;
import com.moud.net.protocol.ScriptFileReadResponse;
import com.moud.net.protocol.ScriptFileWriteAck;
import com.miry.ui.widgets.ContextMenu;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;
import java.util.ArrayList;

public final class ScriptEditorDialog {
    private static final int DIALOG_W = 980;
    private static final int DIALOG_H = 680;
    private static final long CONFIRM_TIMEOUT_MS = 3500L;

    private static final LanguageProvider JS = new JSLanguageProvider();
    private static final LanguageProvider TS = new TypeScriptLanguageProvider();
    private static final LanguageProvider JAVA = new JavaLanguageProvider();
    private static final LanguageProvider GLSL = new GLSLLanguageProvider();
    private static final LanguageProvider LUAU = new LuauLanguageProvider();

    private enum ConfirmAction {
        CLOSE,
        RELOAD
    }

    private final EditorRuntime runtime;
    private final CodeEditor editor = new CodeEditor();
    private final FindBarWidget findBar = new FindBarWidget();
    private UiContext lastUiContext;

    private boolean open;
    private boolean justOpened;
    private long nodeId;
    private String scriptPath = "";
    private boolean findBarVisible;

    private long pendingReadId;
    private long pendingWriteId;
    private boolean loading;
    private boolean saving;
    private String lastLoadedText = "";
    private String error;
    private ConfirmAction confirmAction;
    private long confirmUntilMs;
    private final ArrayList<String> recentScripts = new ArrayList<>();
    private final ContextMenu recentMenu = new ContextMenu();

    public ScriptEditorDialog(EditorRuntime runtime) {
        this.runtime = runtime;
        findBar.setEditor(editor);
    }

    public void open(long nodeId, String scriptPath) {
        this.nodeId = nodeId;
        this.scriptPath = scriptPath == null ? "" : scriptPath.trim();
        if (!this.scriptPath.isBlank()) {
            recentScripts.remove(this.scriptPath);
            recentScripts.add(0, this.scriptPath);
            if (recentScripts.size() > 10) recentScripts.remove(recentScripts.size() - 1);
        }
        this.error = null;
        this.confirmAction = null;
        this.confirmUntilMs = 0L;
        this.justOpened = true;
        this.open = true;
        this.findBarVisible = false;
        this.lastLoadedText = "";
        editor.setLanguage(languageFor(scriptPath));
        editor.setText("");
        findBar.clear();
        requestReload();
    }

    public void cancelInteractions(UiContext ctx) {
        editor.cancelInteractions(ctx);
    }

    public void close() {
        if (lastUiContext != null) {
            editor.cancelInteractions(lastUiContext);
        }
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

    public String scriptPath() {
        return scriptPath;
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
            if (editor.text().isEmpty()) {
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
        lastLoadedText = editor.text();
        error = null;
    }

    public boolean handleKey(UiContext ctx, KeyEvent event) {
        if (!open || ctx == null || event == null) {
            return false;
        }
        lastUiContext = ctx;

        boolean ctrl = event.hasCtrl() || event.hasSuper();
        if (ctrl && event.isPressOrRepeat() && event.key() == InputConstants.KEY_F) {
            findBarVisible = !findBarVisible;
            if (!findBarVisible) {
                findBar.clear();
            }
            return true;
        }

        if (event.isPress() && event.key() == InputConstants.KEY_ESCAPE) {
            if (findBarVisible) {
                findBarVisible = false;
                findBar.clear();
            } else {
                requestClose();
            }
            return true;
        }

        if (ctrl && event.isPressOrRepeat() && event.key() == InputConstants.KEY_S) {
            confirmAction = null;
            confirmUntilMs = 0L;
            save();
            return true;
        }
        if (findBarVisible) {
            findBar.handleKey(ctx, event);
            return true;
        }
        editor.handleKey(ctx, event);
        return true;
    }

    public void handleTextInput(UiContext ctx, int codepoint) {
        if (!open || ctx == null) {
            return;
        }
        lastUiContext = ctx;
        if (findBarVisible) {
            findBar.handleTextInput(codepoint);
        } else {
            editor.handleTextInput(ctx, codepoint);
        }
    }

    public void render(UiRenderer r, UiContext ctx, Ui ui, Theme theme, int screenW, int screenH) {
        if (!open) {
            return;
        }
        lastUiContext = ctx;

        long now = System.currentTimeMillis();
        if (confirmAction != null && now >= confirmUntilMs) {
            confirmAction = null;
            confirmUntilMs = 0L;
        }

        if (justOpened) {
            justOpened = false;
        }

        int mx = (int) ui.mouse().x;
        int my = (int) ui.mouse().y;
        boolean canInteract = ui.input() != null;
        boolean pressed = canInteract && ui.input().mousePressed();

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

        int recentBtnW = 90;
        int recentBtnH = 26;
        int recentBtnX = dialogX + dialogW - pad - recentBtnW;
        int recentBtnY = dialogY + (headerH - recentBtnH) / 2;
        boolean recentEnabled = !recentScripts.isEmpty();
        boolean recentHovered = recentEnabled && hit(mx, my, recentBtnX, recentBtnY, recentBtnW, recentBtnH);
        int recentBg = recentHovered ? Theme.toArgb(theme.widgetHover) : Theme.toArgb(theme.widgetBg);
        r.drawRoundedRect(recentBtnX, recentBtnY, recentBtnW, recentBtnH, theme.design.radius_sm, recentBg, theme.design.border_thin, outline);
        r.drawText("Recent \u25be", recentBtnX + theme.design.space_md, r.baselineForBox(recentBtnY, recentBtnH), recentEnabled ? text : muted);

        if (recentMenu.isOpen()) {
            int menuItemH = Math.max(18, theme.design.widget_height_sm);
            var uiInput = ui.input();
            if (uiInput != null) recentMenu.updateFromInput(uiInput, theme, menuItemH);
            recentMenu.render(r, theme, menuItemH,
                    Theme.toArgb(theme.panelBg),
                    Theme.toArgb(theme.widgetHover),
                    Theme.toArgb(theme.text),
                    recentMenu.hoverIndex());
            if (canInteract && pressed && uiInput != null) {
                recentMenu.handleClick(mx, my, menuItemH);
            }
        }

        int btnH = theme.design.widget_height_md + theme.design.border_thin * 2;
        int btnW = 120;
        int btnY = dialogY + dialogH - pad - btnH;
        int closeW = 110;
        int closeX = dialogX + dialogW - pad - closeW;
        int saveX = closeX - theme.design.space_sm - btnW;
        int reloadX = saveX - theme.design.space_sm - btnW;

        String currentText = editor.text();
        boolean dirty = !currentText.equals(lastLoadedText == null ? "" : lastLoadedText);
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

        if (findBarVisible) {
            int fbH = findBar.preferredHeight(r, theme);
            findBar.render(r, ctx, ui.input(), theme, editorX, editorY, editorW, fbH, true);
            editorY += fbH;
            editorH = Math.max(1, editorH - fbH);
        }

        float iconSize = Math.min(theme.design.icon_sm, 18);
        MoudIcons.drawOrFallback(r, theme, Icon.CODE, editorX, editorY - 26, iconSize, Theme.toArgb(theme.textMuted));

        editor.setReadOnly(loading);
        editor.render(r, ctx, ui.input(), theme, editorX, editorY, editorW, editorH, true);

        if (!canInteract || !pressed) {
            return;
        }

        if (mx < dialogX || my < dialogY || mx >= dialogX + dialogW || my >= dialogY + dialogH) {
            recentMenu.close();
            requestClose();
            return;
        }

        if (recentEnabled && hit(mx, my, recentBtnX, recentBtnY, recentBtnW, recentBtnH)) {
            if (!recentMenu.isOpen()) {
                recentMenu.clear();
                for (String p : recentScripts) {
                    String captured = p;
                    recentMenu.addItem(p, () -> open(nodeId, captured));
                }
                recentMenu.open(recentBtnX, recentBtnY + recentBtnH);
            } else {
                recentMenu.close();
            }
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

    public void renderInline(UiRenderer r, UiContext ctx, Ui ui, Theme theme, int x, int y, int w, int h) {
        if (!open) {
            return;
        }
        lastUiContext = ctx;

        long now = System.currentTimeMillis();
        if (confirmAction != null && now >= confirmUntilMs) {
            confirmAction = null;
            confirmUntilMs = 0L;
        }
        if (justOpened) {
            justOpened = false;
        }

        int mx = (int) ui.mouse().x;
        int my = (int) ui.mouse().y;
        boolean canInteract = ui.input() != null;
        boolean pressed = canInteract && ui.input().mousePressed();

        int textColor = Theme.toArgb(theme.text);
        int muted = Theme.toArgb(theme.textMuted);
        int danger = Theme.toArgb(theme.danger);
        int outline = Theme.toArgb(theme.widgetOutline);
        int pad = theme.design.space_md;

        String currentText = editor.text();
        boolean dirty = !currentText.equals(lastLoadedText == null ? "" : lastLoadedText);
        boolean canSave = dirty && !saving && !loading && hasSession();
        boolean canReload = !loading && !saving && hasSession();

        int btnH = theme.design.widget_height_md + theme.design.border_thin * 2;
        int barY = y + (pad >> 1);
        int closeW = 70, btnW = 80;
        int closeX = x + w - pad - closeW;
        int saveX = closeX - theme.design.space_sm - btnW;
        int reloadX = saveX - theme.design.space_sm - btnW;

        int reloadTextColor = (dirty && confirmAction == ConfirmAction.RELOAD) ? danger : textColor;
        int closeTextColor = (dirty && confirmAction == ConfirmAction.CLOSE) ? danger : textColor;
        drawButton(r, theme, "Reload", reloadX, barY, btnW, btnH, mx, my, canReload, reloadTextColor);
        drawButton(r, theme, "Save", saveX, barY, btnW, btnH, mx, my, canSave, textColor);
        drawButton(r, theme, "Close", closeX, barY, closeW, btnH, mx, my, true, closeTextColor);

        String statusText;
        int statusColor;
        if (dirty && confirmAction != null) {
            statusColor = danger;
            statusText = confirmAction == ConfirmAction.CLOSE
                    ? "Unsaved — close again to discard"
                    : "Unsaved — reload again to discard";
        } else if (loading) {
            statusText = "Loading…";
            statusColor = muted;
        } else if (saving) {
            statusText = "Saving…";
            statusColor = muted;
        } else if (error != null && !error.isBlank()) {
            statusText = error;
            statusColor = danger;
        } else if (dirty) {
            statusText = "● " + (scriptPath.isBlank() ? "(no script)" : scriptPath);
            statusColor = textColor;
        } else {
            statusText = scriptPath.isBlank() ? "(no script)" : scriptPath;
            statusColor = muted;
        }
        r.drawText(statusText, x + pad, r.baselineForBox(barY, btnH), statusColor);

        int editorY = barY + btnH + (pad >> 1);
        int editorW = Math.max(1, w - pad * 2);
        int editorH = Math.max(1, y + h - editorY - (pad >> 1));
        int editorX = x + pad;

        if (findBarVisible) {
            int fbH = findBar.preferredHeight(r, theme);
            findBar.render(r, ctx, ui.input(), theme, editorX, editorY, editorW, fbH, true);
            editorY += fbH;
            editorH = Math.max(1, editorH - fbH);
        }

        editor.setReadOnly(loading);
        editor.render(r, ctx, ui.input(), theme, editorX, editorY, editorW, editorH, true);

        if (!canInteract || !pressed) {
            return;
        }
        if (hit(mx, my, closeX, barY, closeW, btnH)) {
            requestClose();
            return;
        }
        if (hit(mx, my, reloadX, barY, btnW, btnH) && canReload) {
            requestReloadWithConfirm();
            return;
        }
        if (hit(mx, my, saveX, barY, btnW, btnH) && canSave) {
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
        String current = editor.text();
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
        String current = editor.text();
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

    public void saveIfDirty() {
        String current = editor.text();
        boolean dirty = !current.equals(lastLoadedText == null ? "" : lastLoadedText);
        if (dirty && !saving && !loading) {
            save();
        }
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
        pendingWriteId = net.writeScriptFile(session, state, scriptPath, editor.text());
    }

    private boolean hasSession() {
        Session session = runtime.session();
        return session != null && session.state() == SessionState.CONNECTED;
    }

    private static LanguageProvider languageFor(String path) {
        if (path == null) {
            return LanguageProvider.PLAIN_TEXT;
        }
        String p = path.trim().toLowerCase();
        if (p.endsWith(".ts") || p.endsWith(".tsx")) {
            return TS;
        }
        if (p.endsWith(".js") || p.endsWith(".mjs") || p.endsWith(".cjs")) {
            return JS;
        }
        if (p.endsWith(".luau")) {
            return LUAU;
        }
        if (p.endsWith(".java")) {
            return JAVA;
        }
        if (p.endsWith(".glsl") || p.endsWith(".vert") || p.endsWith(".frag")) {
            return GLSL;
        }
        return LanguageProvider.PLAIN_TEXT;
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
