package com.meekdev.moud.mod.client.editor.panel;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.mod.addon.Addons;
import com.meekdev.moud.mod.client.editor.document.Batch;
import com.meekdev.moud.mod.client.editor.document.Edit;
import com.meekdev.moud.mod.client.editor.document.Reparent;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.kit.Disclosure;
import com.meekdev.moud.mod.client.editor.kit.SearchField;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.style.ClassIcons;
import com.meekdev.moud.mod.client.editor.style.EditorIcon;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiListClipper;
import imgui.callback.ImListClipperCallback;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiSelectableFlags;
import imgui.type.ImString;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ExplorerPanel implements Panel {

    public static final String ID = "explorer";

    static final String PAYLOAD_INSTANCE = "moud-instance";

    private static final List<String> COMMON_CLASSES = List.of(
            "Folder", "Part", "MeshPart", "Attachment", "PointLight", "SpotLight", "AreaLight", "Sound", "Zone", "ScreenGui");
    private static final int TRANSPARENT = 0;
    private static final int SELECTION_COLOR_COUNT = 3;
    private static final float SELECTED_ALPHA = 0.16f;
    private static final float HOVER_ALPHA = 0.5f;
    private static final float MARKER_WIDTH = 2.0f;
    private static final float MARKER_INSET = 2.0f;
    private static final float LABEL_INSET = 6.0f;
    private static final float INDENT_GUIDE_OFFSET = 6.0f;
    private static final int INDENT_GUIDE_COLOR = EditorStyle.rgba(255, 255, 255, 26);
    private static final int FILTER_CAPACITY = 128;

    private record Row(Instance instance, int depth, boolean hasChildren) {}

    private final SceneDocument document;
    private final IconWidgets icons;
    private final Runnable onFrameRequested;
    private final ImString filterInput = new ImString(FILTER_CAPACITY);
    private final List<Row> rows = new ArrayList<>();
    private final Set<Integer> collapsed = new HashSet<>();
    private int anchor = -1;

    public ExplorerPanel(SceneDocument document, IconWidgets icons, Runnable onFrameRequested) {
        this.document = document;
        this.icons = icons;
        this.onFrameRequested = onFrameRequested;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Explorer";
    }

    @Override
    public void render() {
        renderHeader();
        ImGui.separator();
        rebuildRows();
        renderRows();
        renderBackgroundDropZone();
        handleShortcuts();
    }

    private void renderHeader() {
        if (icons.iconButton("explorer-add", EditorIcon.ADD, EditorStyle.iconSizeSmall())) ImGui.openPopup("explorer-insert");
        if (ImGui.isItemHovered()) ImGui.setTooltip("Insert");
        if (ImGui.beginPopup("explorer-insert")) {
            renderInsertItems(insertParent());
            ImGui.endPopup();
        }
        ImGui.sameLine();
        ImGui.beginDisabled(document.selection().count() == 0);
        if (icons.iconButton("explorer-remove", EditorIcon.REMOVE, EditorStyle.iconSizeSmall())) deleteSelected();
        if (ImGui.isItemHovered()) ImGui.setTooltip("Delete");
        ImGui.endDisabled();
        ImGui.sameLine();
        SearchField.render("##explorer-filter", "Filter", filterInput, ImGui.getContentRegionAvailX());
    }

    private int insertParent() {
        Instance world = document.world();
        Instance primary = document.primary();
        if (document.editable(primary)) return primary.id();
        return world == null ? 0 : world.id();
    }

    void renderInsertItems(int parent) {
        for (String name : COMMON_CLASSES) {
            if (Addons.classes().find(name) != null && ImGui.menuItem(name)) document.insert(name, parent);
        }
        ImGui.separator();
        if (!ImGui.beginMenu("All classes")) return;
        List<ClassDef<?>> all = new ArrayList<>(Addons.classes().all());
        all.sort(Comparator.comparing(ClassDef::name));
        for (ClassDef<?> def : all) {
            if (ImGui.menuItem(def.name())) document.insert(def.name(), parent);
        }
        ImGui.endMenu();
    }

    private String query() {
        return filterInput.get().replace("\0", "").strip().toLowerCase(Locale.ROOT);
    }

    private void rebuildRows() {
        rows.clear();
        Instance world = document.world();
        if (world == null) return;
        String query = query();
        for (Instance child : world.children()) collect(child, 0, query);
    }

    private boolean collect(Instance instance, int depth, String query) {
        if (instance.id() < 0) return false;
        int at = rows.size();
        boolean matches = query.isEmpty() || instance.name().toLowerCase(Locale.ROOT).contains(query);
        boolean hasChildren = instance.children().stream().anyMatch(child -> child.id() >= 0);
        rows.add(new Row(instance, depth, hasChildren));
        boolean childMatched = false;
        if (!query.isEmpty() || !collapsed.contains(instance.id())) {
            for (Instance child : instance.children()) childMatched |= collect(child, depth + 1, query);
        }
        if (!matches && !childMatched) {
            rows.subList(at, rows.size()).clear();
            return false;
        }
        return true;
    }

    private void renderRows() {
        if (rows.isEmpty()) {
            if (query().isEmpty()) {
                Texts.muted("The scene is empty.");
                Texts.muted("Right click or use the add button to insert one.");
            } else {
                Texts.muted("Nothing matches the filter.");
            }
            return;
        }
        ImGuiListClipper.forEach(rows.size(), new ImListClipperCallback() {
            @Override
            public void accept(int index) {
                renderRow(rows.get(index), index);
            }
        });
    }

    private void renderRow(Row row, int index) {
        ImGui.pushID(row.instance().id());
        drawIndentGuides(row.depth());
        ImGui.indent(row.depth() * EditorStyle.indentSpacing() + 1.0f);
        renderDisclosure(row);
        icons.drawInline(ClassIcons.of(row.instance().def()), EditorStyle.iconSizeSmall());
        renderSelectable(row, index);
        ImGui.unindent(row.depth() * EditorStyle.indentSpacing() + 1.0f);
        ImGui.popID();
    }

    private void renderDisclosure(Row row) {
        float size = EditorStyle.iconSizeSmall();
        if (!row.hasChildren()) {
            Disclosure.spacer(size);
            ImGui.sameLine();
            return;
        }
        int id = row.instance().id();
        if (Disclosure.arrow("##fold", !collapsed.contains(id), size) && !collapsed.remove(id)) collapsed.add(id);
        ImGui.sameLine();
    }

    private void renderSelectable(Row row, int index) {
        Instance instance = row.instance();
        boolean selected = document.selection().isSelected(instance.id());
        boolean editable = document.editable(instance);
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        ImGui.pushStyleColor(ImGuiCol.Header, TRANSPARENT);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, TRANSPARENT);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, TRANSPARENT);
        boolean activated = ImGui.selectable("##row", selected, ImGuiSelectableFlags.AllowDoubleClick);
        ImGui.popStyleColor(SELECTION_COLOR_COUNT);
        paintRow(left, top, instance.name(), selected, ImGui.isItemHovered(), editable);
        if (activated) handleRowClick(row, index);
        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) onFrameRequested.run();
        if (ImGui.isItemHovered() && !editable) ImGui.setTooltip(instance.def().name() + ", made by the engine, not saved");
        if (editable) {
            renderRowDragSource(instance);
            renderRowDropTarget(instance);
            renderRowContextMenu(instance);
        }
    }

    private static void paintRow(float left, float top, String label, boolean selected, boolean hovered, boolean editable) {
        float height = ImGui.getTextLineHeightWithSpacing();
        float right = left + ImGui.getContentRegionAvailX();
        ImDrawList drawList = ImGui.getWindowDrawList();
        int fill = selected
                ? EditorStyle.withAlpha(EditorStyle.COLOR_ACCENT, SELECTED_ALPHA)
                : EditorStyle.withAlpha(EditorStyle.COLOR_WIDGET_HOVER, hovered ? HOVER_ALPHA : 0.0f);
        drawList.addRectFilled(left, top, right, top + height, fill, EditorStyle.frameRounding());
        if (selected) {
            drawList.addRectFilled(left, top + MARKER_INSET,
                    left + EditorScale.ofAtLeastOne(MARKER_WIDTH), top + height - MARKER_INSET, EditorStyle.COLOR_ACCENT);
        }
        int color = !editable ? EditorStyle.COLOR_TEXT_FAINT : selected ? EditorStyle.COLOR_TEXT : EditorStyle.COLOR_TEXT_MUTED;
        drawList.addText(left + EditorScale.of(LABEL_INSET), top + (height - ImGui.getTextLineHeight()) * 0.5f, color, label);
    }

    private static void drawIndentGuides(int depth) {
        if (depth == 0) return;
        float startX = ImGui.getCursorScreenPosX();
        float startY = ImGui.getCursorScreenPosY();
        float height = ImGui.getTextLineHeightWithSpacing();
        for (int level = 0; level < depth; level++) {
            float x = startX + level * EditorStyle.indentSpacing() + EditorScale.of(INDENT_GUIDE_OFFSET);
            ImGui.getWindowDrawList().addLine(x, startY, x, startY + height, INDENT_GUIDE_COLOR);
        }
    }

    private void handleRowClick(Row row, int index) {
        int id = row.instance().id();
        if (ImGui.getIO().getKeyShift() && anchor >= 0) {
            selectRange(index);
            return;
        }
        if (ImGui.getIO().getKeyCtrl()) document.selection().toggle(id);
        else document.selection().select(id);
        anchor = id;
    }

    private void selectRange(int index) {
        int from = -1;
        for (int n = 0; n < rows.size(); n++) {
            if (rows.get(n).instance().id() == anchor) from = n;
        }
        if (from < 0) return;
        document.selection().clear();
        for (int n = Math.min(from, index); n <= Math.max(from, index); n++) document.selection().add(rows.get(n).instance().id());
    }

    private void renderRowDragSource(Instance instance) {
        if (!ImGui.beginDragDropSource()) return;
        ImGui.setDragDropPayload(PAYLOAD_INSTANCE, instance.id());
        ImGui.textUnformatted(instance.name());
        ImGui.endDragDropSource();
    }

    private void renderRowDropTarget(Instance target) {
        if (!ImGui.beginDragDropTarget()) return;
        Integer dropped = ImGui.acceptDragDropPayload(PAYLOAD_INSTANCE, Integer.class);
        if (dropped != null) moveInto(dropped, target.id());
        ImGui.endDragDropTarget();
    }

    private void moveInto(int dropped, int parent) {
        List<Integer> moving = document.selection().isSelected(dropped) ? document.selection().all() : List.of(dropped);
        List<Edit> edits = new ArrayList<>();
        for (int id : moving) {
            if (id != parent && !isAncestor(id, parent)) edits.add(new Reparent(id, parent));
        }
        if (edits.isEmpty()) return;
        document.history().execute(edits.size() == 1 ? edits.getFirst() : new Batch("Move", edits));
    }

    private boolean isAncestor(int id, int of) {
        Instance ancestor = document.find(id);
        for (Instance at = document.find(of); at != null; at = at.parent()) {
            if (at == ancestor) return true;
        }
        return false;
    }

    private void renderRowContextMenu(Instance instance) {
        if (!ImGui.beginPopupContextItem("explorer-row-menu")) return;
        if (!document.selection().isSelected(instance.id())) document.selection().select(instance.id());
        if (ImGui.beginMenu("Insert child")) {
            renderInsertItems(instance.id());
            ImGui.endMenu();
        }
        ImGui.separator();
        Instance world = document.world();
        if (world != null && instance.parent() != world && ImGui.menuItem("Move to top")) moveInto(instance.id(), world.id());
        ImGui.separator();
        if (ImGui.menuItem("Delete", "Del")) deleteSelected();
        ImGui.endPopup();
    }

    private void renderBackgroundDropZone() {
        ImGui.invisibleButton("##explorer-background", Math.max(1.0f, ImGui.getContentRegionAvailX()),
                Math.max(24.0f, ImGui.getContentRegionAvailY()));
        if (ImGui.isItemClicked()) document.selection().clear();
        if (ImGui.beginPopupContextItem("explorer-background-menu")) {
            Instance world = document.world();
            if (world != null) renderInsertItems(world.id());
            ImGui.endPopup();
        }
        if (!ImGui.beginDragDropTarget()) return;
        Integer dropped = ImGui.acceptDragDropPayload(PAYLOAD_INSTANCE, Integer.class);
        Instance world = document.world();
        if (dropped != null && world != null) moveInto(dropped, world.id());
        ImGui.endDragDropTarget();
    }

    private void handleShortcuts() {
        if (!ImGui.isWindowFocused() || ImGui.getIO().getWantTextInput()) return;
        if (ImGui.isKeyPressed(ImGuiKey.Delete, false)) deleteSelected();
        if (ImGui.isKeyPressed(ImGuiKey.Escape, false)) document.selection().clear();
        if (ImGui.isKeyPressed(ImGuiKey.F, false)) onFrameRequested.run();
    }

    void deleteSelected() {
        document.destroy(document.selection().all());
        document.selection().clear();
    }
}
