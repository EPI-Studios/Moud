package com.meekdev.moud.mod.client.editor.panel;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.document.SetAttribute;
import com.meekdev.moud.mod.client.editor.kit.NumberFields;
import com.meekdev.moud.mod.client.editor.kit.Switches;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImGui;
import imgui.flag.ImGuiColorEditFlags;
import imgui.type.ImString;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

final class AttributeRows {

    private static final Map<String, Object> KINDS = new LinkedHashMap<>();

    static {
        KINDS.put("Boolean", false);
        KINDS.put("Number", 0.0);
        KINDS.put("String", "");
        KINDS.put("Vector3", Vector3.ZERO);
        KINDS.put("Color", Color.WHITE);
        KINDS.put("CFrame", CFrame.at(0, 0, 0));
        KINDS.put("UDim2", new UDim2(0, 0, 0, 0));
    }

    private final SceneDocument document;
    private final Map<String, ImString> strings = new HashMap<>();
    private final ImString newName = new ImString(128);
    private @Nullable String typing;

    AttributeRows(SceneDocument document) {
        this.document = document;
    }

    void render(Instance instance) {
        for (Map.Entry<String, Object> attribute : List.copyOf(instance.attributes().entrySet())) {
            ImGui.pushID("attribute-" + attribute.getKey());
            row(instance, attribute.getKey(), attribute.getValue());
            ImGui.popID();
        }
        if (instance.attributes().isEmpty()) Texts.muted("No attributes. Scripts read them with instance:getAttribute(name).");
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.5f);
        ImGui.inputTextWithHint("##attribute-new-name", "Name", newName);
        ImGui.sameLine();
        ImGui.beginDisabled(newName.get().isBlank());
        if (ImGui.button("Add attribute")) ImGui.openPopup("##attribute-new-kind");
        ImGui.endDisabled();
        if (!ImGui.beginPopup("##attribute-new-kind")) return;
        for (Map.Entry<String, Object> kind : KINDS.entrySet()) {
            if (!ImGui.menuItem(kind.getKey())) continue;
            String name = newName.get().strip();
            if (instance.attribute(name) == null) set(instance, name, kind.getValue(), "Add attribute " + name);
            newName.set("");
        }
        ImGui.endPopup();
    }

    private void row(Instance instance, String name, Object value) {
        float start = ImGui.getCursorPosX();
        float column = PropertyRows.labelColumn(ImGui.getContentRegionAvailX());
        ImGui.alignTextToFramePadding();
        ImGui.textUnformatted(name);
        if (ImGui.isItemHovered()) ImGui.setTooltip(kindOf(value) + ", right-click to remove");
        if (ImGui.beginPopupContextItem("##attribute-menu")) {
            if (ImGui.menuItem("Remove")) set(instance, name, null, "Remove attribute " + name);
            ImGui.endPopup();
        }
        ImGui.sameLine(start + column);
        float remove = ImGui.getFrameHeight();
        float width = ImGui.getContentRegionAvailX() - remove - EditorStyle.itemSpacingX();
        ImGui.setNextItemWidth(width);
        Object next = edit(instance.id() + ":" + name, value, width, start + column);
        if (next != null && !next.equals(value)) set(instance, name, next, "Set attribute " + name);
        ImGui.sameLine();
        if (ImGui.button("x##attribute-remove", remove, remove)) set(instance, name, null, "Remove attribute " + name);
        if (ImGui.isItemHovered()) ImGui.setTooltip("Remove " + name);
    }

    private @Nullable Object edit(String key, Object value, float width, float indent) {
        return switch (value) {
            case Boolean on -> Switches.draw("##value", on);
            case Double number -> {
                float updated = NumberFields.scalar("##" + key, number.floatValue(), PropertyRows.DRAG_STEP, width);
                yield Float.compare(updated, number.floatValue()) == 0 ? number : (double) updated;
            }
            case String text -> {
                ImString buffer = strings.computeIfAbsent(key, ignored -> new ImString(512));
                if (!key.equals(typing)) buffer.set(text);
                boolean changed = ImGui.inputText("##value", buffer);
                if (ImGui.isItemActive()) typing = key;
                else if (key.equals(typing)) typing = null;
                yield changed ? buffer.get() : text;
            }
            case Vector3 v -> {
                float[] values = {(float) v.x(), (float) v.y(), (float) v.z()};
                yield NumberFields.vector("##" + key, values, 3, width, PropertyRows.DRAG_STEP) ? new Vector3(values[0], values[1], values[2]) : v;
            }
            case Color c -> {
                float[] values = {c.r(), c.g(), c.b(), c.a()};
                yield ImGui.colorEdit4("##value", values, ImGuiColorEditFlags.AlphaBar) ? new Color(values[0], values[1], values[2], values[3]) : c;
            }
            case UDim2 u -> {
                float[] x = {(float) u.xScale(), (float) u.xOffset()};
                float[] y = {(float) u.yScale(), (float) u.yOffset()};
                boolean changed = NumberFields.pair("##" + key + "x", x, PropertyRows.UDIM_LABELS, PropertyRows.UDIM_STEPS, width);
                ImGui.setCursorPosX(indent);
                changed |= NumberFields.pair("##" + key + "y", y, PropertyRows.UDIM_LABELS, PropertyRows.UDIM_STEPS, width);
                yield changed ? new UDim2(x[0], x[1], y[0], y[1]) : u;
            }
            case CFrame frame -> {
                float[] position = {(float) frame.position().x(), (float) frame.position().y(), (float) frame.position().z()};
                Quat turn = frame.rotation();
                Vector3f angles = new Quaternionf((float) turn.x(), (float) turn.y(), (float) turn.z(), (float) turn.w())
                        .getEulerAnglesYXZ(new Vector3f()).mul(PropertyRows.RADIANS_TO_DEGREES);
                float[] degrees = {angles.x, angles.y, angles.z};
                boolean moved = NumberFields.vector("##" + key + "p", position, 3, width, PropertyRows.DRAG_STEP);
                ImGui.setCursorPosX(indent);
                boolean turned = NumberFields.vector("##" + key + "r", degrees, 3, width, PropertyRows.DEGREE_STEP);
                if (!moved && !turned) yield frame;
                Quaternionf rotated = new Quaternionf()
                        .rotateY(degrees[1] * PropertyRows.DEGREES_TO_RADIANS)
                        .rotateX(degrees[0] * PropertyRows.DEGREES_TO_RADIANS)
                        .rotateZ(degrees[2] * PropertyRows.DEGREES_TO_RADIANS);
                yield new CFrame(new Vector3(position[0], position[1], position[2]),
                        turned ? new Quat(rotated.x, rotated.y, rotated.z, rotated.w) : turn);
            }
            default -> {
                Texts.muted(String.valueOf(value));
                yield null;
            }
        };
    }

    private static String kindOf(Object value) {
        for (Map.Entry<String, Object> kind : KINDS.entrySet()) {
            if (kind.getValue().getClass() == value.getClass()) return kind.getKey();
        }
        return value.getClass().getSimpleName();
    }

    private void set(Instance instance, String name, @Nullable Object value, String label) {
        document.history().execute(new SetAttribute(document.ref(instance.id()), name, value, label));
    }
}
