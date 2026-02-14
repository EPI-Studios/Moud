package com.moud.client.fabric.editor.panels;

import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.editor.net.EditorNet;
import com.moud.client.fabric.editor.theme.EditorTheme;

import com.miry.ui.PanelContext;
import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.event.KeyEvent;
import com.miry.ui.event.TextInputEvent;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Icon;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.PropertyGrid;
import com.miry.ui.widgets.TextField;
import com.miry.platform.InputConstants;
import com.moud.core.NodeTypeDef;
import com.moud.core.PropertyDef;
import com.moud.core.PropertyType;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.session.Session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

public final class InspectorPanel extends Panel {
    private final EditorRuntime runtime;
    private final TextField renameField = new TextField();
    private final PropertyGrid propertyGrid = new PropertyGrid();
    private final Map<String, TextField> propertyFields = new HashMap<>();
    private final Map<String, String> propertyErrors = new HashMap<>();
    private long lastSelectedId;
    private String lastSelectedTypeId = "";

    public InspectorPanel(EditorRuntime runtime) {
        super("Inspector");
        this.runtime = runtime;
    }

    public void handleKey(UiContext ctx, KeyEvent e) {
        if (ctx == null || e == null) {
            return;
        }
        if (renameField.isFocused(ctx)) {
            renameField.handleKey(e, ctx.clipboard());
            if (e.isPressOrRepeat() && e.key() == InputConstants.KEY_ENTER) {
                commitRename();
            }
            return;
        }

        for (Map.Entry<String, TextField> entry : propertyFields.entrySet()) {
            TextField field = entry.getValue();
            if (!field.isFocused(ctx)) {
                continue;
            }
            field.handleKey(e, ctx.clipboard());
            if (e.isPressOrRepeat() && e.key() == InputConstants.KEY_ENTER) {
                commitProperty(entry.getKey());
            }
            return;
        }
    }

    public void handleTextInput(UiContext ctx, TextInputEvent e) {
        if (ctx == null || e == null) {
            return;
        }
        if (renameField.isFocused(ctx)) {
            renameField.handleTextInput(e);
            return;
        }
        for (TextField field : propertyFields.values()) {
            if (field.isFocused(ctx)) {
                field.handleTextInput(e);
                return;
            }
        }
    }

    @Override
    public void render(PanelContext ctx) {
        Ui ui = ctx.ui();
        UiRenderer r = ctx.renderer();
        int x = ctx.x();
        int y = ctx.y();
        int w = ctx.width();
        int h = ctx.height();

        ui.beginPanel(x, y, w, h);
        Theme theme = ui.theme();
        int pad = theme.tokens.padding;
        int itemH = theme.tokens.itemHeight;
        int itemGap = theme.tokens.itemSpacing;
        int contentX = x + pad;
        int contentW = Math.max(1, w - pad * 2);

        EditorState state = runtime.state();
        SceneSnapshot.NodeSnapshot sel = state.scene.getNode(state.selectedId);
        if (sel == null) {
            r.drawText("No selection", contentX, r.baselineForBox(y + pad, 22), Theme.toArgb(theme.textMuted));
            ui.endPanel();
            return;
        }
        if (lastSelectedId != sel.nodeId() || !sel.type().equals(lastSelectedTypeId)) {
            lastSelectedId = sel.nodeId();
            lastSelectedTypeId = sel.type();
            renameField.setText(sel.name());
            renameField.setCursorPos(renameField.text().length());
            syncPropertyEditors(state, sel);
        }

        Session session = runtime.session();
        EditorNet net = runtime.net();

        int cursorY = y + pad;
        if (ui.button(r, "+ Add Child")) {
            runtime.getCreateNodeDialog().open(state.selectedId);
        }
        cursorY += itemH + itemGap;

        if (sel.parentId() != 0L) {
            if (ui.button(r, "Delete Node")) {
                net.sendOps(session, state, List.of(new SceneOp.QueueFree(state.selectedId)));
            }
            cursorY += itemH + itemGap;
        }

        ui.label(r, "Rename (Enter to apply)", true);
        cursorY += itemH + itemGap;
        renameField.render(r, ctx.uiContext(), ui.input(), theme, contentX, cursorY, contentW, itemH, true);
        ui.spacer(itemH + itemGap);
        cursorY += itemH + itemGap;

        renderProperties(ui, r, ctx, theme, state, session, sel, contentX, cursorY + pad, x, y, w, h);
        ui.endPanel();
    }

