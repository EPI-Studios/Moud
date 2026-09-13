package com.meekdev.moud.mod.client.editor;

import com.meekdev.amnetic.client.ui.Inspector;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.mod.client.ClientScene;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class TreeInspector extends Inspector {

    private static final int MAX_CHILDREN = 200;

    private @Nullable Instance selected;

    public TreeInspector() {
        super("moud", "tree", true);
    }

    @Override
    public void render() {
        Instance world = ClientScene.world();
        if (world == null) {
            ImGui.textDisabled("no place running");
            return;
        }

        if (ImGui.beginChild("moud.outliner", 260, 0, true)) {
            node(world);
        }
        ImGui.endChild();

        ImGui.sameLine();

        if (ImGui.beginChild("moud.properties", 0, 0, true)) {
            properties();
        }
        ImGui.endChild();
    }

    private void node(Instance instance) {
        List<Instance> children = instance.children();
        int flags = ImGuiTreeNodeFlags.SpanAvailWidth
                | (children.isEmpty() ? ImGuiTreeNodeFlags.Leaf : 0)
                | (instance == selected ? ImGuiTreeNodeFlags.Selected : 0);

        boolean open = ImGui.treeNodeEx(
                instance.id() + "##" + instance.id(),
                flags,
                instance.name() + "  (" + instance.def().name() + ")");
        if (ImGui.isItemClicked()) selected = instance;

        if (open) {
            int shown = Math.min(children.size(), MAX_CHILDREN);
            for (int n = 0; n < shown; n++) node(children.get(n));
            if (children.size() > shown) {
                ImGui.textDisabled((children.size() - shown) + " more not listed");
            }
            ImGui.treePop();
        }
    }

    private void properties() {
        Instance instance = selected;
        if (instance == null) {
            ImGui.textDisabled("select something");
            return;
        }
        if (!instance.isAlive()) {
            ImGui.textDisabled("destroyed");
            selected = null;
            return;
        }

        ImGui.text(instance.name());
        ImGui.textDisabled(instance.def().name() + "  id " + instance.id()
                + "  " + instance.children().size() + " children");
        ImGui.separator();

        for (PropertyDef property : instance.def().properties()) {
            ImGui.text(property.name());
            ImGui.sameLine(160);
            ImGui.textDisabled(value(instance, property));
        }

        if (instance instanceof Spatial) {
            ImGui.separator();
            ImGui.text("worldCframe");
            ImGui.sameLine(160);
            ImGui.textDisabled(String.valueOf(Transforms.world(instance).position()));
        }
    }

    private static String value(Instance instance, PropertyDef property) {
        if (property.type().isBool()) return String.valueOf(property.getBool(instance));
        if (property.isNumeric()) return String.valueOf(property.getNum(instance));
        return String.valueOf(property.getObj(instance));
    }
}
