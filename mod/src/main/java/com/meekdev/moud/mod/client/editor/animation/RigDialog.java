package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.part.MeshPart;
import com.meekdev.moud.mod.client.editor.kit.Dialogs;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import org.jspecify.annotations.Nullable;

final class RigDialog {

    private static final String POPUP = "##anim-make-animatable";
    private static final float WIDTH = 380.0f;

    private final AnimationWorkspace workspace;
    private @Nullable MeshPart mesh;
    private boolean opening;
    private boolean confirming;

    RigDialog(AnimationWorkspace workspace) {
        this.workspace = workspace;
    }

    void ask(MeshPart chosen) {
        mesh = chosen;
        opening = true;
    }

    void confirm() {
        confirming = true;
    }

    void render() {
        if (opening) {
            opening = false;
            ImGui.openPopup(POPUP);
        }
        if (!Dialogs.begin(POPUP, WIDTH)) return;
        MeshPart asked = mesh;
        if (asked == null || !asked.isAlive()) {
            ImGui.closeCurrentPopup();
            Dialogs.end();
            return;
        }
        Dialogs.title("Make " + asked.name() + " animatable?");
        ImGui.pushTextWrapPos(ImGui.getCursorPosX() + ImGui.getContentRegionAvailX());
        Texts.muted("Its groups become bones and its clips are written to " + Rigging.directory(asked.name()));
        ImGui.popTextWrapPos();
        Dialogs.gap();
        Dialogs.alignFooter(2);
        if (Dialogs.button("Cancel##anim-rig-cancel") || ImGui.isKeyPressed(ImGuiKey.Escape)) {
            mesh = null;
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (Dialogs.primaryButton("Convert##anim-rig-go", true) || confirming) {
            confirming = false;
            mesh = null;
            ImGui.closeCurrentPopup();
            workspace.makeAnimatable(asked);
        }
        Dialogs.end();
    }
}
