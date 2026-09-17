package com.meekdev.moud.mod.client.editor.panel;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.clazz.PropertyType;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.render.Camera;
import com.meekdev.moud.core.render.CameraPath;
import com.meekdev.moud.core.value.Value;
import com.meekdev.moud.mod.client.editor.document.Batch;
import com.meekdev.moud.mod.client.editor.document.Edit;
import com.meekdev.moud.mod.client.editor.document.Rename;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.document.SetProperty;
import com.meekdev.moud.mod.client.editor.document.Tag;
import com.meekdev.moud.mod.client.editor.kit.Category;
import com.meekdev.moud.mod.client.editor.kit.EmptyStates;
import com.meekdev.moud.mod.client.editor.kit.Notices;
import com.meekdev.moud.mod.client.editor.kit.SearchField;
import com.meekdev.moud.mod.client.editor.kit.Sections;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.style.ClassIcons;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import com.meekdev.moud.mod.client.editor.viewport.ViewTools;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImString;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class PropertiesPanel implements Panel {

    public static final String ID = "properties";

    private final SceneDocument document;
    private final IconWidgets icons;
    private final PropertyRows rows;
    private final AttributeRows attributes;
    private final ImString nameInput = new ImString(256);
    private int naming;
    private boolean nameActive;
    private final ImString search = new ImString(64);
    private final ImString tagInput = new ImString(64);
    private final ImString valueName = new ImString(64);
    private static @Nullable Map<String, Object> copiedAll;
    private @Nullable ViewTools view;
    private static final String[] VALUE_CLASSES = {"NumberValue", "StringValue", "BoolValue", "Vector3Value", "ObjectValue"};

    public PropertiesPanel(SceneDocument document, IconWidgets icons) {
        this.document = document;
        this.icons = icons;
        this.rows = new PropertyRows(document);
        this.attributes = new AttributeRows(document);
    }

    public void viewTools(ViewTools tools) {
        view = tools;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Properties";
    }

    @Override
    public void render() {
        rows.beginFrame();
        Instance selected = document.primary();
        if (selected == null) {
            EmptyStates.centered("Nothing selected", List.of("Pick an instance in the Explorer", "to edit its properties."));
        } else {
            renderBody(selected);
        }
        rows.pruneStaleKeys();
    }

    private void renderBody(Instance instance) {
        boolean editable = document.editable(instance);
        List<Instance> targets = new ArrayList<>();
        for (int id : document.selection().all()) {
            Instance target = document.find(id);
            if (document.editable(target)) targets.add(target);
        }
        if (targets.isEmpty()) targets.add(instance);
        int count = targets.size();
        Texts.muted(count > 1 ? count + " instances selected" : instance.def().name());
        Category.draw(count > 1 ? instance.name() + " and " + (count - 1) + " more" : instance.name(), icons.textureId(ClassIcons.of(instance.def())));
        if (!editable) Notices.info("Made by the engine, it is not part of the scene file.");
        renderToolbar(instance, targets);
        if (count == 1 && editable) renderCameraActions(instance);
        ImGui.separator();
        ImGui.beginDisabled(!editable);
        if (count == 1) renderName(instance);
        List<ClassDef<?>> chain = new ArrayList<>();
        for (ClassDef<?> at = instance.def(); at != null; at = at.parent()) chain.addFirst(at);
        int from = 0;
        for (ClassDef<?> level : chain) {
            PropertyDef[] properties = level.properties();
            List<PropertyDef> shared = new ArrayList<>();
            for (int index = from; index < properties.length; index++) {
                PropertyDef property = properties[index];
                if (!matches(property)) continue;
                if (targets.stream().allMatch(target -> PropertyRows.same(target, property) != null)) shared.add(property);
            }
            if (!shared.isEmpty() && Sections.header(level.name(), true, icons.textureId(ClassIcons.of(level)))) {
                for (PropertyDef property : shared) rows.render(instance, property, targets);
            }
            from = properties.length;
        }
        if (count == 1 && search.get().isBlank()) {
            renderTags(instance);
            if (Sections.header("Attributes", false)) attributes.render(instance);
            renderValues(instance);
        }
        ImGui.endDisabled();
    }

    private boolean matches(PropertyDef property) {
        String query = search.get().strip().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return true;
        String label = PropertyRows.labelOf(property).toLowerCase(Locale.ROOT);
        return label.contains(query) || property.name().toLowerCase(Locale.ROOT).contains(query)
                || (property.type() == PropertyType.CFRAME && ("position".contains(query) || "rotation".contains(query)));
    }

    private void renderToolbar(Instance instance, List<Instance> targets) {
        float menu = ImGui.getFrameHeight() * 1.6f;
        SearchField.render("##property-search", "Search properties", search, ImGui.getContentRegionAvailX() - menu - EditorStyle.itemSpacingX());
        ImGui.sameLine();
        if (ImGui.button("...##properties-menu", menu, ImGui.getFrameHeight())) ImGui.openPopup("##properties-menu");
        if (ImGui.isItemHovered()) ImGui.setTooltip("Copy or paste every property");
        if (!ImGui.beginPopup("##properties-menu")) return;
        if (ImGui.menuItem("Copy all properties")) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (PropertyDef property : instance.def().properties()) values.put(property.name() + "|" + property.type(), SceneDocument.wire(instance, property));
            copiedAll = values;
        }
        if (ImGui.menuItem("Paste all properties", "", false, copiedAll != null && document.editable(instance))) pasteAll(targets);
        ImGui.endPopup();
    }

    private void renderCameraActions(Instance instance) {
        ViewTools tools = view;
        if (tools == null) return;
        if (instance instanceof Camera) {
            if (ImGui.button("View from here")) tools.lookFrom(Transforms.world(instance));
            if (ImGui.isItemHovered()) ImGui.setTooltip("Move the editor camera to this camera");
            ImGui.sameLine();
            if (ImGui.button("Set to the view")) {
                PropertyDef frame = instance.def().property("cframe");
                CFrame local = Transforms.localFor(instance, tools.view()).mul(CFrame.at(((Spatial) instance).pivot));
                document.history().execute(new SetProperty(document.ref(instance.id()), frame.index(), local, "Set camera to the view"));
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip("Place this camera where the editor camera is, looking the same way");
        }
        if (instance instanceof CameraPath path) {
            if (tools.previewing()) {
                if (ImGui.button("Stop preview")) tools.stopPreview();
            } else if (ImGui.button("Preview")) {
                tools.preview(path);
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip(path.points().isEmpty() ? "Add points first" : "Fly the editor camera along the path. Right click or Escape stops it.");
            ImGui.sameLine();
            if (ImGui.button("Add point at the view")) {
                CFrame local = Transforms.world(path).inverse().mul(tools.view());
                document.addAttachment(path.id(), local, "Point" + (path.points().size() + 1));
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip("Adds an Attachment inside the path where the editor camera is. Points are passed through in Explorer order.");
        }
    }

    private void pasteAll(List<Instance> targets) {
        List<Edit> edits = new ArrayList<>();
        for (Instance target : targets) {
            for (PropertyDef property : target.def().properties()) {
                String key = property.name() + "|" + property.type();
                if (property.driven() || !copiedAll.containsKey(key) || property.name().equals("cframe")) continue;
                Object value = copiedAll.get(key);
                if (Objects.equals(value, SceneDocument.wire(target, property))) continue;
                edits.add(new SetProperty(document.ref(target.id()), property.index(), value, "Paste properties"));
            }
        }
        if (!edits.isEmpty()) document.history().execute(new Batch("Paste properties", edits));
    }

    private void renderTags(Instance instance) {
        if (!Sections.header("Tags", false)) return;
        float x = 0;
        float room = ImGui.getContentRegionAvailX();
        for (String tag : List.copyOf(instance.tags())) {
            float width = ImGui.calcTextSize(tag + "  x").x + ImGui.getStyle().getFramePaddingX() * 2;
            if (x > 0 && x + width < room) ImGui.sameLine();
            else x = 0;
            if (ImGui.button(tag + "  x##tag-" + tag)) document.history().execute(new Tag(document.ref(instance.id()), tag, false));
            if (ImGui.isItemHovered()) ImGui.setTooltip("Remove the tag " + tag);
            x += width + EditorStyle.itemSpacingX();
        }
        if (instance.tags().isEmpty()) Texts.muted("No tags. Scripts find tagged things with game.tags:tagged(name).");
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.inputTextWithHint("##new-tag", "Add a tag and press Enter", tagInput, ImGuiInputTextFlags.EnterReturnsTrue)) {
            String tag = tagInput.get().strip();
            if (!tag.isEmpty() && !instance.hasTag(tag)) document.history().execute(new Tag(document.ref(instance.id()), tag, true));
            tagInput.set("");
            ImGui.setKeyboardFocusHere(-1);
        }
    }

    private void renderValues(Instance instance) {
        if (!Sections.header("Values", false)) return;
        boolean any = false;
        for (Instance child : instance.children()) {
            if (!(child instanceof Value)) continue;
            PropertyDef value = child.def().property("value");
            if (value == null) continue;
            any = true;
            ImGui.pushID("value-" + child.id());
            rows.render(child, value, List.of(child), child.name());
            ImGui.popID();
        }
        if (!any) Texts.muted("Values stored on this instance, read by scripts as instance:values().");
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.5f);
        ImGui.inputTextWithHint("##value-name", "Name", valueName);
        ImGui.sameLine();
        if (ImGui.button("Add##value-add")) ImGui.openPopup("##value-kind");
        if (ImGui.beginPopup("##value-kind")) {
            for (String kind : VALUE_CLASSES) {
                if (ImGui.menuItem(kind.replace("Value", ""))) {
                    document.addValue(instance.id(), kind, valueName.get());
                    valueName.set("");
                }
            }
            ImGui.endPopup();
        }
    }

    private void renderName(Instance instance) {
        if (instance.id() != naming) {
            naming = instance.id();
            nameInput.set(instance.name());
        } else if (!nameActive && !nameInput.get().equals(instance.name())) {
            nameInput.set(instance.name());
        }
        float start = ImGui.getCursorPosX();
        float column = Math.clamp(ImGui.getContentRegionAvailX() * 0.36f, EditorScale.of(84.0f), EditorScale.of(150.0f));
        ImGui.alignTextToFramePadding();
        ImGui.textUnformatted("Name");
        ImGui.sameLine(start + column);
        ImGui.setNextItemWidth(-1.0f);
        boolean submitted = ImGui.inputText("##name", nameInput, ImGuiInputTextFlags.EnterReturnsTrue);
        nameActive = ImGui.isItemActive();
        if (submitted || ImGui.isItemDeactivatedAfterEdit()) {
            String name = nameInput.get().trim();
            if (!name.isEmpty() && !name.equals(instance.name())) document.history().execute(new Rename(document.ref(instance.id()), name));
        }
    }
}
