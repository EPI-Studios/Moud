package com.moud.client.editor.ui.panel.timeline;

import com.moud.client.editor.ui.SceneEditorOverlay;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class Navigation {
    private Navigation() {
    }

    public record Result(
            float zoomMin,
            float zoomMax,
            boolean draggingPlayhead,
            float currentTime,
            float dragStartX,
            float dragStartZoomMin,
            float dragStartZoomMax,
            boolean draggingZoomLeft,
            boolean draggingZoomRight,
            boolean draggingZoomBar
    ) {
    }

    public static Result handle(
            SceneEditorOverlay overlay,
            Supplier<String> resolveAnimationId,
            Consumer<Float> applyAnimationAtTime,
            float mouseX,
            float mouseY,
            float timelineX,
            float timelineWidth,
            float topY,
            float rulerHeight,
            float trackHeight,
            float zoomHeight,
            float zoomMin,
            float zoomMax,
            boolean draggingPlayhead,
            float currentTime,
            float dragStartX,
            float dragStartZoomMin,
            float dragStartZoomMax,
            boolean draggingZoomLeft,
            boolean draggingZoomRight,
            boolean draggingZoomBar,
            double clipDurationSeconds
    ) {
        boolean hoveredTimeline = mouseX >= timelineX
                && mouseX <= timelineX + timelineWidth
                && mouseY >= topY
                && mouseY <= topY + rulerHeight + trackHeight;
        boolean hoveredRuler = hoveredTimeline && mouseY <= topY + rulerHeight;
        boolean hoveredZoom = mouseX >= timelineX
                && mouseX <= timelineX + timelineWidth
                && mouseY >= topY + rulerHeight + trackHeight
                && mouseY <= topY + rulerHeight + trackHeight + zoomHeight;

        double duration = Math.max(0.001, clipDurationSeconds);
        double visibleStart = zoomMin * duration;
        double visibleEnd = zoomMax * duration;
        double visibleSpan = visibleEnd - visibleStart;

        // mouse wheel zoom/pan
        float wheel = ImGui.getIO().getMouseWheel();
        boolean ctrlHeld = ImGui.getIO().getKeyCtrl();
        if (hoveredTimeline && wheel != 0f) {
            if (ctrlHeld) {
                double zoomDelta = zoomMax - zoomMin;
                double mousePercent = (mouseX - timelineX) / timelineWidth;
                if (wheel > 0 && zoomDelta > 0.001) {
                    zoomMin = (float) (zoomMin + zoomDelta * 0.05 * mousePercent);
                    zoomMax = (float) (zoomMax - zoomDelta * 0.05 * (1 - mousePercent));
                } else if (wheel < 0) {
                    zoomMin = (float) Math.max(0, zoomMin - zoomDelta * 0.05 * mousePercent);
                    zoomMax = (float) Math.min(1, zoomMax + zoomDelta * 0.05 * (1 - mousePercent));
                }
                zoomMin = clamp01(zoomMin);
                zoomMax = clamp01(Math.max(zoomMin + 0.001f, zoomMax));
            } else {
                double zoomDelta = zoomMax - zoomMin;
                double panAmount = zoomDelta * 0.05 * wheel;
                double newMin = zoomMin - panAmount;
                double newMax = zoomMax - panAmount;
                if (newMin >= 0 && newMax <= 1) {
                    zoomMin = (float) newMin;
                    zoomMax = (float) newMax;
                }
            }
        }

        // middle mouse pan
        if (hoveredTimeline && ImGui.isMouseDragging(ImGuiMouseButton.Middle)) {
            float dx = ImGui.getIO().getMouseDeltaX();
            double shiftSeconds = dx / timelineWidth * visibleSpan;
            double newStart = visibleStart - shiftSeconds;
            double newEnd = visibleEnd - shiftSeconds;
            if (newStart < 0) {
                newEnd -= newStart;
                newStart = 0;
            }
            if (newEnd > duration) {
                double diff = newEnd - duration;
                newStart -= diff;
                newEnd = duration;
            }
            zoomMin = clamp01((float) (newStart / duration));
            zoomMax = clamp01((float) Math.max(zoomMin + 0.001, newEnd / duration));
        }

        // playhead drag on ruler
        if (hoveredRuler && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            draggingPlayhead = true;
        }
        if (draggingPlayhead) {
            if (ImGui.isMouseDown(ImGuiMouseButton.Left)) {
                double t = visibleStart + (mouseX - timelineX) / timelineWidth * visibleSpan;
                currentTime = (float) Math.max(0, Math.min(duration, t));
                overlay.seekAnimation(resolveAnimationId.get(), currentTime);
                applyAnimationAtTime.accept(currentTime);
            } else {
                draggingPlayhead = false;
            }
        }

        // zoom bar interactions
        float handleMin = timelineX + zoomMin * timelineWidth;
        float handleMax = timelineX + zoomMax * timelineWidth;
        if (hoveredZoom && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            dragStartX = mouseX;
            dragStartZoomMin = zoomMin;
            dragStartZoomMax = zoomMax;
            float edgeSize = 8f;
            if (mouseX >= handleMin - edgeSize && mouseX <= handleMin + edgeSize) {
                draggingZoomLeft = true;
            } else if (mouseX >= handleMax - edgeSize && mouseX <= handleMax + edgeSize) {
                draggingZoomRight = true;
            } else if (mouseX >= handleMin && mouseX <= handleMax) {
                draggingZoomBar = true;
            }
        }
        if (draggingZoomBar || draggingZoomLeft || draggingZoomRight) {
            if (ImGui.isMouseDown(ImGuiMouseButton.Left)) {
                float dx = mouseX - dragStartX;
                float delta = dx / timelineWidth;
                if (draggingZoomBar) {
                    zoomMin = clamp01(dragStartZoomMin + delta);
                    zoomMax = clamp01(dragStartZoomMax + delta);
                    float span = zoomMax - zoomMin;
                    if (span < 0.01f) {
                        zoomMax = zoomMin + 0.01f;
                    }
                    if (zoomMax > 1f) {
                        float diff = zoomMax - 1f;
                        zoomMax = 1f;
                        zoomMin = Math.max(0f, zoomMin - diff);
                    }
                    if (zoomMin < 0f) {
                        float diff = -zoomMin;
                        zoomMin = 0f;
                        zoomMax = Math.min(1f, zoomMax + diff);
                    }
                } else if (draggingZoomLeft) {
                    zoomMin = clamp01(dragStartZoomMin + delta);
                    if (zoomMin > zoomMax - 0.01f) {
                        zoomMin = zoomMax - 0.01f;
                    }
                } else if (draggingZoomRight) {
                    zoomMax = clamp01(dragStartZoomMax + delta);
                    if (zoomMax < zoomMin + 0.01f) {
                        zoomMax = zoomMin + 0.01f;
                    }
                }
            } else {
                draggingZoomBar = false;
                draggingZoomLeft = false;
                draggingZoomRight = false;
            }
        }

        return new Result(
                zoomMin,
                zoomMax,
                draggingPlayhead,
                currentTime,
                dragStartX,
                dragStartZoomMin,
                dragStartZoomMax,
                draggingZoomLeft,
                draggingZoomRight,
                draggingZoomBar
        );
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
