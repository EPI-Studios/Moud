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
import com.miry.ui.util.MathUtils;
import com.miry.ui.widgets.ColorPicker;
import com.miry.ui.widgets.ContextMenu;
import com.miry.ui.widgets.DraggableNumberField;
import com.miry.ui.widgets.StripTabs;
import com.miry.ui.widgets.TextField;
import com.moud.client.fabric.assets.MoudTextAssets;
import com.moud.client.fabric.editor.net.EditorNet;
import com.moud.client.fabric.editor.state.EditorHistory;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.editor.theme.EditorTheme;
import com.moud.client.fabric.editor.util.EditorUiUtil;
import com.moud.client.fabric.model.ModelAsset;
import com.moud.client.fabric.model.ModelCache;
import com.moud.client.fabric.render.MoudIcons;
import com.moud.client.fabric.render.MoudTextures;
import com.moud.client.fabric.render.preview.MaterialPreviewRenderer;
import com.moud.client.fabric.util.ParseUtils;
import com.moud.core.NodeTypeDef;
import com.moud.core.player.AttachPoint;
import com.moud.core.PropertyDef;
import com.moud.core.PropertyType;
import com.moud.core.assets.ResPath;
import com.moud.core.scene.Model3D;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.session.Session;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class InspectorPanel extends Panel {
    private static final long DRAG_NUMBER_SEND_INTERVAL_MS = 50;

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
    private final Map<String, String> preDragNumbers = new HashMap<>();

    private final Map<String, Boolean> groupExpanded = new HashMap<>();
    private final ColorPicker fogColorPicker = new ColorPicker();
    private final ColorPicker tintColorPicker = new ColorPicker();

    private boolean fogColorPickerOpen;
    private boolean tintColorPickerOpen;

    private final ContextMenu assetMenu = new ContextMenu();
    private long assetMenuNodeId;
    private String assetMenuKey;

    private final ContextMenu selectMenu = new ContextMenu();
    private long selectMenuNodeId;
    private String selectMenuKey;

    private boolean syncingNumbers;
    private long lastSelectedId;
    private String lastSelectedTypeId = "";

    private static final AttachPoint[] ATTACH_POINTS = AttachPoint.values();

    private static final String GROUP_LABEL = "Player Attachment";
    private static final String PROP_ATTACH = "attachment_point";
    private static final String PROP_FOLLOW = "follow_animation";
    private static final String DEFAULT_ATTACH = "root";
    private static final String INHERIT_VALUE = "";
    private static final String INHERIT_LABEL = "(inherit)";


    public InspectorPanel(EditorRuntime runtime) {
        super("");
        this.runtime = runtime;
        this.materialEditor = new MaterialEditor(runtime);
        groupExpanded.put("Transform", true);
    }

    public void handleKey(UiContext context, KeyEvent event) {
        if (context == null || event == null) return;

        Clipboard clipboard = context.clipboard();

        if (propertyFilter.isFocused(context)) {
            propertyFilter.handleKey(event, clipboard);
            return;
        }

        if (renameField.isFocused(context)) {
            renameField.handleKey(event, clipboard);
            if (event.isPressOrRepeat() && event.key() == InputConstants.KEY_ENTER) {
                commitRename();
            }
            return;
        }

        for (var entry : stringFields.entrySet()) {
            TextField field = entry.getValue();
            if (field != null && field.isFocused(context)) {
                field.handleKey(event, clipboard);
                if (event.isPressOrRepeat() && event.key() == InputConstants.KEY_ENTER) {
                    commitStringProperty(entry.getKey(), field.text());
                }
                return;
            }
        }

        if (materialEditor.handleKey(context, event, clipboard)) return;

        for (DraggableNumberField field : numberFields.values()) {
            if (field != null && field.handleKey(context, event, clipboard)) return;
        }
    }

    public void handleTextInput(UiContext context, TextInputEvent event) {
        if (context == null || event == null) return;

        if (propertyFilter.isFocused(context)) {
            propertyFilter.handleTextInput(event);
            return;
        }

        if (renameField.isFocused(context)) {
            renameField.handleTextInput(event);
            return;
        }

        for (TextField field : stringFields.values()) {
            if (field != null && field.isFocused(context)) {
                field.handleTextInput(event);
                return;
            }
        }

        if (materialEditor.handleTextInput(context, event)) return;

        for (DraggableNumberField field : numberFields.values()) {
            if (field != null && field.handleTextInput(context, event)) return;
        }
    }

    @Override
    public void render(PanelContext context) {
        Ui ui = context.ui();
        UiRenderer renderer = context.renderer();
        Theme theme = ui.theme();
        UiContext uiContext = context.uiContext();
        boolean interactive = runtime != null && !runtime.uiBlocked();

        int x = context.x();
        int y = context.y();
        int width = context.width();
        int height = context.height();

        ui.beginPanel(x, y, width, height);

        int tabHeight = theme.design.tab_height_md;
        int headerHeight = 70;
        int cursorY = y;

        renderDockTabs(ui, renderer, uiContext, theme, x, cursorY, width, tabHeight, interactive);
        cursorY += tabHeight;

        EditorState state = runtime.state();
        boolean isMulti = state != null && state.selectedIds.size() >= 2;
        SceneSnapshot.NodeSnapshot selection = (state == null || state.scene == null) ? null : state.scene.getNode(state.selectedId);

        if (selection == null && isMulti) {
            for (long id : state.selectedIds) {
                selection = state.scene.getNode(id);
                if (selection != null) break;
            }
        }

        if (selection == null) {
            int padding = theme.design.space_md;
            renderer.drawText("No selection", x + padding, renderer.baselineForBox(cursorY + padding, 22), Theme.toArgb(theme.textMuted));
            ui.endPanel();
            return;
        }

        onSelectionMaybeChanged(selection);
        if (isMulti) {
            renderMultiHeader(renderer, theme, x, cursorY, width, headerHeight, state.selectedIds.size());
        } else {
            renderHeader(ui, renderer, uiContext, theme, x, cursorY, width, headerHeight, selection, interactive);
        }
        cursorY += headerHeight;

        int contentHeight = Math.max(0, y + height - cursorY);
        renderProperties(ui, renderer, uiContext, selection, x, cursorY, width, contentHeight, interactive);

        flushPendingNumberOps(uiContext, selection.nodeId());
        materialEditor.flushPendingMaterialUploads(uiContext);

        ui.endPanel();
    }

    private void renderDockTabs(Ui ui, UiRenderer renderer, UiContext uiContext, Theme theme, int x, int y, int width, int height, boolean interactive) {
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

        String[] labels = {"Inspector"};
        dockTabs.render(renderer, uiContext, input, theme, x, y, width, height, labels, 0, true, dockTabStyle);
    }

    private void renderHeader(Ui ui, UiRenderer renderer, UiContext uiContext, Theme theme, int x, int y, int width, int height, SceneSnapshot.NodeSnapshot selection, boolean interactive) {
        var input = interactive ? ui.input() : null;
        int backgroundColor = Theme.toArgb(theme.windowBg);

        renderer.drawRect(x, y, width, height, backgroundColor);
        renderer.drawRect(x, y + height - 1, width, 1, Theme.toArgb(theme.headerLine));

        int padding = theme.design.space_md;
        int leftX = x + padding;
        int nameHeight = 18;
        int typeHeight = 16;

        renderer.drawText(selection.name(), leftX, renderer.baselineForBox(y + padding, nameHeight), Theme.toArgb(theme.text));
        renderer.drawText(selection.type(), leftX, renderer.baselineForBox(y + padding + nameHeight, typeHeight), Theme.toArgb(theme.disabledFg));

        int renameWidth = Math.min(220, Math.max(120, width - padding * 2));
        int renameHeight = 22;
        int renameX = x + width - padding - renameWidth;
        int renameY = y + padding;
        renameField.render(renderer, uiContext, input, theme, renameX, renameY, renameWidth, renameHeight, true);

        int searchHeight = 22;
        int searchX = x + padding;
        int searchY = y + height - padding - searchHeight;
        int searchWidth = Math.max(1, width - padding * 2);
        propertyFilter.render(renderer, uiContext, input, theme, searchX, searchY, searchWidth, searchHeight, true);

        if ((propertyFilter.text() == null || propertyFilter.text().isEmpty()) && (uiContext == null || !propertyFilter.isFocused(uiContext))) {
            int hintColor = Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.70f);
            int spacing = theme.design.space_sm;
            float iconSize = Math.min(theme.design.icon_sm, searchHeight - spacing * 2);
            MoudIcons.drawOrFallback(renderer, theme, Icon.SEARCH, searchX + spacing, searchY + (searchHeight - iconSize) * 0.5f, iconSize, hintColor);
            renderer.drawText("Filter Properties", searchX + spacing + iconSize + spacing, renderer.baselineForBox(searchY, searchHeight), hintColor);
        }
    }

    private void renderMultiHeader(UiRenderer renderer, Theme theme, int x, int y, int width, int height, int count) {
        renderer.drawRect(x, y, width, height, Theme.toArgb(theme.windowBg));
        renderer.drawRect(x, y + height - 1, width, 1, Theme.toArgb(theme.headerLine));
        int padding = theme.design.space_md;
        renderer.drawText(count + " nodes selected", x + padding, renderer.baselineForBox(y + padding, 22), Theme.toArgb(theme.text));
    }

    private void renderProperties(Ui ui, UiRenderer renderer, UiContext uiContext, SceneSnapshot.NodeSnapshot selection, int x, int y, int width, int height, boolean interactive) {
        Theme theme = ui.theme();
        int padding = theme.design.space_md;
        int innerX = x + padding;
        int innerWidth = Math.max(1, width - padding * 2);

        EditorState state = runtime.state();
        NodeTypeDef typeDef = state != null ? state.typesById.get(selection.type()) : null;
        if (typeDef == null) {
            renderer.drawText("(missing schema)", innerX, renderer.baselineForBox(y + padding, 18), Theme.toArgb(theme.textMuted));
            return;
        }

        Map<String, String> values = toPropertyMap(selection.properties());
        if (state != null && state.selectedIds.size() >= 2) {
            values = mergePropertyValues(state, values);
        }
        ArrayList<PropertyDef> properties = new ArrayList<>(typeDef.properties().values());
        addBuiltInEditorProperties(typeDef, properties);

        properties.sort(Comparator
                .comparing(PropertyDef::category)
                .thenComparingInt(PropertyDef::order)
                .thenComparing(PropertyDef::uiLabel)
                .thenComparing(PropertyDef::key));

        int rowHeight = 24;
        int labelWidth = 110;

        String scriptPath = values.get("script");
        if (scriptPath != null) {
            scriptPath = scriptPath.trim();
        }

        if (state != null) {
            maybeRequestScriptActions(state, selection.nodeId(), scriptPath);
        }

        int scriptActionRows = estimateScriptActionRows(state, selection.nodeId(), scriptPath);
        int materialParamRows = materialEditor.estimateMaterialParamRows(properties, values);
        int contentHeight = estimateContentHeight(properties, rowHeight, scriptActionRows + materialParamRows);

        Ui.ScrollArea area = ui.beginScrollArea(renderer, "inspectorScroll", x, y, width, height, contentHeight);
        int scrollY = (int) area.scrollY();

        int cursorY = y + padding - scrollY;
        int maxY = y + height + scrollY;
        String filterLower = propertyFilter.text() == null ? "" : propertyFilter.text().trim().toLowerCase(Locale.ROOT);

        cursorY = renderGroupHeader(ui, renderer, theme, innerX, cursorY, innerWidth, "Transform");
        boolean isTransformExpanded = isExpanded("Transform");

        if (isTransformExpanded) {
            cursorY = renderVec3Row(ui, renderer, uiContext, theme, selection.nodeId(), typeDef, values, innerX, cursorY, innerWidth, rowHeight, labelWidth, "Position", "x", "y", "z", filterLower);
            cursorY = renderVec3Row(ui, renderer, uiContext, theme, selection.nodeId(), typeDef, values, innerX, cursorY, innerWidth, rowHeight, labelWidth, "Rotation", "rx", "ry", "rz", filterLower);

            String sizeLabel = "Scale";
            PropertyDef scaleXDef = typeDef.properties().get("sx");
            if (scaleXDef != null && "Size".equalsIgnoreCase(scaleXDef.category())) {
                sizeLabel = "Size";
            }
            cursorY = renderVec3Row(ui, renderer, uiContext, theme, selection.nodeId(), typeDef, values, innerX, cursorY, innerWidth, rowHeight, labelWidth, sizeLabel, "sx", "sy", "sz", filterLower);
        }

        String lastCategory = null;
        boolean fogColorRendered = false;
        boolean tintColorRendered = false;

        for (PropertyDef property : properties) {
            if (cursorY > maxY) break;

            if (property == null || property.key() == null || property.key().isBlank() || "@type".equals(property.key())) {
                continue;
            }

            if (isTransformExpanded && ("sx".equals(property.key()) || "sy".equals(property.key()) || "sz".equals(property.key()))) {
                continue;
            }

            if ("Transform".equals(property.category())) {
                continue;
            }

            if (!filterLower.isEmpty()) {
                String target = (property.uiLabel() + " " + property.key()).toLowerCase(Locale.ROOT);
                if (!target.contains(filterLower)) continue;
            }

            String category = property.category() == null ? "" : property.category();
            if (!category.equals(lastCategory)) {
                lastCategory = category;
                cursorY = renderGroupHeader(ui, renderer, theme, innerX, cursorY, innerWidth, category);
            }

            if (!isExpanded(category)) continue;

            if ("Fog Color".equals(category) && isFogColorKey(property.key())) {
                if (!fogColorRendered) {
                    fogColorRendered = true;
                    cursorY = renderFogColorPicker(ui, renderer, uiContext, theme, selection.nodeId(), values, innerX, cursorY, innerWidth, rowHeight, labelWidth);
                }
                continue;
            }

            if ("Color Tint".equals(category) && isColorTintKey(property.key())) {
                if (!tintColorRendered) {
                    tintColorRendered = true;
                    cursorY = renderTintColorPicker(ui, renderer, uiContext, theme, selection.nodeId(), values, innerX, cursorY, innerWidth, rowHeight, labelWidth);
                }
                continue;
            }

            String value = values.get(property.key());
            if (value == null) {
                value = property.defaultValue() != null ? property.defaultValue() : "";
            }

            if ("attachment_point".equals(property.key()) && "PlayerAttachment".equals(selection.type())) {
                continue;
            }

            if ("Model3D".equals(selection.type())) {
                if (Model3D.PROP_MODEL_PATH.equals(property.key())) {
                    cursorY = renderModel3DModelPathRow(ui, renderer, uiContext, theme, selection.nodeId(), property, innerX, cursorY, innerWidth, rowHeight, labelWidth, value, interactive);
                    continue;
                }
                if (Model3D.PROP_ANIMATION.equals(property.key())) {
                    cursorY = renderModel3DAnimationRow(ui, renderer, uiContext, theme, selection.nodeId(), property, values, innerX, cursorY, innerWidth, rowHeight, labelWidth, value, interactive);
                    continue;
                }
                if (Model3D.PROP_ANIMATION_LOOP.equals(property.key())) {
                    cursorY = renderModel3DAnimationLoopRow(ui, renderer, uiContext, theme, selection.nodeId(), property, innerX, cursorY, innerWidth, rowHeight, labelWidth, value, interactive);
                    continue;
                }
            }

            cursorY = renderPropertyRow(ui, renderer, uiContext, theme, selection.nodeId(), property, innerX, cursorY, innerWidth, rowHeight, labelWidth, value);
        }

        cursorY = renderPlayerBodyAttachRow(ui, renderer, theme, state, selection, values, innerX, cursorY, innerWidth, rowHeight, labelWidth, interactive);

        cursorY = materialEditor.renderMaterialShaderParams(this, ui, renderer, uiContext, theme, properties, values, filterLower, innerX, cursorY, innerWidth, rowHeight, labelWidth, interactive);
        cursorY = renderScriptActions(ui, renderer, uiContext, theme, state, selection.nodeId(), scriptPath, filterLower, innerX, cursorY, innerWidth, rowHeight, interactive);

        ui.endScrollArea(area);

        renderAssetMenu(ui, renderer, theme, interactive);
        renderSelectMenu(ui, renderer, theme, interactive);
        materialEditor.renderMaterialTextureMenu(ui, renderer, theme, interactive);
    }

    private int renderModel3DModelPathRow(Ui ui, UiRenderer renderer, UiContext uiContext, Theme theme, long nodeId, PropertyDef property, int x, int y, int width, int rowHeight, int labelWidth, String value, boolean interactive) {
        y = renderPropertyRow(ui, renderer, uiContext, theme, nodeId, property, x, y, width, rowHeight, labelWidth, value);

        String path = value == null ? "" : value.trim();
        if (path.isEmpty()) {
            int warningHeight = rowHeight - 4;
            renderer.drawText("\u26a0 No model path set", x, renderer.baselineForBox(y, warningHeight), EditorTheme.WARNING_TEXT);
            return y + warningHeight;
        }

        if (ModelCache.isFailed(path)) {
            int warningHeight = rowHeight - 4;
            int retryWidth = 52;
            int retryX = x + width - retryWidth;

            renderer.drawText("\u2717 Model load failed", x, renderer.baselineForBox(y, warningHeight), EditorTheme.ERROR_TEXT);
            boolean clicked = EditorUiUtil.buttonOutlined(ui, renderer, theme, retryX, y, retryWidth, warningHeight, "Retry", interactive);

            if (clicked) {
                ModelCache.retry(path);
            }
            return y + warningHeight;
        }

        return y;
    }

    private int renderModel3DAnimationRow(Ui ui, UiRenderer renderer, UiContext uiContext, Theme theme, long nodeId, PropertyDef property, Map<String, String> values, int x, int y, int width, int rowHeight, int labelWidth, String value, boolean interactive) {
        if (ui == null || renderer == null || theme == null || property == null) return y + rowHeight;

        String modelPath = values == null ? null : values.get(Model3D.PROP_MODEL_PATH);
        if (modelPath != null) {
            modelPath = modelPath.trim();
        }

        if (modelPath == null || modelPath.isBlank()) {
            return renderPropertyRow(ui, renderer, uiContext, theme, nodeId, property, x, y, width, rowHeight, labelWidth, value);
        }

        ModelAsset asset = ModelCache.get(modelPath);
        if (asset == null || asset.animations() == null || asset.animations().isEmpty()) {
            return renderPropertyRow(ui, renderer, uiContext, theme, nodeId, property, x, y, width, rowHeight, labelWidth, value);
        }

        ArrayList<String> options = new ArrayList<>(asset.animations().keySet().size() + 1);
        options.add("");
        options.addAll(asset.animations().keySet());
        options.sort(String::compareToIgnoreCase);

        String current = value == null ? "" : value.trim();
        String display = current.isEmpty() ? "(none)" : current;

        renderSelectRow(ui, renderer, theme, x, y, width, rowHeight, labelWidth, property.uiLabel(), display, interactive, () -> {
            ArrayList<SelectOption> items = new ArrayList<>(options.size());
            for (String option : options) {
                if (option == null) continue;
                String trimmedValue = option.trim();
                items.add(new SelectOption(trimmedValue.isEmpty() ? "(none)" : trimmedValue, trimmedValue));
            }
            toggleSelectMenu(x + labelWidth + theme.design.space_sm, y + rowHeight, nodeId, property.key(), items);
        });

        return y + rowHeight;
    }

    private int renderModel3DAnimationLoopRow(Ui ui, UiRenderer renderer, UiContext uiContext, Theme theme, long nodeId, PropertyDef property, int x, int y, int width, int rowHeight, int labelWidth, String value, boolean interactive) {
        String current = value == null ? "" : value.trim();
        if (current.isEmpty()) {
            current = "once";
        }

        renderSelectRow(ui, renderer, theme, x, y, width, rowHeight, labelWidth, property.uiLabel(), current, interactive, () -> {
            ArrayList<SelectOption> items = new ArrayList<>(3);
            items.add(new SelectOption("once", "once"));
            items.add(new SelectOption("loop", "loop"));
            items.add(new SelectOption("hold", "hold"));
            toggleSelectMenu(x + labelWidth + theme.design.space_sm, y + rowHeight, nodeId, property.key(), items);
        });

        return y + rowHeight;
    }


    private int renderPlayerBodyAttachRow(
            Ui ui, UiRenderer renderer, Theme theme,
            EditorState state, SceneSnapshot.NodeSnapshot selection,
            Map<String, String> values,
            int x, int y, int width, int rowHeight, int labelWidth,
            boolean interactive) {

        if (state == null) return y;

        if ("PlayerAttachment".equals(selection.type())) {
            y = renderGroupHeader(ui, renderer, theme, x, y, width, GROUP_LABEL);
            if (!isExpanded(GROUP_LABEL)) return y;

            String current = values.getOrDefault(PROP_ATTACH, DEFAULT_ATTACH);
            if (current == null || current.isBlank()) current = DEFAULT_ATTACH;

            final String attachDisplay = current;
            final int rowY = y;

            renderSelectRow(ui, renderer, theme, x, y, width, rowHeight, labelWidth,
                    "Attach Point", attachDisplay, interactive, () -> {
                        List<SelectOption> items = buildAttachPointOptions(false);
                        toggleSelectMenu(
                                x + labelWidth + theme.design.space_sm,
                                rowY + rowHeight,
                                selection.nodeId(), PROP_ATTACH, items);
                    });

            return y + rowHeight;
        }

        long parentId = selection.parentId();
        if (parentId <= 0L) return y;

        SceneSnapshot.NodeSnapshot parent = state.scene.getNode(parentId);
        if (parent == null) return y;

        boolean parentIsAttachment = "PlayerAttachment".equals(parent.type());
        if (!parentIsAttachment) return y;

        y = renderGroupHeader(ui, renderer, theme, x, y, width, GROUP_LABEL);
        if (!isExpanded(GROUP_LABEL)) return y;

        String current = values.getOrDefault(PROP_ATTACH, INHERIT_VALUE);
        String displayAp = current.isBlank() ? INHERIT_LABEL : current;
        final int rowY = y;

        renderSelectRow(ui, renderer, theme, x, y, width, rowHeight, labelWidth,
                "Attach Point", displayAp, interactive, () -> {
                    List<SelectOption> items = buildAttachPointOptions(true);
                    toggleSelectMenu(
                            x + labelWidth + theme.design.space_sm,
                            rowY + rowHeight,
                            selection.nodeId(), PROP_ATTACH, items);
                });
        y += rowHeight;

        boolean followAnim = ParseUtils.parseBool(values.get(PROP_FOLLOW), false);
        int baseline = renderer.baselineForBox(y, rowHeight);

        renderer.drawText("Follow Anim", x, baseline, Theme.toArgb(theme.textMuted));
        renderBool(ui, renderer, theme,
                x + labelWidth + theme.design.space_sm, y,
                width - labelWidth - theme.design.space_sm, rowHeight,
                followAnim, interactive,
                next -> commitBoolProperty(selection.nodeId(), PROP_FOLLOW, next));
        y += rowHeight;

        return y;
    }

    private List<SelectOption> buildAttachPointOptions(boolean includeInherit) {
        int capacity = ATTACH_POINTS.length + (includeInherit ? 1 : 0);
        List<SelectOption> items = new ArrayList<>(capacity);
        if (includeInherit) items.add(new SelectOption(INHERIT_LABEL, INHERIT_VALUE));
        for (AttachPoint pt : ATTACH_POINTS) items.add(new SelectOption(pt.id(), pt.id()));
        return items;
    }

    private record SelectOption(String label, String value) {}

    private void renderSelectRow(Ui ui, UiRenderer renderer, Theme theme, int x, int y, int width, int rowHeight, int labelWidth, String label, String displayValue, boolean interactive, Runnable onOpenMenu) {
        int valueX = x + labelWidth + theme.design.space_sm;
        int valueWidth = Math.max(1, width - (valueX - x));

        renderer.drawText(label == null ? "" : label, x, renderer.baselineForBox(y, rowHeight), Theme.toArgb(theme.textMuted));

        int fieldY = y + 2;
        int fieldHeight = rowHeight - 4;

        var input = interactive ? ui.input() : null;
        boolean canInteract = input != null;
        float mouseX = canInteract ? input.mousePos().x : -1;
        float mouseY = canInteract ? input.mousePos().y : -1;
        boolean hovered = canInteract && mouseX >= valueX && mouseY >= fieldY && mouseX < valueX + valueWidth && mouseY < fieldY + fieldHeight;

        int backgroundColor = hovered ? Theme.toArgb(theme.widgetHover) : Theme.toArgb(theme.widgetBg);
        int outlineColor = Theme.toArgb(theme.widgetOutline);

        renderer.drawRoundedRect(valueX, fieldY, valueWidth, fieldHeight, theme.design.radius_sm, backgroundColor, theme.design.border_thin, outlineColor);

        String text = displayValue == null ? "" : displayValue;
        int textX = valueX + theme.design.space_sm;
        int textY = (int) renderer.baselineForBox(y, rowHeight);
        renderer.drawText(text, textX, textY, Theme.toArgb(theme.text));

        float iconSize = Math.min(theme.design.icon_sm, fieldHeight - 6);
        int iconColor = Theme.toArgb(theme.textMuted);
        MoudIcons.drawOrFallback(renderer, theme, Icon.CHEVRON_DOWN, valueX + valueWidth - theme.design.space_sm - iconSize, fieldY + (fieldHeight - iconSize) * 0.5f, iconSize, iconColor);

        if (hovered && canInteract && input.mouseReleased() && onOpenMenu != null) {
            onOpenMenu.run();
        }
    }

    private void toggleSelectMenu(int x, int y, long nodeId, String key, List<SelectOption> options) {
        if (key == null || key.isBlank()) return;

        if (selectMenu.isOpen() && nodeId == selectMenuNodeId && Objects.equals(selectMenuKey, key)) {
            selectMenu.close();
            return;
        }

        selectMenuNodeId = nodeId;
        selectMenuKey = key;
        selectMenu.clear();

        if (options != null) {
            for (SelectOption option : options) {
                if (option == null || option.label == null) continue;
                String value = option.value == null ? "" : option.value;
                selectMenu.addItem(option.label, () -> commitStringProperty(key, value));
            }
        }

        EditorUiUtil.openMenuClamped(selectMenu, runtime, x, y);
    }

    private void renderSelectMenu(Ui ui, UiRenderer renderer, Theme theme, boolean interactive) {
        if (ui == null || renderer == null || theme == null || !selectMenu.isOpen()) return;

        var input = interactive ? ui.input() : null;
        int itemHeight = theme.design.menu_item_height;

        if (input != null) {
            selectMenu.updateFromInput(input, theme, itemHeight);
            EditorUiUtil.clampOpenMenuToScreen(selectMenu, runtime);
        }

        selectMenu.render(renderer, theme, itemHeight, Theme.toArgb(theme.panelBg), Theme.toArgb(theme.widgetHover), Theme.toArgb(theme.text), selectMenu.hoverIndex());

        if (interactive && input != null && input.mousePressed()) {
            selectMenu.handleClick((int) ui.mouse().x, (int) ui.mouse().y, itemHeight);
        }
    }

    private static void addBuiltInEditorProperties(NodeTypeDef typeDef, List<PropertyDef> properties) {
        if (typeDef == null || properties == null) return;

        Map<String, PropertyDef> existing = typeDef.properties();
        if (!existing.containsKey("visible")) {
            properties.add(new PropertyDef("visible", PropertyType.BOOL, "true", "Visible", "Editor", -1000, Map.of()));
        }
        if (!existing.containsKey("editor_locked")) {
            properties.add(new PropertyDef("editor_locked", PropertyType.BOOL, "false", "Locked", "Editor", -999, Map.of()));
        }
        if (!existing.containsKey("solid")) {
            properties.add(new PropertyDef("solid", PropertyType.BOOL, "true", "Solid", "Collision", 0, Map.of()));
        }
        if (!existing.containsKey("color_tint_r")) {
            properties.add(new PropertyDef("color_tint_r", PropertyType.FLOAT, "1", "R", "Color Tint", 0, Map.of("min", "0", "max", "1", "step", "0.01")));
        }
        if (!existing.containsKey("color_tint_g")) {
            properties.add(new PropertyDef("color_tint_g", PropertyType.FLOAT, "1", "G", "Color Tint", 1, Map.of("min", "0", "max", "1", "step", "0.01")));
        }
        if (!existing.containsKey("color_tint_b")) {
            properties.add(new PropertyDef("color_tint_b", PropertyType.FLOAT, "1", "B", "Color Tint", 2, Map.of("min", "0", "max", "1", "step", "0.01")));
        }
    }

    private int estimateContentHeight(List<PropertyDef> properties, int rowHeight, int extraRows) {
        int groupsCount = 3;
        int rowsCount = properties != null ? Math.max(0, properties.size()) : 0;
        int extraHeight = (fogColorPickerOpen || tintColorPickerOpen) ? 180 : 0;
        return 100 + (groupsCount + rowsCount + Math.max(0, extraRows)) * rowHeight + extraHeight;
    }

    private static int estimateScriptActionRows(EditorState state, long nodeId, String scriptPath) {
        if (state == null || nodeId <= 0L || scriptPath == null || scriptPath.isBlank()) return 0;

        EditorState.ScriptActions actions = state.scriptActionsByNode.get(nodeId);
        int actionCount = actions == null || actions.actions == null ? 0 : actions.actions.size();
        return 2 + Math.max(1, actionCount);
    }

    private void maybeRequestScriptActions(EditorState state, long nodeId, String scriptPath) {
        if (state == null || nodeId <= 0L || scriptPath == null || scriptPath.isBlank()) return;

        EditorState.ScriptActions cache = state.scriptActions(nodeId);
        if (cache == null) return;

        String script = scriptPath.trim();
        if (!Objects.equals(cache.scriptPath, script)) {
            cache.scriptPath = script;
            cache.pending = false;
            cache.loaded = false;
            cache.error = null;
            cache.actions = List.of();
        }

        if (cache.pending || cache.loaded) return;

        Session session = runtime.session();
        EditorNet net = runtime.net();
        if (session == null || net == null) return;

        cache.pending = true;
        net.requestScriptActions(session, state, nodeId);
    }

    private int renderScriptActions(Ui ui, UiRenderer renderer, UiContext uiContext, Theme theme, EditorState state, long nodeId, String scriptPath, String filterLower, int x, int y, int width, int rowHeight, boolean interactive) {
        if (state == null || nodeId <= 0L || scriptPath == null || scriptPath.isBlank()) return y;
        if (!filterLower.isEmpty() && !filterLower.contains("script") && !filterLower.contains("action")) return y;

        EditorState.ScriptActions cache = state.scriptActions(nodeId);
        if (cache == null) return y;

        y = renderGroupHeader(ui, renderer, theme, x, y, width, "Script Actions");
        if (!isExpanded("Script Actions")) return y;

        int buttonHeight = rowHeight;
        int mutedColor = Theme.toArgb(theme.textMuted);

        int refreshWidth = 90;
        int statusWidth = Math.max(1, width - refreshWidth - theme.design.space_sm);
        String statusText;
        int statusColor = mutedColor;

        if (cache.pending) {
            statusText = "Loading…";
        } else if (cache.error != null && !cache.error.isBlank()) {
            statusText = cache.error;
            statusColor = Theme.toArgb(theme.danger);
        } else if (cache.actions == null || cache.actions.isEmpty()) {
            statusText = "No actions";
        } else {
            statusText = "Actions: " + cache.actions.size();
        }

        renderer.drawText(statusText, x, renderer.baselineForBox(y, buttonHeight), statusColor);

        boolean refreshClicked = EditorUiUtil.buttonOutlined(ui, renderer, theme, x + statusWidth + theme.design.space_sm, y, refreshWidth, buttonHeight, "Refresh", interactive);
        if (refreshClicked) {
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
        y += buttonHeight;

        if (cache.pending) return y;

        List<String> actions = cache.actions == null ? List.of() : cache.actions;
        for (String action : actions) {
            if (action == null || action.isBlank()) continue;

            boolean clicked = EditorUiUtil.buttonOutlined(ui, renderer, theme, x, y, width, buttonHeight, action, interactive);
            if (clicked) {
                Session session = runtime.session();
                EditorNet net = runtime.net();
                if (session != null && net != null) {
                    net.invokeScriptAction(session, state, nodeId, action);
                }
            }
            y += buttonHeight;
        }

        if (actions.isEmpty() && cache.error == null) {
            renderer.drawText("(tool=true + actions={...})", x, renderer.baselineForBox(y, buttonHeight), mutedColor);
            y += buttonHeight;
        }

        return y;
    }

    int renderGroupHeader(Ui ui, UiRenderer renderer, Theme theme, int x, int y, int width, String title) {
        int height = 26;
        int backgroundColor = Theme.toArgb(theme.headerBg);
        int hoverColor = Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.7f);
        boolean isExpanded = isExpanded(title);

        boolean canInteract = (runtime == null || !runtime.uiBlocked()) && ui.input() != null;
        float mouseX = canInteract ? ui.mouse().x : -1;
        float mouseY = canInteract ? ui.mouse().y : -1;
        boolean isHovered = canInteract && mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;

        renderer.drawRect(x, y, width, height, isHovered ? hoverColor : backgroundColor);
        renderer.drawRect(x, y, 3, height, Theme.toArgb(theme.accent));

        float iconSize = Math.min(theme.design.icon_sm, height - 8);
        Icon icon = isExpanded ? Icon.CHEVRON_DOWN : Icon.CHEVRON_RIGHT;
        MoudIcons.drawOrFallback(renderer, theme, icon, x + 6, y + (height - iconSize) * 0.5f, iconSize, Theme.toArgb(theme.textMuted));
        renderer.drawText(title, x + 22, renderer.baselineForBox(y, height), Theme.toArgb(theme.text));

        renderer.drawRect(x, y + height - 1, width, 1, Theme.toArgb(theme.headerLine));

        if (isHovered && canInteract && ui.input().mousePressed()) {
            groupExpanded.put(title, !isExpanded);
        }

        return y + height;
    }

    private int renderPropertyRow(Ui ui, UiRenderer renderer, UiContext uiContext, Theme theme, long nodeId, PropertyDef property, int x, int y, int width, int rowHeight, int labelWidth, String value) {
        int valueX = x + labelWidth + theme.design.space_sm;
        int valueWidth = Math.max(1, width - (valueX - x));

        renderer.drawText(property.uiLabel(), x, renderer.baselineForBox(y, rowHeight), Theme.toArgb(theme.textMuted));

        if (property.type() == PropertyType.BOOL) {
            boolean interactive = runtime != null && !runtime.uiBlocked();
            boolean parsedBool = ParseUtils.parseBool(value, ParseUtils.parseBool(property.defaultValue(), false));
            renderBool(ui, renderer, theme, valueX, y, valueWidth, rowHeight, parsedBool, interactive, next -> commitBoolProperty(nodeId, property.key(), next));
            return y + rowHeight;
        }

        if (property.type() == PropertyType.INT || property.type() == PropertyType.FLOAT) {
            DraggableNumberField numberField = numberField(property.key(), property, ParseUtils.parseFloat(value, 0.0f));
            syncNumberValue(uiContext, numberField, ParseUtils.parseFloat(value, numberField.value()));
            var input = (runtime != null && !runtime.uiBlocked()) ? ui.input() : null;
            numberField.render(renderer, uiContext, input, theme, valueX, y + 2, valueWidth, rowHeight - 4, true);
            return y + rowHeight;
        }

        boolean isScriptPath = "script".equals(property.key());
        boolean hasScript = isScriptPath && value != null && !value.trim().isEmpty();
        boolean isImageAsset = isAssetKind(property, "image");
        boolean isShaderAsset = isAssetKind(property, "shader");
        boolean isMaterialAsset = isAssetKind(property, "material");
        boolean isTextAsset = isAssetKind(property, "text");
        boolean isAnyAsset = isImageAsset || isShaderAsset || isMaterialAsset || isTextAsset;

        int iconButtonWidth = Math.max(18, rowHeight - 4);
        int iconButtonHeight = rowHeight - 4;
        int iconGap = theme.design.space_xs;
        int iconCount = (isAnyAsset ? 1 : 0) + (isScriptPath ? 1 : 0) + (hasScript ? 1 : 0);
        int iconsTotalWidth = iconCount == 0 ? 0 : (iconCount * iconButtonWidth + (iconCount - 1) * iconGap);
        int fieldToIconsGap = iconCount == 0 ? 0 : iconGap;

        int fieldWidth = Math.max(1, valueWidth - iconsTotalWidth - fieldToIconsGap);
        TextField textField = stringFields.computeIfAbsent(property.key(), k -> new TextField());

        if (uiContext == null || !textField.isFocused(uiContext)) {
            textField.setText(value == null ? "" : value);
            textField.setCursorPos(textField.text().length());
        }

        var input = (runtime != null && !runtime.uiBlocked()) ? ui.input() : null;
        textField.render(renderer, uiContext, input, theme, valueX, y + 2, fieldWidth, rowHeight - 4, true);

        int buttonY = y + 2;
        int buttonX = valueX + fieldWidth + fieldToIconsGap;

        if (isAnyAsset) {
            Icon icon = isImageAsset ? Icon.IMAGE : (isShaderAsset ? Icon.CODE : Icon.TEXT);
            int menuX = buttonX;
            int menuY = buttonY + iconButtonHeight;
            EditorUiUtil.iconButtonOutlined(ui, renderer, theme, menuX, buttonY, iconButtonWidth, iconButtonHeight, icon, input != null, () -> toggleAssetMenu(menuX, menuY, nodeId, property));
            buttonX += iconButtonWidth + iconGap;
        }

        if (isScriptPath) {
            EditorUiUtil.iconButtonOutlined(ui, renderer, theme, buttonX, buttonY, iconButtonWidth, iconButtonHeight, Icon.ADD, input != null, () -> attachScriptFromFile(nodeId));
            buttonX += iconButtonWidth + iconGap;
        }

        if (hasScript) {
            String script = value == null ? "" : value;
            EditorUiUtil.iconButtonOutlined(ui, renderer, theme, buttonX, buttonY, iconButtonWidth, iconButtonHeight, Icon.CODE, input != null, () -> runtime.openScriptEditor(nodeId, script));
        }

        return y + rowHeight;
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
            String selectedPath = TinyFileDialogs.tinyfd_openFileDialog("Attach Script (.js, .luau)", "", null, "Script (.js, .luau)", false);
            if (selectedPath == null || selectedPath.isBlank()) return;

            File file = new File(selectedPath);
            if (!file.exists() || !file.isFile()) {
                runtime.requestToast("Script file not found", true, 4500);
                return;
            }

            String filename = file.getName();
            if (filename == null || filename.isBlank()) {
                runtime.requestToast("Invalid filename", true, 4500);
                return;
            }

            String lower = filename.toLowerCase(Locale.ROOT);
            boolean isLuau = lower.endsWith(".luau");
            if (!(lower.endsWith(".js") || lower.endsWith(".mjs") || lower.endsWith(".cjs") || isLuau)) {
                filename = filename + ".js";
                lower = filename.toLowerCase(Locale.ROOT);
                isLuau = false;
            }

            String scriptPath = "res://scripts/" + filename;
            try {
                new ResPath(scriptPath);
            } catch (Exception ignored) {
                scriptPath = "res://scripts/node_" + nodeId + (isLuau ? ".luau" : ".js");
            }

            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            net.writeScriptFile(session, state, scriptPath, content);

            sendOpsRecorded(List.of(new SceneOp.SetProperty(nodeId, "script", scriptPath)));
            runtime.requestToast("Attached script: " + scriptPath, false, 2500);
            runtime.openScriptEditor(nodeId, scriptPath);

        } catch (Exception exception) {
            String message = exception.getMessage();
            runtime.requestToast("Attach failed" + (message == null || message.isBlank() ? "" : ": " + message), true, 6000);
        }
    }

    static boolean isAssetKind(PropertyDef property, String targetKind) {
        if (property == null || property.editorHints() == null || targetKind == null) return false;
        String assetKind = property.editorHints().get("asset");
        return assetKind != null && assetKind.equalsIgnoreCase(targetKind);
    }

    private void toggleAssetMenu(int x, int y, long nodeId, PropertyDef property) {
        if (property == null || property.key() == null || property.key().isBlank()) return;

        if (assetMenu.isOpen() && nodeId == assetMenuNodeId && Objects.equals(assetMenuKey, property.key())) {
            assetMenu.close();
            return;
        }

        assetMenuNodeId = nodeId;
        assetMenuKey = property.key();
        buildAssetMenu(property);
        EditorUiUtil.openMenuClamped(assetMenu, runtime, x, y);
    }

    private void buildAssetMenu(PropertyDef property) {
        assetMenu.clear();
        String propertyKey = property.key();
        String defaultValue = property.defaultValue() == null ? "" : property.defaultValue();
        String kind = property.editorHints() == null ? "" : property.editorHints().getOrDefault("asset", "");
        String kindLower = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);

        assetMenu.addItem("Default", () -> commitStringProperty(propertyKey, defaultValue));
        assetMenu.addItem("Clear", () -> commitStringProperty(propertyKey, ""));

        if ("image".equals(kindLower)) {
            assetMenu.addSeparator();
            assetMenu.addItem(MoudTextures.WHITE_ID.toString(), () -> commitStringProperty(propertyKey, MoudTextures.WHITE_ID.toString()));

            List<String> images = MoudTextures.imageAssetPaths();
            if (images.isEmpty()) {
                assetMenu.addSeparator();
                assetMenu.addItem("(No res:// images)", () -> {});
                return;
            }

            assetMenu.addSeparator();
            for (String path : images) {
                if (path == null || path.isBlank()) continue;
                assetMenu.addItem(path, () -> commitStringProperty(propertyKey, path));
            }
            return;
        }

        List<String> textAssets = MoudTextAssets.textAssetPaths();
        if (textAssets.isEmpty()) {
            assetMenu.addSeparator();
            assetMenu.addItem("(No res:// text assets)", () -> {});
            return;
        }

        String suffixFilter = null;
        if ("shader".equals(kindLower)) {
            suffixFilter = ".moudshader";
        } else if ("material".equals(kindLower)) {
            suffixFilter = ".moudmat";
        }

        boolean isMaterial = "material".equals(kindLower);
        boolean foundAny = false;

        assetMenu.addSeparator();
        for (String path : textAssets) {
            if (path == null || path.isBlank()) continue;
            if (suffixFilter != null && !path.toLowerCase(Locale.ROOT).endsWith(suffixFilter)) continue;

            foundAny = true;
            String label = path;
            if (isMaterial) {
                label = path.replace("res://materials/", "").replace(".moudmat", "");
                MaterialPreviewRenderer.request(path);
            }
            String finalPath = path;
            assetMenu.addItem(label, () -> commitStringProperty(propertyKey, finalPath));
        }

        if (!foundAny && suffixFilter != null) {
            assetMenu.addItem("(No " + suffixFilter + " files)", () -> {});
        }
    }

    private void renderAssetMenu(Ui ui, UiRenderer renderer, Theme theme, boolean interactive) {
        if (ui == null || renderer == null || theme == null || !assetMenu.isOpen()) return;

        var input = interactive ? ui.input() : null;
        int itemHeight = theme.design.menu_item_height;

        if (input != null) {
            assetMenu.updateFromInput(input, theme, itemHeight);
            EditorUiUtil.clampOpenMenuToScreen(assetMenu, runtime);
        }

        assetMenu.render(renderer, theme, itemHeight, Theme.toArgb(theme.panelBg), Theme.toArgb(theme.widgetHover), Theme.toArgb(theme.text), assetMenu.hoverIndex());

        if (interactive && input != null && input.mousePressed()) {
            assetMenu.handleClick((int) ui.mouse().x, (int) ui.mouse().y, itemHeight);
        }
    }

    private int renderVec3Row(Ui ui, UiRenderer renderer, UiContext uiContext, Theme theme, long nodeId, NodeTypeDef typeDef, Map<String, String> values, int x, int y, int width, int rowHeight, int labelWidth, String label, String keyX, String keyY, String keyZ, String filterLower) {
        boolean hasAnyValues = values.containsKey(keyX) || values.containsKey(keyY) || values.containsKey(keyZ);
        if (!hasAnyValues) return y;

        if (!filterLower.isEmpty()) {
            StringBuilder searchBuilder = new StringBuilder(64);
            searchBuilder.append(label).append(' ').append(keyX).append(' ').append(keyY).append(' ').append(keyZ);
            if (typeDef != null) {
                PropertyDef propX = typeDef.properties().get(keyX);
                PropertyDef propY = typeDef.properties().get(keyY);
                PropertyDef propZ = typeDef.properties().get(keyZ);
                if (propX != null && propX.uiLabel() != null) searchBuilder.append(' ').append(propX.uiLabel());
                if (propY != null && propY.uiLabel() != null) searchBuilder.append(' ').append(propY.uiLabel());
                if (propZ != null && propZ.uiLabel() != null) searchBuilder.append(' ').append(propZ.uiLabel());
            }
            String searchTarget = searchBuilder.toString().toLowerCase(Locale.ROOT);
            if (!searchTarget.contains(filterLower)) return y;
        }

        renderer.drawText(label, x, renderer.baselineForBox(y, rowHeight), Theme.toArgb(theme.textMuted));

        int valueX = x + labelWidth + theme.design.space_sm;
        int valueWidth = Math.max(1, width - (valueX - x));
        int gap = theme.design.space_xs;
        int componentWidth = Math.max(1, (valueWidth - gap * 2) / 3);
        int fieldHeight = rowHeight - 4;
        int fieldY = y + 2;

        PropertyDef propX = typeDef != null ? typeDef.properties().get(keyX) : null;
        PropertyDef propY = typeDef != null ? typeDef.properties().get(keyY) : null;
        PropertyDef propZ = typeDef != null ? typeDef.properties().get(keyZ) : null;

        renderPrefixedNumber(ui, renderer, uiContext, theme, nodeId, propX, keyX, valueX, fieldY, componentWidth, fieldHeight, "x", values.get(keyX));
        renderPrefixedNumber(ui, renderer, uiContext, theme, nodeId, propY, keyY, valueX + componentWidth + gap, fieldY, componentWidth, fieldHeight, "y", values.get(keyY));
        int lastX = valueX + (componentWidth + gap) * 2;
        renderPrefixedNumber(ui, renderer, uiContext, theme, nodeId, propZ, keyZ, lastX, fieldY, valueX + valueWidth - lastX, fieldHeight, "z", values.get(keyZ));

        return y + rowHeight;
    }

    private void renderPrefixedNumber(Ui ui, UiRenderer renderer, UiContext uiContext, Theme theme, long nodeId, PropertyDef propertyDef, String key, int x, int y, int width, int height, String prefix, String rawValue) {
        DraggableNumberField numberField = numberField(key, propertyDef, ParseUtils.parseFloat(rawValue, 0.0f));
        syncNumberValue(uiContext, numberField, ParseUtils.parseFloat(rawValue, numberField.value()));

        var input = (runtime != null && !runtime.uiBlocked()) ? ui.input() : null;
        numberField.render(renderer, uiContext, input, theme, x, y, width, height, true);

        int mutedColor = Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.70f);
        renderer.drawText(prefix, x + 4, renderer.baselineForBox(y, height), mutedColor);
    }

    private DraggableNumberField numberField(String key, PropertyDef propertyDef, float initialValue) {
        return numberFields.computeIfAbsent(key, k -> {
            float minBoundary = -1_000_000.0f;
            float maxBoundary = 1_000_000.0f;
            DraggableNumberField field = new DraggableNumberField(initialValue, minBoundary, maxBoundary);

            if (propertyDef != null) {
                numberFieldTypes.put(key, propertyDef.type());

                if (propertyDef.editorHints() != null) {
                    String stepSize = propertyDef.editorHints().get("step");
                    if (stepSize != null) {
                        field.setSnapStep(ParseUtils.parseFloat(stepSize, 1.0f));
                    }

                    float rangeMin = minBoundary;
                    float rangeMax = maxBoundary;

                    String minString = propertyDef.editorHints().get("min");
                    if (minString != null) {
                        rangeMin = ParseUtils.parseFloat(minString, rangeMin);
                    }

                    String maxString = propertyDef.editorHints().get("max");
                    if (maxString != null) {
                        rangeMax = ParseUtils.parseFloat(maxString, rangeMax);
                    }

                    field.setRange(rangeMin, rangeMax);
                }
            }

            field.setListener(value -> {
                if (!syncingNumbers) {
                    pendingNumbers.put(key, value);
                }
            });

            return field;
        });
    }

    private void syncNumberValue(UiContext uiContext, DraggableNumberField numberField, float value) {
        if (numberField == null || numberField.isEditing()) return;
        if (uiContext != null && uiContext.pointer().isCaptured(numberField.id())) return;

        syncingNumbers = true;
        try {
            numberField.setValue(value);
        } finally {
            syncingNumbers = false;
        }
    }

    private static Collection<Long> effectiveSelectedIds(EditorState state) {
        if (state.selectedIds.size() >= 2) return state.selectedIds;
        if (state.selectedId > 0L) return List.of(state.selectedId);
        return List.of();
    }

    private Map<String, String> mergePropertyValues(EditorState state, Map<String, String> primaryValues) {
        HashMap<String, String> merged = new HashMap<>(primaryValues);
        for (long id : state.selectedIds) {
            if (id == state.selectedId) continue;
            SceneSnapshot.NodeSnapshot node = state.scene != null ? state.scene.getNode(id) : null;
            if (node == null) continue;
            Map<String, String> nodeValues = toPropertyMap(node.properties());
            for (String key : new ArrayList<>(merged.keySet())) {
                if (!Objects.equals(merged.get(key), nodeValues.get(key))) {
                    merged.put(key, "\u2014");
                }
            }
        }
        return merged;
    }

    private void flushPendingNumberOps(UiContext uiContext, long nodeId) {
        if (pendingNumbers.isEmpty()) return;

        EditorState state = runtime.state();
        Session session = runtime.session();
        EditorNet net = runtime.net();

        if (state == null || session == null || net == null) return;

        long currentTime = System.currentTimeMillis();
        ArrayList<String> keysToProcess = new ArrayList<>(pendingNumbers.keySet());

        for (String key : keysToProcess) {
            DraggableNumberField numberField = numberFields.get(key);
            boolean isCaptured = numberField != null && uiContext != null && uiContext.pointer().isCaptured(numberField.id());

            if (!preDragNumbers.containsKey(key)) {
                String sceneValue = state.scene != null ? state.scene.getPropertyValue(nodeId, key) : null;
                preDragNumbers.put(key, sceneValue);
            }

            if (isCaptured) {
                long lastSentTime = lastSentNumberAtMs.getOrDefault(key, 0L);
                if ((currentTime - lastSentTime) < DRAG_NUMBER_SEND_INTERVAL_MS) continue;
            }

            float value = pendingNumbers.get(key);
            PropertyType type = numberFieldTypes.get(key);
            float comparisonValue = type == PropertyType.INT ? Math.round(value) : value;
            Float lastSentValue = lastSentNumbers.get(key);

            if (lastSentValue != null && Math.abs(lastSentValue - comparisonValue) < 1e-6f) {
                pendingNumbers.remove(key);
                if (!isCaptured) preDragNumbers.remove(key);
                continue;
            }

            lastSentNumbers.put(key, comparisonValue);
            lastSentNumberAtMs.put(key, currentTime);
            pendingNumbers.remove(key);

            String encodedValue = type == PropertyType.INT ? Integer.toString(Math.round(value)) : ParseUtils.trimFloat(value);
            Collection<Long> ids = effectiveSelectedIds(state);
            ArrayList<SceneOp> operations = new ArrayList<>(ids.size());
            for (long id : ids) {
                operations.add(new SceneOp.SetProperty(id, key, encodedValue));
            }
            net.sendOps(session, state, operations);

            if (!isCaptured) {
                String oldValue = preDragNumbers.remove(key);
                if (oldValue != null && !oldValue.equals(encodedValue)) {
                    ArrayList<SceneOp> undoOperations = new ArrayList<>(ids.size());
                    for (long id : ids) {
                        undoOperations.add(new SceneOp.SetProperty(id, key, oldValue));
                    }
                    runtime.history().push(undoOperations, new ArrayList<>(operations));
                }
            }
        }
    }

    private static boolean isFogColorKey(String key) {
        return "fog_color_r".equals(key) || "fog_color_g".equals(key) || "fog_color_b".equals(key);
    }

    private static boolean isColorTintKey(String key) {
        return "color_tint_r".equals(key) || "color_tint_g".equals(key) || "color_tint_b".equals(key);
    }

    private int renderFogColorPicker(Ui ui, UiRenderer renderer, UiContext uiContext, Theme theme, long nodeId, Map<String, String> values, int x, int y, int width, int rowHeight, int labelWidth) {
        float red = ParseUtils.parseFloat(values.get("fog_color_r"), 0.5f);
        float green = ParseUtils.parseFloat(values.get("fog_color_g"), 0.5f);
        float blue = ParseUtils.parseFloat(values.get("fog_color_b"), 0.5f);

        int valueX = x + labelWidth + theme.design.space_sm;
        int swatchWidth = Math.max(1, width - (valueX - x));

        renderer.drawText("Fog Color", x, renderer.baselineForBox(y, rowHeight), Theme.toArgb(theme.textMuted));

        int swatchHeight = rowHeight - 4;
        int swatchY = y + 2;
        int swatchColor = 0xFF000000 | (Math.round(MathUtils.clamp01(red) * 255) << 16) | (Math.round(MathUtils.clamp01(green) * 255) << 8) | Math.round(MathUtils.clamp01(blue) * 255);

        renderer.drawRoundedRect(valueX, swatchY, swatchWidth, swatchHeight, theme.design.radius_sm, swatchColor, theme.design.border_thin, Theme.toArgb(theme.widgetOutline));

        var input = (runtime != null && !runtime.uiBlocked()) ? ui.input() : null;
        boolean canInteract = input != null;
        float mouseX = canInteract ? input.mousePos().x : -1;
        float mouseY = canInteract ? input.mousePos().y : -1;
        boolean isHovered = canInteract && mouseX >= valueX && mouseY >= swatchY && mouseX < valueX + swatchWidth && mouseY < swatchY + swatchHeight;

        if (isHovered && canInteract && input.mousePressed()) {
            fogColorPickerOpen = !fogColorPickerOpen;
            if (fogColorPickerOpen) {
                tintColorPickerOpen = false;
                float[] hsv = rgbToHsv(red, green, blue);
                fogColorPicker.setHsva(hsv[0], hsv[1], hsv[2], 1.0f);
            }
        }

        int cursorY = y + rowHeight;

        if (fogColorPickerOpen) {
            int pickerHeight = 160;
            int pickerWidth = Math.max(200, width);
            boolean changed = fogColorPicker.render(renderer, input, theme, x, cursorY, pickerWidth, pickerHeight, true);

            if (changed) {
                int argb = fogColorPicker.toArgb();
                float newRed = ((argb >> 16) & 0xFF) / 255.0f;
                float newGreen = ((argb >> 8) & 0xFF) / 255.0f;
                float newBlue = (argb & 0xFF) / 255.0f;
                commitFogColor(nodeId, newRed, newGreen, newBlue);
            }
            cursorY += pickerHeight + theme.design.space_sm;
        }

        return cursorY;
    }

    private int renderTintColorPicker(Ui ui, UiRenderer renderer, UiContext uiContext, Theme theme, long nodeId, Map<String, String> values, int x, int y, int width, int rowHeight, int labelWidth) {
        float red = ParseUtils.parseFloat(values.get("color_tint_r"), 1.0f);
        float green = ParseUtils.parseFloat(values.get("color_tint_g"), 1.0f);
        float blue = ParseUtils.parseFloat(values.get("color_tint_b"), 1.0f);

        int valueX = x + labelWidth + theme.design.space_sm;
        int swatchWidth = Math.max(1, width - (valueX - x));

        renderer.drawText("Color Tint", x, renderer.baselineForBox(y, rowHeight), Theme.toArgb(theme.textMuted));

        int swatchHeight = rowHeight - 4;
        int swatchY = y + 2;
        int swatchColor = 0xFF000000 | (Math.round(MathUtils.clamp01(red) * 255) << 16) | (Math.round(MathUtils.clamp01(green) * 255) << 8) | Math.round(MathUtils.clamp01(blue) * 255);

        renderer.drawRoundedRect(valueX, swatchY, swatchWidth, swatchHeight, theme.design.radius_sm, swatchColor, theme.design.border_thin, Theme.toArgb(theme.widgetOutline));

        var input = (runtime != null && !runtime.uiBlocked()) ? ui.input() : null;
        boolean canInteract = input != null;
        float mouseX = canInteract ? input.mousePos().x : -1;
        float mouseY = canInteract ? input.mousePos().y : -1;
        boolean isHovered = canInteract && mouseX >= valueX && mouseY >= swatchY && mouseX < valueX + swatchWidth && mouseY < swatchY + swatchHeight;

        if (isHovered && canInteract && input.mousePressed()) {
            tintColorPickerOpen = !tintColorPickerOpen;
            if (tintColorPickerOpen) {
                fogColorPickerOpen = false;
                float[] hsv = rgbToHsv(red, green, blue);
                tintColorPicker.setHsva(hsv[0], hsv[1], hsv[2], 1.0f);
            }
        }

        int cursorY = y + rowHeight;

        if (tintColorPickerOpen) {
            int pickerHeight = 160;
            int pickerWidth = Math.max(200, width);
            boolean changed = tintColorPicker.render(renderer, input, theme, x, cursorY, pickerWidth, pickerHeight, true);

            if (changed) {
                int argb = tintColorPicker.toArgb();
                float newRed = ((argb >> 16) & 0xFF) / 255.0f;
                float newGreen = ((argb >> 8) & 0xFF) / 255.0f;
                float newBlue = (argb & 0xFF) / 255.0f;
                commitTintColor(nodeId, newRed, newGreen, newBlue);
            }
            cursorY += pickerHeight + theme.design.space_sm;
        }

        return cursorY;
    }

    private void commitFogColor(long nodeId, float red, float green, float blue) {
        sendOpsRecorded(List.of(
                new SceneOp.SetProperty(nodeId, "fog_color_r", ParseUtils.trimFloat(red)),
                new SceneOp.SetProperty(nodeId, "fog_color_g", ParseUtils.trimFloat(green)),
                new SceneOp.SetProperty(nodeId, "fog_color_b", ParseUtils.trimFloat(blue))
        ));
    }

    private void commitTintColor(long nodeId, float red, float green, float blue) {
        sendOpsRecorded(List.of(
                new SceneOp.SetProperty(nodeId, "color_tint_r", ParseUtils.trimFloat(red)),
                new SceneOp.SetProperty(nodeId, "color_tint_g", ParseUtils.trimFloat(green)),
                new SceneOp.SetProperty(nodeId, "color_tint_b", ParseUtils.trimFloat(blue))
        ));
    }

    private static float[] rgbToHsv(float red, float green, float blue) {
        float max = Math.max(red, Math.max(green, blue));
        float min = Math.min(red, Math.min(green, blue));
        float delta = max - min;
        float hue = 0.0f;

        if (delta > 0.0f) {
            if (max == red) {
                hue = ((green - blue) / delta) % 6.0f;
            } else if (max == green) {
                hue = ((blue - red) / delta) + 2.0f;
            } else {
                hue = ((red - green) / delta) + 4.0f;
            }
            hue /= 6.0f;
            if (hue < 0.0f) hue += 1.0f;
        }

        float saturation = max > 0.0f ? delta / max : 0.0f;
        return new float[]{hue, saturation, max};
    }

    boolean isExpanded(String group) {
        return groupExpanded.getOrDefault(group == null ? "" : group, true);
    }

    private void onSelectionMaybeChanged(SceneSnapshot.NodeSnapshot selection) {
        if (selection == null) return;

        if (lastSelectedId == selection.nodeId() && Objects.equals(lastSelectedTypeId, selection.type())) {
            return;
        }

        lastSelectedId = selection.nodeId();
        lastSelectedTypeId = selection.type();

        renameField.setText(selection.name() == null ? "" : selection.name());
        renameField.setCursorPos(renameField.text().length());

        stringFields.clear();
        numberFields.clear();
        numberFieldTypes.clear();
        pendingNumbers.clear();
        lastSentNumbers.clear();
        lastSentNumberAtMs.clear();
        preDragNumbers.clear();

        materialEditor.onSelectionChanged();

        fogColorPickerOpen = false;
        tintColorPickerOpen = false;

        assetMenu.close();
        assetMenuNodeId = 0L;
        assetMenuKey = null;

        selectMenu.close();
        selectMenuNodeId = 0L;
        selectMenuKey = null;
    }

    private void sendOpsRecorded(List<SceneOp> operations) {
        EditorState state = runtime.state();
        Session session = runtime.session();
        EditorNet net = runtime.net();

        if (state == null || session == null || net == null) return;

        List<SceneOp> inverseOperations = EditorHistory.buildInverseOps(state.scene, operations);
        net.sendOps(session, state, operations);

        if (!inverseOperations.isEmpty()) {
            runtime.history().push(inverseOperations, operations);
        }
    }

    private void commitRename() {
        EditorState state = runtime.state();
        SceneSnapshot.NodeSnapshot selection = (state == null || state.scene == null) ? null : state.scene.getNode(state.selectedId);
        if (selection == null) return;

        String nextName = renameField.text();
        if (nextName == null) nextName = "";
        nextName = nextName.trim();

        if (nextName.isEmpty() || nextName.equals(selection.name())) return;

        sendOpsRecorded(List.of(new SceneOp.Rename(selection.nodeId(), nextName)));
    }

    private void commitBoolProperty(long nodeId, String key, boolean value) {
        EditorState state = runtime.state();
        if (state == null || state.scene == null) return;
        String encodedValue = value ? "true" : "false";
        Collection<Long> ids = effectiveSelectedIds(state);
        ArrayList<SceneOp> ops = new ArrayList<>();
        if ("editor_locked".equals(key) || "@locked".equals(key)) {
            for (long id : ids) {
                ops.add(new SceneOp.SetProperty(id, "editor_locked", encodedValue));
                ops.add(new SceneOp.SetProperty(id, "@locked", encodedValue));
            }
        } else {
            for (long id : ids) {
                ops.add(new SceneOp.SetProperty(id, key, encodedValue));
            }
        }
        if (!ops.isEmpty()) sendOpsRecorded(ops);
    }

    private void commitStringProperty(String key, String value) {
        EditorState state = runtime.state();
        if (state == null || state.scene == null) return;
        String nextValue = value == null ? "" : value;
        Collection<Long> ids = effectiveSelectedIds(state);
        ArrayList<SceneOp> ops = new ArrayList<>(ids.size());
        for (long id : ids) {
            ops.add(new SceneOp.SetProperty(id, key, nextValue));
        }
        if (!ops.isEmpty()) sendOpsRecorded(ops);
    }

    private static Map<String, String> toPropertyMap(List<SceneSnapshot.Property> properties) {
        HashMap<String, String> map = new HashMap<>();
        if (properties == null) return map;

        for (SceneSnapshot.Property property : properties) {
            if (property == null || property.key() == null || "@type".equals(property.key())) continue;
            map.put(property.key(), property.value());
        }

        if (map.containsKey("@locked") && !map.containsKey("editor_locked")) {
            map.put("editor_locked", map.get("@locked"));
        }

        return map;
    }

    static void renderBool(Ui ui, UiRenderer renderer, Theme theme, int x, int y, int width, int height, boolean value, boolean interactive, Consumer<Boolean> onToggle) {
        var input = interactive ? ui.input() : null;
        boolean canInteract = input != null;
        float mouseX = canInteract ? input.mousePos().x : -1;
        float mouseY = canInteract ? input.mousePos().y : -1;
        boolean isHovered = canInteract && mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;

        int boxSize = Math.min(16, height);
        int boxY = y + (height - boxSize) / 2;
        int outlineColor = Theme.mulAlpha(Theme.toArgb(theme.widgetOutline), 0.85f);
        int fillColor = value ? Theme.mulAlpha(Theme.toArgb(theme.widgetActive), 0.85f) : Theme.mulAlpha(Theme.toArgb(theme.widgetBg), 0.65f);

        if (isHovered) {
            fillColor = Theme.lerpArgbInt(fillColor, Theme.toArgb(theme.widgetHover), 0.35f);
        }

        renderer.drawRoundedRect(x, boxY, boxSize, boxSize, Math.min(theme.design.radius_sm, 3.0f), fillColor, theme.design.border_thin, outlineColor);

        if (value) {
            float iconSize = Math.min(theme.design.icon_sm, boxSize - 4);
            MoudIcons.drawOrFallback(renderer, theme, Icon.CHECK, x + (boxSize - iconSize) * 0.5f, boxY + (boxSize - iconSize) * 0.5f, iconSize, Theme.toArgb(theme.text));
        }

        renderer.drawText(value ? "true" : "false", x + boxSize + 10, renderer.baselineForBox(y, height), Theme.toArgb(theme.textMuted));

        if (isHovered && canInteract && input.mousePressed() && onToggle != null) {
            onToggle.accept(!value);
        }
    }
}
