package com.moud.client.editor.ui.panel.timeline;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TrackLabelsView {
    private TrackLabelsView() {
    }

    public record Result(int renamingTrackIndex, int colorPickerTrackIndex, int draggingTrackIndex, float dragTrackStartY) {
    }

    public static Result render(
            ImDrawList drawList,
            float leftX,
            float y,
            List<RowEntry> visibleRows,
            Map<Integer, Float> trackAnimatedOffsets,
            List<TrackView> tracks,
            Map<Integer, Boolean> trackVisibility,
            Map<Integer, float[]> trackColors,
            Map<String, Boolean> groupExpanded,
            int renamingTrackIndex,
            ImString renameBuffer,
            int colorPickerTrackIndex,
            int draggingTrackIndex,
            float dragTrackStartY,
            Runnable rebuildRowEntries
    ) {
        float rowHeight = ImGui.getTextLineHeightWithSpacing() + 6f;
        int rowIndex = 0;
        List<RowEntry> rows = new ArrayList<>(visibleRows);
        for (RowEntry row : rows) {
            float animOffset = 0f;
            if (row.trackIndex() != null) {
                animOffset = trackAnimatedOffsets.getOrDefault(row.trackIndex(), 0f);
            }

            float rowTop = y + rowIndex * rowHeight + animOffset;
            int textColor = ImGui.getColorU32(ImGuiCol.Text);
            float cursorY = rowTop + 2;
            float indentX = leftX + 4 + row.indent() * 14f;
            ImGui.setCursorScreenPos(indentX, cursorY);
            ImGui.pushID(rowIndex);

            if (row.type() == RowType.TRACK && row.trackIndex() != null) {
                int i = row.trackIndex();
                TrackView track = tracks.get(i);

                ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 2, 1);
                if (ImGui.button("=##drag" + i) || (ImGui.isItemActive() && ImGui.isMouseDragging(ImGuiMouseButton.Left))) {
                    draggingTrackIndex = i;
                    dragTrackStartY = ImGui.getIO().getMousePosY();
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Drag to reorder");
                }
                ImGui.popStyleVar();

                ImGui.sameLine();

                boolean visible = trackVisibility.getOrDefault(i, true);
                String visIcon = visible ? "O" : "-";
                ImGui.pushStyleColor(ImGuiCol.Text, visible ? 0xFFFFFFFF : 0xFF666666);
                if (ImGui.smallButton(visIcon + "##vis" + i)) {
                    trackVisibility.put(i, !visible);
                }
                ImGui.popStyleColor();
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(visible ? "Hide track (click to hide)" : "Show track (click to show)");
                }
                ImGui.sameLine();

                float[] color = trackColors.getOrDefault(i, null);
                int colorU32 = color != null ? ImGui.colorConvertFloat4ToU32(color[0], color[1], color[2], 1f) : textColor;
                ImGui.colorButton("##col" + i, color != null ? color : new float[]{1f, 1f, 1f});
                if (ImGui.isItemClicked()) {
                    colorPickerTrackIndex = i;
                    ImGui.openPopup("##TrackColorPicker");
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Click to change track color");
                }
                ImGui.sameLine();

                if (renamingTrackIndex == i) {
                    ImGui.setNextItemWidth(140);
                    if (ImGui.inputText("##RenameTrack", renameBuffer, ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll)) {
                        tracks.set(i, new TrackView(track.objectLabel(), renameBuffer.get().trim(), track.propertyPath(), track.propertyMap(), track.propertyTrack()));
                        renamingTrackIndex = -1;
                        rebuildRowEntries.run();
                    }
                    if (!ImGui.isItemActive() && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
                        renamingTrackIndex = -1;
                    }
                } else {
                    ImGui.pushStyleColor(ImGuiCol.Text, colorU32);
                    ImGui.text(track.label());
                    ImGui.popStyleColor();
                    if (ImGui.isItemClicked(ImGuiMouseButton.Right)) {
                        ImGui.openPopup("##TrackContext");
                    }
                }
            } else {
                boolean expanded = groupExpanded.getOrDefault(row.id(), true);
                String arrow = expanded ? "v" : ">";
                if (ImGui.smallButton(arrow + "##exp")) {
                    groupExpanded.put(row.id(), !expanded);
                    rebuildRowEntries.run();
                }
                ImGui.sameLine();

                if (row.type() == RowType.OBJECT) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0xFFFFCC66);
                } else {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0xFFAAAAAA);
                }
                ImGui.text(row.label());
                ImGui.popStyleColor();
            }

            ImGui.popID();
            rowIndex++;
        }

        return new Result(renamingTrackIndex, colorPickerTrackIndex, draggingTrackIndex, dragTrackStartY);
    }
}
