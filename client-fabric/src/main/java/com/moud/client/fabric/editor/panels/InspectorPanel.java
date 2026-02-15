package com.moud.client.fabric.editor.panels;

import com.miry.platform.InputConstants;
import com.miry.ui.PanelContext;
import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.clipboard.Clipboard;
import com.miry.ui.event.KeyEvent;
import com.miry.ui.event.TextInputEvent;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Icon;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.DraggableNumberField;
import com.miry.ui.widgets.StripTabs;
import com.miry.ui.widgets.TextField;
import com.moud.client.fabric.editor.net.EditorNet;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.core.NodeTypeDef;
import com.moud.core.PropertyDef;
import com.moud.core.PropertyType;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.session.Session;

import java.util.*;

public final class InspectorPanel extends Panel {
    private final EditorRuntime runtime;

    private final StripTabs dockTabs = new StripTabs();
    private final StripTabs.Style dockTabStyle = new StripTabs.Style();

    private final TextField propertyFilter = new TextField();
    private final TextField renameField = new TextField();

    private final Map<String, TextField> stringFields = new HashMap<>();
    private final Map<String, DraggableNumberField> numberFields = new HashMap<>();
    private final Map<String, PropertyType> numberFieldTypes = new HashMap<>();
    private final Map<String, Float> pendingNumbers = new HashMap<>();
    private final Map<String, Float> lastSentNumbers = new HashMap<>();
    private final Map<String, Boolean> groupExpanded = new HashMap<>();

    private boolean syncingNumbers;
    private long lastSelectedId;
    private String lastSelectedTypeId = "";

    public InspectorPanel(EditorRuntime runtime) {
        super("");
        this.runtime = runtime;
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

        int x = ctx.x();
        int y = ctx.y();
        int w = ctx.width();
        int h = ctx.height();

        ui.beginPanel(x, y, w, h);

        int tabH = 26;
        int headerH = 70;
        int cursorY = y;

        renderDockTabs(ui, r, uiContext, theme, x, cursorY, w, tabH);
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
        renderHeader(ui, r, uiContext, theme, x, cursorY, w, headerH, sel);
        cursorY += headerH;

        int contentX = x;
        int contentY = cursorY;
        int contentW = w;
        int contentH = Math.max(0, y + h - contentY);

        renderProperties(ui, r, uiContext, sel, contentX, contentY, contentW, contentH);

        flushPendingNumberOps(uiContext, sel.nodeId());

        ui.endPanel();
    }

    private void renderDockTabs(Ui ui, UiRenderer r, UiContext uiContext, Theme theme, int x, int y, int w, int h) {
        var input = ui.input();
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
                              SceneSnapshot.NodeSnapshot sel) {
        var input = ui.input();
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
                                  int h) {
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
        props.sort(Comparator
                .comparing(PropertyDef::category)
                .thenComparingInt(PropertyDef::order)
                .thenComparing(PropertyDef::uiLabel)
                .thenComparing(PropertyDef::key));

        int rowH = 22;
        int labelW = 100;

        int contentHeight = estimateContentHeight(props, rowH);
        Ui.ScrollArea area = ui.beginScrollArea(r, "inspectorScroll", x, y, w, h, contentHeight);
        int scrollY = (int) area.scrollY();

        int cursorY = y + pad - scrollY;
        int maxY = y + h + scrollY;

        String filterLower = propertyFilter.text() == null ? "" : propertyFilter.text().trim().toLowerCase(Locale.ROOT);

        cursorY = renderGroupHeader(ui, r, theme, innerX, cursorY, innerW, "Transform");
        if (isExpanded("Transform")) {
            cursorY = renderVec3Row(ui, r, uiContext, theme, sel.nodeId(), values, innerX, cursorY, innerW, rowH, labelW, "Position", "x", "y", "z", filterLower);
            cursorY = renderVec3Row(ui, r, uiContext, theme, sel.nodeId(), values, innerX, cursorY, innerW, rowH, labelW, "Rotation", "rx", "ry", "rz", filterLower);
            cursorY = renderVec3Row(ui, r, uiContext, theme, sel.nodeId(), values, innerX, cursorY, innerW, rowH, labelW, "Scale", "sx", "sy", "sz", filterLower);
        }

        String lastCategory = null;
        for (PropertyDef prop : props) {
            if (cursorY > maxY) {
                break;
            }
            if (prop == null || prop.key() == null || prop.key().isBlank() || "@type".equals(prop.key())) {
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

            String value = values.get(prop.key());
            if (value == null) {
                value = prop.defaultValue() != null ? prop.defaultValue() : "";
            }
            cursorY = renderPropertyRow(ui, r, uiContext, theme, sel.nodeId(), prop, innerX, cursorY, innerW, rowH, labelW, value);
        }

        ui.endScrollArea(area);
    }

    private int estimateContentHeight(List<PropertyDef> props, int rowH) {
        int groups = 3;
        int rows = props != null ? Math.max(0, props.size()) : 0;
        return 100 + (groups + rows) * rowH;
    }

    private int renderGroupHeader(Ui ui, UiRenderer r, Theme theme, int x, int y, int w, String title) {
        int h = 22;
        int bg = 0xFF262A30;
        int hover = 0xFF2D323A;
        boolean expanded = isExpanded(title);

        boolean canInteract = ui.input() != null;
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
            boolean b = parseBool(value, parseBool(prop.defaultValue(), false));
            renderBool(ui, r, theme, valueX, y, valueW, rowH, b, next -> commitBoolProperty(nodeId, prop.key(), next));
            return y + rowH;
        }

        if (prop.type() == PropertyType.INT || prop.type() == PropertyType.FLOAT) {
            DraggableNumberField nf = numberField(prop.key(), prop, parseFloat(value, 0.0f));
            syncNumberValue(uiContext, nf, parseFloat(value, nf.value()));
            nf.render(r, uiContext, ui.input(), theme, valueX, y + 2, valueW, rowH - 4, true);
            return y + rowH;
        }

        TextField tf = stringFields.computeIfAbsent(prop.key(), k -> new TextField());
        if (uiContext == null || !tf.isFocused(uiContext)) {
            tf.setText(value == null ? "" : value);
            tf.setCursorPos(tf.text().length());
        }
        tf.render(r, uiContext, ui.input(), theme, valueX, y + 2, valueW, rowH - 4, true);
        return y + rowH;
    }

