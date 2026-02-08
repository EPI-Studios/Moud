package com.moud.client.editor.ui.panel.timeline;

import com.moud.api.animation.Keyframe;
import com.moud.api.animation.PropertyTrack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class History {
    private final Deque<HistoryEntry> undoStack = new ArrayDeque<>(50);
    private final Deque<HistoryEntry> redoStack = new ArrayDeque<>(50);

    private final List<TrackView> tracks;
    private final Map<Integer, Boolean> trackVisibility;
    private final Function<TrackView, List<Keyframe>> ensureKeyframes;
    private final Function<Keyframe, Keyframe> cloneKeyframe;
    private final Runnable onTracksChanged;

    public History(
            List<TrackView> tracks,
            Map<Integer, Boolean> trackVisibility,
            Function<TrackView, List<Keyframe>> ensureKeyframes,
            Function<Keyframe, Keyframe> cloneKeyframe,
            Runnable onTracksChanged
    ) {
        this.tracks = tracks;
        this.trackVisibility = trackVisibility;
        this.ensureKeyframes = ensureKeyframes;
        this.cloneKeyframe = cloneKeyframe;
        this.onTracksChanged = onTracksChanged;
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    public void push(String description, List<HistoryAction> undo, List<HistoryAction> redo) {
        if (undo == null || redo == null) {
            return;
        }
        undoStack.push(new HistoryEntry(description, new ArrayList<>(undo), new ArrayList<>(redo)));
        redoStack.clear();
        if (undoStack.size() > 50) {
            undoStack.removeLast();
        }
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        HistoryEntry entry = undoStack.pop();
        for (HistoryAction action : entry.undoActions()) {
            applyAction(action);
        }
        redoStack.push(entry);
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        HistoryEntry entry = redoStack.pop();
        for (HistoryAction action : entry.redoActions()) {
            applyAction(action);
        }
        undoStack.push(entry);
    }

    public void apply(HistoryAction action) {
        applyAction(action);
    }

    public TrackSnapshot snapshotTrack(TrackView track) {
        List<Keyframe> copy = track.keyframes() != null ? new ArrayList<>(track.keyframes()) : new ArrayList<>();
        PropertyTrack propertyTrackCopy = new PropertyTrack(
                track.propertyTrack().propertyPath(),
                track.propertyTrack().propertyType(),
                track.propertyTrack().minValue(),
                track.propertyTrack().maxValue(),
                copy
        );
        return new TrackSnapshot(track.objectLabel(), track.label(), track.propertyPath(), track.propertyMap(), propertyTrackCopy, copy);
    }

    private TrackView restoreTrack(TrackSnapshot snapshot) {
        return new TrackView(snapshot.objectLabel(), snapshot.label(), snapshot.propertyPath(), snapshot.propertyMap(), snapshot.propertyTrackCopy());
    }

    private void applyAction(HistoryAction action) {
        switch (action) {
            case HistoryAction.AddKeyframe add -> {
                List<Keyframe> keyframes = ensureKeyframes.apply(tracks.get(add.trackIndex()));
                keyframes.add(cloneKeyframe.apply(add.keyframe()));
                keyframes.sort((a, b) -> Float.compare(a.time(), b.time()));
            }
            case HistoryAction.RemoveKeyframe rem -> {
                TrackView track = tracks.get(rem.trackIndex());
                List<Keyframe> keyframes = track.keyframes();
                if (keyframes != null) {
                    keyframes.removeIf(k -> Math.abs(k.time() - rem.time()) < 1e-4);
                }
            }
            case HistoryAction.MoveKeyframe move -> {
                TrackView track = tracks.get(move.trackIndex());
                List<Keyframe> keyframes = ensureKeyframes.apply(track);
                for (int i = 0; i < keyframes.size(); i++) {
                    if (Math.abs(keyframes.get(i).time() - move.fromTime()) < 1e-4) {
                        Keyframe kf = keyframes.get(i);
                        keyframes.set(i, new Keyframe(
                                move.toTime(),
                                kf.value(),
                                kf.interpolation(),
                                kf.inTangent(),
                                kf.outTangent()
                        ));
                        break;
                    }
                }
                keyframes.sort((a, b) -> Float.compare(a.time(), b.time()));
            }
            case HistoryAction.ChangeValue cv -> {
                TrackView track = tracks.get(cv.trackIndex());
                List<Keyframe> keyframes = ensureKeyframes.apply(track);
                for (int i = 0; i < keyframes.size(); i++) {
                    if (Math.abs(keyframes.get(i).time() - cv.time()) < 1e-4) {
                        Keyframe kf = keyframes.get(i);
                        keyframes.set(i, new Keyframe(
                                kf.time(),
                                cv.newValue(),
                                kf.interpolation(),
                                kf.inTangent(),
                                kf.outTangent()
                        ));
                        break;
                    }
                }
            }
            case HistoryAction.ChangeInterpolation ci -> {
                TrackView track = tracks.get(ci.trackIndex());
                List<Keyframe> keyframes = ensureKeyframes.apply(track);
                for (int i = 0; i < keyframes.size(); i++) {
                    if (Math.abs(keyframes.get(i).time() - ci.time()) < 1e-4) {
                        Keyframe kf = keyframes.get(i);
                        keyframes.set(i, new Keyframe(
                                kf.time(),
                                kf.value(),
                                ci.newType(),
                                kf.inTangent(),
                                kf.outTangent()
                        ));
                        break;
                    }
                }
            }
            case HistoryAction.AddTrack addTrack -> {
                TrackView restored = restoreTrack(addTrack.snapshot());
                int idx = Math.min(Math.max(0, addTrack.trackIndex()), tracks.size());
                tracks.add(idx, restored);
                trackVisibility.put(idx, true);
                onTracksChanged.run();
            }
            case HistoryAction.RemoveTrack remTrack -> {
                if (remTrack.trackIndex() >= 0 && remTrack.trackIndex() < tracks.size()) {
                    TrackView removed = tracks.remove(remTrack.trackIndex());
                    if (removed != null && removed.propertyMap() != null) {
                        removed.propertyMap().remove(removed.propertyPath());
                    }
                    onTracksChanged.run();
                }
            }
        }
    }
}
