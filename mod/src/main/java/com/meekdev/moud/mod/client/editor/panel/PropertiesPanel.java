package com.meekdev.moud.mod.client.editor.panel;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.kit.Category;
import com.meekdev.moud.mod.client.editor.kit.EmptyStates;
import com.meekdev.moud.mod.client.editor.kit.Notices;
import com.meekdev.moud.mod.client.editor.kit.Sections;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.style.ClassIcons;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import com.meekdev.moud.mod.client.editor.document.Rename;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImString;
import java.util.ArrayList;
import java.util.List;

public final class PropertiesPanel implements Panel {

    public static final String ID = "properties";

    private final SceneDocument document;
    private final IconWidgets icons;
    private final PropertyRows rows;
    private final ImString nameInput = new ImString(256);
    private int naming;
    private boolean nameActive;

    public PropertiesPanel(SceneDocument document, IconWidgets icons) {
        this.document = document;
        this.icons = icons;
        this.rows = new PropertyRows(document);
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
                if (targets.stream().allMatch(target -> PropertyRows.same(target, property) != null)) shared.add(property);
            }
            if (!shared.isEmpty() && Sections.header(level.name(), true, icons.textureId(ClassIcons.of(level)))) {
                for (PropertyDef property : shared) rows.render(instance, property, targets);
            }
            from = properties.length;
        }
        ImGui.endDisabled();
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
