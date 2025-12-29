package com.moud.client.editor.ui.panel.timeline;

import com.moud.api.animation.Keyframe;

public record CopiedKeyframe(int trackIndex, float time, float value, Keyframe.Interpolation interpolation) {
}
