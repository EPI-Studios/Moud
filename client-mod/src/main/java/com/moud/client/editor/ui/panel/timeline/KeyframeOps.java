package com.moud.client.editor.ui.panel.timeline;

import com.moud.api.animation.Keyframe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;

public final class KeyframeOps {
    private KeyframeOps() {
    }

    public static List<CopiedKeyframe> copyKeyframes(List<SelectedKeyframe> selection, List<TrackView> tracks) {
        List<CopiedKeyframe> out = new ArrayList<>();
        for (SelectedKeyframe sk : selection) {
            if (sk.trackIndex() < 0 || sk.trackIndex() >= tracks.size()) {
                continue;
            }
            TrackView track = tracks.get(sk.trackIndex());
            List<Keyframe> keyframes = track.keyframes();
            if (keyframes == null) continue;
            for (Keyframe kf : keyframes) {
                if (Math.abs(kf.time() - sk.time()) < 1e-4) {
                    out.add(new CopiedKeyframe(sk.trackIndex(), kf.time(), kf.value(), kf.interpolation()));
                    break;
                }
            }
        }
        return out;
    }

    public static void pasteKeyframes(List<CopiedKeyframe> copiedKeyframes, float targetTime, History history) {
        if (copiedKeyframes == null || copiedKeyframes.isEmpty()) {
            return;
        }
        float minTime = Float.MAX_VALUE;
        for (CopiedKeyframe ck : copiedKeyframes) {
            minTime = Math.min(minTime, ck.time());
        }
        float delta = targetTime - minTime;
        List<HistoryAction> undo = new ArrayList<>();
        List<HistoryAction> redo = new ArrayList<>();
        for (CopiedKeyframe ck : copiedKeyframes) {
            float newTime = ck.time() + delta;
            Keyframe newKf = new Keyframe(newTime, ck.value(), ck.interpolation(), 0f, 0f);
            undo.add(new HistoryAction.RemoveKeyframe(ck.trackIndex(), newTime));
            redo.add(new HistoryAction.AddKeyframe(ck.trackIndex(), newKf));
            history.apply(new HistoryAction.AddKeyframe(ck.trackIndex(), newKf));
        }
        if (!redo.isEmpty()) {
            history.push("Paste keyframes", undo, redo);
        }
    }

    public static void addKeyframe(
            int trackIndex,
            float time,
            float value,
            Keyframe.Interpolation interp,
            DoubleUnaryOperator snapToFrame,
            List<TrackView> tracks,
            Function<TrackView, List<Keyframe>> ensureKeyframes,
            History history
    ) {
        if (trackIndex < 0 || trackIndex >= tracks.size()) return;
        time = (float) snapToFrame.applyAsDouble(time);
        TrackView track = tracks.get(trackIndex);
        List<Keyframe> keyframes = ensureKeyframes.apply(track);
        Keyframe kf = new Keyframe(time, value, interp, 0f, 0f);

        List<HistoryAction> undo = new ArrayList<>();
        List<HistoryAction> redo = new ArrayList<>();
        undo.add(new HistoryAction.RemoveKeyframe(trackIndex, time));
        redo.add(new HistoryAction.AddKeyframe(trackIndex, kf));

        keyframes.add(kf);
        keyframes.sort((a, b) -> Float.compare(a.time(), b.time()));
        history.push("Add keyframe", undo, redo);
    }

    public static void deleteKeyframes(
            List<SelectedKeyframe> selection,
            List<TrackView> tracks,
            Map<Integer, Boolean> trackVisibility,
            List<SelectedKeyframe> selectedKeyframes,
            History history
    ) {
        if (selection.isEmpty()) {
            return;
        }
        List<HistoryAction> undo = new ArrayList<>();
        List<HistoryAction> redo = new ArrayList<>();
        for (SelectedKeyframe sk : new ArrayList<>(selection)) {
            if (sk.trackIndex() < 0 || sk.trackIndex() >= tracks.size()) {
                continue;
            }
            TrackView track = tracks.get(sk.trackIndex());
            if (!trackVisibility.getOrDefault(sk.trackIndex(), true)) {
                continue;
            }
            List<Keyframe> keyframes = track.keyframes();
            if (keyframes == null) continue;
            for (Keyframe kf : new ArrayList<>(keyframes)) {
                if (Math.abs(kf.time() - sk.time()) < 1e-4) {
                    undo.add(new HistoryAction.AddKeyframe(sk.trackIndex(), kf));
                    redo.add(new HistoryAction.RemoveKeyframe(sk.trackIndex(), kf.time()));
                    keyframes.remove(kf);
                    break;
                }
            }
        }
        selectedKeyframes.clear();
        if (!redo.isEmpty()) {
            history.push("Delete keyframes", undo, redo);
        }
    }
}