    private void commitRename() {
        EditorState state = runtime.state();
        SceneSnapshot.NodeSnapshot sel = state.scene.getNode(state.selectedId);
        if (sel == null) {
            return;
        }
        String next = renameField.text().trim();
        if (next.isEmpty() || next.equals(sel.name())) {
            return;
        }
        runtime.net().sendOps(runtime.session(), state, List.of(new SceneOp.Rename(sel.nodeId(), next)));
    }

    private void syncPropertyEditors(EditorState state, SceneSnapshot.NodeSnapshot sel) {
        NodeTypeDef def = state.typesById.get(sel.type());
        if (def == null) {
            return;
        }
        Map<String, String> values = toPropertyMap(sel.properties());
        for (PropertyDef prop : def.properties().values()) {
            if (prop == null || prop.key() == null || prop.key().isBlank()) {
                continue;
            }
            if (prop.type() == PropertyType.BOOL) {
                continue;
            }
            String key = prop.key();
            TextField field = propertyFields.computeIfAbsent(key, k -> new TextField());
            String value = values.get(key);
            if (value == null) {
                value = prop.defaultValue();
            }
            if (value == null) {
                value = "";
            }
            field.setText(value);
            field.setCursorPos(field.text().length());
        }
        propertyErrors.clear();
    }

    private void commitProperty(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        EditorState state = runtime.state();
        SceneSnapshot.NodeSnapshot sel = state.scene.getNode(state.selectedId);
        if (sel == null) {
            return;
        }
        NodeTypeDef def = state.typesById.get(sel.type());
        if (def == null) {
            return;
        }
        PropertyDef prop = def.properties().get(key);
        if (prop == null) {
            return;
        }
        if (prop.type() == PropertyType.BOOL) {
            return;
        }
        TextField field = propertyFields.get(key);
        if (field == null) {
            return;
        }
        String raw = field.text();
        String value = prop.type() == PropertyType.STRING ? raw : (raw == null ? "" : raw.trim());
        var vr = prop.type().validate(value);
        if (!vr.ok()) {
            propertyErrors.put(key, vr.message());
            return;
        }
        propertyErrors.remove(key);
        runtime.net().sendOps(runtime.session(), state, List.of(new SceneOp.SetProperty(sel.nodeId(), key, value)));
    }

