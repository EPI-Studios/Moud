package com.moud.client.editor.ui.panel;

import com.moud.api.animation.AnimatableProperty;
import com.moud.api.animation.AnimatableRegistry;
import com.moud.api.animation.AnimationClip;
import com.moud.api.animation.EventKeyframe;
import com.moud.api.animation.Keyframe;
import com.moud.api.animation.ObjectTrack;
import com.moud.api.animation.PropertyTrack;
import com.moud.client.editor.scene.SceneObject;
import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.client.editor.ui.panel.timeline.CopiedKeyframe;
import com.moud.client.editor.ui.panel.timeline.History;
import com.moud.client.editor.ui.panel.timeline.HistoryAction;
import com.moud.client.editor.ui.panel.timeline.KeyframeDrag;
import com.moud.client.editor.ui.panel.timeline.KeyframeOps;
import com.moud.client.editor.ui.panel.timeline.KeyframePopups;
import com.moud.client.editor.ui.panel.timeline.KeyframeSelection;
import com.moud.client.editor.ui.panel.timeline.Navigation;
import com.moud.client.editor.ui.panel.timeline.Playback;
import com.moud.client.editor.ui.panel.timeline.Recording;
import com.moud.client.editor.ui.panel.timeline.RowEntry;
import com.moud.client.editor.ui.panel.timeline.RowType;
import com.moud.client.editor.ui.panel.timeline.SelectedKeyframe;
import com.moud.client.editor.ui.panel.timeline.ToolbarView;
import com.moud.client.editor.ui.panel.timeline.TrackLabelsView;
import com.moud.client.editor.ui.panel.timeline.TrackSnapshot;
import com.moud.client.editor.ui.panel.timeline.TrackView;
import com.moud.client.editor.ui.panel.timeline.TransformSnapshot;
import com.moud.network.MoudPackets;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

public final class AnimationTimelinePanel {
    private final SceneEditorOverlay overlay;
    private final History history;
    private final ImString savePathBuffer = new ImString(256);
    private final ImString filterBuffer = new ImString(64);
    private final List<MoudPackets.AnimationFileInfo> availableAnimations = new ArrayList<>();
    private final List<String> recentEvents = new ArrayList<>();
    private final List<SelectedKeyframe> selectedKeyframes = new ArrayList<>();
    private final List<TrackView> tracks = new ArrayList<>();
    private final List<RowEntry> visibleRows = new ArrayList<>();
    private final Map<Integer, Boolean> trackVisibility = new LinkedHashMap<>();
    private final Map<Integer, float[]> trackColors = new LinkedHashMap<>();
    private final Map<String, Boolean> groupExpanded = new HashMap<>();
    private final Map<String, TransformSnapshot> lastRecordedTransforms = new HashMap<>();
    private int snapFps = 30;
    private boolean snappingEnabled = true;
    private boolean listRequested;
    private AnimationClip currentClip;
    private String selectedPath;
    private float currentTime;
    private boolean playing;
    private boolean loop;
    private float speed = 1.0f;
    private boolean recording;
    private float zoomMin = 0f;
    private float zoomMax = 1f;
    private boolean boxSelecting;
    private float boxStartX;
    private float boxStartY;
    private float boxEndX;
    private float boxEndY;
    private boolean draggingPlayhead;
    private boolean draggingZoomBar;
    private boolean draggingZoomLeft;
    private boolean draggingZoomRight;
    private float dragStartX;
    private float dragStartZoomMin;
    private float dragStartZoomMax;
    private boolean draggingKeyframes;
    private float dragKeyStartX;
    private List<Double> dragOriginalTimes = new ArrayList<>();
    private List<Double> dragCurrentTimes = new ArrayList<>();
    private boolean snapDuringDrag;
    private float timelineStartX;
    private float timelineWidth;
    private double visibleStartSeconds;
    private double visibleSpanSeconds;
    private List<SelectedKeyframe> dragSelectedSnapshot = new ArrayList<>();
    private SelectedKeyframe editingKeyframe;
    private int editingTrackIndex = -1;
    private boolean keyframeContextPopupOpen;
    private boolean keyframeValueEditorPopupOpen;
    private List<CopiedKeyframe> copiedKeyframes = new ArrayList<>();
    private int contextTrackIndex = -1;
    private double contextTime = 0;
    private boolean contextOnKeyframe;
    private final List<SelectedKeyframe> contextSelectedKeyframes = new ArrayList<>();
    private int renamingTrackIndex = -1;
    private final ImString renameBuffer = new ImString(128);
    private int colorPickerTrackIndex = -1;
    private int draggingTrackIndex = -1;
    private float dragTrackStartY;

    private long lastRenderNanos = 0;
    private long renderDeltaNanos = 0;
    private final Map<Integer, Float> trackAnimatedOffsets = new HashMap<>();  // Smooth track reordering
    private float trackScrollY = 0f;

    public AnimationTimelinePanel(SceneEditorOverlay overlay) {
        this.overlay = overlay;
        this.history = new History(tracks, trackVisibility, this::ensureKeyframes, this::cloneKeyframe, this::rebuildRowEntries);
        AnimatableRegistry.registerDefaults();
        this.currentClip = null;
        rebuildTrackViews();
    }

    public void renderInCurrentWindow() {
        long currentTime = System.nanoTime();
        renderDeltaNanos = Math.max(0, Math.min(1_000_000_000, currentTime - lastRenderNanos));
        lastRenderNanos = currentTime;

        if (!listRequested) {
            listRequested = true;
            overlay.requestAnimationList();
        }
        autoLoadFirstIfNeeded();

        tickPlayback(ImGui.getIO().getDeltaTime());
        updateSmoothAnimations();
        handleUndoRedoHotkeys();
        handleAnimationHotkeys();

        renderToolbar();
        renderTimelineArea();
        renderKeyframeContextMenu();
        renderValueEditorPopup();
    }

    private void updateSmoothAnimations() {
        double animationMultiplier = Math.pow(0.85, renderDeltaNanos / 10_000_000.0);
        trackAnimatedOffsets.entrySet().removeIf(entry -> {
            float offset = entry.getValue();
            offset *= (float) animationMultiplier;
            if (Math.abs(offset) < 0.5f) {
                return true;
            }
            entry.setValue(offset);
            return false;
        });
    }

    private void autoLoadFirstIfNeeded() {
        if (currentClip != null || availableAnimations.isEmpty()) {
            return;
        }
        MoudPackets.AnimationFileInfo first = availableAnimations.getFirst();
        selectedPath = first.path();
        savePathBuffer.set(first.path());
        overlay.openAnimation(first.path());
    }

    private void renderToolbar() {
        ToolbarView.Result result = ToolbarView.render(
                overlay,
                savePathBuffer,
                filterBuffer,
                availableAnimations,
                recentEvents,
                history,
                lastRecordedTransforms,
                this::rebuildTrackViews,
                this::insertKeyframeAtCurrentTime,
                this::pushEventIndicator,
                this::resolveAnimationId,
                t -> applyAnimationAtTime(t),
                t -> dispatchEventTrack(t),
                currentClip,
                selectedPath,
                currentTime,
                playing,
                loop,
                speed,
                recording,
                listRequested
        );
        this.currentClip = result.currentClip();
        this.selectedPath = result.selectedPath();
        this.currentTime = result.currentTime();
        this.playing = result.playing();
        this.loop = result.loop();
        this.speed = result.speed();
        this.recording = result.recording();
        this.listRequested = result.listRequested();
    }

