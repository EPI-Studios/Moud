package com.moud.client.editor.ui.panel.timeline;

import com.moud.api.animation.Keyframe;
import imgui.ImGui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class KeyframeSelection {
    private KeyframeSelection() {
    }

    public static void selectKeyframeAt(
            float mouseX,
            float mouseY,
            float timelineX,
            double visibleStart,
            double visibleSpan,
            float keyY,
            List<RowEntry> visibleRows,
            Map<Integer, Boolean> trackVisibility,
            List<TrackView> tracks,
            List<SelectedKeyframe> selectedKeyframes,
            float keyWidth,
            boolean additive
    ) {
        float rowHeight = ImGui.getTextLineHeightWithSpacing() + 6f;
        double visibleEnd = visibleStart + visibleSpan;

        int rowIndex = 0;
        for (RowEntry row : visibleRows) {
            if (row.type() != RowType.TRACK || row.trackIndex() == null) {
                rowIndex++;
                continue;
            }
            int trackIndex = row.trackIndex();
            if (!trackVisibility.getOrDefault(trackIndex, true)) {
                rowIndex++;
                continue;
            }
            float rowTop = keyY + rowIndex * rowHeight;
            float rowBottom = rowTop + rowHeight;
            if (mouseY < rowTop || mouseY > rowBottom) {
                rowIndex++;
                continue;
            }
            TrackView track = tracks.get(trackIndex);
            List<Keyframe> keyframes = track.keyframes();
            if (keyframes == null) {
                rowIndex++;
                continue;
            }
            for (Keyframe keyframe : keyframes) {
                double time = keyframe.time();
                if (time < visibleStart || time > visibleEnd) {
                    continue;
                }
                float px = (float) (timelineX + (time - visibleStart) / visibleSpan * keyWidth);
                float py = rowTop + rowHeight * 0.5f;
                float size = 8f;
                if (Math.abs(mouseX - px) <= size && Math.abs(mouseY - py) <= size) {
                    if (!additive) {
                        selectedKeyframes.clear();
                    }
                    toggleSelection(trackIndex, time, additive, selectedKeyframes);
                    return;
                }
            }
            rowIndex++;
        }
        if (!additive) {
            selectedKeyframes.clear();
        }
    }

    public static void toggleSelection(int trackIndex, double time, boolean additive, List<SelectedKeyframe> selectedKeyframes) {
        if (!additive) {
            selectedKeyframes.clear();
            selectedKeyframes.add(new SelectedKeyframe(trackIndex, time));
            return;
        }
        for (int i = 0; i < selectedKeyframes.size(); i++) {
            SelectedKeyframe selected = selectedKeyframes.get(i);
            if (selected.trackIndex() == trackIndex && Math.abs(selected.time() - time) < 1e-4) {
                selectedKeyframes.remove(i);
                return;
            }
        }
        selectedKeyframes.add(new SelectedKeyframe(trackIndex, time));
    }

    public static boolean isSelected(int trackIndex, double time, List<SelectedKeyframe> selectedKeyframes) {
        for (SelectedKeyframe selected : selectedKeyframes) {
            if (selected.trackIndex() == trackIndex && Math.abs(selected.time() - time) < 1e-4) {
                return true;
            }
        }
        return false;
    }

    public static void performBoxSelection(
            float boxStartX,
            float boxStartY,
            float boxEndX,
            float boxEndY,
            float timelineX,
            double visibleStart,
            double visibleEnd,
            double visibleSpan,
            float keyY,
            List<RowEntry> visibleRows,
            Map<Integer, Boolean> trackVisibility,
            List<TrackView> tracks,
            List<SelectedKeyframe> selectedKeyframes,
            float keyWidth
    ) {
        float minX = Math.min(boxStartX, boxEndX);
        float maxX = Math.max(boxStartX, boxEndX);
        float minY = Math.min(boxStartY, boxEndY);
        float maxY = Math.max(boxStartY, boxEndY);
        float rowHeight = ImGui.getTextLineHeightWithSpacing() + 6f;

        selectedKeyframes.clear();
        int rowIndex = 0;
        for (RowEntry row : visibleRows) {
            if (row.type() != RowType.TRACK || row.trackIndex() == null) {
                rowIndex++;
                continue;
            }
            int trackIndex = row.trackIndex();
            if (!trackVisibility.getOrDefault(trackIndex, true)) {
                rowIndex++;
                continue;
            }
            float rowTop = keyY + rowIndex * rowHeight;
            float rowBottom = rowTop + rowHeight;
            if (rowBottom < minY || rowTop > maxY) {
                rowIndex++;
                continue;
            }
            TrackView track = tracks.get(trackIndex);
            List<Keyframe> keyframes = track.keyframes();
            if (keyframes == null) {
                rowIndex++;
                continue;
            }
            for (Keyframe keyframe : keyframes) {
                double time = keyframe.time();
                if (time < visibleStart || time > visibleEnd) {
                    continue;
                }
                float px = (float) (timelineX + (time - visibleStart) / visibleSpan * keyWidth);
                float py = rowTop + rowHeight * 0.5f;
                if (px >= minX && px <= maxX && py >= minY && py <= maxY) {
                    selectedKeyframes.add(new SelectedKeyframe(trackIndex, time));
                }
            }
            rowIndex++;
        }
    }

    public static List<Double> snapshotSelectedTimes(List<SelectedKeyframe> selectedKeyframes) {
        List<Double> times = new ArrayList<>();
        for (SelectedKeyframe selected : selectedKeyframes) {
            times.add(selected.time());
        }
        return times;
    }

    public static double findSelectedTimeForTrack(int trackIndex, List<SelectedKeyframe> selectedKeyframes) {
        for (SelectedKeyframe selected : selectedKeyframes) {
            if (selected.trackIndex() == trackIndex) {
                return selected.time();
            }
        }
        return Double.NaN;
    }

    public static double snapToOtherTracks(int excludeTrackIndex, double target, List<TrackView> tracks) {
        double best = target;
        double bestDiff = 0.05;
        for (int trackIndex = 0; trackIndex < tracks.size(); trackIndex++) {
            if (trackIndex == excludeTrackIndex) {
                continue;
            }
            TrackView track = tracks.get(trackIndex);
            List<Keyframe> keyframes = track.keyframes();
            if (keyframes == null) {
                continue;
            }
            for (Keyframe keyframe : keyframes) {
                double diff = Math.abs(keyframe.time() - target);
                if (diff < bestDiff) {
                    bestDiff = diff;
                    best = keyframe.time();
                }
            }
        }
        return best;
    }
}
