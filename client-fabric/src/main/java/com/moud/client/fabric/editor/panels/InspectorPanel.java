package com.moud.client.fabric.editor.panels;

import com.miry.platform.InputConstants;
import com.miry.ui.clipboard.Clipboard;
import com.miry.ui.PanelContext;
import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.event.KeyEvent;
import com.miry.ui.event.TextInputEvent;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.ColorPicker;
import com.miry.ui.widgets.ContextMenu;
import com.miry.ui.widgets.DraggableNumberField;
import com.miry.ui.widgets.StripTabs;
import com.miry.ui.widgets.TextField;
import com.miry.ui.theme.Icon;
import com.moud.client.fabric.editor.net.EditorNet;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.editor.util.EditorUiUtil;
import com.moud.client.fabric.assets.MoudTextAssets;
import com.moud.client.fabric.render.MoudTextures;
import com.moud.client.fabric.util.ParseUtils;
import com.moud.core.NodeTypeDef;
import com.moud.core.PropertyDef;
import com.moud.core.PropertyType;
import com.moud.core.assets.ResPath;
import com.moud.core.math.Transform;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.session.Session;
import com.miry.ui.util.MathUtils;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.*;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class InspectorPanel extends Panel {
    private final EditorRuntime runtime;
    private final MaterialEditor materialEditor;

    private final StripTabs dockTabs = new StripTabs();
    private final StripTabs.Style dockTabStyle = new StripTabs.Style();

    private final TextField propertyFilter = new TextField();
    private final TextField renameField = new TextField();

    private final Map<String, TextField> stringFields = new HashMap<>();
    private final Map<String, DraggableNumberField> numberFields = new HashMap<>();
    private final Map<String, PropertyType> numberFieldTypes = new HashMap<>();
    private final Map<String, Float> pendingNumbers = new HashMap<>();
    private final Map<String, Float> lastSentNumbers = new HashMap<>();
    private final Map<String, Long> lastSentNumberAtMs = new HashMap<>();

    private final Map<String, Boolean> groupExpanded = new HashMap<>();
    private final ColorPicker fogColorPicker = new ColorPicker();
    private final ColorPicker tintColorPicker = new ColorPicker();
    private boolean fogColorPickerOpen = false;
    private boolean tintColorPickerOpen = false;

    private final ContextMenu assetMenu = new ContextMenu();
    private long assetMenuNodeId;
    private String assetMenuKey;

    private boolean syncingNumbers;
    private long lastSelectedId;
    private String lastSelectedTypeId = "";

    private static final long DRAG_NUMBER_SEND_INTERVAL_MS = 50;

    public InspectorPanel(EditorRuntime runtime) {
        super("");
        this.runtime = runtime;
        this.materialEditor = new MaterialEditor(runtime);
        groupExpanded.put("Transform", true);
    }

    public void handleKey(UiContext ctx, KeyEvent e) {
        if (ctx == null || e == null) {
            return;
        }

        Clipboard clipboard = ctx.clipboard();
        if (propertyFilter.isFocused(ctx)) {
            propertyFilter.handleKey(e, clipboard);
            return;
        }

        if (renameField.isFocused(ctx)) {
            renameField.handleKey(e, clipboard);
            if (e.isPressOrRepeat() && e.key() == InputConstants.KEY_ENTER) {
                commitRename();
            }
            return;
        }

        for (var entry : stringFields.entrySet()) {
            TextField tf = entry.getValue();
            if (tf != null && tf.isFocused(ctx)) {
                tf.handleKey(e, clipboard);
                if (e.isPressOrRepeat() && e.key() == InputConstants.KEY_ENTER) {
                    commitStringProperty(entry.getKey(), tf.text());
                }
                return;
            }
        }
        if (materialEditor.handleKey(ctx, e, clipboard)) {
            return;
        }

        for (DraggableNumberField nf : numberFields.values()) {
            if (nf != null && nf.handleKey(ctx, e, clipboard)) {
                return;
            }
        }
    }

    public void handleTextInput(UiContext ctx, TextInputEvent e) {
        if (ctx == null || e == null) {
            return;
        }
        if (propertyFilter.isFocused(ctx)) {
            propertyFilter.handleTextInput(e);
            return;
        }
        if (renameField.isFocused(ctx)) {
            renameField.handleTextInput(e);
            return;
        }
        for (TextField tf : stringFields.values()) {
            if (tf != null && tf.isFocused(ctx)) {
                tf.handleTextInput(e);
                return;
            }
        }
        if (materialEditor.handleTextInput(ctx, e)) {
            return;
        }
        for (DraggableNumberField nf : numberFields.values()) {
            if (nf != null && nf.handleTextInput(ctx, e)) {
                return;
            }
        }
    }

    @Override
    public void render(PanelContext ctx) {
        Ui ui = ctx.ui();
        UiRenderer r = ctx.renderer();
        Theme theme = ui.theme();
        UiContext uiContext = ctx.uiContext();
        boolean interactive = runtime != null && !runtime.uiBlocked();

        int x = ctx.x();
        int y = ctx.y();
        int w = ctx.width();
        int h = ctx.height();

        ui.beginPanel(x, y, w, h);

        int tabH = 26;
        int headerH = 70;
        int cursorY = y;

        renderDockTabs(ui, r, uiContext, theme, x, cursorY, w, tabH, interactive);
        cursorY += tabH;

        EditorState state = runtime.state();
        SceneSnapshot.NodeSnapshot sel = (state == null || state.scene == null) ? null : state.scene.getNode(state.selectedId);
        if (sel == null) {
            int pad = theme.design.space_md;
            r.drawText("No selection", x + pad, r.baselineForBox(cursorY + pad, 22), Theme.toArgb(theme.textMuted));
            ui.endPanel();
            return;
        }

        onSelectionMaybeChanged(sel);
        renderHeader(ui, r, uiContext, theme, x, cursorY, w, headerH, sel, interactive);
        cursorY += headerH;

        int contentX = x;
        int contentY = cursorY;
        int contentW = w;
        int contentH = Math.max(0, y + h - contentY);

        renderProperties(ui, r, uiContext, sel, contentX, contentY, contentW, contentH, interactive);

        flushPendingNumberOps(uiContext, sel.nodeId());
        materialEditor.flushPendingMaterialUploads(uiContext);

        ui.endPanel();
    }

    private void renderDockTabs(Ui ui, UiRenderer r, UiContext uiContext, Theme theme, int x, int y, int w, int h, boolean interactive) {
        var input = interactive ? ui.input() : null;
        dockTabStyle.containerBg = Theme.toArgb(theme.headerLine);
        dockTabStyle.tabActiveBg = Theme.toArgb(theme.windowBg);
        dockTabStyle.tabInactiveBg = Theme.toArgb(theme.headerBg);
        dockTabStyle.tabHoverBg = Theme.toArgb(theme.widgetHover);
        dockTabStyle.borderColor = Theme.toArgb(theme.headerLine);
        dockTabStyle.highlightColor = Theme.toArgb(theme.accent);
        dockTabStyle.textActive = Theme.toArgb(theme.text);
        dockTabStyle.textInactive = Theme.toArgb(theme.textMuted);
        dockTabStyle.equalWidth = true;
        dockTabStyle.highlightTop = true;
        dockTabStyle.highlightThickness = 2;

        String[] labels = new String[]{"Inspector"};
        dockTabs.render(r, uiContext, input, theme, x, y, w, h, labels, 0, true, dockTabStyle);
    }

    private void renderHeader(Ui ui,
                              UiRenderer r,
                              UiContext uiContext,
                              Theme theme,
                              int x,
                              int y,
                              int w,
                              int h,
                              SceneSnapshot.NodeSnapshot sel,
                              boolean interactive) {
        var input = interactive ? ui.input() : null;
        int bg = Theme.toArgb(theme.windowBg);
        r.drawRect(x, y, w, h, bg);
        r.drawRect(x, y + h - 1, w, 1, Theme.toArgb(theme.headerLine));

        int pad = theme.design.space_md;
        int leftX = x + pad;
        int nameH = 18;
        int typeH = 16;

        r.drawText(sel.name(), leftX, r.baselineForBox(y + pad, nameH), Theme.toArgb(theme.text));
        r.drawText(sel.type(), leftX, r.baselineForBox(y + pad + nameH, typeH), Theme.toArgb(theme.disabledFg));

        int renameW = Math.min(220, Math.max(120, w - pad * 2));
        int renameH = 22;
        int renameX = x + w - pad - renameW;
        int renameY = y + pad;
        renameField.render(r, uiContext, input, theme, renameX, renameY, renameW, renameH, true);

        int searchH = 22;
        int searchX = x + pad;
        int searchY = y + h - pad - searchH;
        int searchW = Math.max(1, w - pad * 2);
        propertyFilter.render(r, uiContext, input, theme, searchX, searchY, searchW, searchH, true);
        if ((propertyFilter.text() == null || propertyFilter.text().isEmpty()) && (uiContext == null || !propertyFilter.isFocused(uiContext))) {
            int hint = Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.70f);
            float iconSize = Math.min(theme.design.icon_sm, searchH - 6);
            theme.icons.draw(r, Icon.SEARCH, searchX + 6, searchY + (searchH - iconSize) * 0.5f, iconSize, hint);
            r.drawText("Filter Properties", searchX + 6 + iconSize + 6, r.baselineForBox(searchY, searchH), hint);
        }
    }

    private void renderProperties(Ui ui,
                                  UiRenderer r,
                                  UiContext uiContext,
                                  SceneSnapshot.NodeSnapshot sel,
                                  int x,
                                  int y,
                                  int w,
                                  int h,
                                  boolean interactive) {
        Theme theme = ui.theme();
        int pad = theme.design.space_md;
        int innerX = x + pad;
        int innerW = Math.max(1, w - pad * 2);

        EditorState state = runtime.state();
        NodeTypeDef typeDef = state != null ? state.typesById.get(sel.type()) : null;
        if (typeDef == null) {
            r.drawText("(missing schema)", innerX, r.baselineForBox(y + pad, 18), Theme.toArgb(theme.textMuted));
            return;
        }

        Map<String, String> values = toPropertyMap(sel.properties());
        ArrayList<PropertyDef> props = new ArrayList<>(typeDef.properties().values());
        addBuiltInEditorProperties(typeDef, props);
        props.sort(Comparator
                .comparing(PropertyDef::category)
                .thenComparingInt(PropertyDef::order)
                .thenComparing(PropertyDef::uiLabel)
                .thenComparing(PropertyDef::key));

        int rowH = 22;
        int labelW = 100;

        String scriptPath = values.get("script");
        if (scriptPath != null) {
            scriptPath = scriptPath.trim();
        }
        if (state != null) {
            maybeRequestScriptActions(state, sel.nodeId(), scriptPath);
        }
        int scriptActionRows = estimateScriptActionRows(state, sel.nodeId(), scriptPath);
        int materialParamRows = materialEditor.estimateMaterialParamRows(props, values);

        int contentHeight = estimateContentHeight(props, rowH, scriptActionRows + materialParamRows);
        Ui.ScrollArea area = ui.beginScrollArea(r, "inspectorScroll", x, y, w, h, contentHeight);
        int scrollY = (int) area.scrollY();

        int cursorY = y + pad - scrollY;
        int maxY = y + h + scrollY;

        String filterLower = propertyFilter.text() == null ? "" : propertyFilter.text().trim().toLowerCase(Locale.ROOT);

        cursorY = renderGroupHeader(ui, r, theme, innerX, cursorY, innerW, "Transform");
        boolean transformExpanded = isExpanded("Transform");
        if (transformExpanded) {
            cursorY = renderVec3Row(ui, r, uiContext, theme, sel.nodeId(), typeDef, values, innerX, cursorY, innerW, rowH, labelW, "Position", "x", "y", "z", filterLower);
            cursorY = renderVec3Row(ui, r, uiContext, theme, sel.nodeId(), typeDef, values, innerX, cursorY, innerW, rowH, labelW, "Rotation", "rx", "ry", "rz", filterLower);
            String sizeLabel = "Scale";
            PropertyDef sxDef = typeDef.properties().get("sx");
            if (sxDef != null && "Size".equalsIgnoreCase(sxDef.category())) {
                sizeLabel = "Size";
            }
            cursorY = renderVec3Row(ui, r, uiContext, theme, sel.nodeId(), typeDef, values, innerX, cursorY, innerW, rowH, labelW, sizeLabel, "sx", "sy", "sz", filterLower);
        }

        String lastCategory = null;
        boolean fogColorRendered = false;
        boolean tintColorRendered = false;
        for (PropertyDef prop : props) {
            if (cursorY > maxY) {
                break;
            }
            if (prop == null || prop.key() == null || prop.key().isBlank() || "@type".equals(prop.key())) {
                continue;
            }
            if (transformExpanded && ("sx".equals(prop.key()) || "sy".equals(prop.key()) || "sz".equals(prop.key()))) {
                continue;
            }
            if ("Transform".equals(prop.category())) {
                continue;
            }

            if (!filterLower.isEmpty()) {
                String hay = (prop.uiLabel() + " " + prop.key()).toLowerCase(Locale.ROOT);
                if (!hay.contains(filterLower)) {
                    continue;
                }
            }

            String cat = prop.category() == null ? "" : prop.category();
            if (!cat.equals(lastCategory)) {
                lastCategory = cat;
                cursorY = renderGroupHeader(ui, r, theme, innerX, cursorY, innerW, cat);
            }
            if (!isExpanded(cat)) {
                continue;
            }

            if ("Fog Color".equals(cat) && isFogColorKey(prop.key()) && !fogColorRendered) {
                fogColorRendered = true;
                cursorY = renderFogColorPicker(ui, r, uiContext, theme, sel.nodeId(), values, innerX, cursorY, innerW, rowH, labelW);
                continue;
            }
            if ("Fog Color".equals(cat) && isFogColorKey(prop.key())) {
                continue;
            }

            if ("Color Tint".equals(cat) && isColorTintKey(prop.key()) && !tintColorRendered) {
                tintColorRendered = true;
                cursorY = renderTintColorPicker(ui, r, uiContext, theme, sel.nodeId(), values, innerX, cursorY, innerW, rowH, labelW);
                continue;
            }
            if ("Color Tint".equals(cat) && isColorTintKey(prop.key())) {
                continue;
            }

            String value = values.get(prop.key());
            if (value == null) {
                value = prop.defaultValue() != null ? prop.defaultValue() : "";
            }
            cursorY = renderPropertyRow(ui, r, uiContext, theme, sel.nodeId(), prop, innerX, cursorY, innerW, rowH, labelW, value);
        }

        cursorY = materialEditor.renderMaterialShaderParams(this, ui, r, uiContext, theme, props, values, filterLower, innerX, cursorY, innerW, rowH, labelW, interactive);
        cursorY = renderScriptActions(ui, r, uiContext, theme, state, sel.nodeId(), scriptPath, filterLower, innerX, cursorY, innerW, rowH, interactive);

        ui.endScrollArea(area);

        renderAssetMenu(ui, r, theme, interactive);
        materialEditor.renderMaterialTextureMenu(ui, r, theme, interactive);
    }

    private static void addBuiltInEditorProperties(NodeTypeDef typeDef, List<PropertyDef> props) {
        if (typeDef == null || props == null) {
            return;
        }
        Map<String, PropertyDef> existing = typeDef.properties();
        if (!existing.containsKey("visible")) {
            props.add(new PropertyDef("visible", PropertyType.BOOL, "true", "Visible", "Editor", -1000, Map.of()));
        }
        if (!existing.containsKey("editor_locked")) {
            props.add(new PropertyDef("editor_locked", PropertyType.BOOL, "false", "Locked", "Editor", -999, Map.of()));
        }
        if (!existing.containsKey("solid")) {
            props.add(new PropertyDef("solid", PropertyType.BOOL, "true", "Solid", "Collision", 0, Map.of()));
        }
        if (!existing.containsKey("color_tint_r")) {
            props.add(new PropertyDef("color_tint_r", PropertyType.FLOAT, "1", "R", "Color Tint", 0, Map.of("min", "0", "max", "1", "step", "0.01")));
        }
        if (!existing.containsKey("color_tint_g")) {
            props.add(new PropertyDef("color_tint_g", PropertyType.FLOAT, "1", "G", "Color Tint", 1, Map.of("min", "0", "max", "1", "step", "0.01")));
        }
        if (!existing.containsKey("color_tint_b")) {
            props.add(new PropertyDef("color_tint_b", PropertyType.FLOAT, "1", "B", "Color Tint", 2, Map.of("min", "0", "max", "1", "step", "0.01")));
        }
    }

    private int estimateContentHeight(List<PropertyDef> props, int rowH, int extraRows) {
        int groups = 3;
        int rows = props != null ? Math.max(0, props.size()) : 0;
        int extra = (fogColorPickerOpen || tintColorPickerOpen) ? 180 : 0;
        return 100 + (groups + rows + Math.max(0, extraRows)) * rowH + extra;
    }

    private static int estimateScriptActionRows(EditorState state, long nodeId, String scriptPath) {
        if (state == null || nodeId <= 0L) {
            return 0;
        }
        if (scriptPath == null || scriptPath.isBlank()) {
            return 0;
        }
        EditorState.ScriptActions actions = state.scriptActionsByNode.get(nodeId);
        int actionCount = actions == null || actions.actions == null ? 0 : actions.actions.size();
        return 2 + Math.max(1, actionCount);
    }

    private void maybeRequestScriptActions(EditorState state, long nodeId, String scriptPath) {
        if (state == null || nodeId <= 0L) {
            return;
        }
        if (scriptPath == null || scriptPath.isBlank()) {
            return;
        }

        EditorState.ScriptActions cache = state.scriptActions(nodeId);
        if (cache == null) {
            return;
        }
        String script = scriptPath.trim();
        if (!Objects.equals(cache.scriptPath, script)) {
            cache.scriptPath = script;
            cache.pending = false;
            cache.loaded = false;
            cache.error = null;
            cache.actions = List.of();
        }
        if (cache.pending || cache.loaded) {
            return;
        }

        Session session = runtime.session();
        EditorNet net = runtime.net();
        if (session == null || net == null) {
            return;
        }

        cache.pending = true;
        net.requestScriptActions(session, state, nodeId);
    }

    private int renderScriptActions(Ui ui,
                                   UiRenderer r,
                                   UiContext uiContext,
                                   Theme theme,
                                   EditorState state,
                                   long nodeId,
                                   String scriptPath,
                                   String filterLower,
                                   int x,
                                   int y,
                                   int w,
                                   int rowH,
                                   boolean interactive) {
        if (state == null || nodeId <= 0L) {
            return y;
        }
        if (scriptPath == null || scriptPath.isBlank()) {
            return y;
        }
        if (!filterLower.isEmpty() && !filterLower.contains("script") && !filterLower.contains("action")) {
            return y;
        }

        EditorState.ScriptActions cache = state.scriptActions(nodeId);
        if (cache == null) {
            return y;
        }

        y = renderGroupHeader(ui, r, theme, x, y, w, "Script Actions");
        if (!isExpanded("Script Actions")) {
            return y;
        }

        int btnH = rowH;
        int outline = Theme.toArgb(theme.widgetOutline);
        int text = Theme.toArgb(theme.text);
        int muted = Theme.toArgb(theme.textMuted);

        int refreshW = 90;
        int statusW = Math.max(1, w - refreshW - theme.design.space_sm);
        String status;
        int statusColor = muted;
        if (cache.pending) {
            status = "Loading…";
        } else if (cache.error != null && !cache.error.isBlank()) {
            status = cache.error;
            statusColor = Theme.toArgb(theme.danger);
        } else if (cache.actions == null || cache.actions.isEmpty()) {
            status = "No actions";
        } else {
            status = "Actions: " + cache.actions.size();
        }

        r.drawText(status, x, r.baselineForBox(y, btnH), statusColor);
        boolean refresh = renderButton(ui, r, theme, x + statusW + theme.design.space_sm, y, refreshW, btnH, "Refresh", interactive);
        if (refresh) {
            cache.pending = true;
            cache.loaded = false;
            cache.error = null;
            cache.actions = List.of();
            Session session = runtime.session();
            EditorNet net = runtime.net();
            if (session != null && net != null) {
                net.requestScriptActions(session, state, nodeId);
            }
        }
        y += btnH;

        if (cache.pending) {
            return y;
        }

        List<String> actions = cache.actions == null ? List.of() : cache.actions;
        for (String action : actions) {
            if (action == null || action.isBlank()) {
                continue;
            }
            boolean clicked = renderButton(ui, r, theme, x, y, w, btnH, action, interactive);
            if (clicked) {
                Session session = runtime.session();
                EditorNet net = runtime.net();
                if (session != null && net != null) {
                    net.invokeScriptAction(session, state, nodeId, action);
                }
            }
            y += btnH;
        }

        if (actions.isEmpty() && cache.error == null) {
            r.drawText("(tool=true + actions={...})", x, r.baselineForBox(y, btnH), muted);
            y += btnH;
        }

        return y;
    }

    private static boolean renderButton(Ui ui,
                                        UiRenderer r,
                                        Theme theme,
                                        int x,
                                        int y,
                                        int w,
                                        int h,
                                        String label,
                                        boolean interactive) {
        var input = interactive ? ui.input() : null;
        boolean canInteract = input != null;
        float mx = canInteract ? input.mousePos().x : -1;
        float my = canInteract ? input.mousePos().y : -1;
        boolean hovered = canInteract && mx >= x && my >= y && mx < x + w && my < y + h;

        int bg = hovered ? Theme.toArgb(theme.widgetHover) : Theme.toArgb(theme.widgetBg);
        int outline = Theme.toArgb(theme.widgetOutline);
        int text = Theme.toArgb(theme.text);
        r.drawRoundedRect(x, y + 2, w, h - 4, theme.design.radius_sm, bg, theme.design.border_thin, outline);
        r.drawText(label, x + theme.design.space_sm, r.baselineForBox(y, h), text);

        return hovered && canInteract && input.mousePressed();
    }

    int renderGroupHeader(Ui ui, UiRenderer r, Theme theme, int x, int y, int w, String title) {
        int h = 22;
        int bg = Theme.toArgb(theme.headerBg);
        int hover = Theme.toArgb(theme.widgetHover);
        boolean expanded = isExpanded(title);

        boolean canInteract = (runtime == null || !runtime.uiBlocked()) && ui.input() != null;
        float mx = canInteract ? ui.mouse().x : -1;
        float my = canInteract ? ui.mouse().y : -1;
        boolean hovered = canInteract && mx >= x && my >= y && mx < x + w && my < y + h;
        r.drawRect(x, y, w, h, hovered ? hover : bg);

        r.drawText(title, x + 18, r.baselineForBox(y, h), Theme.toArgb(theme.text));
        float iconSize = Math.min(theme.design.icon_sm, h - 6);
        Icon icon = expanded ? Icon.CHEVRON_DOWN : Icon.CHEVRON_RIGHT;
        theme.icons.draw(r, icon, x + 4, y + (h - iconSize) * 0.5f, iconSize, Theme.toArgb(theme.textMuted));

        if (hovered && canInteract && ui.input().mousePressed()) {
            groupExpanded.put(title, !expanded);
        }
        return y + h;
    }

    private int renderPropertyRow(Ui ui,
                                  UiRenderer r,
                                  UiContext uiContext,
                                  Theme theme,
                                  long nodeId,
                                  PropertyDef prop,
                                  int x,
                                  int y,
                                  int w,
                                  int rowH,
                                  int labelW,
                                  String value) {
        int valueX = x + labelW + theme.design.space_sm;
        int valueW = Math.max(1, w - (valueX - x));

        r.drawText(prop.uiLabel(), x, r.baselineForBox(y, rowH), Theme.toArgb(theme.textMuted));

        if (prop.type() == PropertyType.BOOL) {
            boolean interactive = runtime != null && !runtime.uiBlocked();
            boolean b = ParseUtils.parseBool(value, ParseUtils.parseBool(prop.defaultValue(), false));
            renderBool(ui, r, theme, valueX, y, valueW, rowH, b, interactive, next -> commitBoolProperty(nodeId, prop.key(), next));
            return y + rowH;
        }

        if (prop.type() == PropertyType.INT || prop.type() == PropertyType.FLOAT) {
            DraggableNumberField nf = numberField(prop.key(), prop, ParseUtils.parseFloat(value, 0.0f));
            syncNumberValue(uiContext, nf, ParseUtils.parseFloat(value, nf.value()));
            var input = (runtime != null && !runtime.uiBlocked()) ? ui.input() : null;
            nf.render(r, uiContext, input, theme, valueX, y + 2, valueW, rowH - 4, true);
            return y + rowH;
        }

        boolean isScriptPath = "script".equals(prop.key());
        boolean showAttachScript = isScriptPath;
        boolean hasScript = isScriptPath && value != null && !value.trim().isEmpty();
        boolean isImageAsset = isAssetKind(prop, "image");
        boolean isShaderAsset = isAssetKind(prop, "shader");
        boolean isMaterialAsset = isAssetKind(prop, "material");
        boolean isTextAsset = isAssetKind(prop, "text");
        boolean isAnyAsset = isImageAsset || isShaderAsset || isMaterialAsset || isTextAsset;

        int iconBtnW = Math.max(18, rowH - 4);
        int iconBtnH = rowH - 4;
        int iconGap = theme.design.space_xs;
        int iconCount = (isAnyAsset ? 1 : 0) + (showAttachScript ? 1 : 0) + (hasScript ? 1 : 0);
        int iconsW = iconCount == 0 ? 0 : (iconCount * iconBtnW + (iconCount - 1) * iconGap);
        int fieldToIconsGap = iconCount == 0 ? 0 : iconGap;

        int fieldW = Math.max(1, valueW - iconsW - fieldToIconsGap);
        TextField tf = stringFields.computeIfAbsent(prop.key(), k -> new TextField());
        if (uiContext == null || !tf.isFocused(uiContext)) {
            tf.setText(value == null ? "" : value);
            tf.setCursorPos(tf.text().length());
        }
        var input = (runtime != null && !runtime.uiBlocked()) ? ui.input() : null;
        tf.render(r, uiContext, input, theme, valueX, y + 2, fieldW, rowH - 4, true);

        int btnY = y + 2;
        int btnX = valueX + fieldW + fieldToIconsGap;

        if (isAnyAsset) {
            Icon icon = isImageAsset ? Icon.IMAGE : (isShaderAsset ? Icon.CODE : Icon.TEXT);
            int menuX = btnX;
            int menuY = btnY + iconBtnH;
            renderIconButton(ui, r, theme, menuX, btnY, iconBtnW, iconBtnH, icon, input != null, () -> toggleAssetMenu(menuX, menuY, nodeId, prop));
            btnX += iconBtnW + iconGap;
        }
        if (showAttachScript) {
            renderIconButton(ui, r, theme, btnX, btnY, iconBtnW, iconBtnH, Icon.ADD, input != null, () -> attachScriptFromFile(nodeId));
            btnX += iconBtnW + iconGap;
        }
        if (hasScript) {
            String script = value == null ? "" : value;
            renderIconButton(ui, r, theme, btnX, btnY, iconBtnW, iconBtnH, Icon.CODE, input != null, () -> runtime.openScriptEditor(nodeId, script));
        }
        return y + rowH;
    }

    private void attachScriptFromFile(long nodeId) {
        EditorState state = runtime.state();
        EditorNet net = runtime.net();
        Session session = runtime.session();
        if (state == null || net == null || session == null) {
            runtime.requestToast("Attach failed: not connected", true, 3500);
            return;
        }

        try {
            String osPath = TinyFileDialogs.tinyfd_openFileDialog(
                    "Attach Script (.js)",
                    "",
                    null,
                    "JavaScript (.js)",
                    false
            );
            if (osPath == null || osPath.isBlank()) {
                return;
            }

            File file = new File(osPath);
            if (!file.exists() || !file.isFile()) {
                runtime.requestToast("Script file not found", true, 4500);
                return;
            }

            String filename = file.getName();
            if (filename == null || filename.isBlank()) {
                runtime.requestToast("Invalid filename", true, 4500);
                return;
            }
            if (!filename.toLowerCase(Locale.ROOT).endsWith(".js")) {
                filename = filename + ".js";
            }

            String scriptPath = "res://scripts/" + filename;
            try {
                new ResPath(scriptPath);
            } catch (Exception ignored) {
                scriptPath = "res://scripts/node_" + nodeId + ".js";
            }

            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            net.writeScriptFile(session, state, scriptPath, content);

            net.sendOps(session, state, List.of(new SceneOp.SetProperty(nodeId, "script", scriptPath)));
            runtime.requestToast("Attached script: " + scriptPath, false, 2500);
            runtime.openScriptEditor(nodeId, scriptPath);
        } catch (Exception e) {
            String msg = e.getMessage();
            runtime.requestToast("Attach failed" + (msg == null || msg.isBlank() ? "" : ": " + msg), true, 6000);
        }
    }

    static boolean isAssetKind(PropertyDef prop, String kind) {
        if (prop == null || prop.editorHints() == null || kind == null) {
            return false;
        }
        String asset = prop.editorHints().get("asset");
        return asset != null && asset.equalsIgnoreCase(kind);
    }

    private void toggleAssetMenu(int x, int y, long nodeId, PropertyDef prop) {
        if (prop == null || prop.key() == null || prop.key().isBlank()) {
            return;
        }
        if (assetMenu.isOpen() && nodeId == assetMenuNodeId && Objects.equals(assetMenuKey, prop.key())) {
            assetMenu.close();
            return;
        }
        assetMenuNodeId = nodeId;
        assetMenuKey = prop.key();
        buildAssetMenu(prop);
        EditorUiUtil.openMenuClamped(assetMenu, runtime, x, y);
    }

    private void buildAssetMenu(PropertyDef prop) {
        assetMenu.clear();
        String key = prop.key();
        String dv = prop.defaultValue() == null ? "" : prop.defaultValue();
        String kind = prop.editorHints() == null ? "" : prop.editorHints().getOrDefault("asset", "");
        String kindLower = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);

        assetMenu.addItem("Default", () -> commitStringProperty(key, dv));
        assetMenu.addItem("Clear", () -> commitStringProperty(key, ""));

        if ("image".equals(kindLower)) {
            assetMenu.addSeparator();
            assetMenu.addItem(MoudTextures.WHITE_ID.toString(), () -> commitStringProperty(key, MoudTextures.WHITE_ID.toString()));

            List<String> images = MoudTextures.imageAssetPaths();
            if (images.isEmpty()) {
                assetMenu.addSeparator();
                assetMenu.addItem("(No res:// images)", () -> {
                });
                return;
            }

            assetMenu.addSeparator();
            for (String path : images) {
                if (path == null || path.isBlank()) {
                    continue;
                }
                assetMenu.addItem(path, () -> commitStringProperty(key, path));
            }
            return;
        }

        List<String> texts = MoudTextAssets.textAssetPaths();
        if (texts.isEmpty()) {
            assetMenu.addSeparator();
            assetMenu.addItem("(No res:// text assets)", () -> {
            });
            return;
        }

        String suffixFilter = null;
        if ("shader".equals(kindLower)) {
            suffixFilter = ".moudshader";
        } else if ("material".equals(kindLower)) {
            suffixFilter = ".moudmat";
        }

        boolean any = false;
        assetMenu.addSeparator();
        for (String path : texts) {
            if (path == null || path.isBlank()) {
                continue;
            }
            if (suffixFilter != null && !path.toLowerCase(Locale.ROOT).endsWith(suffixFilter)) {
                continue;
            }
            any = true;
            assetMenu.addItem(path, () -> commitStringProperty(key, path));
        }
        if (!any && suffixFilter != null) {
            assetMenu.addItem("(No res:// " + suffixFilter + ")", () -> {
            });
        }
    }

    private void renderAssetMenu(Ui ui, UiRenderer r, Theme theme, boolean interactive) {
        if (ui == null || r == null || theme == null) {
            return;
        }
        if (!assetMenu.isOpen()) {
            return;
        }
        var input = interactive ? ui.input() : null;
        int itemH = 22;
        if (input != null) {
            assetMenu.updateFromInput(input, theme, itemH);
            EditorUiUtil.clampOpenMenuToScreen(assetMenu, runtime);
        }
        assetMenu.render(r, theme, itemH,
                Theme.toArgb(theme.panelBg),
                Theme.toArgb(theme.widgetHover),
                Theme.toArgb(theme.text),
                assetMenu.hoverIndex());

        if (!interactive || input == null || !input.mousePressed()) {
            return;
        }
        assetMenu.handleClick((int) ui.mouse().x, (int) ui.mouse().y, itemH);
    }

    static void renderIconButton(Ui ui,
                                         UiRenderer r,
                                         Theme theme,
                                         int x,
                                         int y,
                                         int w,
                                         int h,
                                         Icon icon,
                                         boolean interactive,
                                         Runnable action) {
        var input = ui != null ? ui.input() : null;
        boolean canInteract = interactive && input != null;
        float mx = canInteract ? input.mousePos().x : -1;
        float my = canInteract ? input.mousePos().y : -1;
        boolean hovered = canInteract && mx >= x && my >= y && mx < x + w && my < y + h;

        int bg = hovered ? Theme.toArgb(theme.widgetHover) : Theme.toArgb(theme.widgetBg);
        int outline = Theme.toArgb(theme.widgetOutline);
        r.drawRoundedRect(x, y, w, h, theme.design.radius_sm, bg, theme.design.border_thin, outline);
        float iconSize = Math.min(theme.design.icon_sm, h - 6);
        int iconCol = Theme.toArgb(theme.textMuted);
        theme.icons.draw(r, icon, x + (w - iconSize) * 0.5f, y + (h - iconSize) * 0.5f, iconSize, iconCol);

        if (hovered && canInteract && input.mouseReleased() && action != null) {
            action.run();
        }
    }

    private int renderVec3Row(Ui ui,
                              UiRenderer r,
                              UiContext uiContext,
                              Theme theme,
                              long nodeId,
                              NodeTypeDef typeDef,
                              Map<String, String> values,
                              int x,
                              int y,
                              int w,
                              int rowH,
                              int labelW,
                              String label,
                              String kx,
                              String ky,
                              String kz,
                              String filterLower) {
        boolean hasAny = values.containsKey(kx) || values.containsKey(ky) || values.containsKey(kz);
        if (!hasAny) {
            return y;
        }
        if (!filterLower.isEmpty()) {
            StringBuilder sb = new StringBuilder(64);
            sb.append(label).append(' ').append(kx).append(' ').append(ky).append(' ').append(kz);
            if (typeDef != null) {
                PropertyDef dx = typeDef.properties().get(kx);
                PropertyDef dy = typeDef.properties().get(ky);
                PropertyDef dz = typeDef.properties().get(kz);
                if (dx != null && dx.uiLabel() != null) sb.append(' ').append(dx.uiLabel());
                if (dy != null && dy.uiLabel() != null) sb.append(' ').append(dy.uiLabel());
                if (dz != null && dz.uiLabel() != null) sb.append(' ').append(dz.uiLabel());
            }
            String hay = sb.toString().toLowerCase(Locale.ROOT);
            if (!hay.contains(filterLower)) {
                return y;
            }
        }

        r.drawText(label, x, r.baselineForBox(y, rowH), Theme.toArgb(theme.textMuted));

        int valueX = x + labelW + theme.design.space_sm;
        int valueW = Math.max(1, w - (valueX - x));
        int gap = theme.design.space_xs;
        int eachW = Math.max(1, (valueW - gap * 2) / 3);
        int fieldH = rowH - 4;
        int fy = y + 2;

        PropertyDef dx = typeDef != null ? typeDef.properties().get(kx) : null;
        PropertyDef dy = typeDef != null ? typeDef.properties().get(ky) : null;
        PropertyDef dz = typeDef != null ? typeDef.properties().get(kz) : null;

        renderPrefixedNumber(ui, r, uiContext, theme, nodeId, dx, kx, valueX, fy, eachW, fieldH, "x", values.get(kx));
        renderPrefixedNumber(ui, r, uiContext, theme, nodeId, dy, ky, valueX + eachW + gap, fy, eachW, fieldH, "y", values.get(ky));
        int lastX = valueX + (eachW + gap) * 2;
        renderPrefixedNumber(ui, r, uiContext, theme, nodeId, dz, kz, lastX, fy, valueX + valueW - lastX, fieldH, "z", values.get(kz));

        return y + rowH;
    }

    private void renderPrefixedNumber(Ui ui,
                                      UiRenderer r,
                                      UiContext uiContext,
                                      Theme theme,
                                      long nodeId,
                                      PropertyDef def,
                                      String key,
                                      int x,
                                      int y,
                                      int w,
                                      int h,
                                      String prefix,
                                      String rawValue) {
        DraggableNumberField nf = numberField(key, def, ParseUtils.parseFloat(rawValue, 0.0f));
        syncNumberValue(uiContext, nf, ParseUtils.parseFloat(rawValue, nf.value()));
        var input = (runtime != null && !runtime.uiBlocked()) ? ui.input() : null;
        nf.render(r, uiContext, input, theme, x, y, w, h, true);

        int muted = Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.70f);
        r.drawText(prefix, x + 4, r.baselineForBox(y, h), muted);
    }

    private DraggableNumberField numberField(String key, PropertyDef def, float initial) {
        return numberFields.computeIfAbsent(key, k -> {
            float min = -1_000_000.0f;
            float max = 1_000_000.0f;
            DraggableNumberField nf = new DraggableNumberField(initial, min, max);
            if (def != null) {
                numberFieldTypes.put(key, def.type());
            }
            if (def != null && def.editorHints() != null) {
                String step = def.editorHints().get("step");
                if (step != null) {
                    nf.setSnapStep(ParseUtils.parseFloat(step, 1.0f));
                }
                float rangeMin = min;
                float rangeMax = max;
                String minS = def.editorHints().get("min");
                if (minS != null) {
                    rangeMin = ParseUtils.parseFloat(minS, rangeMin);
                }
                String maxS = def.editorHints().get("max");
                if (maxS != null) {
                    rangeMax = ParseUtils.parseFloat(maxS, rangeMax);
                }
                nf.setRange(rangeMin, rangeMax);
            }
            nf.setListener(v -> {
                if (!syncingNumbers) {
                    pendingNumbers.put(key, v);
                }
            });
            return nf;
        });
    }

    private void syncNumberValue(UiContext uiContext, DraggableNumberField nf, float value) {
        if (nf == null) {
            return;
        }
        if (nf.isEditing()) {
            return;
        }
        if (uiContext != null && uiContext.pointer().isCaptured(nf.id())) {
            return;
        }
        syncingNumbers = true;
        try {
            nf.setValue(value);
        } finally {
            syncingNumbers = false;
        }
    }

    private void flushPendingNumberOps(UiContext uiContext, long nodeId) {
        if (pendingNumbers.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        ArrayList<String> keys = new ArrayList<>(pendingNumbers.keySet());
        for (String key : keys) {
            DraggableNumberField nf = numberFields.get(key);
            boolean captured = nf != null && uiContext != null && uiContext.pointer().isCaptured(nf.id());
            if (captured) {
                long last = lastSentNumberAtMs.getOrDefault(key, 0L);
                if ((now - last) < DRAG_NUMBER_SEND_INTERVAL_MS) {
                    continue;
                }
            }

            float v = pendingNumbers.get(key);
            PropertyType type = numberFieldTypes.get(key);
            float cmp = type == PropertyType.INT ? Math.round(v) : v;
            Float last = lastSentNumbers.get(key);
            if (last != null && Math.abs(last - cmp) < 1e-6f) {
                pendingNumbers.remove(key);
                continue;
            }
            lastSentNumbers.put(key, cmp);
            lastSentNumberAtMs.put(key, now);
            pendingNumbers.remove(key);
            commitNumberProperty(nodeId, key, v);
        }
    }

    private static boolean isFogColorKey(String key) {
        return "fog_color_r".equals(key) || "fog_color_g".equals(key) || "fog_color_b".equals(key);
    }

    private static boolean isColorTintKey(String key) {
        return "color_tint_r".equals(key) || "color_tint_g".equals(key) || "color_tint_b".equals(key);
    }

    private int renderFogColorPicker(Ui ui,
                                     UiRenderer r,
                                     UiContext uiContext,
                                     Theme theme,
                                     long nodeId,
                                     Map<String, String> values,
                                     int x,
                                     int y,
                                     int w,
                                     int rowH,
                                     int labelW) {
        float fogR = ParseUtils.parseFloat(values.get("fog_color_r"), 0.5f);
        float fogG = ParseUtils.parseFloat(values.get("fog_color_g"), 0.5f);
        float fogB = ParseUtils.parseFloat(values.get("fog_color_b"), 0.5f);

        int valueX = x + labelW + theme.design.space_sm;
        int swatchW = Math.max(1, w - (valueX - x));

        r.drawText("Fog Color", x, r.baselineForBox(y, rowH), Theme.toArgb(theme.textMuted));

        int swatchH = rowH - 4;
        int swatchY = y + 2;
        int swatchColor = 0xFF000000
                | (Math.round(MathUtils.clamp01(fogR) * 255) << 16)
                | (Math.round(MathUtils.clamp01(fogG) * 255) << 8)
                | Math.round(MathUtils.clamp01(fogB) * 255);
        r.drawRoundedRect(valueX, swatchY, swatchW, swatchH, theme.design.radius_sm, swatchColor,
                theme.design.border_thin, Theme.toArgb(theme.widgetOutline));

        var input = (runtime != null && !runtime.uiBlocked()) ? ui.input() : null;
        boolean canInteract = input != null;
        float mx = canInteract ? input.mousePos().x : -1;
        float my = canInteract ? input.mousePos().y : -1;
        boolean hovered = canInteract && mx >= valueX && my >= swatchY && mx < valueX + swatchW && my < swatchY + swatchH;
        if (hovered && canInteract && input.mousePressed()) {
            fogColorPickerOpen = !fogColorPickerOpen;
            if (fogColorPickerOpen) {
                tintColorPickerOpen = false;
                float[] hsv = rgbToHsv(fogR, fogG, fogB);
                fogColorPicker.setHsva(hsv[0], hsv[1], hsv[2], 1.0f);
            }
        }

        int cursorY = y + rowH;

        if (fogColorPickerOpen) {
            int pickerH = 160;
            int pickerW = Math.max(200, w);
            boolean changed = fogColorPicker.render(r, input, theme, x, cursorY, pickerW, pickerH, true);
            if (changed) {
                int argb = fogColorPicker.toArgb();
                float newR = ((argb >> 16) & 0xFF) / 255.0f;
                float newG = ((argb >> 8) & 0xFF) / 255.0f;
                float newB = (argb & 0xFF) / 255.0f;
                commitFogColor(nodeId, newR, newG, newB);
            }
            cursorY += pickerH + theme.design.space_sm;
        }

        return cursorY;
    }

    private int renderTintColorPicker(Ui ui,
                                      UiRenderer r,
                                      UiContext uiContext,
                                      Theme theme,
                                      long nodeId,
                                      Map<String, String> values,
                                      int x,
                                      int y,
                                      int w,
                                      int rowH,
                                      int labelW) {
        float tintR = ParseUtils.parseFloat(values.get("color_tint_r"), 1.0f);
        float tintG = ParseUtils.parseFloat(values.get("color_tint_g"), 1.0f);
        float tintB = ParseUtils.parseFloat(values.get("color_tint_b"), 1.0f);

        int valueX = x + labelW + theme.design.space_sm;
        int swatchW = Math.max(1, w - (valueX - x));

        r.drawText("Color Tint", x, r.baselineForBox(y, rowH), Theme.toArgb(theme.textMuted));

        int swatchH = rowH - 4;
        int swatchY = y + 2;
        int swatchColor = 0xFF000000
                | (Math.round(MathUtils.clamp01(tintR) * 255) << 16)
                | (Math.round(MathUtils.clamp01(tintG) * 255) << 8)
                | Math.round(MathUtils.clamp01(tintB) * 255);
        r.drawRoundedRect(valueX, swatchY, swatchW, swatchH, theme.design.radius_sm, swatchColor,
                theme.design.border_thin, Theme.toArgb(theme.widgetOutline));

        var input = (runtime != null && !runtime.uiBlocked()) ? ui.input() : null;
        boolean canInteract = input != null;
        float mx = canInteract ? input.mousePos().x : -1;
        float my = canInteract ? input.mousePos().y : -1;
        boolean hovered = canInteract && mx >= valueX && my >= swatchY && mx < valueX + swatchW && my < swatchY + swatchH;
        if (hovered && canInteract && input.mousePressed()) {
            tintColorPickerOpen = !tintColorPickerOpen;
            if (tintColorPickerOpen) {
                fogColorPickerOpen = false;
                float[] hsv = rgbToHsv(tintR, tintG, tintB);
                tintColorPicker.setHsva(hsv[0], hsv[1], hsv[2], 1.0f);
            }
        }

        int cursorY = y + rowH;

        if (tintColorPickerOpen) {
            int pickerH = 160;
            int pickerW = Math.max(200, w);
            boolean changed = tintColorPicker.render(r, input, theme, x, cursorY, pickerW, pickerH, true);
            if (changed) {
                int argb = tintColorPicker.toArgb();
                float newR = ((argb >> 16) & 0xFF) / 255.0f;
                float newG = ((argb >> 8) & 0xFF) / 255.0f;
                float newB = (argb & 0xFF) / 255.0f;
                commitTintColor(nodeId, newR, newG, newB);
            }
            cursorY += pickerH + theme.design.space_sm;
        }

        return cursorY;
    }

    private void commitFogColor(long nodeId, float r, float g, float b) {
        EditorState state = runtime.state();
        EditorNet net = runtime.net();
        Session session = runtime.session();
        if (net == null || session == null || state == null) {
            return;
        }
        net.sendOps(session, state, List.of(
                new SceneOp.SetProperty(nodeId, "fog_color_r", ParseUtils.trimFloat(r)),
                new SceneOp.SetProperty(nodeId, "fog_color_g", ParseUtils.trimFloat(g)),
                new SceneOp.SetProperty(nodeId, "fog_color_b", ParseUtils.trimFloat(b))
        ));
    }

    private void commitTintColor(long nodeId, float r, float g, float b) {
        EditorState state = runtime.state();
        EditorNet net = runtime.net();
        Session session = runtime.session();
        if (net == null || session == null || state == null) {
            return;
        }
        net.sendOps(session, state, List.of(
                new SceneOp.SetProperty(nodeId, "color_tint_r", ParseUtils.trimFloat(r)),
                new SceneOp.SetProperty(nodeId, "color_tint_g", ParseUtils.trimFloat(g)),
                new SceneOp.SetProperty(nodeId, "color_tint_b", ParseUtils.trimFloat(b))
        ));
    }

    private static float[] rgbToHsv(float r, float g, float b) {
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float h = 0.0f;
        if (delta > 0.0f) {
            if (max == r) {
                h = ((g - b) / delta) % 6.0f;
            } else if (max == g) {
                h = ((b - r) / delta) + 2.0f;
            } else {
                h = ((r - g) / delta) + 4.0f;
            }
            h /= 6.0f;
            if (h < 0.0f) h += 1.0f;
        }
        float s = max > 0.0f ? delta / max : 0.0f;
        return new float[]{h, s, max};
    }

    boolean isExpanded(String group) {
        return groupExpanded.getOrDefault(group == null ? "" : group, true);
    }

    private void onSelectionMaybeChanged(SceneSnapshot.NodeSnapshot sel) {
        if (sel == null) {
            return;
        }
        if (lastSelectedId == sel.nodeId() && Objects.equals(lastSelectedTypeId, sel.type())) {
            return;
        }
        lastSelectedId = sel.nodeId();
        lastSelectedTypeId = sel.type();
        renameField.setText(sel.name() == null ? "" : sel.name());
        renameField.setCursorPos(renameField.text().length());
        stringFields.clear();
        numberFields.clear();
        numberFieldTypes.clear();
        pendingNumbers.clear();
        lastSentNumbers.clear();
        lastSentNumberAtMs.clear();
        materialEditor.onSelectionChanged();
        fogColorPickerOpen = false;
        tintColorPickerOpen = false;
        assetMenu.close();
        assetMenuNodeId = 0L;
        assetMenuKey = null;
    }

    private void commitRename() {
        EditorState state = runtime.state();
        SceneSnapshot.NodeSnapshot sel = (state == null || state.scene == null) ? null : state.scene.getNode(state.selectedId);
        if (sel == null) {
            return;
        }
        String next = renameField.text();
        if (next == null) {
            next = "";
        }
        next = next.trim();
        if (next.isEmpty() || next.equals(sel.name())) {
            return;
        }

        EditorNet net = runtime.net();
        Session session = runtime.session();
        if (net == null || session == null || state == null) {
            return;
        }
        net.sendOps(session, state, List.of(new SceneOp.Rename(sel.nodeId(), next)));
    }

    private void commitBoolProperty(long nodeId, String key, boolean value) {
        EditorState state = runtime.state();
        EditorNet net = runtime.net();
        Session session = runtime.session();
        if (net == null || session == null || state == null) {
            return;
        }
        if ("editor_locked".equals(key) || "@locked".equals(key)) {
            String encoded = value ? "true" : "false";
            net.sendOps(session, state, List.of(
                    new SceneOp.SetProperty(nodeId, "editor_locked", encoded),
                    new SceneOp.SetProperty(nodeId, "@locked", encoded)
            ));
            return;
        }
        net.sendOps(session, state, List.of(new SceneOp.SetProperty(nodeId, key, value ? "true" : "false")));
    }

    private void commitNumberProperty(long nodeId, String key, float value) {
        EditorState state = runtime.state();
        EditorNet net = runtime.net();
        Session session = runtime.session();
        if (net == null || session == null || state == null) {
            return;
        }
        if (!Float.isFinite(value)) {
            return;
        }
        PropertyType type = numberFieldTypes.get(key);
        String encoded = type == PropertyType.INT ? Integer.toString(Math.round(value)) : ParseUtils.trimFloat(value);
        net.sendOps(session, state, List.of(new SceneOp.SetProperty(nodeId, key, encoded)));
    }

    private void commitStringProperty(String key, String value) {
        EditorState state = runtime.state();
        SceneSnapshot.NodeSnapshot sel = (state == null || state.scene == null) ? null : state.scene.getNode(state.selectedId);
        if (sel == null) {
            return;
        }
        EditorState st = runtime.state();
        EditorNet net = runtime.net();
        Session session = runtime.session();
        if (net == null || session == null || st == null) {
            return;
        }
        String next = value == null ? "" : value;
        net.sendOps(session, st, List.of(new SceneOp.SetProperty(sel.nodeId(), key, next)));
    }

    private static Map<String, String> toPropertyMap(List<SceneSnapshot.Property> props) {
        HashMap<String, String> map = new HashMap<>();
        if (props == null) {
            return map;
        }
        for (SceneSnapshot.Property prop : props) {
            if (prop == null || prop.key() == null) {
                continue;
            }
            if ("@type".equals(prop.key())) {
                continue;
            }
            map.put(prop.key(), prop.value());
        }
        if (map.containsKey("@locked") && !map.containsKey("editor_locked")) {
            map.put("editor_locked", map.get("@locked"));
        }
        return map;
    }

    static void renderBool(Ui ui,
                                   UiRenderer r,
                                   Theme theme,
                                   int x,
                                   int y,
                                   int w,
                                   int h,
                                   boolean value,
                                   boolean interactive,
                                   Consumer<Boolean> onToggle) {
        var input = interactive ? ui.input() : null;
        boolean canInteract = input != null;
        float mx = canInteract ? input.mousePos().x : -1;
        float my = canInteract ? input.mousePos().y : -1;
        boolean hovered = canInteract && mx >= x && my >= y && mx < x + w && my < y + h;

        int box = Math.min(16, h);
        int boxY = y + (h - box) / 2;
        int boxX = x;
        int outline = Theme.mulAlpha(Theme.toArgb(theme.widgetOutline), 0.85f);
        int fill = value
                ? Theme.mulAlpha(Theme.toArgb(theme.widgetActive), 0.85f)
                : Theme.mulAlpha(Theme.toArgb(theme.widgetBg), 0.65f);
        if (hovered) {
            fill = Theme.lerpArgbInt(fill, Theme.toArgb(theme.widgetHover), 0.35f);
        }
        r.drawRoundedRect(boxX, boxY, box, box, Math.min(theme.design.radius_sm, 3.0f), fill, theme.design.border_thin, outline);
        if (value) {
            float iconSize = Math.min(theme.design.icon_sm, box - 4);
            theme.icons.draw(r, Icon.CHECK, boxX + (box - iconSize) * 0.5f, boxY + (box - iconSize) * 0.5f, iconSize, Theme.toArgb(theme.text));
        }
        r.drawText(value ? "true" : "false", boxX + box + 10, r.baselineForBox(y, h), Theme.toArgb(theme.textMuted));

        if (hovered && canInteract && input.mousePressed() && onToggle != null) {
            onToggle.accept(!value);
        }
    }
}
