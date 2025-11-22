package com.moud.client.editor.ui.panel;

import com.moud.client.editor.assets.EditorAssetCatalog;
import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.client.editor.ui.layout.EditorDockingLayout;
import com.moud.network.MoudPackets;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImString;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;


public final class AssetBrowserPanel {
    private final SceneEditorOverlay overlay;
    private final ImString assetFilter = new ImString(64);

    public AssetBrowserPanel(SceneEditorOverlay overlay) {
        this.overlay = overlay;
    }

    public void render() {
        if (!overlay.beginDockedPanel(EditorDockingLayout.Region.ASSET_BROWSER, "Asset Browser")) {
            ImGui.end();
            return;
        }
        List<MoudPackets.EditorAssetDefinition> assets = EditorAssetCatalog.getInstance().getAssets();
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##asset_search", "Search models, displays, lights...", assetFilter);
        ImGui.separator();
        if (assets.isEmpty()) {
            ImGui.textDisabled("No assets available. Trigger a server rescan.");
            ImGui.end();
            return;
        }
        float availWidth = Math.max(1f, ImGui.getContentRegionAvailX());
        int columns = Math.max(1, (int) (availWidth / 180f));
        if (ImGui.beginTable("asset_grid", columns, ImGuiTableFlags.PadOuterX | ImGuiTableFlags.NoBordersInBody)) {
            for (MoudPackets.EditorAssetDefinition entry : assets) {
                if (!assetMatchesFilter(entry)) {
                    continue;
                }
                ImGui.tableNextColumn();
                ImGui.pushID(entry.id());
                ImGui.beginGroup();
                float buttonWidth = Math.min(160f, ImGui.getColumnWidth());
                if (ImGui.button(entry.label(), buttonWidth, 70f)) {
                    overlay.spawnAsset(entry);
                }
                if (ImGui.beginDragDropSource()) {
                    byte[] idBytes = entry.id().getBytes(StandardCharsets.UTF_8);
                    ImGui.setDragDropPayload(SceneEditorOverlay.PAYLOAD_ASSET, idBytes);
                    ImGui.text("Place " + entry.label());
                    ImGui.endDragDropSource();
                }
                renderTypeBadge(entry.objectType());
                ImGui.endGroup();
                ImGui.popID();
            }
            ImGui.endTable();
        }
        ImGui.end();
    }

    private void renderTypeBadge(String type) {
        float[] color = typeColor(type);
        ImGui.pushStyleColor(ImGuiCol.Text, color[0], color[1], color[2], 0.95f);
        ImGui.textDisabled(type);
        ImGui.popStyleColor();
    }

    private boolean assetMatchesFilter(MoudPackets.EditorAssetDefinition entry) {
        String filter = assetFilter.get().trim().toLowerCase(Locale.ROOT);
        if (filter.isEmpty()) {
            return true;
        }
        return entry.label().toLowerCase(Locale.ROOT).contains(filter) || entry.id().toLowerCase(Locale.ROOT).contains(filter);
    }

    private float[] typeColor(String type) {
        if (type == null) {
            return new float[]{0.7f, 0.7f, 0.7f};
        }
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "model" -> new float[]{0.55f, 0.8f, 1f};
            case "display" -> new float[]{0.9f, 0.65f, 1f};
            case "light" -> new float[]{1f, 0.82f, 0.55f};
            default -> new float[]{0.75f, 0.75f, 0.75f};
        };
    }
}
