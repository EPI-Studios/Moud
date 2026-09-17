package com.meekdev.moud.mod.client.editor.panel;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.Enums;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.part.PartShape;
import com.meekdev.moud.core.script.LocalScript;
import com.meekdev.moud.core.script.ModuleScript;
import com.meekdev.moud.core.script.Script;
import com.meekdev.moud.core.ui.GuiLayout;
import com.meekdev.moud.core.ui.UIComponent;
import com.meekdev.moud.core.ui.ViewportFrame;
import com.meekdev.moud.mod.addon.Addons;
import com.meekdev.moud.mod.client.editor.assets.AssetsPanel;
import com.meekdev.moud.mod.client.editor.document.Batch;
import com.meekdev.moud.mod.client.editor.document.Edit;
import com.meekdev.moud.mod.client.editor.document.Rename;
import com.meekdev.moud.mod.client.editor.document.Reparent;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.document.ScriptTemplate;
import com.meekdev.moud.mod.client.editor.document.SetProperty;
import com.meekdev.moud.mod.client.editor.files.CodeEditor;
import com.meekdev.moud.mod.client.editor.kit.Disclosure;
import com.meekdev.moud.mod.client.editor.kit.SearchField;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.style.ClassIcons;
import com.meekdev.moud.mod.client.editor.style.EditorIcon;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import com.meekdev.moud.mod.client.editor.viewport.Manipulate;
import com.meekdev.moud.mod.place.PlaceToml;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiListClipper;
import imgui.callback.ImListClipperCallback;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiSelectableFlags;
import imgui.type.ImString;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class ExplorerPanel implements Panel {

    public static final String ID = "explorer";

    static final String PAYLOAD_INSTANCE = "moud-instance";

    private static final List<String> COMMON_CLASSES = List.of(
            "Folder", "Model", "Part", "MeshPart", "SpawnLocation", "Camera", "CameraPath", "Attachment", "PointLight", "SpotLight", "AreaLight", "Sound", "Zone", "ClickDetector", "ScreenGui");
    private static final List<String> EFFECT_CLASSES = List.of(
            "ParticleEmitter", "Beam", "Trail", "Highlight", "Decal", "Texture", "Fire", "Smoke", "Sparkles", "SelectionBox", "SelectionSphere", "Explosion");
    private static final List<String> LIGHTING_CLASSES = List.of("Lighting", "Sky", "Atmosphere", "Clouds");
    private static final List<String> INTERFACE_CLASSES = List.of(
            "Frame", "TextLabel", "TextButton", "TextBox", "ImageLabel", "ImageButton", "ScrollingFrame", "CanvasGroup",
            "ViewportFrame", "UIListLayout", "UIGridLayout", "UIPadding", "UICorner", "UIStroke", "UIGradient",
            "UIAspectRatioConstraint", "UISizeConstraint", "UIScale");
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
    private static final int RENAME_CAPACITY = 256;

    private record Row(Instance instance, int depth, boolean hasChildren) {}

    private final SceneDocument document;
    private final IconWidgets icons;
    private final Runnable onFrameRequested;
    private final ImString filterInput = new ImString(FILTER_CAPACITY);
    private final List<Row> rows = new ArrayList<>();
    private final Set<Integer> collapsed = new HashSet<>();
    private int anchor = -1;
    private int renaming;
    private boolean focusRename;
    private final ImString renameInput = new ImString(RENAME_CAPACITY);

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

    public int insertParent() {
        Instance world = document.world();
        Instance primary = document.primary();
        if (document.editable(primary)) return primary.id();
        return world == null ? 0 : world.id();
    }

    void renderInsertItems(int parent) {
        Instance at = document.find(parent);
        boolean inInterface = at != null && GuiLayout.interfacePart(at) && !(at instanceof UIComponent) && !(at instanceof ViewportFrame);
        for (String name : inInterface ? INTERFACE_CLASSES : COMMON_CLASSES) {
            if (Addons.classes().find(name) != null && ImGui.menuItem(name)) document.insert(name, parent);
        }
        if (ImGui.beginMenu("Shapes")) {
            for (PartShape shape : PartShape.values()) {
                if (shape == PartShape.BLOCK) continue;
                String spelled = Enums.name(shape);
                if (ImGui.menuItem(Character.toUpperCase(spelled.charAt(0)) + spelled.substring(1))) {
                    document.insertShape(shape, parent);
                }
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Effects")) {
            for (String name : EFFECT_CLASSES) {
                if (ImGui.menuItem(name)) document.insert(name, parent);
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Lighting")) {
            for (String name : LIGHTING_CLASSES) {
                if (ImGui.menuItem(name)) document.insert(name, parent);
            }
            ImGui.endMenu();
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
        boolean any = world.children().stream().anyMatch(child -> child.id() >= 0);
        rows.add(new Row(world, 0, any));
        if (!query.isEmpty() || !collapsed.contains(world.id())) {
            for (Instance child : world.children()) collect(child, 1, query);
        }
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
        if (rows.size() <= 1 && !query().isEmpty()) {
            Texts.muted("Nothing matches the filter.");
            return;
        }
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
        icons.drawInline(row.instance() == document.world() ? EditorIcon.PACKED_SCENE : ClassIcons.of(row.instance().def()), EditorStyle.iconSizeSmall());
        if (renaming == row.instance().id()) renderRenameField();
        else renderSelectable(row, index);
        ImGui.unindent(row.depth() * EditorStyle.indentSpacing() + 1.0f);
        ImGui.popID();
    }

    public void beginRename(int id) {
        Instance instance = document.find(id);
        if (!document.editable(instance)) return;
        renaming = id;
        focusRename = true;
        renameInput.set(instance.name());
    }

    private void renderRenameField() {
        if (focusRename) {
            ImGui.setKeyboardFocusHere();
            focusRename = false;
        }
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        boolean submitted = ImGui.inputText("##rename", renameInput, ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll);
        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            renaming = 0;
            return;
        }
        if (submitted || (!focusRename && ImGui.isItemDeactivated())) commitRename();
    }

    private void commitRename() {
        int id = renaming;
        renaming = 0;
        String name = renameInput.get().trim();
        Instance instance = document.find(id);
        if (instance == null || name.isEmpty() || name.equals(instance.name())) return;
        document.history().execute(new Rename(document.ref(id), name));
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
        boolean root = instance == document.world();
        String shown = instance instanceof Part part && part.locked ? instance.name() + "  · locked" : instance.name();
        paintRow(left, top, shown, selected && !root, ImGui.isItemHovered(), editable || root);
        if (root) {
            if (activated) document.selection().clear();
            renderRootDropAndMenu(instance);
            return;
        }
        if (activated) handleRowClick(row, index);
        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
            if (!openScript(instance)) onFrameRequested.run();
        }
        if (ImGui.isItemHovered() && !editable) ImGui.setTooltip(instance.def().name() + ", made by the engine, not saved");
        if (editable) {
            renderRowDragSource(instance);
            renderRowDropTarget(instance);
            renderRowContextMenu(instance);
        }
    }

    private void renderRootDropAndMenu(Instance world) {
        if (ImGui.beginDragDropTarget()) {
            Integer dropped = ImGui.acceptDragDropPayload(PAYLOAD_INSTANCE, Integer.class);
            if (dropped != null) moveInto(dropped, world.id());
            String asset = ImGui.acceptDragDropPayload(AssetsPanel.PAYLOAD, String.class);
            if (asset != null) document.placeAsset(asset, world.id(), null);
            ImGui.endDragDropTarget();
        }
        if (ImGui.beginPopupContextItem("explorer-root-menu")) {
            if (ImGui.beginMenu("Insert")) {
                renderInsertItems(world.id());
                ImGui.endMenu();
            }
            renderScriptItems(world.id());
            if (ImGui.menuItem("Paste", "Ctrl+V")) document.pasteText(ImGui.getClipboardText());
            ImGui.endPopup();
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
        ImGui.setDragDropPayload(PAYLOAD_INSTANCE, instance.id(), ImGuiCond.Once);
        ImGui.textUnformatted(instance.name());
        ImGui.endDragDropSource();
    }

    private void renderRowDropTarget(Instance target) {
        if (!ImGui.beginDragDropTarget()) return;
        Integer dropped = ImGui.acceptDragDropPayload(PAYLOAD_INSTANCE, Integer.class);
        if (dropped != null) moveInto(dropped, target.id());
        String asset = ImGui.acceptDragDropPayload(AssetsPanel.PAYLOAD, String.class);
        if (asset != null) document.placeAsset(asset, target.id(), target instanceof Spatial ? Transforms.world(target).position() : null);
        ImGui.endDragDropTarget();
    }

    private void moveInto(int dropped, int parent) {
        List<Integer> moving = document.selection().isSelected(dropped) ? document.selection().all() : List.of(dropped);
        List<Edit> edits = new ArrayList<>();
        for (int id : moving) {
            if (id == parent || isAncestor(id, parent)) continue;
            edits.add(new Reparent(document.ref(id), document.ref(parent)));
            Edit placed = keepPlace(id, parent);
            if (placed != null) edits.add(placed);
        }
        if (edits.isEmpty()) return;
        document.history().execute(edits.size() == 1 ? edits.getFirst() : new Batch("Move", edits));
    }

    private @Nullable Edit keepPlace(int id, int parentId) {
        Instance instance = document.find(id);
        Instance parent = document.find(parentId);
        if (!(instance instanceof Spatial spatial) || parent == null) return null;
        PropertyDef property = instance.def().property("cframe");
        if (property == null) return null;
        CFrame before = Transforms.world(instance);
        CFrame parentWorld = Transforms.world(parent);
        ViewportFrame from = ViewportFrame.around(instance);
        ViewportFrame to = parent instanceof ViewportFrame viewport ? viewport : ViewportFrame.around(parent);
        CFrame target = from == to ? before : new CFrame(parentWorld.position(), before.rotation());
        CFrame local = parentWorld.inverse().mul(target).mul(CFrame.at(spatial.pivot));
        return new SetProperty(document.ref(id), property.index(), local, "Move");
    }

    private boolean isAncestor(int id, int of) {
        Instance ancestor = document.find(id);
        for (Instance at = document.find(of); at != null; at = at.parent()) {
            if (at == ancestor) return true;
        }
        return false;
    }

    private static boolean openScript(Instance instance) {
        String source = switch (instance) {
            case Script script -> script.source;
            case LocalScript script -> script.source;
            case ModuleScript script -> script.source;
            default -> null;
        };
        if (source == null || !source.startsWith(Res.SCHEME)) return false;
        Path file = PlaceToml.root().resolve(source.substring(Res.SCHEME.length()));
        CodeEditor.open(file, 1);
        return true;
    }

    private static boolean openable(Instance instance) {
        return instance instanceof Script || instance instanceof LocalScript || instance instanceof ModuleScript;
    }

    private void renderScriptItems(int parent) {
        if (ImGui.beginMenu("Add script")) {
            for (ScriptTemplate template : ScriptTemplate.SERVER) {
                if (ImGui.menuItem(template.label())) document.insertScript(template, false, parent);
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Add local script")) {
            for (ScriptTemplate template : ScriptTemplate.CLIENT) {
                if (ImGui.menuItem(template.label())) document.insertScript(template, true, parent);
            }
            ImGui.endMenu();
        }
        if (ImGui.menuItem("Add module script")) document.insertModule(ScriptTemplate.MODULE, parent);
    }

    private void renderRowContextMenu(Instance instance) {
        if (!ImGui.beginPopupContextItem("explorer-row-menu")) return;
        if (!document.selection().isSelected(instance.id())) document.selection().select(instance.id());
        if (ImGui.beginMenu("Insert child")) {
            renderInsertItems(instance.id());
            ImGui.endMenu();
        }
        renderScriptItems(instance.id());
        if (openable(instance) && ImGui.menuItem("Open script")) openScript(instance);
        ImGui.separator();
        if (ImGui.menuItem("Rename", "F2")) beginRename(instance.id());
        if (ImGui.menuItem("Duplicate", "Ctrl+D")) document.duplicateSelected();
        if (ImGui.menuItem("Copy", "Ctrl+C")) copy(document);
        if (ImGui.menuItem("Paste", "Ctrl+V")) document.pasteText(ImGui.getClipboardText());
        Instance world = document.world();
        if (world != null && instance.parent() != world && ImGui.menuItem("Move to top")) moveInto(instance.id(), world.id());
        if (ImGui.beginMenu("Select")) {
            if (ImGui.menuItem("Children", "Alt+Down")) document.selectChildren();
            if (ImGui.menuItem("Parent", "Alt+Up")) document.selectParent();
            if (ImGui.menuItem("Every " + instance.def().name())) {
                String name = instance.def().name();
                document.selectWhere(each -> each.def().name().equals(name));
            }
            for (String tag : instance.tags()) {
                if (ImGui.menuItem("Tagged " + tag)) document.selectWhere(each -> each.hasTag(tag));
            }
            ImGui.endMenu();
        }
        if (instance instanceof Part part) {
            if (ImGui.menuItem(part.locked ? "Unlock" : "Lock", part.locked ? "Ctrl+Shift+L" : "Ctrl+L")) Manipulate.lock(document, !part.locked);
        }
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
        if (ImGui.isKeyPressed(ImGuiKey.Escape, false)) document.selection().clear();
        if (ImGui.isKeyPressed(ImGuiKey.F, false)) onFrameRequested.run();
    }

    public static void copy(SceneDocument document) {
        String text = document.copySelected();
        if (text != null) ImGui.setClipboardText(text);
    }

    void deleteSelected() {
        document.deleteSelected();
    }
}
