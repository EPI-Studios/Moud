package com.moud.client.editor.ui.panel.timeline;

import com.moud.api.animation.Keyframe;
import imgui.ImGui;
import imgui.type.ImFloat;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;

public final class KeyframePopups {
    private KeyframePopups() {
    }

    public record ContextResult(boolean open, List<CopiedKeyframe> copiedKeyframes) {
    }

    public static ContextResult renderKeyframeContextMenu(
            boolean contextOnKeyframe,
            int contextTrackIndex,
            double contextTime,
            List<SelectedKeyframe> contextSelectedKeyframes,
            List<SelectedKeyframe> selectedKeyframes,
            List<TrackView> tracks,
            DoubleUnaryOperator snapToFrame,
            Function<TrackView, Float> defaultValueForTrack,
            Function<TrackView, List<Keyframe>> ensureKeyframes,
            java.util.Map<Integer, Boolean> trackVisibility,
            History history,
            List<CopiedKeyframe> copiedKeyframes,
            float currentTime
    ) {
        boolean popupOpen = ImGui.beginPopup("##KeyframeContext");
        if (!popupOpen) {
            return new ContextResult(false, copiedKeyframes);
        }

        if (!contextOnKeyframe && contextTrackIndex >= 0) {
            if (ImGui.menuItem("Add keyframe here")) {
                TrackView track = tracks.get(contextTrackIndex);
                float defaultVal = defaultValueForTrack.apply(track);
                KeyframeOps.addKeyframe(
                        contextTrackIndex,
                        (float) contextTime,
                        defaultVal,
                        Keyframe.Interpolation.SMOOTH,
                        snapToFrame,
                        tracks,
                        ensureKeyframes,
                        history
                );
            }
            ImGui.endPopup();
            return new ContextResult(true, copiedKeyframes);
        }

        if (contextTrackIndex >= 0) {
            List<SelectedKeyframe> selection = contextSelectedKeyframes.isEmpty() ? selectedKeyframes : contextSelectedKeyframes;
            if (ImGui.beginMenu("Interpolation")) {
                for (Keyframe.Interpolation type : Keyframe.Interpolation.values()) {
                    boolean sel = false;
                    if (ImGui.menuItem(type.name(), "", sel)) {
                        List<HistoryAction> undo = new ArrayList<>();
                        List<HistoryAction> redo = new ArrayList<>();
                        for (SelectedKeyframe sk : selection) {
                            if (sk.trackIndex() < 0 || sk.trackIndex() >= tracks.size()) {
                                continue;
                            }
                            TrackView track = tracks.get(sk.trackIndex());
                            List<Keyframe> keyframes = ensureKeyframes.apply(track);
                            for (int i = 0; i < keyframes.size(); i++) {
                                if (Math.abs(keyframes.get(i).time() - sk.time()) < 1e-4) {
                                    Keyframe kf = keyframes.get(i);
                                    if (kf.interpolation() != type) {
                                        undo.add(new HistoryAction.ChangeInterpolation(sk.trackIndex(), kf.time(), type, kf.interpolation()));
                                        redo.add(new HistoryAction.ChangeInterpolation(sk.trackIndex(), kf.time(), kf.interpolation(), type));
                                        keyframes.set(i, new Keyframe(kf.time(), kf.value(), type, kf.inTangent(), kf.outTangent()));
                                    }
                                }
                            }
                        }
                        if (!redo.isEmpty()) {
                            history.push("Change interpolation", undo, redo);
                        }
                    }
                }
                ImGui.endMenu();
            }
            ImGui.separator();
            if (ImGui.menuItem("Copy")) {
                copiedKeyframes = KeyframeOps.copyKeyframes(selection, tracks);
            }
            if (ImGui.menuItem("Paste") && copiedKeyframes != null) {
                KeyframeOps.pasteKeyframes(copiedKeyframes, currentTime, history);
            }
            ImGui.separator();
            if (ImGui.menuItem("Delete")) {
                KeyframeOps.deleteKeyframes(selection, tracks, trackVisibility, selectedKeyframes, history);
            }
        }

        ImGui.endPopup();
        return new ContextResult(true, copiedKeyframes);
    }

