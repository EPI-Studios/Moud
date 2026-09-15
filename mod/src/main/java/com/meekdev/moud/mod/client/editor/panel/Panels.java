package com.meekdev.moud.mod.client.editor.panel;

import com.meekdev.moud.mod.MoudMod;
import imgui.ImGui;
import java.util.ArrayList;
import java.util.List;

public final class Panels {

    private final List<Panel> panels = new ArrayList<>();

    public Panels add(Panel panel) {
        panels.add(panel);
        return this;
    }

    public void render() {
        for (Panel panel : panels) {
            boolean shown = ImGui.begin(panel.windowName(), panel.windowFlags());
            try {
                if (shown) panel.render();
            } catch (RuntimeException e) {
                MoudMod.LOG.error("editor panel {} failed", panel.id(), e);
            } finally {
                ImGui.end();
            }
        }
    }
}
