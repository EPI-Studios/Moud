package com.moud.client.editor.ui.panel.timeline;

import com.moud.api.animation.Keyframe;
import imgui.ImGui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;

public final class KeyframeDrag {
    private KeyframeDrag() {
    }

    public record BeginResult(
            boolean started,
            float dragKeyStartX,
            List<Double> dragOriginalTimes,
            List<Double> dragCurrentTimes,
            List<SelectedKeyframe> dragSelectedSnapshot
    ) {
    }

    public static BeginResult attemptBegin(
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
            float keyWidth
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
                    if (!KeyframeSelection.isSelected(trackIndex, time, selectedKeyframes)) {
                        selectedKeyframes.clear();
                        selectedKeyframes.add(new SelectedKeyframe(trackIndex, time));
                    }
                    List<Double> originalTimes = KeyframeSelection.snapshotSelectedTimes(selectedKeyframes);
                    return new BeginResult(true, mouseX, originalTimes, new ArrayList<>(originalTimes), new ArrayList<>(selectedKeyframes));
                }
            }
            rowIndex++;
        }
        return new BeginResult(false, 0f, List.of(), List.of(), List.of());
    }

    public record UpdateResult(
            boolean snapDuringDrag,
            List<Double> dragCurrentTimes
    ) {
    }

    public static UpdateResult update(
            float mouseX,
            float dragKeyStartX,
            double visibleSpanSeconds,
            float timelineWidth,
            boolean snap,
            List<SelectedKeyframe> dragSelectedSnapshot,
            List<Double> dragOriginalTimes,
            List<Double> dragCurrentTimes,
            DoubleUnaryOperator snapToFrame,
            List<TrackView> tracks,
            Function<TrackView, List<Keyframe>> ensureKeyframes,
            double clipDurationSeconds,
            List<SelectedKeyframe> selectedKeyframes
    ) {
        float dx = mouseX - dragKeyStartX;
        double dt = (dx / timelineWidth) * visibleSpanSeconds;

        List<SelectedKeyframe> updated = new ArrayList<>();
        List<Double> newCurrentTimes = new ArrayList<>();
        for (int i = 0; i < dragSelectedSnapshot.size(); i++) {
            SelectedKeyframe originalSelection = dragSelectedSnapshot.get(i);
            double originalTime = i < dragOriginalTimes.size() ? dragOriginalTimes.get(i) : originalSelection.time();
            double currentTime = i < dragCurrentTimes.size() ? dragCurrentTimes.get(i) : originalTime;
            double newTime = originalTime + dt;
            newTime = snapToFrame.applyAsDouble(newTime);
            if (snap) {
                newTime = KeyframeSelection.snapToOtherTracks(originalSelection.trackIndex(), newTime, tracks);
            }
            newTime = Math.max(0, Math.min(clipDurationSeconds, newTime));
            updated.add(new SelectedKeyframe(originalSelection.trackIndex(), newTime));
            newCurrentTimes.add(newTime);

            TrackView track = tracks.get(originalSelection.trackIndex());
            List<Keyframe> keyframes = ensureKeyframes.apply(track);
            for (int keyIndex = 0; keyIndex < keyframes.size(); keyIndex++) {
                if (Math.abs(keyframes.get(keyIndex).time() - currentTime) < 1e-4) {
                    Keyframe existing = keyframes.get(keyIndex);
                    keyframes.set(keyIndex, new Keyframe(
                            (float) newTime,
                            existing.value(),
                            existing.interpolation(),
                            existing.inTangent(),
                            existing.outTangent()
                    ));
                    break;
                }
            }
            keyframes.sort((a, b) -> Float.compare(a.time(), b.time()));
        }

        selectedKeyframes.clear();
        selectedKeyframes.addAll(updated);
        return new UpdateResult(snap, newCurrentTimes);
    }

    public static void finalizeDrag(
            List<SelectedKeyframe> dragSelectedSnapshot,
            List<Double> dragOriginalTimes,
            List<SelectedKeyframe> selectedKeyframes,
            History history
    ) {
        if (dragSelectedSnapshot == null || dragSelectedSnapshot.isEmpty()) {
            return;
        }
        List<HistoryAction> undo = new ArrayList<>();
        List<HistoryAction> redo = new ArrayList<>();
        for (int i = 0; i < dragSelectedSnapshot.size(); i++) {
            SelectedKeyframe original = dragSelectedSnapshot.get(i);
            double oldTime = dragOriginalTimes.size() > i ? dragOriginalTimes.get(i) : original.time();
            double newTime = i < selectedKeyframes.size()
                    ? selectedKeyframes.get(i).time()
                    : KeyframeSelection.findSelectedTimeForTrack(original.trackIndex(), selectedKeyframes);
            if (Double.isNaN(newTime)) {
                continue;
            }
            if (Math.abs(newTime - oldTime) > 1e-6) {
                undo.add(new HistoryAction.MoveKeyframe(original.trackIndex(), (float) newTime, (float) oldTime));
                redo.add(new HistoryAction.MoveKeyframe(original.trackIndex(), (float) oldTime, (float) newTime));
            }
        }
        if (!redo.isEmpty()) {
            history.push("Move keyframes", undo, redo);
        }
    }
}