    private void renderProperties(Ui ui,
                                  UiRenderer r,
                                  PanelContext ctx,
                                  Theme theme,
                                  EditorState state,
                                  Session session,
                                  SceneSnapshot.NodeSnapshot sel,
                                  int textX,
                                  int startY,
                                  int x,
                                  int y,
                                  int w,
                                  int h) {
        int yy = startY;

        r.drawText("Type: " + sel.type(), textX, r.baselineForBox(yy, 18), Theme.toArgb(theme.textMuted));
        yy += 22;
        ui.spacer(22);

        NodeTypeDef def = state.typesById.get(sel.type());
        if (def == null) {
            renderRawProperties(r, theme, sel, textX, yy, x, y, w, h);
            return;
        }

        r.drawText("Properties", textX, r.baselineForBox(yy, 18), Theme.toArgb(theme.textMuted));
        yy += 22;
        ui.spacer(22);

        Map<String, String> values = toPropertyMap(sel.properties());
        ArrayList<PropertyDef> props = new ArrayList<>(def.properties().values());
        props.sort(Comparator
                .comparing(PropertyDef::category)
                .thenComparingInt(PropertyDef::order)
                .thenComparing(PropertyDef::uiLabel)
                .thenComparing(PropertyDef::key));

        int fieldX = x + theme.tokens.padding;
        int itemW = Math.max(1, w - theme.tokens.padding * 2);
        int rowH = theme.tokens.itemHeight;
        int maxY = y + h - theme.tokens.padding - rowH;
        propertyGrid.setLabelWidthPx(Math.min(190, Math.max(110, itemW / 2)));

        String lastCategory = null;
        for (PropertyDef prop : props) {
            if (yy > maxY) {
                break;
            }
            if (prop == null || prop.key() == null || prop.key().isBlank() || "@type".equals(prop.key())) {
                continue;
            }

            String cat = prop.category();
            if (cat != null && !cat.isBlank() && !cat.equals(lastCategory)) {
                lastCategory = cat;
                r.drawText(cat, textX, r.baselineForBox(yy, 18), Theme.toArgb(theme.textMuted));
                yy += 22;
                ui.spacer(22);
            }

            if (prop.type() == PropertyType.BOOL) {
                String cur = values.get(prop.key());
                boolean fallback = parseBool(prop.defaultValue(), false);
                boolean b = parseBool(cur, fallback);
                PropertyDef boolProp = prop;
                propertyGrid.row(ui, r, theme, fieldX, yy, itemW, rowH, boolProp.uiLabel(), true, (vx, vy, vw, vh) -> {
                    float mx = ui.mouse().x;
                    float my = ui.mouse().y;
                    boolean hovered = mx >= vx && my >= vy && mx < vx + vw && my < vy + vh;
                    if (hovered && ui.input().mousePressed()) {
                        boolean next = !b;
                        runtime.net().sendOps(session, state, List.of(new SceneOp.SetProperty(
                                sel.nodeId(),
                                boolProp.key(),
                                next ? "true" : "false"
                        )));
                    }

                    int box = Math.min(16, vh);
                    int boxY = vy + (vh - box) / 2;
                    int boxX = vx;
                    int outline = Theme.mulAlpha(Theme.toArgb(theme.widgetOutline), 0.85f);
                    int fill = b
                            ? Theme.mulAlpha(Theme.toArgb(theme.widgetActive), 0.85f)
                            : Theme.mulAlpha(Theme.toArgb(theme.widgetBg), 0.65f);
                    if (hovered) {
                        fill = Theme.lerpArgbInt(fill, Theme.toArgb(theme.widgetHover), 0.35f);
                    }
                    r.drawRoundedRect(boxX, boxY, box, box, Math.min(theme.design.radius_sm, 3.0f), fill, theme.design.border_thin, outline);
                    if (b) {
                        float iconSize = Math.min(theme.design.icon_sm, box - 4);
                        theme.icons.draw(
                                r,
                                Icon.CHECK,
                                boxX + (box - iconSize) * 0.5f,
                                boxY + (box - iconSize) * 0.5f,
                                iconSize,
                                Theme.toArgb(theme.text)
                        );
                    }

                    String t = b ? "true" : "false";
                    r.drawText(t, boxX + box + 10, r.baselineForBox(vy, vh), Theme.toArgb(theme.textMuted));
                });
                yy += rowH;
                ui.spacer(rowH);
                continue;
            }

            TextField field = propertyFields.get(prop.key());
            if (field == null) {
                field = new TextField();
                String current = values.get(prop.key());
                if (current == null) {
                    current = prop.defaultValue();
                }
                if (current == null) {
                    current = "";
                }
                field.setText(current);
                field.setCursorPos(field.text().length());
                propertyFields.put(prop.key(), field);
            }

            TextField textField = field;
            PropertyDef textProp = prop;
            propertyGrid.row(ui, r, theme, fieldX, yy, itemW, rowH, textProp.uiLabel(), true, (vx, vy, vw, vh) -> {
                textField.render(r, ctx.uiContext(), ui.input(), theme, vx, vy, vw, vh, true);
            });
            yy += rowH;
            ui.spacer(rowH);

            String err = propertyErrors.get(prop.key());
            if (err != null && !err.isBlank()) {
                int labelW = Math.min(propertyGrid.labelWidthPx(), Math.max(60, itemW - 110));
                int errX = fieldX + labelW + Math.max(1, theme.design.border_thin) + theme.design.space_sm;
                r.drawText("! " + err, errX, r.baselineForBox(yy, 18), Theme.toArgb(theme.textMuted));
                yy += 18 + theme.tokens.itemSpacing;
                ui.spacer(18 + theme.tokens.itemSpacing);
            }
        }
    }

    private static void renderRawProperties(UiRenderer r,
                                            Theme theme,
                                            SceneSnapshot.NodeSnapshot sel,
                                            int textX,
                                            int startY,
                                            int x,
                                            int y,
                                            int w,
                                            int h) {
        int yy = startY;
        r.drawText("Properties", textX, r.baselineForBox(yy, 18), Theme.toArgb(theme.textMuted));
        yy += 22;

        if (sel.properties().isEmpty()) {
            r.drawText("(none)", textX, r.baselineForBox(yy, 18), Theme.toArgb(theme.textMuted));
            return;
        }

        int maxY = y + h - 18;
        for (SceneSnapshot.Property prop : sel.properties()) {
            if (yy > maxY) {
                break;
            }
            if ("@type".equals(prop.key())) {
                continue;
            }
            r.drawText(prop.key() + ": " + prop.value(), textX, r.baselineForBox(yy, 18), Theme.toArgb(theme.text));
            yy += 18;
        }
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
            map.put(prop.key(), prop.value());
        }
        return map;
    }

    private static boolean parseBool(String value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        String v = value.trim().toLowerCase();
        return switch (v) {
            case "true", "1" -> true;
            case "false", "0" -> false;
            default -> fallback;
        };
    }
}