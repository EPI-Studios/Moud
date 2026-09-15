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
import imgui.ImGui;
import java.util.ArrayList;
import java.util.List;

public final class PropertiesPanel implements Panel {

    public static final String ID = "properties";

    private final SceneDocument document;
    private final IconWidgets icons;
    private final PropertyRows rows;

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
        Instance selected = document.selection().primary().stream().mapToObj(document::find).findFirst().orElse(null);
        if (selected == null) {
            EmptyStates.centered("Nothing selected", List.of("Pick an instance in the Explorer", "to edit its properties."));
        } else {
            renderBody(selected);
        }
        rows.pruneStaleKeys();
    }

    private void renderBody(Instance instance) {
        boolean editable = document.editable(instance);
        int count = document.selection().count();
        Texts.muted(instance.def().name() + (count > 1 ? "  ·  " + count + " selected, showing the last" : ""));
        Category.draw(instance.name(), icons.textureId(ClassIcons.of(instance.def())));
        if (!editable) Notices.info("Made by the engine, it is not part of the scene file.");
        ImGui.separator();
        ImGui.beginDisabled(!editable);
        List<ClassDef<?>> chain = new ArrayList<>();
        for (ClassDef<?> at = instance.def(); at != null; at = at.parent()) chain.addFirst(at);
        int from = 0;
        for (ClassDef<?> level : chain) {
            PropertyDef[] properties = level.properties();
            if (properties.length > from && Sections.header(level.name(), true, icons.textureId(ClassIcons.of(level)))) {
                for (int index = from; index < properties.length; index++) rows.render(instance, properties[index]);
            }
            from = properties.length;
        }
        ImGui.endDisabled();
    }
}
