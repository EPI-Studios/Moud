package com.moud.client.editor.ui.panel;

import com.moud.client.editor.scene.SceneSessionManager;
import com.moud.client.editor.scene.blueprint.ClientBlueprintNetwork;
import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.client.editor.ui.layout.EditorDockingLayout;
import imgui.ImGui;
import imgui.type.ImString;

import java.util.List;
import java.util.Locale;

public final class BlueprintBrowserPanel {
    private final SceneEditorOverlay overlay;
    private final ImString filter = new ImString(64);
    private List<String> availableBlueprints = List.of();
    private long lastListFetch = 0;
    private static final long LIST_FETCH_COOLDOWN = 2000;
    private String selectedBlueprintName = null;

    public BlueprintBrowserPanel(SceneEditorOverlay overlay) {
        this.overlay = overlay;
    }

    public void render(SceneSessionManager session) {
        if (!overlay.beginDockedPanel(EditorDockingLayout.Region.INSPECTOR, "Blueprint Browser")) {
            ImGui.end();
            return;
        }

        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##blueprint_search", "Search blueprints...", filter);
        ImGui.separator();

        if (ImGui.button("Refresh List", -1, 0)) {
            requestBlueprintList();
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        long now = System.currentTimeMillis();
        if (now - lastListFetch > LIST_FETCH_COOLDOWN) {
            requestBlueprintList();
        }

        ImGui.beginChild("blueprint_list_scroll", 0, 0, true);

        if (availableBlueprints.isEmpty()) {
            ImGui.textDisabled("No saved blueprints found.");
            ImGui.spacing();
            ImGui.textWrapped("Create blueprints using the tools at the bottom.");
        } else {
            String filterText = filter.get().toLowerCase(Locale.ROOT);

            for (String name : availableBlueprints) {
                if (!filterText.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(filterText)) {
                    continue;
                }

                boolean isSelected = name.equals(selectedBlueprintName);

                if (ImGui.selectable(name, isSelected)) {
                    selectedBlueprintName = name;
                    overlay.loadBlueprintPreview(name, overlay.getPreviewPositionBuffer());
                }

                if (ImGui.beginPopupContextItem("bp_ctx_" + name)) {
                    if (ImGui.menuItem("Load Preview")) {
                        selectedBlueprintName = name;
                        overlay.loadBlueprintPreview(name, overlay.getPreviewPositionBuffer());
                    }
                    if (ImGui.menuItem("Delete")) {
                        String toDelete = name;
                        ClientBlueprintNetwork.getInstance().deleteBlueprint(toDelete, success -> {
                            if (success) {
                                availableBlueprints.remove(toDelete);
                                if (toDelete.equals(selectedBlueprintName)) {
                                    selectedBlueprintName = null;
                                }
                            }
                        });
                    }
                    ImGui.endPopup();
                }
            }
        }

        ImGui.endChild();
        ImGui.end();
    }

    private void requestBlueprintList() {
        lastListFetch = System.currentTimeMillis();
        ClientBlueprintNetwork.getInstance().listBlueprints(list -> {
            availableBlueprints = list;
        });
    }

    public void updateBlueprintList(List<String> blueprints) {
        this.availableBlueprints = blueprints;
    }

    public String getSelectedBlueprintName() {
        return selectedBlueprintName;
    }
}
