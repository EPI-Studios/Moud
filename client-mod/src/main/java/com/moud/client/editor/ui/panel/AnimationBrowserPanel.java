package com.moud.client.editor.ui.panel;

import com.moud.client.editor.assets.ProjectFileIndex;
import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.client.editor.ui.layout.EditorDockingLayout;
import imgui.ImGui;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImString;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public final class AnimationBrowserPanel {
    private final SceneEditorOverlay overlay;
    private final ImString filter = new ImString(64);

    public AnimationBrowserPanel(SceneEditorOverlay overlay) {
        this.overlay = overlay;
    }

    public void render() {
        if (!overlay.beginDockedPanel(EditorDockingLayout.Region.CONSOLE, "Animation Browser")) {
            ImGui.end();
            return;
        }

        ProjectFileIndex.getInstance().requestSyncIfNeeded();
        List<ProjectFileIndex.Node> animations = ProjectFileIndex.getInstance().listFilesWithExtensions(".an");

        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##animation_search", "Search animations...", filter);
        ImGui.separator();
        ImGui.spacing();

        if (ImGui.button("Refresh Files", -1, 0)) {
            ProjectFileIndex.getInstance().forceRefresh();
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        ImGui.beginChild("animations_scroll", 0, 0, true);

        if (animations.isEmpty()) {
            ImGui.textDisabled("No .an files found.");
            ImGui.spacing();
            ImGui.textWrapped("Add animation files (.an) to your project under the animations/ folder.");
        } else {
            String filterText = filter.get().trim().toLowerCase(Locale.ROOT);

            if (ImGui.beginTable("an_list", 2, ImGuiTableFlags.RowBg | ImGuiTableFlags.BordersInnerV | ImGuiTableFlags.Resizable | ImGuiTableFlags.SizingStretchProp)) {
                ImGui.tableSetupColumn("Name", ImGuiTableColumnFlags.WidthStretch, 0.4f);
                ImGui.tableSetupColumn("Path", ImGuiTableColumnFlags.WidthStretch, 0.6f);
                ImGui.tableHeadersRow();

                for (ProjectFileIndex.Node node : animations) {
                    if (!filterText.isEmpty() &&
                        !node.name().toLowerCase(Locale.ROOT).contains(filterText) &&
                        !node.path().toLowerCase(Locale.ROOT).contains(filterText)) {
                        continue;
                    }

                    ImGui.tableNextRow();
                    ImGui.tableSetColumnIndex(0);

                    if (ImGui.selectable(node.name() + "##" + node.path(), false, 0, 0, 0)) {
                        overlay.openAnimation(node.path());
                    }

                    if (ImGui.beginDragDropSource()) {
                        byte[] bytes = node.path().getBytes(StandardCharsets.UTF_8);
                        ImGui.setDragDropPayload("MoudAnimationFile", bytes);
                        ImGui.text("Attach " + node.name());
                        ImGui.endDragDropSource();
                    }

                    ImGui.tableSetColumnIndex(1);
                    ImGui.textDisabled(node.path());
                }
                ImGui.endTable();
            }
        }

        ImGui.endChild();
        ImGui.end();
    }
}