    public boolean isRecording() {
        return recording;
    }

    public void recordTransform(SceneObject object, float[] translation, float[] rotation, float[] scale) {
        Recording.ClipUpdate update = Recording.recordTransform(
                currentClip,
                recording,
                lastRecordedTransforms,
                object,
                currentTime,
                translation,
                rotation,
                scale
        );
        if (update.clip() != currentClip) {
            currentClip = update.clip();
        }
        if (update.changed()) {
            rebuildTrackViews();
        }
    }

    public void recordLimbTransform(SceneObject object, String limbKey, float[] translation, float[] rotation, float[] scale) {
        Recording.ClipUpdate update = Recording.recordLimbTransform(
                currentClip,
                recording,
                lastRecordedTransforms,
                object,
                limbKey,
                currentTime,
                translation,
                rotation,
                scale
        );
        if (update.clip() != currentClip) {
            currentClip = update.clip();
        }
        if (update.changed()) {
            rebuildTrackViews();
        }
    }

    public void pushEventIndicator(String eventName) {
        if (eventName == null || eventName.isEmpty()) {
            return;
        }
        recentEvents.add(eventName);
        if (recentEvents.size() > 5) {
            recentEvents.remove(0);
        }
    }

    private void renderTimelineArea() {
        if (currentClip == null) {
            ImGui.textDisabled("No animation loaded. Select one from the dropdown above.");
            return;
        }
        if (visibleRows.isEmpty()) {
            rebuildRowEntries();
        }
        float contentWidth = Math.max(1f, ImGui.getContentRegionAvailX());
        float rowHeight = ImGui.getTextLineHeightWithSpacing() + 6f;
        float availableHeight = ImGui.getContentRegionAvailY() - 10f;
        float leftWidth = 220f;
        float rulerHeight = 28f;
        float zoomBarHeight = 18f;
        float maxTrackHeight = availableHeight - rulerHeight - zoomBarHeight;
        float totalTrackHeight = rowHeight * Math.max(1, visibleRows.size());
        float trackAreaHeight = Math.min(totalTrackHeight, maxTrackHeight);
        float contentHeight = rulerHeight + trackAreaHeight + zoomBarHeight;

        float leftX = ImGui.getCursorScreenPosX();
        float leftY = ImGui.getCursorScreenPosY();
        this.timelineStartX = leftX + leftWidth;
        this.timelineWidth = contentWidth - leftWidth;
        double duration = Math.max(0.001, currentClip.duration());
        this.visibleStartSeconds = zoomMin * duration;
        this.visibleSpanSeconds = (zoomMax - zoomMin) * duration;

        ImDrawList drawList = ImGui.getWindowDrawList();
        float rightX = leftX + leftWidth;

        drawList.addRectFilled(leftX, leftY, leftX + leftWidth, leftY + contentHeight, 0xFF1A1A1A);  // Dark panel
        drawList.addRectFilled(rightX, leftY, rightX + (contentWidth - leftWidth), leftY + contentHeight, 0xFF252525);  // Slightly lighter timeline

        drawList.addLine(rightX, leftY, rightX, leftY + contentHeight, 0xFF404040, 1f);

        boolean needsScroll = totalTrackHeight > maxTrackHeight;
        float maxScrollY = Math.max(0, totalTrackHeight - trackAreaHeight);

        float mouseX = ImGui.getIO().getMousePosX();
        float mouseY = ImGui.getIO().getMousePosY();
        boolean inTrackArea = mouseX >= leftX && mouseX <= leftX + contentWidth &&
                              mouseY >= leftY + rulerHeight && mouseY <= leftY + rulerHeight + trackAreaHeight;
        if (inTrackArea && !ImGui.getIO().getKeyCtrl()) {
            float wheel = ImGui.getIO().getMouseWheel();
            if (wheel != 0 && needsScroll) {
                trackScrollY = Math.max(0, Math.min(maxScrollY, trackScrollY - wheel * rowHeight * 2));
            }
        }

        drawList.pushClipRect(rightX, leftY, rightX + (contentWidth - leftWidth), leftY + contentHeight, true);
        renderRuler(drawList, rightX, leftY, contentWidth - leftWidth, rulerHeight);

        drawList.pushClipRect(rightX, leftY + rulerHeight, rightX + (contentWidth - leftWidth), leftY + rulerHeight + trackAreaHeight, true);
        renderTracks(drawList, leftX, leftY + rulerHeight - trackScrollY, leftWidth, rightX, totalTrackHeight, contentWidth - leftWidth);
        drawList.popClipRect();

        renderPlayhead(drawList, rightX, leftY + rulerHeight, contentWidth - leftWidth, trackAreaHeight);
        renderZoomBar(drawList, rightX, leftY + contentHeight - zoomBarHeight, contentWidth - leftWidth, zoomBarHeight);
        drawList.popClipRect();

        drawList.pushClipRect(leftX, leftY + rulerHeight, rightX, leftY + rulerHeight + trackAreaHeight, true);
        renderTrackLabels(drawList, leftX, leftY + rulerHeight - trackScrollY);
        drawList.popClipRect();

        if (needsScroll) {
            float scrollPercent = trackScrollY / maxScrollY;
            float scrollBarHeight = Math.max(20f, trackAreaHeight * (trackAreaHeight / totalTrackHeight));
            float scrollBarY = leftY + rulerHeight + scrollPercent * (trackAreaHeight - scrollBarHeight);
            drawList.addRectFilled(rightX - 6, scrollBarY, rightX - 2, scrollBarY + scrollBarHeight, 0x80FFFFFF, 2f);
        }

        handleInteractions(leftX, leftY, leftWidth, rightX, contentWidth - leftWidth, rulerHeight, trackAreaHeight, zoomBarHeight);

        ImGui.dummy(contentWidth, contentHeight);
    }

    private void renderRuler(ImDrawList drawList, float x, float y, float width, float height) {
        double duration = Math.max(0.001, currentClip.duration());
        double visibleStart = visibleStartSeconds;
        double visibleEnd = visibleStartSeconds + visibleSpanSeconds;
        double visibleSpan = visibleSpanSeconds;

        drawList.addRectFilled(x, y, x + width, y + height, ImGui.getColorU32(ImGuiCol.FrameBg));
        drawList.addLine(x, y + height - 1, x + width, y + height - 1, ImGui.getColorU32(ImGuiCol.Border));

        double pixelsPerSecond = width / visibleSpan;
        double targetPxPerTick = 80.0;
        double rawStep = targetPxPerTick / pixelsPerSecond;
        double step = chooseStep(rawStep);

        int startTick = (int) Math.floor(visibleStart / step);
        int endTick = (int) Math.ceil(visibleEnd / step);

        for (int i = startTick; i <= endTick; i++) {
            double t = i * step;
            float px = (float) (x + (t - visibleStart) / visibleSpan * width);
            boolean major = i % 5 == 0;
            float lineHeight = major ? height : height * 0.6f;
            drawList.addLine(px, y + height - lineHeight, px, y + height, ImGui.getColorU32(ImGuiCol.TextDisabled));
            if (major) {
                String label = formatTime(t);
                drawList.addText(px + 2, y + 2, ImGui.getColorU32(ImGuiCol.Text), label);
            }
        }

        if (currentClip != null && currentClip.eventTrack() != null) {
            int eventColor = 0xFFFF9900;
            for (EventKeyframe ev : currentClip.eventTrack()) {
                double t = ev.time();
                if (t < visibleStart || t > visibleEnd) continue;
                float px = (float) (x + (t - visibleStart) / visibleSpan * width);
                float markerSize = 5f;
                float markerY = y + height - 2;
                drawList.addTriangleFilled(px, markerY, px + markerSize, markerY - markerSize, px, markerY - markerSize * 2, eventColor);
                drawList.addRectFilled(px - 1, markerY - markerSize * 2, px + 1, markerY, eventColor);
                if (ImGui.isMouseHoveringRect(px - markerSize, markerY - markerSize * 2, px + markerSize, markerY)) {
                    ImGui.setTooltip("Event: " + ev.name() + "\nTime: " + formatTime(t));
                }
            }
        }
    }

