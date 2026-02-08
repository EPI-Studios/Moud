package com.moud.client.editor.ui.panel.timeline;

import com.moud.api.animation.Keyframe;

public sealed interface HistoryAction permits HistoryAction.AddKeyframe, HistoryAction.RemoveKeyframe, HistoryAction.MoveKeyframe,
        HistoryAction.ChangeValue, HistoryAction.ChangeInterpolation, HistoryAction.AddTrack, HistoryAction.RemoveTrack {
    record AddKeyframe(int trackIndex, Keyframe keyframe) implements HistoryAction {
    }

    record RemoveKeyframe(int trackIndex, float time) implements HistoryAction {
    }

    record MoveKeyframe(int trackIndex, float fromTime, float toTime) implements HistoryAction {
    }

    record ChangeValue(int trackIndex, float time, float oldValue, float newValue) implements HistoryAction {
    }

    record ChangeInterpolation(int trackIndex, float time, Keyframe.Interpolation oldType, Keyframe.Interpolation newType)
            implements HistoryAction {
    }

    record AddTrack(int trackIndex, TrackSnapshot snapshot) implements HistoryAction {
    }

    record RemoveTrack(int trackIndex, TrackSnapshot snapshot) implements HistoryAction {
    }
}
