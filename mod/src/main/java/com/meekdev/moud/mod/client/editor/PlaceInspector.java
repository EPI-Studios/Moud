package com.meekdev.moud.mod.client.editor;

import com.meekdev.amnetic.client.ui.Inspector;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.place.Errors;
import com.meekdev.moud.mod.place.Place;
import com.meekdev.moud.mod.server.MoudServer;
import com.meekdev.moud.script.err.ScriptError;
import com.meekdev.moud.script.vm.Vm;
import imgui.ImGui;
import java.util.List;

// the numbers and the errors, which are otherwise only in a log file nobody reads mid session
public final class PlaceInspector extends Inspector {

    public PlaceInspector() {
        super("moud", "place", true);
    }

    @Override
    public void render() {
        InstanceTree tree = ClientScene.tree();
        Place place = MoudServer.place();

        ImGui.text("instances");
        row("parts", tree == null ? 0 : tree.ofClass(Classes.PART).size());
        row("moving", ClientScene.motion().moving().size());
        row("dirty", tree == null ? 0 : tree.dirtyCount());

        ImGui.separator();
        ImGui.text("collision");
        row("boxes", Physics.boxes().size());
        row("sub levels", Physics.shapes().size());

        ImGui.separator();
        ImGui.text("script");
        Vm vm = place == null ? null : place.vm();
        row("sleeping tasks", vm == null ? 0 : vm.scheduler().sleepingCount());

        List<ScriptError> errors = Errors.recent();
        ImGui.separator();
        if (errors.isEmpty()) {
            ImGui.textDisabled("no errors");
            return;
        }
        ImGui.text(errors.size() + " recent errors");
        for (ScriptError error : errors) {
            ImGui.textWrapped(error.getMessage());
        }
        if (ImGui.button("clear")) Errors.clear();
    }

    private static void row(String name, int value) {
        ImGui.textDisabled(name);
        ImGui.sameLine(140);
        ImGui.text(String.valueOf(value));
    }
}
