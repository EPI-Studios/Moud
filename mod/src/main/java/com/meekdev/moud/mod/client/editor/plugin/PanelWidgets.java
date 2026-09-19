package com.meekdev.moud.mod.client.editor.plugin;

import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.mod.client.editor.kit.NumberFields;
import com.meekdev.moud.mod.client.editor.kit.Rows;
import com.meekdev.moud.mod.client.editor.kit.Sections;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.script.api.PluginRef;
import imgui.ImGui;
import imgui.flag.ImGuiColorEditFlags;
import imgui.type.ImString;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PanelWidgets implements PluginRef.Ui {

    private static final int TEXT_CAPACITY = 1024;
    private static final int SLIDER_STEPS = 100;

    private final Map<String, ImString> buffers = new HashMap<>();
    private final Set<String> typing = new HashSet<>();

    @Override
    public void text(String text) {
        Texts.wrapped(text);
    }

    @Override
    public void muted(String text) {
        Texts.muted(text);
    }

    @Override
    public void heading(String text) {
        Sections.title(text);
    }

    @Override
    public boolean button(String label) {
        return ImGui.button(label);
    }

    @Override
    public String input(String label, String value) {
        ImString buffer = buffers.computeIfAbsent(label, key -> new ImString(value, TEXT_CAPACITY));
        if (!typing.contains(label) && !buffer.get().equals(value)) buffer.set(value);
        row(label, () -> {
            ImGui.inputText("##value", buffer);
            if (ImGui.isItemActive()) typing.add(label);
            else typing.remove(label);
        });
        return buffer.get();
    }

    @Override
    public double number(String label, double value, double step) {
        float[] out = {(float) value};
        row(label, () -> out[0] = NumberFields.scalar("##value", (float) value, (float) step, ImGui.getContentRegionAvailX()));
        return out[0] == (float) value ? value : typed(out[0]);
    }

    @Override
    public double slider(String label, double value, double minimum, double maximum) {
        float[] out = {(float) value};
        float step = (float) ((maximum - minimum) / SLIDER_STEPS);
        row(label, () -> out[0] = NumberFields.ranged("##value", (float) value, step, ImGui.getContentRegionAvailX(), (float) minimum, (float) maximum));
        return out[0] == (float) value ? value : Math.clamp(typed(out[0]), minimum, maximum);
    }

    @Override
    public boolean checkbox(String label, boolean value) {
        ImGui.pushID(label);
        boolean out = Rows.toggle(shown(label), value);
        ImGui.popID();
        return out;
    }

    @Override
    public Color color(String label, Color value) {
        float[] picked = {value.r(), value.g(), value.b(), value.a()};
        boolean[] changed = {false};
        row(label, () -> changed[0] = ImGui.colorEdit4("##value", picked, ImGuiColorEditFlags.NoInputs | ImGuiColorEditFlags.AlphaBar));
        return changed[0] ? new Color(picked[0], picked[1], picked[2], picked[3]) : value;
    }

    @Override
    public String choice(String label, String value, List<String> options) {
        String[] out = {value};
        row(label, () -> {
            if (!ImGui.beginCombo("##value", value)) return;
            for (String option : options) {
                if (ImGui.selectable(option, option.equals(value))) out[0] = option;
            }
            ImGui.endCombo();
        });
        return out[0];
    }

    @Override
    public void separator() {
        Sections.divider();
    }

    @Override
    public void sameLine() {
        ImGui.sameLine();
    }

    static String shown(String label) {
        int id = label.indexOf("##");
        return id < 0 ? label : label.substring(0, id);
    }

    static double typed(float value) {
        return Double.parseDouble(Float.toString(value));
    }

    private static void row(String label, Runnable control) {
        ImGui.pushID(label);
        Rows.of(shown(label), control);
        ImGui.popID();
    }
}