    public static boolean renderValueEditorPopup(
            SelectedKeyframe editingKeyframe,
            int editingTrackIndex,
            List<TrackView> tracks,
            List<SelectedKeyframe> selectedKeyframes,
            History history
    ) {
        boolean popupOpen = ImGui.beginPopup("##KeyframeValueEditor");
        if (!popupOpen) {
            return false;
        }

        if (editingKeyframe != null && editingTrackIndex >= 0 && editingTrackIndex < tracks.size()) {
            TrackView track = tracks.get(editingTrackIndex);
            Keyframe target = null;
            int idx = -1;
            List<Keyframe> keyframes = track.keyframes();
            if (keyframes != null) {
                for (int i = 0; i < keyframes.size(); i++) {
                    if (Math.abs(keyframes.get(i).time() - editingKeyframe.time()) < 1e-4) {
                        target = keyframes.get(i);
                        idx = i;
                        break;
                    }
                }
            }
            if (target != null) {
                ImFloat valueBuf = new ImFloat(target.value());
                ImGui.setNextItemWidth(150);
                if (ImGui.inputFloat("Value", valueBuf, 0.1f, 1.0f, "%.3f")) {
                    float old = target.value();
                    float newVal = valueBuf.get();
                    if (Math.abs(newVal - old) > 1e-6) {
                        keyframes.set(idx, new Keyframe(target.time(), newVal, target.interpolation(), target.inTangent(), target.outTangent()));
                        history.push("Change value",
                                List.of(new HistoryAction.ChangeValue(editingTrackIndex, target.time(), old, old)),
                                List.of(new HistoryAction.ChangeValue(editingTrackIndex, target.time(), old, newVal)));
                    }
                }
                ImFloat timeBuf = new ImFloat(target.time());
                ImGui.setNextItemWidth(150);
                if (ImGui.inputFloat("Time", timeBuf, 0.01f, 0.1f, "%.3f")) {
                    float newTime = Math.max(0f, timeBuf.get());
                    if (Math.abs(newTime - target.time()) > 1e-6) {
                        float oldTime = target.time();
                        keyframes.set(idx, new Keyframe(newTime, target.value(), target.interpolation(), target.inTangent(), target.outTangent()));
                        selectedKeyframes.clear();
                        selectedKeyframes.add(new SelectedKeyframe(editingTrackIndex, newTime));
                        history.push("Move keyframe",
                                List.of(new HistoryAction.MoveKeyframe(editingTrackIndex, newTime, oldTime)),
                                List.of(new HistoryAction.MoveKeyframe(editingTrackIndex, oldTime, newTime)));
                    }
                }
                ImInt interpIndex = new ImInt(target.interpolation().ordinal());
                ImGui.setNextItemWidth(150);
                String[] interpNames = Arrays.stream(Keyframe.Interpolation.values()).map(Enum::name).toArray(String[]::new);
                if (ImGui.combo("Interpolation", interpIndex, interpNames)) {
                    Keyframe.Interpolation chosen = Keyframe.Interpolation.values()[interpIndex.get()];
                    if (chosen != target.interpolation()) {
                        HistoryAction undo = new HistoryAction.ChangeInterpolation(editingTrackIndex, target.time(), chosen, target.interpolation());
                        HistoryAction redo = new HistoryAction.ChangeInterpolation(editingTrackIndex, target.time(), target.interpolation(), chosen);
                        keyframes.set(idx, new Keyframe(target.time(), target.value(), chosen, target.inTangent(), target.outTangent()));
                        history.push("Change interpolation", List.of(undo), List.of(redo));
                    }
                }
            }
        }

        ImGui.endPopup();
        return true;
    }
}