    private void renderTracks(ImDrawList drawList, float leftX, float y, float leftWidth, float rightX, float trackAreaHeight, float timelineWidth) {
        float rowHeight = ImGui.getTextLineHeightWithSpacing() + 6f;
        double visibleStart = visibleStartSeconds;
        double visibleEnd = visibleStartSeconds + visibleSpanSeconds;
        double visibleSpan = visibleSpanSeconds;
        float keyWidth = timelineWidth;

        List<RowEntry> rows = new ArrayList<>(visibleRows);
        int rowIndex = 0;
        for (RowEntry row : rows) {
            float rowTop = y + rowIndex * rowHeight;
            float rowCenter = rowTop + rowHeight * 0.5f;

            int bg = (rowIndex % 2 == 0) ? ImGui.getColorU32(ImGuiCol.FrameBg) : ImGui.getColorU32(ImGuiCol.FrameBgHovered);
            drawList.addRectFilled(rightX, rowTop, rightX + keyWidth, rowTop + rowHeight, bg);

            if (row.type() != RowType.TRACK || row.trackIndex() == null) {
                rowIndex++;
                continue;
            }
            int trackIdx = row.trackIndex();
            TrackView track = tracks.get(trackIdx);
            if (!trackVisibility.getOrDefault(trackIdx, true)) {
                rowIndex++;
                continue;
            }

            List<Keyframe> keyframes = track.keyframes();
            if (keyframes != null) {
                for (Keyframe kf : keyframes) {
                    double t = kf.time();
                    if (t < visibleStart || t > visibleEnd) continue;
                    float px = (float) (rightX + (t - visibleStart) / visibleSpan * keyWidth);
                    float size = 6f;
                    int color = ImGui.getColorU32(ImGuiCol.PlotLines);
                    float[] custom = trackColors.get(trackIdx);
                    if (custom != null) {
                        color = ImGui.colorConvertFloat4ToU32(custom[0], custom[1], custom[2], 1f);
                    }
                    if (isSelected(trackIdx, kf.time())) {
                        color = ImGui.getColorU32(ImGuiCol.PlotHistogram);
                    }
                    drawKeyframeShape(drawList, px, rowCenter, (int) size, color, kf.interpolation());
                }
            }
            rowIndex++;
        }
    }

    private void renderPlayhead(ImDrawList drawList, float x, float y, float width, float height) {
        double visibleStart = visibleStartSeconds;
        double visibleEnd = visibleStartSeconds + visibleSpanSeconds;
        double visibleSpan = visibleSpanSeconds;
        double t = Math.max(visibleStart, Math.min(visibleEnd, currentTime));
        float px = (float) (x + (t - visibleStart) / visibleSpan * width);
        int color = ImGui.getColorU32(ImGuiCol.PlotHistogramHovered);
        drawList.addLine(px, y, px, y + height, color, 1.5f);
        float triangleSize = 6f;
        drawList.addTriangleFilled(px, y, px - triangleSize, y - triangleSize, px + triangleSize, y - triangleSize, color);

        // box selection
        if (boxSelecting) {
            float minX = Math.min(boxStartX, boxEndX);
            float maxX = Math.max(boxStartX, boxEndX);
            float minY = Math.min(boxStartY, boxEndY);
            float maxY = Math.max(boxStartY, boxEndY);
            int fill = ImGui.getColorU32(ImGuiCol.PlotHistogram) & 0x40FFFFFF;
            int border = ImGui.getColorU32(ImGuiCol.PlotHistogramHovered);
            drawList.addRectFilled(minX, minY, maxX, maxY, fill);
            drawList.addRect(minX, minY, maxX, maxY, border);
        }
    }

    private void selectKeyframeAt(float mouseX, float mouseY, float timelineX, float timelineWidth, float keyY, float keyHeight, double visibleStart, double visibleSpan, boolean additive) {
        KeyframeSelection.selectKeyframeAt(
                mouseX,
                mouseY,
                timelineX,
                visibleStart,
                visibleSpan,
                keyY,
                visibleRows,
                trackVisibility,
                tracks,
                selectedKeyframes,
                this.timelineWidth,
                additive
        );
    }

    private boolean isSelected(int trackIndex, double time) {
        return KeyframeSelection.isSelected(trackIndex, time, selectedKeyframes);
    }

    private void performBoxSelection(float timelineX, float timelineWidth, float keyY, float keyHeight, double visibleStart, double visibleEnd, double visibleSpan) {
        KeyframeSelection.performBoxSelection(
                boxStartX,
                boxStartY,
                boxEndX,
                boxEndY,
                timelineX,
                visibleStart,
                visibleEnd,
                visibleSpan,
                keyY,
                visibleRows,
                trackVisibility,
                tracks,
                selectedKeyframes,
                this.timelineWidth
        );
    }


    private boolean attemptBeginKeyframeDrag(float mouseX, float mouseY, float timelineX, float timelineWidth, float keyY, float keyHeight, double visibleStart, double visibleSpan) {
        KeyframeDrag.BeginResult begin = KeyframeDrag.attemptBegin(
                mouseX,
                mouseY,
                timelineX,
                visibleStart,
                visibleSpan,
                keyY,
                visibleRows,
                trackVisibility,
                tracks,
                selectedKeyframes,
                this.timelineWidth
        );
        if (!begin.started()) {
            return false;
        }
        draggingKeyframes = true;
        dragKeyStartX = begin.dragKeyStartX();
        dragOriginalTimes = begin.dragOriginalTimes();
        dragCurrentTimes = begin.dragCurrentTimes();
        dragSelectedSnapshot = begin.dragSelectedSnapshot();
        snapDuringDrag = false;
        return true;
    }

    private void updateKeyframeDrag(float mouseX, float timelineX, float timelineWidth, double visibleStart, double visibleSpan, boolean snap) {
        KeyframeDrag.UpdateResult result = KeyframeDrag.update(
                mouseX,
                dragKeyStartX,
                visibleSpanSeconds,
                this.timelineWidth,
                snap,
                dragSelectedSnapshot,
                dragOriginalTimes,
                dragCurrentTimes,
                this::snapToFrame,
                tracks,
                this::ensureKeyframes,
                currentClip.duration(),
                selectedKeyframes
        );
        snapDuringDrag = result.snapDuringDrag();
        dragCurrentTimes = result.dragCurrentTimes();
    }