    private int renderVec3Row(Ui ui,
                              UiRenderer r,
                              UiContext uiContext,
                              Theme theme,
                              long nodeId,
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
        if (!filterLower.isEmpty() && !label.toLowerCase(Locale.ROOT).contains(filterLower)) {
            return y;
        }

        r.drawText(label, x, r.baselineForBox(y, rowH), Theme.toArgb(theme.textMuted));

        int valueX = x + labelW + theme.design.space_sm;
        int valueW = Math.max(1, w - (valueX - x));
        int gap = theme.design.space_xs;
        int eachW = Math.max(1, (valueW - gap * 2) / 3);
        int fieldH = rowH - 4;
        int fy = y + 2;

        renderPrefixedNumber(ui, r, uiContext, theme, nodeId, kx, valueX, fy, eachW, fieldH, "x", values.get(kx));
        renderPrefixedNumber(ui, r, uiContext, theme, nodeId, ky, valueX + eachW + gap, fy, eachW, fieldH, "y", values.get(ky));
        int lastX = valueX + (eachW + gap) * 2;
        renderPrefixedNumber(ui, r, uiContext, theme, nodeId, kz, lastX, fy, valueX + valueW - lastX, fieldH, "z", values.get(kz));

        return y + rowH;
    }

    private void renderPrefixedNumber(Ui ui,
                                      UiRenderer r,
                                      UiContext uiContext,
                                      Theme theme,
                                      long nodeId,
                                      String key,
                                      int x,
                                      int y,
                                      int w,
                                      int h,
                                      String prefix,
                                      String rawValue) {
        DraggableNumberField nf = numberField(key, null, parseFloat(rawValue, 0.0f));
        syncNumberValue(uiContext, nf, parseFloat(rawValue, nf.value()));
        nf.render(r, uiContext, ui.input(), theme, x, y, w, h, true);

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
                    nf.setSnapStep(parseFloat(step, 1.0f));
                }
                float rangeMin = min;
                float rangeMax = max;
                String minS = def.editorHints().get("min");
                if (minS != null) {
                    rangeMin = parseFloat(minS, rangeMin);
                }
                String maxS = def.editorHints().get("max");
                if (maxS != null) {
                    rangeMax = parseFloat(maxS, rangeMax);
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
        ArrayList<String> keys = new ArrayList<>(pendingNumbers.keySet());
        for (String key : keys) {
            DraggableNumberField nf = numberFields.get(key);
            if (nf != null && uiContext != null && uiContext.pointer().isCaptured(nf.id())) {
                continue;
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
            pendingNumbers.remove(key);
            commitNumberProperty(nodeId, key, v);
        }
    }

    private boolean isExpanded(String group) {
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
        String encoded = type == PropertyType.INT ? Integer.toString(Math.round(value)) : trimFloat(value);
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
        return map;
    }

    private static float parseFloat(String value, float fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean parseBool(String value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "true", "1" -> true;
            case "false", "0" -> false;
            default -> fallback;
        };
    }

    private static String trimFloat(float v) {
        if (Math.abs(v - Math.round(v)) < 1e-6f) {
            return Integer.toString(Math.round(v));
        }
        return Float.toString(v);
    }

    private static void renderBool(Ui ui,
                                   UiRenderer r,
                                   Theme theme,
                                   int x,
                                   int y,
                                   int w,
                                   int h,
                                   boolean value,
                                   java.util.function.Consumer<Boolean> onToggle) {
        var input = ui.input();
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
