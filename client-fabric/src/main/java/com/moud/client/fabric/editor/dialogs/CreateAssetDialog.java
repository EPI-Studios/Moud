package com.moud.client.fabric.editor.dialogs;

import com.miry.platform.InputConstants;
import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.clipboard.Clipboard;
import com.miry.ui.event.KeyEvent;
import com.miry.ui.event.TextInputEvent;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.StripTabs;
import com.miry.ui.widgets.TextField;
import com.moud.client.fabric.assets.AssetsClient;
import com.moud.client.fabric.assets.MoudTextAssets;
import com.moud.client.fabric.editor.net.EditorNet;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.core.assets.AssetHash;
import com.moud.core.assets.AssetType;
import com.moud.core.assets.ResPath;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CreateAssetDialog {
    private static final int DIALOG_W = 560;
    private static final int DIALOG_H = 280;

    private final EditorRuntime runtime;
    private final StripTabs tabs = new StripTabs();
    private final StripTabs.Style tabStyle = new StripTabs.Style();
    private final TextField nameField = new TextField();
    private boolean open;
    private boolean justOpened;
    private int activeTab;
    private String error;

    public CreateAssetDialog(EditorRuntime runtime) {
        this.runtime = runtime;
    }

    public void open() {
        justOpened = true;
        open = true;
        error = null;
        activeTab = 0;
    }

    public void close() {
        open = false;
        error = null;
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

        Clipboard clipboard = ctx.clipboard();
        if (nameField.isFocused(ctx)) {
            nameField.handleKey(event, clipboard);
            if (event.isPressOrRepeat() && event.key() == InputConstants.KEY_ENTER) {
                create();
            }
            return true;
        }

        if (event.isPressOrRepeat() && event.key() == InputConstants.KEY_TAB) {
            nameField.focus(ctx);
            return true;
        }

        return false;
    }

    public void handleTextInput(UiContext ctx, TextInputEvent event) {
        if (!open || ctx == null || event == null) {
            return;
        }
        if (nameField.isFocused(ctx)) {
            nameField.handleTextInput(event);
        }
    }

    public void render(UiRenderer r, UiContext ctx, Ui ui, Theme theme, int screenW, int screenH) {
        if (!open) {
            return;
        }

        if (justOpened && ctx != null) {
            justOpened = false;
            if (nameField.text() == null || nameField.text().isBlank()) {
                nameField.setText(defaultNameFor(activeTab));
            }
            nameField.focus(ctx);
        }

        int mx = (int) ui.mouse().x;
        int my = (int) ui.mouse().y;
        boolean pressed = ui.input() != null && ui.input().mousePressed();

        r.drawRect(0, 0, screenW, screenH, 0x80000000);

        int dialogW = Math.min(DIALOG_W, Math.max(360, screenW - theme.design.space_lg * 2));
        int dialogH = Math.min(DIALOG_H, Math.max(220, screenH - theme.design.space_lg * 2));
        int dialogX = (screenW - dialogW) / 2;
        int dialogY = (screenH - dialogH) / 2;

        int bg = Theme.toArgb(theme.panelBg);
        int outline = Theme.toArgb(theme.widgetOutline);
        int text = Theme.toArgb(theme.text);
        int muted = Theme.toArgb(theme.textMuted);
        int danger = Theme.toArgb(theme.danger);

        r.drawRoundedRect(dialogX, dialogY, dialogW, dialogH, theme.design.radius_md, bg, theme.design.border_thin, outline);

        int pad = theme.design.space_lg;
        int headerH = 44;
        r.drawText("New File", dialogX + pad, r.baselineForBox(dialogY, headerH), text);
        r.drawText("Creates a file under res:// and opens an editor.", dialogX + pad, r.baselineForBox(dialogY + 22, headerH), muted);

        int tabsH = theme.design.tab_height_md;
        int tabsX = dialogX + pad;
        int tabsY = dialogY + headerH + pad;
        int tabsW = Math.max(1, dialogW - pad * 2);

        tabStyle.containerBg = Theme.toArgb(theme.headerLine);
        tabStyle.tabActiveBg = Theme.toArgb(theme.windowBg);
        tabStyle.tabInactiveBg = Theme.toArgb(theme.headerBg);
        tabStyle.tabHoverBg = Theme.toArgb(theme.widgetHover);
        tabStyle.borderColor = Theme.toArgb(theme.headerLine);
        tabStyle.highlightColor = Theme.toArgb(theme.accent);
        tabStyle.textActive = Theme.toArgb(theme.text);
        tabStyle.textInactive = Theme.toArgb(theme.textMuted);
        tabStyle.equalWidth = true;
        tabStyle.highlightTop = true;
        tabStyle.highlightThickness = 2;

        String[] labels = {"Script", "Shader", "Material", "Text"};
        activeTab = tabs.render(r, ctx, ui.input(), theme, tabsX, tabsY, tabsW, tabsH, labels, activeTab, true, tabStyle);

        int fieldH = theme.design.widget_height_md;
        int fieldX = dialogX + pad;
        int fieldY = tabsY + tabsH + pad;
        int fieldW = Math.max(1, dialogW - pad * 2);

        r.drawText("Name", fieldX, r.baselineForBox(fieldY, 18), muted);
        nameField.render(r, ctx, ui.input(), theme, fieldX, fieldY + 18, fieldW, fieldH, true);

        String hint = hintFor(activeTab);
        if (hint != null) {
            r.drawText(hint, fieldX, r.baselineForBox(fieldY + 18 + fieldH + 8, 18), muted);
        }

        int buttonW = 140;
        int buttonH = theme.design.widget_height_md + theme.design.border_thin * 2;
        int buttonY = dialogY + dialogH - pad - buttonH;
        int createX = dialogX + dialogW - pad - buttonW;
        int cancelX = createX - theme.design.space_sm - buttonW;

        EditorButton.draw(r, theme, "Cancel", cancelX, buttonY, buttonW, buttonH, mx, my, true, text);
        EditorButton.draw(r, theme, "Create", createX, buttonY, buttonW, buttonH, mx, my, true, text);

        int errY = buttonY - 22;
        if (error != null && !error.isBlank()) {
            r.drawText(error, dialogX + pad, r.baselineForBox(errY, 18), danger);
        }

        if (!pressed) {
            return;
        }

        if (mx < dialogX || my < dialogY || mx >= dialogX + dialogW || my >= dialogY + dialogH) {
            close();
            return;
        }

        if (EditorButton.hit(mx, my, cancelX, buttonY, buttonW, buttonH)) {
            close();
            return;
        }
        if (EditorButton.hit(mx, my, createX, buttonY, buttonW, buttonH)) {
            create();
        }
    }

    private void create() {
        EditorState state = runtime.state();
        EditorNet net = runtime.net();
        Session session = runtime.session();
        if (state == null || net == null || session == null || session.state() != SessionState.CONNECTED) {
            error = "Not connected";
            return;
        }

        String raw = nameField.text() == null ? "" : nameField.text().trim();
        String base = normalizeBaseName(raw);
        boolean luauScript = isLuauScriptName(raw);
        if (base == null || base.isBlank()) {
            error = "Invalid name";
            return;
        }

        String primaryPath = primaryPathFor(activeTab, base, luauScript);
        if (primaryPath == null) {
            error = "Unsupported type";
            return;
        }

        if (existsInManifest(primaryPath)) {
            error = "Already exists: " + primaryPath;
            return;
        }

        if (activeTab == 2) {
            String shaderPath = shaderPathFor(base);
            if (shaderPath != null && !shaderPath.equals(primaryPath) && existsInManifest(shaderPath)) {
                error = "Already exists: " + shaderPath;
                return;
            }
        }

        if (activeTab == 0) {
            String script = scriptTemplate(base, luauScript);
            net.writeScriptFile(session, state, primaryPath, script);
            runtime.openScriptEditor(0L, primaryPath);
            requestManifest();
            close();
            return;
        }

        AssetsClient assets = runtime.assets();
        if (assets == null) {
            error = "Assets client missing";
            return;
        }

        try {
            if (activeTab == 1) {
                String shader = shaderTemplate();
                AssetHash hash = uploadText(assets, session, primaryPath, shader);
                runtime.openTextAssetEditor(primaryPath, hash, shader);
            } else if (activeTab == 2) {
                String shaderPath = shaderPathFor(base);
                if (shaderPath == null) {
                    error = "Invalid shader path";
                    return;
                }
                String shader = shaderTemplate();
                String mat = materialTemplate(shaderPath);
                uploadText(assets, session, shaderPath, shader);
                AssetHash hash = uploadText(assets, session, primaryPath, mat);
                runtime.openTextAssetEditor(primaryPath, hash, mat);
            } else if (activeTab == 3) {
                String txt = "";
                AssetHash hash = uploadText(assets, session, primaryPath, txt);
                runtime.openTextAssetEditor(primaryPath, hash, txt);
            }
        } catch (Exception e) {
            error = e.getMessage() == null ? "Create failed" : e.getMessage();
            return;
        }

        requestManifest();
        close();
    }

    private AssetHash uploadText(AssetsClient assets, Session session, String resPath, String text) {
        byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
        AssetHash hash = AssetHash.sha256(bytes);
        assets.upload(session, new ResPath(resPath), bytes, AssetType.TEXT);
        MoudTextAssets.overrideText(resPath, text);
        return hash;
    }

    private void requestManifest() {
        AssetsClient assets = runtime.assets();
        Session session = runtime.session();
        if (assets != null && session != null && session.state() == SessionState.CONNECTED) {
            assets.requestManifest(session);
        }
    }

    private static String primaryPathFor(int tab, String base, boolean luauScript) {
        return switch (tab) {
            case 0 -> "res://scripts/" + base + (luauScript ? ".luau" : ".js");
            case 1 -> shaderPathFor(base);
            case 2 -> "res://materials/" + base + ".moudmat";
            case 3 -> "res://text/" + base + ".txt";
            default -> null;
        };
    }

    private static String shaderPathFor(String base) {
        return "res://shaders/" + base + ".moudshader";
    }

    private boolean existsInManifest(String resPath) {
        if (resPath == null || resPath.isBlank()) {
            return false;
        }
        List<String> textAssets = MoudTextAssets.textAssetPaths();
        if (textAssets == null || textAssets.isEmpty()) {
            return false;
        }
        for (String p : textAssets) {
            if (resPath.equals(p)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeBaseName(String raw) {
        if (raw == null) {
            return null;
        }
        String in = raw.trim();
        if (in.isEmpty()) {
            return null;
        }
        in = in.replace('\\', '_').replace('/', '_');
        int dot = in.lastIndexOf('.');
        if (dot > 0) {
            in = in.substring(0, dot);
        }

        StringBuilder out = new StringBuilder(in.length());
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            if ((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-') {
                out.append(c);
            } else if (Character.isWhitespace(c)) {
                out.append('_');
            }
        }
        String v = out.toString();
        if (v.isBlank()) {
            return null;
        }
        if (v.length() > 64) {
            v = v.substring(0, 64);
        }
        return v;
    }

    private static String defaultNameFor(int tab) {
        return switch (tab) {
            case 0 -> "player";
            case 1 -> "shader";
            case 2 -> "material";
            default -> "note";
        };
    }

    private static String hintFor(int tab) {
        return switch (tab) {
            case 0 -> "Creates res://scripts/<name>.js or .luau";
            case 1 -> "Creates res://shaders/<name>.moudshader";
            case 2 -> "Creates res://materials/<name>.moudmat (+ matching shader)";
            default -> "Creates res://text/<name>.txt";
        };
    }

    private static String scriptTemplate(String name, boolean luauScript) {
        String n = name == null ? "Script" : name;
        String template = luauScript ? "new_script.luau" : "new_script.js";
        return loadTemplate(template).replace("{{name}}", n);
    }

    private static boolean isLuauScriptName(String raw) {
        if (raw == null) {
            return false;
        }
        return raw.trim().toLowerCase().endsWith(".luau");
    }

    private static String shaderTemplate() {
        return loadTemplate("new_shader.moudshader");
    }

    private static String materialTemplate(String shaderPath) {
        String sp = shaderPath == null ? "res://shaders/unnamed.moudshader" : shaderPath.trim();
        return loadTemplate("new_material.moudmat").replace("{{shader}}", sp);
    }

    private static String loadTemplate(String fileName) {
        String path = "/assets/moud/templates/" + fileName;
        try (var in = CreateAssetDialog.class.getResourceAsStream(path)) {
            if (in == null) return "";
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static final class EditorButton {
        static void draw(UiRenderer r,
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

        static boolean hit(int mx, int my, int x, int y, int w, int h) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }
}