    private void finalizeKeyframeDrag() {
        KeyframeDrag.finalizeDrag(dragSelectedSnapshot, dragOriginalTimes, selectedKeyframes, history);
    }

    private void renderZoomBar(ImDrawList drawList, float x, float y, float width, float height) {
        drawList.addRectFilled(x, y, x + width, y + height, 0xFF1A1A1A, 4f);

        float handleMin = x + zoomMin * width;
        float handleMax = x + zoomMax * width;
        float handleWidth = handleMax - handleMin;

        int barColor = (draggingZoomBar || draggingZoomLeft || draggingZoomRight) ? 0xFF5A9BD4 : 0xFF3A7BB4;
        drawList.addRectFilled(handleMin, y + 2, handleMax, y + height - 2, barColor, 3f);
        float handleSize = 8f;
        int leftHandleColor = draggingZoomLeft ? 0xFFFFFFFF : 0xFFCCCCCC;
        drawList.addRectFilled(handleMin - 2, y + 1, handleMin + handleSize, y + height - 1, leftHandleColor, 2f);
        drawList.addLine(handleMin + 2, y + 5, handleMin + 2, y + height - 5, 0xFF888888);
        drawList.addLine(handleMin + 4, y + 5, handleMin + 4, y + height - 5, 0xFF888888);

        int rightHandleColor = draggingZoomRight ? 0xFFFFFFFF : 0xFFCCCCCC;
        drawList.addRectFilled(handleMax - handleSize, y + 1, handleMax + 2, y + height - 1, rightHandleColor, 2f);
        drawList.addLine(handleMax - 4, y + 5, handleMax - 4, y + height - 5, 0xFF888888);
        drawList.addLine(handleMax - 2, y + 5, handleMax - 2, y + height - 5, 0xFF888888);

        float mouseX = ImGui.getIO().getMousePosX();
        float mouseY = ImGui.getIO().getMousePosY();
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            double startSec = zoomMin * currentClip.duration();
            double endSec = zoomMax * currentClip.duration();
            ImGui.setTooltip(String.format("Visible: %.2fs - %.2fs (%.1f%%)",
                    startSec, endSec, (zoomMax - zoomMin) * 100));
        }
    }

    private void handleInteractions(float leftX, float topY, float leftWidth, float timelineX, float timelineWidth, float rulerHeight, float trackHeight, float zoomHeight) {
        float mouseX = ImGui.getIO().getMousePosX();
        float mouseY = ImGui.getIO().getMousePosY();

        double duration = Math.max(0.001, currentClip.duration());
        double visibleStart = zoomMin * duration;
        double visibleEnd = zoomMax * duration;
        double visibleSpan = visibleEnd - visibleStart;

        Navigation.Result navigation = Navigation.handle(
                overlay,
                this::resolveAnimationId,
                this::applyAnimationAtTime,
                mouseX,
                mouseY,
                timelineX,
                timelineWidth,
                topY,
                rulerHeight,
                trackHeight,
                zoomHeight,
                zoomMin,
                zoomMax,
                draggingPlayhead,
                currentTime,
                dragStartX,
                dragStartZoomMin,
                dragStartZoomMax,
                draggingZoomLeft,
                draggingZoomRight,
                draggingZoomBar,
                currentClip.duration()
        );
        zoomMin = navigation.zoomMin();
        zoomMax = navigation.zoomMax();
        draggingPlayhead = navigation.draggingPlayhead();
        currentTime = navigation.currentTime();
        dragStartX = navigation.dragStartX();
        dragStartZoomMin = navigation.dragStartZoomMin();
        dragStartZoomMax = navigation.dragStartZoomMax();
        draggingZoomLeft = navigation.draggingZoomLeft();
        draggingZoomRight = navigation.draggingZoomRight();
        draggingZoomBar = navigation.draggingZoomBar();

        // selection box in keyframe area
        boolean inKeyframeArea = mouseX >= timelineX && mouseX <= timelineX + timelineWidth && mouseY >= topY + rulerHeight && mouseY <= topY + rulerHeight + trackHeight;
        boolean anyPopupOpen = keyframeContextPopupOpen || keyframeValueEditorPopupOpen;
        boolean beganBoxSelection = false;
        if (inKeyframeArea && ImGui.isMouseClicked(ImGuiMouseButton.Left) && !anyPopupOpen) {
            if (!attemptBeginKeyframeDrag(mouseX, mouseY, timelineX, timelineWidth, topY + rulerHeight, trackHeight, visibleStart, visibleSpan)) {
                boxSelecting = true;
                beganBoxSelection = true;
                boxStartX = boxEndX = mouseX;
                boxStartY = boxEndY = mouseY;
            }
        }
        if (boxSelecting && !anyPopupOpen) {
            if (ImGui.isMouseDown(ImGuiMouseButton.Left)) {
                boxEndX = mouseX;
                boxEndY = mouseY;
            } else {
                performBoxSelection(timelineX, timelineWidth, topY + rulerHeight, trackHeight, visibleStart, visibleEnd, visibleSpan);
                boxSelecting = false;
            }
        }
        if (boxSelecting && anyPopupOpen) {
            boxSelecting = false;
        }

        // keyframe click selection
        if (inKeyframeArea && ImGui.isMouseClicked(ImGuiMouseButton.Left) && !beganBoxSelection && !draggingKeyframes && !anyPopupOpen) {
            selectKeyframeAt(mouseX, mouseY, timelineX, timelineWidth, topY + rulerHeight, trackHeight, visibleStart, visibleSpan, ImGui.getIO().getKeyCtrl());
        }

        // keyframe drag move
        if (draggingKeyframes) {
            if (ImGui.isMouseDown(ImGuiMouseButton.Left)) {
                updateKeyframeDrag(mouseX, timelineX, timelineWidth, visibleStart, visibleSpan, ImGui.getIO().getKeyShift());
            } else {
                finalizeKeyframeDrag();
                draggingKeyframes = false;
            }
        }

        // right-click context
        if (inKeyframeArea && ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
            SelectedKeyframe hit = findKeyframeAt(mouseX, mouseY, timelineX, timelineWidth, topY + rulerHeight, visibleStart, visibleSpan);
            if (hit != null) {
                if (!isSelected(hit.trackIndex(), hit.time())) {
                    selectedKeyframes.clear();
                    selectedKeyframes.add(hit);
                }
                contextOnKeyframe = true;
                contextTrackIndex = hit.trackIndex();
                contextTime = hit.time();
                contextSelectedKeyframes.clear();
                contextSelectedKeyframes.addAll(selectedKeyframes);
                ImGui.openPopup("##KeyframeContext");
            } else {
                int trackIdx = rowIndexFromY(mouseY, topY + rulerHeight);
                if (trackIdx >= 0 && trackIdx < tracks.size()) {
                    contextOnKeyframe = false;
                    contextTrackIndex = trackIdx;
                    contextTime = snapToFrame(pixelXToTime(mouseX));
                    contextSelectedKeyframes.clear();
                    contextSelectedKeyframes.addAll(selectedKeyframes);
                    ImGui.openPopup("##KeyframeContext");
                }
            }
        }

        // double-click value editor
        if (inKeyframeArea && ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
            SelectedKeyframe hit = findKeyframeAt(mouseX, mouseY, timelineX, timelineWidth, topY + rulerHeight, visibleStart, visibleSpan);
            if (hit != null) {
                editingKeyframe = hit;
                editingTrackIndex = hit.trackIndex();
                ImGui.openPopup("##KeyframeValueEditor");
            }
        }

        // keyboard delete
        if (!ImGui.getIO().getWantCaptureKeyboard()) {
            if (ImGui.isKeyPressed(ImGuiKey.Delete) || ImGui.isKeyPressed(ImGuiKey.Backspace)) {
                deleteSelectedKeyframes();
            }
        }
    }

    private void handleUndoRedoHotkeys() {
        boolean ctrl = ImGui.getIO().getKeyCtrl();
        boolean wantKeyboard = ImGui.getIO().getWantCaptureKeyboard();
        // space bar play/pause only when not dragging/typing in timeline
        boolean allowSpace = !wantKeyboard && !draggingPlayhead && !draggingKeyframes && !boxSelecting;
        if (allowSpace && ImGui.isKeyPressed(ImGuiKey.Space, false) && currentClip != null) {
            playing = !playing;
            if (playing) {
                overlay.seekAnimation(resolveAnimationId(), currentTime);
                applyAnimationAtTime(currentTime);
                overlay.playAnimation(resolveAnimationId(), loop, speed);
                dispatchEventTrack(currentTime);
            } else {
                overlay.stopAnimation(resolveAnimationId());
            }
        }
        if (!ctrl) {
            return;
        }
        if (ImGui.isKeyPressed(ImGuiKey.Z, false)) {
            history.undo();
        }
        if (ImGui.isKeyPressed(ImGuiKey.Y, false)) {
            history.redo();
        }
        if (!wantKeyboard && ImGui.isKeyPressed(ImGuiKey.C, false) && !selectedKeyframes.isEmpty()) {
            copiedKeyframes = copySelectedKeyframes();
        }
        if (!wantKeyboard && ImGui.isKeyPressed(ImGuiKey.V, false)) {
            KeyframeOps.pasteKeyframes(copiedKeyframes, currentTime, history);
        }
    }

    private void handleAnimationHotkeys() {
        boolean wantKeyboard = ImGui.getIO().getWantCaptureKeyboard();
        if (wantKeyboard) {
            return;
        }

        // insert keyframe at current time (k key)
        if (ImGui.isKeyPressed(ImGuiKey.K, false)) {
            insertKeyframeAtCurrentTime();
        }

        if (ImGui.isKeyPressed(ImGuiKey.R, false)) {
            recording = !recording;
            if (recording) {
                lastRecordedTransforms.clear();
                pushEventIndicator("Recording started");
            } else {
                pushEventIndicator("Recording stopped");
            }
        }

        // go to start (home key)
        if (ImGui.isKeyPressed(ImGuiKey.Home, false) && currentClip != null) {
            currentTime = 0f;
            overlay.seekAnimation(resolveAnimationId(), currentTime);
            applyAnimationAtTime(currentTime);
        }

        // end key (goto end)
        if (ImGui.isKeyPressed(ImGuiKey.End, false) && currentClip != null) {
            currentTime = currentClip.duration();
            overlay.seekAnimation(resolveAnimationId(), currentTime);
            applyAnimationAtTime(currentTime);
        }

        // left and right arrow
        if (currentClip != null && !playing) {
            float frameStep = 1.0f / snapFps;
            if (ImGui.isKeyPressed(ImGuiKey.LeftArrow, true)) {
                currentTime = Math.max(0f, currentTime - frameStep);
                overlay.seekAnimation(resolveAnimationId(), currentTime);
                applyAnimationAtTime(currentTime);
            }
            if (ImGui.isKeyPressed(ImGuiKey.RightArrow, true)) {
                currentTime = Math.min(currentClip.duration(), currentTime + frameStep);
                overlay.seekAnimation(resolveAnimationId(), currentTime);
                applyAnimationAtTime(currentTime);
            }
        }
    }

    private void insertKeyframeAtCurrentTime() {
        Recording.InsertResult result = Recording.insertKeyframeAtCurrentTime(
                overlay,
                currentClip,
                currentTime
        );
        if (result.message() != null) {
            pushEventIndicator(result.message());
        }
        if (result.clip() != null && result.clip() != currentClip) {
            currentClip = result.clip();
        }
        if (result.clip() != null) {
            rebuildTrackViews();
        }
    }

    private void drawDiamond(ImDrawList drawList, float cx, float cy, float size, int color) {
        drawList.addTriangleFilled(cx, cy - size, cx + size, cy, cx, cy + size, color);
        drawList.addTriangleFilled(cx, cy - size, cx - size, cy, cx, cy + size, color);
    }

    private void drawKeyframeShape(ImDrawList drawList, float x, float y, int size, int color, Keyframe.Interpolation interp) {
        if (interp == null) {
            interp = Keyframe.Interpolation.LINEAR;
        }
        int easeSize = Math.max(1, size / 5);
        switch (interp) {
            case SMOOTH -> drawList.addCircleFilled(x, y, size, color);
            case LINEAR -> {
                drawList.addTriangleFilled(x - size, y, x, y - size, x, y + size, color);
                drawList.addTriangleFilled(x + size, y, x, y + size, x, y - size, color);
            }
            case STEP -> {
                drawList.addRectFilled(x - size, y - size, x + size, y + size, color);
            }
            case EASE_IN -> {
                drawList.addTriangleFilled(x - size, y - size, x - easeSize, y - size, x - easeSize, y, color);
                drawList.addTriangleFilled(x - size, y + size, x - easeSize, y, x - easeSize, y + size, color);
                drawList.addTriangleFilled(x + size, y, x + easeSize, y + size, x + easeSize, y - size, color);
                drawList.addRectFilled(x - easeSize, y - size, x + easeSize, y + size, color);
            }
            case EASE_OUT -> {
                drawList.addTriangleFilled(x - size, y, x - easeSize, y - size, x - easeSize, y + size, color);
                drawList.addTriangleFilled(x + size, y - size, x + easeSize, y, x + easeSize, y - size, color);
                drawList.addTriangleFilled(x + size, y + size, x + easeSize, y + size, x + easeSize, y, color);
                drawList.addRectFilled(x - easeSize, y - size, x + easeSize, y + size, color);
            }
            case BEZIER -> drawList.addTriangleFilled(x, y - size, x + size, y + size, x - size, y + size, color);
        }
    }

    private void tickPlayback(float deltaSeconds) {
        Playback.TickResult tick = Playback.tickPlayback(
                overlay,
                currentClip,
                selectedPath,
                currentTime,
                playing,
                loop,
                speed,
                deltaSeconds
        );
        currentTime = tick.currentTime();
        playing = tick.playing();
    }

    private void applyAnimationAtTime(float timeSeconds) {
        Playback.applyAnimationAtTime(currentClip, timeSeconds);
    }

    private void dispatchEventTrack(float time) {
        Playback.dispatchEventTrack(currentClip, time, overlay);
    }

    private String formatTime(double seconds) {
        return Playback.formatTime(seconds);
    }

    private double chooseStep(double raw) {
        double[] steps = {0.1, 0.2, 0.5, 1, 2, 5, 10, 20, 30, 60};
        for (double s : steps) {
            if (s >= raw) return s;
        }
        return steps[steps.length - 1];
    }

    private double snapToFrame(double timeSeconds) {
        if (!snappingEnabled || snapFps <= 0) {
            return timeSeconds;
        }
        double frameDuration = 1.0 / snapFps;
        return Math.round(timeSeconds / frameDuration) * frameDuration;
    }

    private void rebuildTrackViews() {
        tracks.clear();
        if (currentClip == null || currentClip.objectTracks() == null) {
            return;
        }
        for (ObjectTrack obj : currentClip.objectTracks()) {
            if (obj.propertyTracks() == null) continue;
            String objectLabel = (obj.targetObjectName() != null ? obj.targetObjectName() : obj.targetObjectId());
            for (Map.Entry<String, PropertyTrack> entry : obj.propertyTracks().entrySet()) {
                PropertyTrack track = entry.getValue();
                String label = objectLabel + " / " + track.propertyPath();
                tracks.add(new TrackView(objectLabel, label, entry.getKey(), obj.propertyTracks(), track));
            }
        }
        trackVisibility.clear();
        trackColors.clear();
        for (int i = 0; i < tracks.size(); i++) {
            trackVisibility.put(i, true);
            float[] rgb = colorForPath(tracks.get(i).propertyPath());
            trackColors.put(i, rgb);
        }
        rebuildRowEntries();
    }

    private float[] colorForPath(String path) {
        if (path == null) {
            return new float[]{1f, 1f, 1f, 1f};
        }
        String lower = path.toLowerCase(Locale.ROOT);
        // Bright, saturated colors for better visibility
        if (lower.endsWith(".x") || lower.contains("position.x") || lower.contains("rotation.x") || lower.contains("scale.x")) {
            return new float[]{1f, 0.3f, 0.3f, 1f}; // Bright red for X axis
        }
        if (lower.endsWith(".y") || lower.contains("position.y") || lower.contains("rotation.y") || lower.contains("scale.y")) {
            return new float[]{0.3f, 1f, 0.3f, 1f}; // Bright green for Y axis
        }
        if (lower.endsWith(".z") || lower.contains("position.z") || lower.contains("rotation.z") || lower.contains("scale.z")) {
            return new float[]{0.3f, 0.5f, 1f, 1f}; // Bright blue for Z axis
        }
        // Special colors for other properties
        if (lower.contains("opacity") || lower.contains("alpha")) {
            return new float[]{1f, 1f, 0.3f, 1f}; // Yellow
        }
        if (lower.contains("intensity") || lower.contains("emission")) {
            return new float[]{1f, 0.6f, 0.2f, 1f}; // Orange
        }
        if (lower.contains("fov")) {
            return new float[]{0.8f, 0.3f, 1f, 1f}; // Purple
        }
        return new float[]{0.9f, 0.9f, 0.9f, 1f}; // Light gray default
    }


    private String resolveAnimationId() {
        return Playback.resolveAnimationId(currentClip, selectedPath);
    }

    public void setCurrentClip(AnimationClip clip, String sourcePath) {
        this.currentClip = clip;
        this.selectedPath = sourcePath;
        if (sourcePath != null) {
            savePathBuffer.set(sourcePath);
        }
        this.currentTime = 0f;
        this.playing = false;
        rebuildTrackViews();
        history.clear();
        trackVisibility.clear();
        trackColors.clear();
        lastRecordedTransforms.clear();
        if (currentClip != null) {
            applyAnimationAtTime(currentTime);
        }
    }

    public AnimationClip getCurrentClip() {
        return currentClip;
    }

    public void setEventTrack(List<EventKeyframe> events) {
        if (currentClip == null) return;
        List<EventKeyframe> copy = events != null ? new ArrayList<>(events) : new ArrayList<>();
        try {
            Field f = currentClip.getClass().getDeclaredField("eventTrack");
            f.setAccessible(true);
            f.set(currentClip, copy);
        } catch (Exception ignored) {
        }
    }

    public void setAvailableAnimations(List<MoudPackets.AnimationFileInfo> infos) {
        availableAnimations.clear();
        if (infos != null) {
            availableAnimations.addAll(infos);
        }
    }


    private float timeToPixelX(float timeSeconds) {
        float visibleStart = (float) visibleStartSeconds;
        float visibleDuration = (float) visibleSpanSeconds;
        float normalized = (timeSeconds - visibleStart) / visibleDuration;
        return timelineStartX + normalized * timelineWidth;
    }

    private float pixelXToTime(float pixelX) {
        float visibleStart = (float) visibleStartSeconds;
        float visibleDuration = (float) visibleSpanSeconds;
        float normalized = (pixelX - timelineStartX) / timelineWidth;
        return visibleStart + normalized * visibleDuration;
    }

    private void rebuildRowEntries() {
        visibleRows.clear();
        Map<String, List<Integer>> byObject = new LinkedHashMap<>();
        for (int i = 0; i < tracks.size(); i++) {
            TrackView tv = tracks.get(i);
            byObject.computeIfAbsent(tv.objectLabel(), k -> new ArrayList<>()).add(i);
        }

        for (Map.Entry<String, List<Integer>> entry : byObject.entrySet()) {
            String objectLabel = entry.getKey();
            String objectKey = "obj:" + objectLabel;
            if (!groupExpanded.containsKey(objectKey)) {
                groupExpanded.put(objectKey, true);
            }
            visibleRows.add(new RowEntry(RowType.OBJECT, objectKey, objectLabel, 0, null));
            if (!groupExpanded.getOrDefault(objectKey, true)) {
                continue;
            }

            Map<String, List<Integer>> byCategory = new LinkedHashMap<>();
            for (int idx : entry.getValue()) {
                TrackView tv = tracks.get(idx);
                String cat = categoryForPath(tv.propertyPath());
                byCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(idx);
            }

            for (Map.Entry<String, List<Integer>> catEntry : byCategory.entrySet()) {
                String catLabel = catEntry.getKey();
                String catKey = objectKey + "::" + catLabel;
                if (!groupExpanded.containsKey(catKey)) {
                    groupExpanded.put(catKey, true);
                }
                visibleRows.add(new RowEntry(RowType.CATEGORY, catKey, catLabel, 1, null));
                if (!groupExpanded.getOrDefault(catKey, true)) {
                    continue;
                }
                for (int idx : catEntry.getValue()) {
                    TrackView tv = tracks.get(idx);
                    visibleRows.add(new RowEntry(RowType.TRACK, catKey + ":" + tv.propertyPath(), tv.label(), 2, idx));
                }
            }
        }
    }

    private String categoryForPath(String path) {
        if (path == null) return "Other";
        int dot = path.indexOf('.');
        if (dot > 0) {
            return capitalize(path.substring(0, dot));
        }
        return capitalize(path);
    }

    private String capitalize(String in) {
        if (in == null || in.isEmpty()) return "Other";
        return Character.toUpperCase(in.charAt(0)) + in.substring(1);
    }

    private double pixelDeltaToTimeDelta(float pixelDelta) {
        float visibleDuration = (float) visibleSpanSeconds;
        return (pixelDelta / timelineWidth) * visibleDuration;
    }

    private void handleTrackReorder(float startY, float rowHeight) {
        if (draggingTrackIndex < 0) {
            return;
        }
        if (ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            float mouseY = ImGui.getIO().getMousePosY();
            int delta = (int) Math.floor((mouseY - dragTrackStartY) / rowHeight);
            int targetIndex = Math.max(0, Math.min(tracks.size() - 1, draggingTrackIndex + delta));
            if (targetIndex != draggingTrackIndex) {
                TrackView moved = tracks.remove(draggingTrackIndex);
                tracks.add(targetIndex, moved);

                if (targetIndex > draggingTrackIndex) {
                    trackAnimatedOffsets.put(targetIndex - 1, rowHeight);
                } else {
                    trackAnimatedOffsets.put(targetIndex + 1, -rowHeight);
                }

                reindexVisibility();
                rebuildRowEntries();
                draggingTrackIndex = targetIndex;
                dragTrackStartY = mouseY;
                selectedKeyframes.clear(); // avoid mismatch after reorder
            }
        } else {
            draggingTrackIndex = -1;
        }
    }

    private void reindexVisibility() {
        Map<Integer, Boolean> newVis = new LinkedHashMap<>();
        Map<Integer, float[]> newColors = new LinkedHashMap<>();
        for (int i = 0; i < tracks.size(); i++) {
            newVis.put(i, trackVisibility.getOrDefault(i, true));
            if (trackColors.containsKey(i)) {
                newColors.put(i, trackColors.get(i));
            }
        }
        trackVisibility.clear();
        trackVisibility.putAll(newVis);
        trackColors.clear();
        trackColors.putAll(newColors);
    }

    private void renderAddTrackPopup() {
        SceneObject selected = overlay.getSelectedObject();
        if (currentClip == null || selected == null) {
            ImGui.textDisabled("Select an object in the scene to add tracks.");
            return;
        }
        ObjectTrack targetTrack = ensureObjectTrack(selected);
        List<AnimatableProperty> props = AnimatableRegistry.getAllProperties(selected.getType());
        ImGui.textDisabled("Object: " + selected.getId());
        String selectedType = selected.getType() == null ? "" : selected.getType().toLowerCase(Locale.ROOT);
        boolean isPlayerModel = selectedType.contains("player_model") || selectedType.contains("player model");
        if (isPlayerModel) {
            if (ImGui.treeNode("Player Model")) {
                String preferredLimb = overlay.getSelectedLimbType();
                boolean showRootProps = preferredLimb == null || preferredLimb.isEmpty() || preferredLimb.contains("torso");
                if (showRootProps) {
                    renderPropertyMenuItems(targetTrack, props, p -> true);
                }
                if (ImGui.treeNodeEx("Head", preferredLimb != null && preferredLimb.contains("head") ? ImGuiTreeNodeFlags.DefaultOpen : 0)) {
                    renderLimbMenu(targetTrack, "player_model:head");
                    ImGui.treePop();
                }
                if (ImGui.treeNodeEx("Torso", preferredLimb != null && preferredLimb.contains("torso") ? ImGuiTreeNodeFlags.DefaultOpen : 0)) {
                    renderLimbMenu(targetTrack, "player_model:torso");
                    ImGui.treePop();
                }
                if (ImGui.treeNodeEx("Left Arm", preferredLimb != null && preferredLimb.contains("left_arm") ? ImGuiTreeNodeFlags.DefaultOpen : 0)) {
                    renderLimbMenu(targetTrack, "player_model:left_arm");
                    ImGui.treePop();
                }
                if (ImGui.treeNodeEx("Right Arm", preferredLimb != null && preferredLimb.contains("right_arm") ? ImGuiTreeNodeFlags.DefaultOpen : 0)) {
                    renderLimbMenu(targetTrack, "player_model:right_arm");
                    ImGui.treePop();
                }
                if (ImGui.treeNodeEx("Left Leg", preferredLimb != null && preferredLimb.contains("left_leg") ? ImGuiTreeNodeFlags.DefaultOpen : 0)) {
                    renderLimbMenu(targetTrack, "player_model:left_leg");
                    ImGui.treePop();
                }
                if (ImGui.treeNodeEx("Right Leg", preferredLimb != null && preferredLimb.contains("right_leg") ? ImGuiTreeNodeFlags.DefaultOpen : 0)) {
                    renderLimbMenu(targetTrack, "player_model:right_leg");
                    ImGui.treePop();
                }
                ImGui.treePop();
            }
        }

        if (ImGui.treeNode("Properties")) {
            boolean isEmitter = "particle_emitter".equalsIgnoreCase(selectedType);
            if (isEmitter) {
                if (ImGui.treeNode("Emitter")) {
                    renderPropertyMenuItems(targetTrack, props, p -> !p.path().startsWith("position.") && !p.path().startsWith("rotation.") && !p.path().startsWith("scale."));
                    ImGui.treePop();
                }
                if (ImGui.treeNode("Transform")) {
                    renderPropertyMenuItems(targetTrack, props, p -> p.path().startsWith("position.") || p.path().startsWith("rotation.") || p.path().startsWith("scale."));
                    ImGui.treePop();
                }
            } else {
                renderPropertyMenuItems(targetTrack, props, p -> true);
            }
            ImGui.treePop();
        }
    }

    private void renderPropertyMenuItems(ObjectTrack targetTrack, List<AnimatableProperty> props, Predicate<AnimatableProperty> filter) {
        renderPropertyMenuItems(targetTrack, props, filter, "", "");
    }

    private void renderPropertyMenuItems(ObjectTrack targetTrack, List<AnimatableProperty> props, Predicate<AnimatableProperty> filter, String pathPrefix, String labelPrefix) {
        for (AnimatableProperty prop : props) {
            if (!filter.test(prop)) continue;
            String fullPath = pathPrefix.isEmpty() ? prop.path() : pathPrefix + prop.path();
            boolean already = targetTrack.propertyTracks().containsKey(fullPath);
            if (already) {
                ImGui.textDisabled((labelPrefix.isEmpty() ? prop.displayName() : labelPrefix + prop.displayName()) + " (added)");
                continue;
            }
            String display = labelPrefix.isEmpty() ? prop.displayName() : labelPrefix + prop.displayName();
            if (ImGui.menuItem(display)) {
                PropertyTrack newTrack = new PropertyTrack(fullPath, prop.type(), prop.minValue(), prop.maxValue(), new ArrayList<>());
                targetTrack.propertyTracks().put(fullPath, newTrack);
                String objectLabel = (targetTrack.targetObjectName() != null ? targetTrack.targetObjectName() : targetTrack.targetObjectId());
                TrackView trackView = new TrackView(objectLabel, objectLabel + " / " + fullPath, fullPath, targetTrack.propertyTracks(), newTrack);
                int idx = tracks.size();
                tracks.add(trackView);
                history.push("Add track",
                        List.of(new HistoryAction.RemoveTrack(idx, history.snapshotTrack(trackView))),
                        List.of(new HistoryAction.AddTrack(idx, history.snapshotTrack(trackView))));
                trackVisibility.put(idx, true);
            }
        }
    }

    private void renderLimbMenu(ObjectTrack parent, String limbType) {
        List<AnimatableProperty> limbProps = AnimatableRegistry.getAllProperties(limbType);
        String labelPrefix = limbType.contains(":") ? limbType.substring(limbType.indexOf(':') + 1) + " / " : limbType + " / ";
        renderPropertyMenuItems(parent, limbProps, p -> true, limbType + ".", labelPrefix);
    }

    private void removeTrackWithHistory(int index) {
        if (index < 0 || index >= tracks.size()) {
            return;
        }
        TrackView track = tracks.get(index);
        TrackSnapshot snap = history.snapshotTrack(track);
        List<HistoryAction> undo = List.of(new HistoryAction.AddTrack(index, snap));
        List<HistoryAction> redo = List.of(new HistoryAction.RemoveTrack(index, snap));
        history.apply(new HistoryAction.RemoveTrack(index, snap));
        history.push("Delete track", undo, redo);
        rebuildTrackViews();
    }

    private float defaultValueForTrack(TrackView track) {
        float min = track.propertyTrack().minValue();
        float max = track.propertyTrack().maxValue();
        if (Float.isNaN(min) || Float.isNaN(max)) {
            return 0f;
        }
        return (min + max) * 0.5f;
    }

    private Keyframe cloneKeyframe(Keyframe kf) {
        return new Keyframe(kf.time(), kf.value(), kf.interpolation(), kf.inTangent(), kf.outTangent());
    }


    private List<Keyframe> ensureKeyframes(TrackView track) {
        List<Keyframe> list = track.propertyTrack().keyframes();
        if (list != null) {
            return list;
        }
        List<Keyframe> created = new ArrayList<>();
        PropertyTrack newTrack = new PropertyTrack(track.propertyPath(), track.propertyTrack().propertyType(), track.propertyTrack().minValue(), track.propertyTrack().maxValue(), created);
        track.propertyMap().put(track.propertyPath(), newTrack);
        int idx = tracks.indexOf(track);
        if (idx >= 0) {
            tracks.set(idx, new TrackView(track.objectLabel(), track.label(), track.propertyPath(), track.propertyMap(), newTrack));
        }
        return newTrack.keyframes();
    }

    private AnimationClip ensureMutableClip() {
        if (currentClip == null) return null;
        List<ObjectTrack> objects = currentClip.objectTracks();
        if (!(objects instanceof ArrayList<ObjectTrack>)) {
            objects = new ArrayList<>(objects != null ? objects : List.of());
            List<EventKeyframe> events = currentClip.eventTrack() != null ? new ArrayList<>(currentClip.eventTrack()) : new ArrayList<>();
            currentClip = new AnimationClip(currentClip.id(), currentClip.name(), currentClip.duration(), currentClip.frameRate(), objects, events, currentClip.metadata());
        }
        return currentClip;
    }

    private ObjectTrack ensureObjectTrack(SceneObject selected) {
        ensureMutableClip();
        List<ObjectTrack> objects = currentClip.objectTracks();
        for (ObjectTrack ot : objects) {
            if (ot.targetObjectId().equals(selected.getId())) {
                return ot;
            }
        }
        Map<String, PropertyTrack> props = new LinkedHashMap<>();
        ObjectTrack newObj = new ObjectTrack(selected.getId(), selected.getProperties().getOrDefault("label", selected.getId()).toString(), props);
        objects.add(newObj);
        rebuildTrackViews();
        return newObj;
    }

    private SelectedKeyframe findKeyframeAt(float mouseX, float mouseY, float timelineX, float timelineWidth, float keyY, double visibleStart, double visibleSpan) {
        float rowHeight = ImGui.getTextLineHeightWithSpacing() + 6f;
        float keyWidth = this.timelineWidth;
        double visibleEnd = visibleStart + visibleSpan;
        int rowIndex = 0;
        for (RowEntry row : visibleRows) {
            if (row.type() != RowType.TRACK || row.trackIndex() == null) {
                rowIndex++;
                continue;
            }
            int trackIdx = row.trackIndex();
            if (!trackVisibility.getOrDefault(trackIdx, true)) {
                rowIndex++;
                continue;
            }
            float rowTop = keyY + rowIndex * rowHeight;
            float rowBottom = rowTop + rowHeight;
            if (mouseY < rowTop || mouseY > rowBottom) continue;
            TrackView track = tracks.get(trackIdx);
            List<Keyframe> keyframes = track.keyframes();
            if (keyframes == null) {
                rowIndex++;
                continue;
            }
            for (Keyframe kf : keyframes) {
                double t = kf.time();
                if (t < visibleStart || t > visibleEnd) continue;
                float px = (float) (timelineX + (t - visibleStart) / visibleSpan * keyWidth);
                float py = rowTop + rowHeight * 0.5f;
                float size = 8f;
                if (Math.abs(mouseX - px) <= size && Math.abs(mouseY - py) <= size) {
                    return new SelectedKeyframe(trackIdx, kf.time());
                }
            }
            rowIndex++;
        }
        return null;
    }

    private int rowIndexFromY(float mouseY, float keyY) {
        float rowHeight = ImGui.getTextLineHeightWithSpacing() + 6f;
        int row = (int) Math.floor((mouseY - keyY) / rowHeight);
        if (row < 0 || row >= visibleRows.size()) {
            return -1;
        }
        RowEntry entry = visibleRows.get(row);
        return entry.trackIndex() != null ? entry.trackIndex() : -1;
    }

    private void renderKeyframeContextMenu() {
        KeyframePopups.ContextResult result = KeyframePopups.renderKeyframeContextMenu(
                contextOnKeyframe,
                contextTrackIndex,
                contextTime,
                contextSelectedKeyframes,
                selectedKeyframes,
                tracks,
                this::snapToFrame,
                this::defaultValueForTrack,
                this::ensureKeyframes,
                trackVisibility,
                history,
                copiedKeyframes,
                currentTime
        );
        keyframeContextPopupOpen = result.open();
        copiedKeyframes = result.copiedKeyframes();
    }

    private List<CopiedKeyframe> copySelectedKeyframes() {
        return KeyframeOps.copyKeyframes(selectedKeyframes, tracks);
    }

    private void deleteSelectedKeyframes() {
        KeyframeOps.deleteKeyframes(selectedKeyframes, tracks, trackVisibility, selectedKeyframes, history);
    }

    private void renderValueEditorPopup() {
        keyframeValueEditorPopupOpen = KeyframePopups.renderValueEditorPopup(
                editingKeyframe,
                editingTrackIndex,
                tracks,
                selectedKeyframes,
                history
        );
    }

    private void renderTrackLabels(ImDrawList drawList, float leftX, float y) {
        TrackLabelsView.Result result = TrackLabelsView.render(
                drawList,
                leftX,
                y,
                visibleRows,
                trackAnimatedOffsets,
                tracks,
                trackVisibility,
                trackColors,
                groupExpanded,
                renamingTrackIndex,
                renameBuffer,
                colorPickerTrackIndex,
                draggingTrackIndex,
                dragTrackStartY,
                this::rebuildRowEntries
        );
        renamingTrackIndex = result.renamingTrackIndex();
        colorPickerTrackIndex = result.colorPickerTrackIndex();
        draggingTrackIndex = result.draggingTrackIndex();
        dragTrackStartY = result.dragTrackStartY();
    }


}
