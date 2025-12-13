package com.moud.client.editor.ui.panel;

import com.moud.api.animation.AnimatableRegistry;
import com.moud.api.animation.AnimationClip;
import com.moud.api.animation.ObjectTrack;
import com.moud.api.animation.PropertyTrack;
import com.moud.api.math.Vector3;
import com.moud.client.editor.scene.SceneObject;
import com.moud.client.editor.scene.SceneSessionManager;
import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.network.MoudPackets;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.util.math.MathHelper;

import java.util.*;


public final class AnimationTimelinePanel {
    private final SceneEditorOverlay overlay;
    private final ImString savePathBuffer = new ImString(256);
    private final ImString filterBuffer = new ImString(64);
    private final List<MoudPackets.AnimationFileInfo> availableAnimations = new ArrayList<>();
    private final List<String> recentEvents = new ArrayList<>();
    private final List<SelectedKeyframe> selectedKeyframes = new ArrayList<>();
    private final List<TrackView> tracks = new ArrayList<>();
    private final List<RowEntry> visibleRows = new ArrayList<>();
    private final Deque<HistoryEntry> undoStack = new ArrayDeque<>(50);
    private final Deque<HistoryEntry> redoStack = new ArrayDeque<>(50);
    private final Map<Integer, Boolean> trackVisibility = new LinkedHashMap<>();
    private final Map<Integer, float[]> trackColors = new LinkedHashMap<>();
    private final Map<String, Boolean> groupExpanded = new HashMap<>();
    private final Map<String, TransformSnapshot> lastRecordedTransforms = new java.util.HashMap<>();
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
    private boolean snapDuringDrag;
    private float timelineStartX;
    private float timelineWidth;
    private double visibleStartSeconds;
    private double visibleSpanSeconds;
    private List<SelectedKeyframe> dragSelectedSnapshot = new ArrayList<>();
    private SelectedKeyframe editingKeyframe;
    private int editingTrackIndex = -1;
    private List<CopiedKeyframe> copiedKeyframes = new ArrayList<>();
    private int contextTrackIndex = -1;
    private double contextTime = 0;
    private boolean contextOnKeyframe;
    private boolean pendingAddKeyframe;
    private int renamingTrackIndex = -1;
    private final ImString renameBuffer = new ImString(128);
    private int colorPickerTrackIndex = -1;
    private int draggingTrackIndex = -1;
    private float dragTrackStartY;
    private final List<com.moud.api.animation.EventKeyframe> eventTrack = new ArrayList<>();

    public AnimationTimelinePanel(SceneEditorOverlay overlay) {
        this.overlay = overlay;
        AnimatableRegistry.registerDefaults();
        this.currentClip = null;
        rebuildTrackViews();
    }

    public void renderInCurrentWindow() {
        if (!listRequested) {
            listRequested = true;
            overlay.requestAnimationList();
        }
        autoLoadFirstIfNeeded();

        tickPlayback(ImGui.getIO().getDeltaTime());
        handleUndoRedoHotkeys();
        handleAnimationHotkeys();

        renderToolbar();
        renderTimelineArea();
        renderKeyframeContextMenu();
        renderValueEditorPopup();
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
        float toolbarHeight = 32f;
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 6, 4);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 8, 6);

        ImGui.setNextItemWidth(180f);
        if (ImGui.beginCombo("##AnimSelector", currentClip != null ? currentClip.name() : "<none>")) {
            String filter = filterBuffer.get().trim().toLowerCase(Locale.ROOT);
            for (MoudPackets.AnimationFileInfo info : availableAnimations) {
                if (!filter.isEmpty() && !info.path().toLowerCase(Locale.ROOT).contains(filter) && !info.name().toLowerCase(Locale.ROOT).contains(filter)) {
                    continue;
                }
                boolean sel = info.path().equals(selectedPath);
                if (ImGui.selectable(info.name(), sel)) {
                    selectedPath = info.path();
                    savePathBuffer.set(info.path());
                    overlay.openAnimation(info.path());
                }
                if (sel) ImGui.setItemDefaultFocus();
            }
            ImGui.endCombo();
        }

        ImGui.sameLine();
        if (ImGui.button("\ue148 New")) {
            ImGui.openPopup("##NewAnimPopup");
        }
        if (ImGui.beginPopup("##NewAnimPopup")) {
            ImGui.inputTextWithHint("##new_name", "Animation name", savePathBuffer);
            ImGui.inputTextWithHint("##new_path", "Path (animations/foo.an)", filterBuffer);
            if (ImGui.button("Create")) {
                String name = savePathBuffer.get().isBlank() ? "New Animation" : savePathBuffer.get();
                String path = filterBuffer.get().isBlank() ? "animations/" + name.replace(' ', '_').toLowerCase(Locale.ROOT) + ".an" : filterBuffer.get();
                this.currentClip = new AnimationClip(java.util.UUID.randomUUID().toString(), name, 8f, 60f, new ArrayList<>(), new ArrayList<>(), Map.of());
                this.selectedPath = path;
                savePathBuffer.set(path);
                rebuildTrackViews();
                undoStack.clear();
                redoStack.clear();
                lastRecordedTransforms.clear();
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }

        ImGui.sameLine();
        if (ImGui.button("\ue161 Save") && currentClip != null) {
            String path = savePathBuffer.get().isBlank() ? (selectedPath != null ? selectedPath : currentClip.name() + ".an") : savePathBuffer.get();
            overlay.saveAnimation(path, currentClip);
            pushEventIndicator("Saved " + path);
            listRequested = false;
            overlay.requestAnimationList();
        }

        ImGui.sameLine();
        if (ImGui.button("\ue5d5 Refresh")) {
            listRequested = false;
            overlay.requestAnimationList();
        }
        ImGui.sameLine();
        ImGui.textDisabled(" | ");
        ImGui.sameLine();

        String playIcon = playing ? "\ue034" : "\ue037";
        if (ImGui.button(playIcon + " Play")) {
            playing = !playing;
            if (playing && currentClip != null) {
                overlay.seekAnimation(resolveAnimationId(), currentTime);
                applyAnimationAtTime(currentTime);
                overlay.playAnimation(resolveAnimationId(), loop, speed);
                dispatchEventTrack(currentTime);
            } else {
                overlay.stopAnimation(resolveAnimationId());
            }
        }
        ImGui.sameLine();
        if (ImGui.button("\ue047 Stop")) {
            playing = false;
            currentTime = 0f;
            overlay.seekAnimation(resolveAnimationId(), currentTime);
            applyAnimationAtTime(currentTime);
            overlay.stopAnimation(resolveAnimationId());
        }
        ImGui.sameLine();
        int loopColor = loop ? ImGui.getColorU32(ImGuiCol.ButtonActive) : ImGui.getColorU32(ImGuiCol.Button);
        ImGui.pushStyleColor(ImGuiCol.Button, loopColor);
        if (ImGui.button("\ue040")) {
            loop = !loop;
        }
        ImGui.popStyleColor();

        ImGui.sameLine();
        ImGui.setNextItemWidth(80f);
        String[] speeds = {"0.25x", "0.5x", "1x", "2x", "4x"};
        float[] speedVals = {0.25f, 0.5f, 1f, 2f, 4f};
        int currentSpeedIdx = 2;
        for (int i = 0; i < speedVals.length; i++) {
            if (Math.abs(speedVals[i] - speed) < 1e-4) currentSpeedIdx = i;
        }
        if (ImGui.beginCombo("##Speed", speeds[currentSpeedIdx])) {
            for (int i = 0; i < speeds.length; i++) {
                if (ImGui.selectable(speeds[i], i == currentSpeedIdx)) {
                    speed = speedVals[i];
                }
            }
            ImGui.endCombo();
        }

        ImGui.sameLine();
        // Recording button with pulsing red indicator when active
        if (recording) {
            // Bright red when recording
            ImGui.pushStyleColor(ImGuiCol.Button, 0xFF0000CC);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0xFF0000EE);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0xFF0000FF);
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(ImGuiCol.Button));
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.getColorU32(ImGuiCol.ButtonHovered));
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, ImGui.getColorU32(ImGuiCol.ButtonActive));
        }
        if (ImGui.button(recording ? "\ue061 REC" : "\ue061 Rec")) {
            recording = !recording;
            if (recording) {
                lastRecordedTransforms.clear(); // Clear cache when starting recording
                pushEventIndicator("Recording started - move objects to record");
            } else {
                pushEventIndicator("Recording stopped");
            }
        }
        ImGui.popStyleColor(3);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(recording ? "Click to stop recording (keyframes auto-added on transform changes)" : "Click to start recording mode");
        }

        // Manual keyframe insertion button
        ImGui.sameLine();
        if (ImGui.button("\ue145 Key")) {
            insertKeyframeAtCurrentTime();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Insert keyframe at current time for selected object (K)");
        }

        ImGui.sameLine();
        ImGui.text(String.format("Time %s / %s", formatTime(currentTime), currentClip != null ? formatTime(currentClip.duration()) : "00:00:00"));

        // Show recording indicator
        if (recording) {
            ImGui.sameLine();
            ImGui.textColored(0xFF0000FF, "\u25cf REC");
        }

        if (!recentEvents.isEmpty()) {
            ImGui.sameLine();
            ImGui.textColored(ImGui.getColorU32(ImGuiCol.PlotHistogramHovered), "\ue7f7 Events: " + recentEvents.get(recentEvents.size() - 1));
            if (recentEvents.size() > 4) {
                recentEvents.remove(0);
            }
        }

        ImGui.popStyleVar(2);
    }

    public boolean isRecording() {
        return recording;
    }

    public void recordTransform(com.moud.client.editor.scene.SceneObject object, float[] translation, float[] rotation, float[] scale) {
        if (currentClip == null || object == null || !recording) {
            return;
        }
        ObjectTrack objTrack = ensureObjectTrack(object);
        float t = currentTime;

        TransformSnapshot last = lastRecordedTransforms.get(object.getId());
        boolean firstRecord = last == null;
        float eps = 1e-4f;
        if (firstRecord) {
            last = TransformSnapshot.from(translation, rotation, scale);
        }

        boolean changed = false;
        if (firstRecord || Math.abs(translation[0] - last.translation[0]) > eps) {
            addOrUpdateKey(objTrack, "position.x", translation[0], t);
            changed = true;
        }
        if (firstRecord || Math.abs(translation[1] - last.translation[1]) > eps) {
            addOrUpdateKey(objTrack, "position.y", translation[1], t);
            changed = true;
        }
        if (firstRecord || Math.abs(translation[2] - last.translation[2]) > eps) {
            addOrUpdateKey(objTrack, "position.z", translation[2], t);
            changed = true;
        }
        if (firstRecord || Math.abs(rotation[0] - last.rotation[0]) > eps) {
            addOrUpdateKey(objTrack, "rotation.x", rotation[0], t);
            changed = true;
        }
        if (firstRecord || Math.abs(rotation[1] - last.rotation[1]) > eps) {
            addOrUpdateKey(objTrack, "rotation.y", rotation[1], t);
            changed = true;
        }
        if (firstRecord || Math.abs(rotation[2] - last.rotation[2]) > eps) {
            addOrUpdateKey(objTrack, "rotation.z", rotation[2], t);
            changed = true;
        }
        if (firstRecord || Math.abs(scale[0] - last.scale[0]) > eps) {
            addOrUpdateKey(objTrack, "scale.x", scale[0], t);
            changed = true;
        }
        if (firstRecord || Math.abs(scale[1] - last.scale[1]) > eps) {
            addOrUpdateKey(objTrack, "scale.y", scale[1], t);
            changed = true;
        }
        if (firstRecord || Math.abs(scale[2] - last.scale[2]) > eps) {
            addOrUpdateKey(objTrack, "scale.z", scale[2], t);
            changed = true;
        }

        if (changed) {
            lastRecordedTransforms.put(object.getId(), TransformSnapshot.from(translation, rotation, scale));
            rebuildTrackViews();
        }
    }

    public void recordLimbTransform(com.moud.client.editor.scene.SceneObject object, String limbKey, float[] translation, float[] rotation, float[] scale) {
        if (currentClip == null || object == null || limbKey == null || !recording) {
            return;
        }
        String limbPath = limbKey.startsWith("player_model:") ? limbKey : "player_model:" + limbKey;
        ObjectTrack objTrack = ensureObjectTrack(object);
        float t = currentTime;
        String cacheKey = object.getId() + "|" + limbPath;

        TransformSnapshot last = lastRecordedTransforms.get(cacheKey);
        boolean firstRecord = last == null;
        float eps = 1e-4f;
        if (firstRecord) {
            last = TransformSnapshot.from(translation, rotation, scale);
        }

        boolean changed = false;
        String prefix = limbPath + ".";
        if (firstRecord || Math.abs(translation[0] - last.translation[0]) > eps) {
            addOrUpdateKey(objTrack, prefix + "position.x", translation[0], t);
            changed = true;
        }
        if (firstRecord || Math.abs(translation[1] - last.translation[1]) > eps) {
            addOrUpdateKey(objTrack, prefix + "position.y", translation[1], t);
            changed = true;
        }
        if (firstRecord || Math.abs(translation[2] - last.translation[2]) > eps) {
            addOrUpdateKey(objTrack, prefix + "position.z", translation[2], t);
            changed = true;
        }
        if (firstRecord || Math.abs(rotation[0] - last.rotation[0]) > eps) {
            addOrUpdateKey(objTrack, prefix + "rotation.x", rotation[0], t);
            changed = true;
        }
        if (firstRecord || Math.abs(rotation[1] - last.rotation[1]) > eps) {
            addOrUpdateKey(objTrack, prefix + "rotation.y", rotation[1], t);
            changed = true;
        }
        if (firstRecord || Math.abs(rotation[2] - last.rotation[2]) > eps) {
            addOrUpdateKey(objTrack, prefix + "rotation.z", rotation[2], t);
            changed = true;
        }
        if (firstRecord || Math.abs(scale[0] - last.scale[0]) > eps) {
            addOrUpdateKey(objTrack, prefix + "scale.x", scale[0], t);
            changed = true;
        }
        if (firstRecord || Math.abs(scale[1] - last.scale[1]) > eps) {
            addOrUpdateKey(objTrack, prefix + "scale.y", scale[1], t);
            changed = true;
        }
        if (firstRecord || Math.abs(scale[2] - last.scale[2]) > eps) {
            addOrUpdateKey(objTrack, prefix + "scale.z", scale[2], t);
            changed = true;
        }

        if (changed) {
            lastRecordedTransforms.put(cacheKey, TransformSnapshot.from(translation, rotation, scale));
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

    private void addOrUpdateKey(ObjectTrack objTrack, String path, float value, float time) {
        PropertyTrack track = objTrack.propertyTracks().computeIfAbsent(path, p -> new PropertyTrack(p, PropertyTrack.PropertyType.FLOAT, -100000f, 100000f, new ArrayList<>()));
        List<com.moud.api.animation.Keyframe> kfs = track.keyframes();
        boolean updated = false;
        for (int i = 0; i < kfs.size(); i++) {
            if (Math.abs(kfs.get(i).time() - time) < 1e-3) {
                kfs.set(i, new com.moud.api.animation.Keyframe(time, value, com.moud.api.animation.Keyframe.Interpolation.SMOOTH, 0f, 0f));
                updated = true;
                break;
            }
        }
        if (!updated) {
            kfs.add(new com.moud.api.animation.Keyframe(time, value, com.moud.api.animation.Keyframe.Interpolation.SMOOTH, 0f, 0f));
            kfs.sort((a, b) -> Float.compare(a.time(), b.time()));
        }
    }

    private void renderPlaybackControls() {
        if (currentClip == null) {
            return;
        }
        if (ImGui.button(playing ? "Pause" : "Play")) {
            playing = !playing;
            if (playing) {
                overlay.playAnimation(resolveAnimationId(), loop, speed);
            } else {
                overlay.stopAnimation(resolveAnimationId());
            }
        }
        ImGui.sameLine();
        if (ImGui.button("Stop")) {
            playing = false;
            currentTime = 0f;
            overlay.seekAnimation(resolveAnimationId(), currentTime);
            applyAnimationAtTime(currentTime);
            overlay.stopAnimation(resolveAnimationId());
        }
        ImGui.sameLine();
        if (ImGui.checkbox("Loop", loop)) {
            loop = !loop;
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(120);
        float[] speedBuf = new float[]{speed};
        if (ImGui.sliderFloat("Speed", speedBuf, 0.25f, 4f, "%.2fx")) {
            speed = Math.max(0.01f, Math.min(4f, speedBuf[0]));
        }
        float duration = Math.max(0.0001f, currentClip.duration());
        ImGui.setNextItemWidth(-1);
        float[] timeBuf = new float[]{currentTime};
        if (ImGui.sliderFloat("##time_slider", timeBuf, 0f, duration, "%.3fs")) {
            currentTime = Math.max(0f, Math.min(duration, timeBuf[0]));
            overlay.seekAnimation(resolveAnimationId(), currentTime);
            applyAnimationAtTime(currentTime);
            dispatchEventTrack(currentTime);
        }
        ImGui.text(String.format("Time: %.3fs / %.3fs", currentTime, duration));
    }

    private void renderTimelineArea() {
        if (currentClip == null) {
            return;
        }
        if (visibleRows.isEmpty()) {
            rebuildRowEntries();
        }
        float contentWidth = Math.max(1f, ImGui.getContentRegionAvailX());
        float rowHeight = ImGui.getTextLineHeightWithSpacing() + 6f;
        float contentHeight = Math.max(rowHeight * Math.max(1, visibleRows.size()) + 48f, ImGui.getContentRegionAvailY() - 50f);
        float leftWidth = 240f;
        float rulerHeight = 24f;
        float zoomBarHeight = 14f;
        float trackAreaHeight = Math.max(rowHeight * Math.max(1, visibleRows.size()), contentHeight - rulerHeight - zoomBarHeight);

        float leftX = ImGui.getCursorScreenPosX();
        float leftY = ImGui.getCursorScreenPosY();
        this.timelineStartX = leftX + leftWidth;
        this.timelineWidth = contentWidth - leftWidth;
        double duration = Math.max(0.001, currentClip.duration());
        this.visibleStartSeconds = zoomMin * duration;
        this.visibleSpanSeconds = (zoomMax - zoomMin) * duration;

        ImDrawList drawList = ImGui.getWindowDrawList();

        drawList.addRectFilled(leftX, leftY, leftX + leftWidth, leftY + contentHeight, ImGui.getColorU32(ImGuiCol.FrameBg));
        float rightX = leftX + leftWidth;
        drawList.addRectFilled(rightX, leftY, rightX + (contentWidth - leftWidth), leftY + contentHeight, ImGui.getColorU32(ImGuiCol.WindowBg));

        // clip timeline area
        drawList.pushClipRect(rightX, leftY, rightX + (contentWidth - leftWidth), leftY + contentHeight, true);
        renderRuler(drawList, rightX, leftY, contentWidth - leftWidth, rulerHeight);
        renderTracks(drawList, leftX, leftY + rulerHeight, leftWidth, rightX, trackAreaHeight, contentWidth - leftWidth);
        renderPlayhead(drawList, rightX, leftY, contentWidth - leftWidth, rulerHeight + trackAreaHeight);
        renderZoomBar(drawList, rightX, leftY + contentHeight - zoomBarHeight, contentWidth - leftWidth, zoomBarHeight);
        drawList.popClipRect();

        // clip track list
        drawList.pushClipRect(leftX, leftY, rightX, leftY + contentHeight, true);
        renderTrackLabels(drawList, leftX, leftY + rulerHeight);
        drawList.popClipRect();

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
    }

    private void renderTracks(ImDrawList drawList, float leftX, float y, float leftWidth, float rightX, float trackAreaHeight, float timelineWidth) {
        float rowHeight = ImGui.getTextLineHeightWithSpacing() + 6f;
        double visibleStart = visibleStartSeconds;
        double visibleEnd = visibleStartSeconds + visibleSpanSeconds;
        double visibleSpan = visibleSpanSeconds;
        float keyWidth = timelineWidth;

        int rowIndex = 0;
        for (RowEntry row : visibleRows) {
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

            List<com.moud.api.animation.Keyframe> keyframes = track.keyframes();
            if (keyframes != null) {
                for (com.moud.api.animation.Keyframe kf : keyframes) {
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
            if (mouseY < rowTop || mouseY > rowBottom) {
                rowIndex++;
                continue;
            }
            TrackView track = tracks.get(trackIdx);
            List<com.moud.api.animation.Keyframe> keyframes = track.keyframes();
            if (keyframes == null) {
                rowIndex++;
                continue;
            }
            for (com.moud.api.animation.Keyframe kf : keyframes) {
                double t = kf.time();
                if (t < visibleStart || t > visibleEnd) continue;
                float px = (float) (timelineX + (t - visibleStart) / visibleSpan * keyWidth);
                float py = rowTop + rowHeight * 0.5f;
                float size = 8f;
                if (Math.abs(mouseX - px) <= size && Math.abs(mouseY - py) <= size) {
                    if (!additive) {
                        selectedKeyframes.clear();
                    }
                    toggleSelection(trackIdx, kf.time(), additive);
                    return;
                }
            }
            rowIndex++;
        }
        if (!additive) {
            selectedKeyframes.clear();
        }
    }


    private void toggleSelection(int trackIndex, double time, boolean additive) {
        if (!additive) {
            selectedKeyframes.clear();
            selectedKeyframes.add(new SelectedKeyframe(trackIndex, time));
            return;
        }
        for (int i = 0; i < selectedKeyframes.size(); i++) {
            SelectedKeyframe sk = selectedKeyframes.get(i);
            if (sk.trackIndex == trackIndex && Math.abs(sk.time - time) < 1e-4) {
                selectedKeyframes.remove(i);
                return;
            }
        }
        selectedKeyframes.add(new SelectedKeyframe(trackIndex, time));
    }

    private boolean isSelected(int trackIndex, double time) {
        for (SelectedKeyframe sk : selectedKeyframes) {
            if (sk.trackIndex == trackIndex && Math.abs(sk.time - time) < 1e-4) {
                return true;
            }
        }
        return false;
    }

    private void performBoxSelection(float timelineX, float timelineWidth, float keyY, float keyHeight, double visibleStart, double visibleEnd, double visibleSpan) {
        float minX = Math.min(boxStartX, boxEndX);
        float maxX = Math.max(boxStartX, boxEndX);
        float minY = Math.min(boxStartY, boxEndY);
        float maxY = Math.max(boxStartY, boxEndY);
        float rowHeight = ImGui.getTextLineHeightWithSpacing() + 6f;
        float keyWidth = this.timelineWidth;

        selectedKeyframes.clear();
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
            if (rowBottom < minY || rowTop > maxY) {
                rowIndex++;
                continue;
            }
            TrackView track = tracks.get(trackIdx);
            List<com.moud.api.animation.Keyframe> keyframes = track.keyframes();
            if (keyframes == null) {
                rowIndex++;
                continue;
            }
            for (com.moud.api.animation.Keyframe kf : keyframes) {
                double t = kf.time();
                if (t < visibleStart || t > visibleEnd) continue;
                float px = (float) (timelineX + (t - visibleStart) / visibleSpan * keyWidth);
                float py = rowTop + rowHeight * 0.5f;
                if (px >= minX && px <= maxX && py >= minY && py <= maxY) {
                    selectedKeyframes.add(new SelectedKeyframe(trackIdx, kf.time()));
                }
            }
            rowIndex++;
        }
    }


    private boolean attemptBeginKeyframeDrag(float mouseX, float mouseY, float timelineX, float timelineWidth, float keyY, float keyHeight, double visibleStart, double visibleSpan) {
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
            if (mouseY < rowTop || mouseY > rowBottom) {
                rowIndex++;
                continue;
            }
            TrackView track = tracks.get(trackIdx);
            List<com.moud.api.animation.Keyframe> keyframes = track.keyframes();
            if (keyframes == null) {
                rowIndex++;
                continue;
            }
            for (com.moud.api.animation.Keyframe kf : keyframes) {
                double t = kf.time();
                if (t < visibleStart || t > visibleEnd) continue;
                float px = (float) (timelineX + (t - visibleStart) / visibleSpan * keyWidth);
                float py = rowTop + rowHeight * 0.5f;
                float size = 8f;
                if (Math.abs(mouseX - px) <= size && Math.abs(mouseY - py) <= size) {
                    if (!isSelected(trackIdx, t)) {
                        selectedKeyframes.clear();
                        selectedKeyframes.add(new SelectedKeyframe(trackIdx, t));
                    }
                    draggingKeyframes = true;
                    dragKeyStartX = mouseX;
                    dragOriginalTimes = snapshotSelectedTimes();
                    dragSelectedSnapshot = new ArrayList<>(selectedKeyframes);
                    snapDuringDrag = false;
                    return true;
                }
            }
            rowIndex++;
        }
        return false;
    }

    private List<Double> snapshotSelectedTimes() {
        List<Double> times = new ArrayList<>();
        for (SelectedKeyframe sk : selectedKeyframes) {
            times.add(sk.time);
        }
        return times;
    }

    private void updateKeyframeDrag(float mouseX, float timelineX, float timelineWidth, double visibleStart, double visibleSpan, boolean snap) {
        float dx = mouseX - dragKeyStartX;
        double dt = pixelDeltaToTimeDelta(dx);
        snapDuringDrag = snap;
        List<SelectedKeyframe> updated = new ArrayList<>();
        for (int i = 0; i < dragSelectedSnapshot.size(); i++) {
            SelectedKeyframe originalSk = dragSelectedSnapshot.get(i);
            double originalTime = i < dragOriginalTimes.size() ? dragOriginalTimes.get(i) : originalSk.time;
            double newTime = originalTime + dt;
            newTime = snapToFrame(newTime);
            if (snap) {
                newTime = snapToOtherTracks(originalSk.trackIndex, newTime);
            }
            newTime = Math.max(0, Math.min(currentClip.duration(), newTime));
            updated.add(new SelectedKeyframe(originalSk.trackIndex, newTime));
            TrackView track = tracks.get(originalSk.trackIndex);
            List<com.moud.api.animation.Keyframe> keyframes = ensureKeyframes(track);
            for (int k = 0; k < keyframes.size(); k++) {
                if (Math.abs(keyframes.get(k).time() - originalTime) < 1e-4) {
                    keyframes.set(k, new com.moud.api.animation.Keyframe((float) newTime, keyframes.get(k).value(), keyframes.get(k).interpolation(), keyframes.get(k).inTangent(), keyframes.get(k).outTangent()));
                    break;
                }
            }
            keyframes.sort((a, b) -> Float.compare(a.time(), b.time()));
        }
        selectedKeyframes.clear();
        selectedKeyframes.addAll(updated);
    }


    private void finalizeKeyframeDrag() {
        if (dragSelectedSnapshot == null || dragSelectedSnapshot.isEmpty()) {
            return;
        }
        List<HistoryAction> undo = new ArrayList<>();
        List<HistoryAction> redo = new ArrayList<>();
        for (int i = 0; i < dragSelectedSnapshot.size(); i++) {
            SelectedKeyframe original = dragSelectedSnapshot.get(i);
            double oldTime = dragOriginalTimes.size() > i ? dragOriginalTimes.get(i) : original.time;
            double newTime = i < selectedKeyframes.size() ? selectedKeyframes.get(i).time : findSelectedTimeForTrack(original.trackIndex);
            if (Double.isNaN(newTime)) {
                continue;
            }
            if (Math.abs(newTime - oldTime) > 1e-6) {
                undo.add(new HistoryAction.MoveKeyframe(original.trackIndex, (float) newTime, (float) oldTime));
                redo.add(new HistoryAction.MoveKeyframe(original.trackIndex, (float) oldTime, (float) newTime));
            }
        }
        if (!redo.isEmpty()) {
            pushHistory("Move keyframes", undo, redo);
        }
    }

    private double findSelectedTimeForTrack(int trackIndex) {
        for (SelectedKeyframe sk : selectedKeyframes) {
            if (sk.trackIndex == trackIndex) {
                return sk.time;
            }
        }
        return Double.NaN;
    }

    private double snapToOtherTracks(int excludeTrack, double target) {
        double best = target;
        double bestDiff = 0.05; // 50ms snap
        for (int i = 0; i < tracks.size(); i++) {
            if (i == excludeTrack) continue;
            TrackView track = tracks.get(i);
            List<com.moud.api.animation.Keyframe> keyframes = track.keyframes();
            if (keyframes == null) continue;
            for (com.moud.api.animation.Keyframe kf : keyframes) {
                double diff = Math.abs(kf.time() - target);
                if (diff < bestDiff) {
                    bestDiff = diff;
                    best = kf.time();
                }
            }
        }
        return best;
    }

    private void renderZoomBar(ImDrawList drawList, float x, float y, float width, float height) {
        int bg = ImGui.getColorU32(ImGuiCol.FrameBg);
        int fg = ImGui.getColorU32(ImGuiCol.PlotHistogram);
        drawList.addRectFilled(x, y, x + width, y + height, bg, height * 0.3f);
        float handleMin = x + zoomMin * width;
        float handleMax = x + zoomMax * width;
        drawList.addRectFilled(handleMin, y, handleMax, y + height, fg, height * 0.3f);
    }

    private void handleInteractions(float leftX, float topY, float leftWidth, float timelineX, float timelineWidth, float rulerHeight, float trackHeight, float zoomHeight) {
        float mouseX = ImGui.getIO().getMousePosX();
        float mouseY = ImGui.getIO().getMousePosY();
        boolean hoveredTimeline = mouseX >= timelineX && mouseX <= timelineX + timelineWidth && mouseY >= topY && mouseY <= topY + rulerHeight + trackHeight;
        boolean hoveredRuler = hoveredTimeline && mouseY <= topY + rulerHeight;
        boolean hoveredZoom = mouseX >= timelineX && mouseX <= timelineX + timelineWidth && mouseY >= topY + rulerHeight + trackHeight && mouseY <= topY + rulerHeight + trackHeight + zoomHeight;

        double duration = Math.max(0.001, currentClip.duration());
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
                overlay.seekAnimation(resolveAnimationId(), currentTime);
                applyAnimationAtTime(currentTime);
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
                draggingZoomBar = draggingZoomLeft = draggingZoomRight = false;
            }
        }

        // selection box in keyframe area
        boolean inKeyframeArea = mouseX >= timelineX && mouseX <= timelineX + timelineWidth && mouseY >= topY + rulerHeight && mouseY <= topY + rulerHeight + trackHeight;
        boolean beganBoxSelection = false;
        if (inKeyframeArea && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            if (!attemptBeginKeyframeDrag(mouseX, mouseY, timelineX, timelineWidth, topY + rulerHeight, trackHeight, visibleStart, visibleSpan)) {
                boxSelecting = true;
                beganBoxSelection = true;
                boxStartX = boxEndX = mouseX;
                boxStartY = boxEndY = mouseY;
            }
        }
        if (boxSelecting) {
            if (ImGui.isMouseDown(ImGuiMouseButton.Left)) {
                boxEndX = mouseX;
                boxEndY = mouseY;
            } else {
                performBoxSelection(timelineX, timelineWidth, topY + rulerHeight, trackHeight, visibleStart, visibleEnd, visibleSpan);
                boxSelecting = false;
            }
        }

        // keyframe click selection
        if (inKeyframeArea && ImGui.isMouseClicked(ImGuiMouseButton.Left) && !beganBoxSelection && !draggingKeyframes) {
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
                if (!isSelected(hit.trackIndex, hit.time)) {
                    selectedKeyframes.clear();
                    selectedKeyframes.add(hit);
                }
                contextOnKeyframe = true;
                contextTrackIndex = hit.trackIndex;
                contextTime = hit.time;
                ImGui.openPopup("##KeyframeContext");
            } else {
                int trackIdx = rowIndexFromY(mouseY, topY + rulerHeight);
                if (trackIdx >= 0 && trackIdx < tracks.size()) {
                    contextOnKeyframe = false;
                    contextTrackIndex = trackIdx;
                    contextTime = snapToFrame(pixelXToTime(mouseX));
                    ImGui.openPopup("##KeyframeContext");
                }
            }
        }

        // double-click value editor
        if (inKeyframeArea && ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
            SelectedKeyframe hit = findKeyframeAt(mouseX, mouseY, timelineX, timelineWidth, topY + rulerHeight, visibleStart, visibleSpan);
            if (hit != null) {
                editingKeyframe = hit;
                editingTrackIndex = hit.trackIndex;
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
        // Space bar play/pause only when not dragging/typing in timeline
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
            undo();
        }
        if (ImGui.isKeyPressed(ImGuiKey.Y, false)) {
            redo();
        }
        if (!wantKeyboard && ImGui.isKeyPressed(ImGuiKey.C, false) && !selectedKeyframes.isEmpty()) {
            copiedKeyframes = copySelectedKeyframes();
        }
        if (!wantKeyboard && ImGui.isKeyPressed(ImGuiKey.V, false)) {
            pasteKeyframes(currentTime);
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
        if (currentClip == null) {
            pushEventIndicator("No animation loaded");
            return;
        }

        var selectedObject = overlay.getSelectedObject();
        String selectedLimb = overlay.getSelectedLimbType();

        if (selectedObject == null) {
            pushEventIndicator("Select an object first");
            return;
        }

        ObjectTrack objTrack = ensureObjectTrack(selectedObject);
        float t = currentTime;
        float[] translation = overlay.getActiveTranslation();
        float[] rotation = overlay.getActiveRotation();
        float[] scale = overlay.getActiveScale();

        int keysAdded = 0;

        if (selectedLimb != null) {
            String limbPath = selectedLimb.startsWith("player_model:") ? selectedLimb : "player_model:" + selectedLimb;
            String prefix = limbPath + ".";

            addOrUpdateKey(objTrack, prefix + "position.x", translation[0], t);
            addOrUpdateKey(objTrack, prefix + "position.y", translation[1], t);
            addOrUpdateKey(objTrack, prefix + "position.z", translation[2], t);
            addOrUpdateKey(objTrack, prefix + "rotation.x", rotation[0], t);
            addOrUpdateKey(objTrack, prefix + "rotation.y", rotation[1], t);
            addOrUpdateKey(objTrack, prefix + "rotation.z", rotation[2], t);
            addOrUpdateKey(objTrack, prefix + "scale.x", scale[0], t);
            addOrUpdateKey(objTrack, prefix + "scale.y", scale[1], t);
            addOrUpdateKey(objTrack, prefix + "scale.z", scale[2], t);
            keysAdded = 9;

            pushEventIndicator("Added keyframes for " + selectedLimb + " at " + formatTime(t));
        } else {
            addOrUpdateKey(objTrack, "position.x", translation[0], t);
            addOrUpdateKey(objTrack, "position.y", translation[1], t);
            addOrUpdateKey(objTrack, "position.z", translation[2], t);
            addOrUpdateKey(objTrack, "rotation.x", rotation[0], t);
            addOrUpdateKey(objTrack, "rotation.y", rotation[1], t);
            addOrUpdateKey(objTrack, "rotation.z", rotation[2], t);
            addOrUpdateKey(objTrack, "scale.x", scale[0], t);
            addOrUpdateKey(objTrack, "scale.y", scale[1], t);
            addOrUpdateKey(objTrack, "scale.z", scale[2], t);
            keysAdded = 9;

            pushEventIndicator("Added " + keysAdded + " keyframes at " + formatTime(t));
        }

        rebuildTrackViews();
    }

    private float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private void drawDiamond(ImDrawList drawList, float cx, float cy, float size, int color) {
        drawList.addTriangleFilled(cx, cy - size, cx + size, cy, cx, cy + size, color);
        drawList.addTriangleFilled(cx, cy - size, cx - size, cy, cx, cy + size, color);
    }

    private void drawKeyframeShape(ImDrawList drawList, float x, float y, int size, int color, com.moud.api.animation.Keyframe.Interpolation interp) {
        int easeSize = Math.max(1, size / 5);
        switch (interp) {
            case SMOOTH -> drawList.addCircleFilled(x, y, size, color);
            case LINEAR -> {
                drawList.addTriangleFilled(x - size, y, x, y - size, x, y + size, color);
                drawList.addTriangleFilled(x + size, y, x, y + size, x, y - size, color);
            }
            case STEP -> drawList.addRectFilled(x - size, y - size, x + size, y + size, color);
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
        if (!playing || currentClip == null || deltaSeconds <= 0f) {
            return;
        }
        float duration = Math.max(0.0001f, currentClip.duration());
        float newTime = currentTime + deltaSeconds * speed;
        if (newTime > duration) {
            if (loop) {
                newTime = newTime % duration;
            } else {
                newTime = duration;
                playing = false;
                overlay.stopAnimation(resolveAnimationId());
            }
        }
        currentTime = newTime;
        overlay.seekAnimation(resolveAnimationId(), currentTime);
        applyAnimationAtTime(currentTime);
        dispatchEventTrack(currentTime);
    }

    private void applyAnimationAtTime(float timeSeconds) {
        if (currentClip == null || currentClip.objectTracks() == null) {
            return;
        }
        SceneSessionManager session = SceneSessionManager.getInstance();
        String sceneId = session.getActiveSceneId();
        if (sceneId == null) {
            return;
        }

        for (ObjectTrack objTrack : currentClip.objectTracks()) {
            if (objTrack == null || objTrack.propertyTracks() == null || objTrack.propertyTracks().isEmpty()) {
                continue;
            }
            SceneObject sceneObj = session.getSceneGraph().get(objTrack.targetObjectId());
            if (sceneObj == null) {
                continue;
            }

            Map<String, Object> props = sceneObj.getProperties();
            Vector3 basePos = readVec3(props.get("position"), Vector3.zero());
            Vector3 baseRot = readRotationVec(props.get("rotation"), Vector3.zero());
            Vector3 baseScale = readVec3(props.get("scale"), Vector3.one());

            Float posX = null, posY = null, posZ = null;
            Float rotX = null, rotY = null, rotZ = null;
            Float scaleX = null, scaleY = null, scaleZ = null;
            Map<String, Float> propertyUpdates = new HashMap<>();

            for (Map.Entry<String, PropertyTrack> entry : objTrack.propertyTracks().entrySet()) {
                PropertyTrack track = entry.getValue();
                if (track == null || track.keyframes() == null || track.keyframes().isEmpty()) {
                    continue;
                }
                String normalizedPath = normalizePropertyPath(entry.getKey());
                boolean limbPath = isLimbProperty(normalizedPath);
                boolean transformPath = !limbPath && isTransformPath(normalizedPath);

                float sampled = sampleTrack(track, timeSeconds, track.propertyType());

                if (transformPath) {
                    switch (normalizedPath) {
                        case "position.x" -> posX = sampled;
                        case "position.y" -> posY = sampled;
                        case "position.z" -> posZ = sampled;
                        case "rotation.x" -> rotX = sampled;
                        case "rotation.y" -> rotY = sampled;
                        case "rotation.z" -> rotZ = sampled;
                        case "scale.x" -> scaleX = sampled;
                        case "scale.y" -> scaleY = sampled;
                        case "scale.z" -> scaleZ = sampled;
                        default -> {
                        }
                    }
                } else if (normalizedPath != null) {
                    propertyUpdates.put(normalizedPath, sampled);
                }
            }

            Vector3 pos = null;
            if (posX != null || posY != null || posZ != null) {
                pos = new Vector3(
                        posX != null ? posX : basePos.x,
                        posY != null ? posY : basePos.y,
                        posZ != null ? posZ : basePos.z
                );
            }

            Vector3 rot = null;
            if (rotX != null || rotY != null || rotZ != null) {
                rot = new Vector3(
                        rotX != null ? rotX : baseRot.x,
                        rotY != null ? rotY : baseRot.y,
                        rotZ != null ? rotZ : baseRot.z
                );
            }

            Vector3 scale = null;
            if (scaleX != null || scaleY != null || scaleZ != null) {
                scale = new Vector3(
                        scaleX != null ? scaleX : baseScale.x,
                        scaleY != null ? scaleY : baseScale.y,
                        scaleZ != null ? scaleZ : baseScale.z
                );
            }

            boolean hasTransforms = pos != null || rot != null || scale != null;
            boolean hasProperties = !propertyUpdates.isEmpty();

            if (hasTransforms || hasProperties) {
                session.mergeAnimationTransform(
                        sceneId,
                        objTrack.targetObjectId(),
                        pos,
                        rot,
                        null,
                        scale,
                        hasProperties ? propertyUpdates : null
                );
            }
        }
    }

    private float sampleTrack(PropertyTrack track, float timeSeconds, PropertyTrack.PropertyType type) {
        List<com.moud.api.animation.Keyframe> keyframes = track.keyframes();
        if (keyframes == null || keyframes.isEmpty()) {
            return clampValue(track, 0f);
        }
        if (keyframes.size() == 1) {
            return clampValue(track, keyframes.getFirst().value());
        }

        keyframes.sort(Comparator.comparing(com.moud.api.animation.Keyframe::time));

        if (timeSeconds <= keyframes.getFirst().time()) {
            return clampValue(track, keyframes.getFirst().value());
        }
        if (timeSeconds >= keyframes.getLast().time()) {
            return clampValue(track, keyframes.getLast().value());
        }

        for (int i = 0; i < keyframes.size() - 1; i++) {
            com.moud.api.animation.Keyframe a = keyframes.get(i);
            com.moud.api.animation.Keyframe b = keyframes.get(i + 1);
            if (timeSeconds < a.time() || timeSeconds > b.time()) {
                continue;
            }
            float span = b.time() - a.time();
            if (span < 1e-6f) {
                return clampValue(track, b.value());
            }
            float t = (timeSeconds - a.time()) / span;
            return clampValue(track, interpolate(a, b, t, type));
        }

        return clampValue(track, keyframes.getLast().value());
    }

    private float interpolate(com.moud.api.animation.Keyframe a, com.moud.api.animation.Keyframe b, float t, PropertyTrack.PropertyType type) {
        float start = a.value();
        float end = b.value();
        if (type == PropertyTrack.PropertyType.ANGLE) {
            float delta = MathHelper.wrapDegrees(end - start);
            end = start + delta;
        }

        return switch (a.interpolation()) {
            case STEP -> start;
            case LINEAR -> start + (end - start) * t;
            case SMOOTH -> start + (end - start) * smoothStep(t);
            case EASE_IN -> start + (end - start) * (t * t);
            case EASE_OUT -> start + (end - start) * (1f - (1f - t) * (1f - t));
            case BEZIER -> {
                float dt = Math.max(1e-4f, b.time() - a.time());
                float m0 = a.outTangent() * dt;
                float m1 = b.inTangent() * dt;
                yield hermite(t, start, end, m0, m1);
            }
        };
    }

    private float hermite(float t, float p0, float p1, float m0, float m1) {
        float t2 = t * t;
        float t3 = t2 * t;
        float h00 = 2f * t3 - 3f * t2 + 1f;
        float h10 = t3 - 2f * t2 + t;
        float h01 = -2f * t3 + 3f * t2;
        float h11 = t3 - t2;
        return h00 * p0 + h10 * m0 + h01 * p1 + h11 * m1;
    }

    private float smoothStep(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private float clampValue(PropertyTrack track, float value) {
        float min = track.minValue();
        float max = track.maxValue();
        if (!Float.isNaN(min)) {
            value = Math.max(min, value);
        }
        if (!Float.isNaN(max)) {
            value = Math.min(max, value);
        }
        return value;
    }

    private Vector3 readVec3(Object raw, Vector3 fallback) {
        if (raw instanceof Map<?, ?> map) {
            float x = toFloat(map.get("x"), fallback.x);
            float y = toFloat(map.get("y"), fallback.y);
            float z = toFloat(map.get("z"), fallback.z);
            return new Vector3(x, y, z);
        }
        return new Vector3(fallback);
    }

    private Vector3 readRotationVec(Object raw, Vector3 fallback) {
        if (raw instanceof Map<?, ?> map) {
            Object rx = map.containsKey("pitch") ? map.get("pitch") : map.get("x");
            Object ry = map.containsKey("yaw") ? map.get("yaw") : map.get("y");
            Object rz = map.containsKey("roll") ? map.get("roll") : map.get("z");
            float x = toFloat(rx, fallback.x);
            float y = toFloat(ry, fallback.y);
            float z = toFloat(rz, fallback.z);
            return new Vector3(x, y, z);
        }
        return new Vector3(fallback);
    }

    private float toFloat(Object value, float fallback) {
        if (value instanceof Number num) {
            return num.floatValue();
        }
        if (value != null) {
            try {
                return Float.parseFloat(String.valueOf(value));
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private String normalizePropertyPath(String raw) {
        if (raw == null) {
            return null;
        }
        boolean hasPrefix = raw.startsWith("player_model:");
        String remainder = hasPrefix ? raw.substring("player_model:".length()) : raw;
        String lower = remainder.toLowerCase(Locale.ROOT);
        int dot = lower.indexOf('.');
        String limb = dot > 0 ? lower.substring(0, dot) : lower;
        String normalizedLimb = "body".equals(limb) ? "torso" : limb;
        boolean isLimb = switch (normalizedLimb) {
            case "head", "torso", "left_arm", "right_arm", "left_leg", "right_leg" -> true;
            default -> false;
        };
        if (!isLimb) {
            return raw;
        }
        String suffix = dot >= 0 && dot < remainder.length() ? remainder.substring(dot) : "";
        return "player_model:" + normalizedLimb + suffix;
    }

    private boolean isLimbProperty(String path) {
        if (path == null) {
            return false;
        }
        String p = path;
        if (p.startsWith("player_model:")) {
            p = p.substring("player_model:".length());
        }
        int dot = p.indexOf('.');
        String limb = dot > 0 ? p.substring(0, dot) : p;
        return switch (limb) {
            case "head", "torso", "body", "left_arm", "right_arm", "left_leg", "right_leg" -> true;
            default -> false;
        };
    }

    private boolean isTransformPath(String path) {
        if (path == null) {
            return false;
        }
        return path.equals("position.x") || path.equals("position.y") || path.equals("position.z")
                || path.equals("rotation.x") || path.equals("rotation.y") || path.equals("rotation.z")
                || path.equals("scale.x") || path.equals("scale.y") || path.equals("scale.z");
    }

    private void dispatchEventTrack(float time) {
        if (currentClip == null || currentClip.eventTrack() == null) return;
        float frameWindow = 1f / Math.max(1f, currentClip.frameRate());
        for (com.moud.api.animation.EventKeyframe ev : currentClip.eventTrack()) {
            if (Math.abs(ev.time() - time) < 1e-3 || (ev.time() <= time && ev.time() > time - frameWindow)) {
                overlay.triggerEvent(ev.name(), ev.payload());
            }
        }
    }

    private String formatTime(double seconds) {
        int totalFrames = (int) Math.round(seconds * 60.0);
        int frames = totalFrames % 60;
        int secs = (int) seconds % 60;
        int mins = (int) (seconds / 60.0);
        return String.format("%02d:%02d:%02d", mins, secs, frames);
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
        if (lower.endsWith(".x")) return new float[]{0.97f, 0.33f, 0.33f, 1f}; // red
        if (lower.endsWith(".y")) return new float[]{0.33f, 0.97f, 0.33f, 1f}; // green
        if (lower.endsWith(".z")) return new float[]{0.33f, 0.55f, 0.97f, 1f}; // blue
        return new float[]{1f, 1f, 1f, 1f};
    }


    private List<com.moud.api.animation.Keyframe> testKeys(float[] times, float[] values) {
        List<com.moud.api.animation.Keyframe> list = new ArrayList<>();
        for (int i = 0; i < times.length && i < values.length; i++) {
            list.add(new com.moud.api.animation.Keyframe(times[i], values[i], com.moud.api.animation.Keyframe.Interpolation.LINEAR, 0f, 0f));
        }
        return list;
    }

    private String resolveAnimationId() {
        if (selectedPath != null && !selectedPath.isBlank()) {
            return selectedPath;
        }
        if (currentClip != null && currentClip.id() != null) {
            return currentClip.id();
        }
        return "animation";
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
        undoStack.clear();
        redoStack.clear();
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

    public void setEventTrack(List<com.moud.api.animation.EventKeyframe> events) {
        if (currentClip == null) return;
        List<com.moud.api.animation.EventKeyframe> copy = events != null ? new ArrayList<>(events) : new ArrayList<>();
        try {
            java.lang.reflect.Field f = currentClip.getClass().getDeclaredField("eventTrack");
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
        com.moud.client.editor.scene.SceneObject selected = overlay.getSelectedObject();
        if (currentClip == null || selected == null) {
            ImGui.textDisabled("Select an object in the scene to add tracks.");
            return;
        }
        ObjectTrack targetTrack = ensureObjectTrack(selected);
        List<com.moud.api.animation.AnimatableProperty> props = AnimatableRegistry.getAllProperties(selected.getType());
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

    private void renderPropertyMenuItems(ObjectTrack targetTrack, List<com.moud.api.animation.AnimatableProperty> props, java.util.function.Predicate<com.moud.api.animation.AnimatableProperty> filter) {
        renderPropertyMenuItems(targetTrack, props, filter, "", "");
    }

    private void renderPropertyMenuItems(ObjectTrack targetTrack, List<com.moud.api.animation.AnimatableProperty> props, java.util.function.Predicate<com.moud.api.animation.AnimatableProperty> filter, String pathPrefix, String labelPrefix) {
        for (com.moud.api.animation.AnimatableProperty prop : props) {
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
                pushHistory("Add track",
                        List.of(new HistoryAction.RemoveTrack(idx, snapshotTrack(trackView))),
                        List.of(new HistoryAction.AddTrack(idx, snapshotTrack(trackView))));
                trackVisibility.put(idx, true);
            }
        }
    }

    private void renderLimbMenu(ObjectTrack parent, String limbType) {
        List<com.moud.api.animation.AnimatableProperty> limbProps = AnimatableRegistry.getAllProperties(limbType);
        String labelPrefix = limbType.contains(":") ? limbType.substring(limbType.indexOf(':') + 1) + " / " : limbType + " / ";
        renderPropertyMenuItems(parent, limbProps, p -> true, limbType + ".", labelPrefix);
    }

    private TrackSnapshot snapshotTrack(TrackView track) {
        List<com.moud.api.animation.Keyframe> copy = track.keyframes() != null ? new ArrayList<>(track.keyframes()) : new ArrayList<>();
        PropertyTrack propertyTrackCopy = new PropertyTrack(track.propertyTrack().propertyPath(), track.propertyTrack().propertyType(), track.propertyTrack().minValue(), track.propertyTrack().maxValue(), copy);
        return new TrackSnapshot(track.objectLabel(), track.label(), track.propertyPath(), track.propertyMap(), propertyTrackCopy, copy);
    }


    private TrackView restoreTrack(TrackSnapshot snapshot) {
        return new TrackView(snapshot.objectLabel(), snapshot.label(), snapshot.propertyPath(), snapshot.propertyMap(), snapshot.propertyTrackCopy());
    }


    private void removeTrackWithHistory(int index) {
        if (index < 0 || index >= tracks.size()) {
            return;
        }
        TrackView track = tracks.get(index);
        TrackSnapshot snap = snapshotTrack(track);
        List<HistoryAction> undo = List.of(new HistoryAction.AddTrack(index, snap));
        List<HistoryAction> redo = List.of(new HistoryAction.RemoveTrack(index, snap));
        applyAction(new HistoryAction.RemoveTrack(index, snap));
        pushHistory("Delete track", undo, redo);
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

    private void pushHistory(String description, List<HistoryAction> undo, List<HistoryAction> redo) {
        if (undo == null || redo == null) {
            return;
        }
        undoStack.push(new HistoryEntry(description, new ArrayList<>(undo), new ArrayList<>(redo)));
        redoStack.clear();
        if (undoStack.size() > 50) {
            undoStack.removeLast();
        }
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        HistoryEntry entry = undoStack.pop();
        for (HistoryAction action : entry.undoActions()) {
            applyAction(action);
        }
        redoStack.push(entry);
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        HistoryEntry entry = redoStack.pop();
        for (HistoryAction action : entry.redoActions()) {
            applyAction(action);
        }
        undoStack.push(entry);
    }

    private void applyAction(HistoryAction action) {
        switch (action) {
            case HistoryAction.AddKeyframe add -> {
                List<com.moud.api.animation.Keyframe> keyframes = ensureKeyframes(tracks.get(add.trackIndex()));
                keyframes.add(cloneKeyframe(add.keyframe()));
                keyframes.sort((a, b) -> Float.compare(a.time(), b.time()));
            }
            case HistoryAction.RemoveKeyframe rem -> {
                TrackView track = tracks.get(rem.trackIndex());
                List<com.moud.api.animation.Keyframe> keyframes = track.keyframes();
                if (keyframes != null) {
                    keyframes.removeIf(k -> Math.abs(k.time() - rem.time()) < 1e-4);
                }
            }
            case HistoryAction.MoveKeyframe move -> {
                TrackView track = tracks.get(move.trackIndex());
                List<com.moud.api.animation.Keyframe> keyframes = ensureKeyframes(track);
                for (int i = 0; i < keyframes.size(); i++) {
                    if (Math.abs(keyframes.get(i).time() - move.fromTime()) < 1e-4) {
                        com.moud.api.animation.Keyframe kf = keyframes.get(i);
                        keyframes.set(i, new com.moud.api.animation.Keyframe(move.toTime(), kf.value(), kf.interpolation(), kf.inTangent(), kf.outTangent()));
                        break;
                    }
                }
                keyframes.sort((a, b) -> Float.compare(a.time(), b.time()));
            }
            case HistoryAction.ChangeValue cv -> {
                TrackView track = tracks.get(cv.trackIndex());
                List<com.moud.api.animation.Keyframe> keyframes = ensureKeyframes(track);
                for (int i = 0; i < keyframes.size(); i++) {
                    if (Math.abs(keyframes.get(i).time() - cv.time()) < 1e-4) {
                        com.moud.api.animation.Keyframe kf = keyframes.get(i);
                        keyframes.set(i, new com.moud.api.animation.Keyframe(kf.time(), cv.newValue(), kf.interpolation(), kf.inTangent(), kf.outTangent()));
                        break;
                    }
                }
            }
            case HistoryAction.ChangeInterpolation ci -> {
                TrackView track = tracks.get(ci.trackIndex());
                List<com.moud.api.animation.Keyframe> keyframes = ensureKeyframes(track);
                for (int i = 0; i < keyframes.size(); i++) {
                    if (Math.abs(keyframes.get(i).time() - ci.time()) < 1e-4) {
                        com.moud.api.animation.Keyframe kf = keyframes.get(i);
                        keyframes.set(i, new com.moud.api.animation.Keyframe(kf.time(), kf.value(), ci.newType(), kf.inTangent(), kf.outTangent()));
                        break;
                    }
                }
            }
            case HistoryAction.AddTrack addTrack -> {
                TrackView restored = restoreTrack(addTrack.snapshot());
                int idx = Math.min(Math.max(0, addTrack.trackIndex()), tracks.size());
                tracks.add(idx, restored);
                trackVisibility.put(idx, true);
            }
            case HistoryAction.RemoveTrack remTrack -> {
                if (remTrack.trackIndex() >= 0 && remTrack.trackIndex() < tracks.size()) {
                    TrackView removed = tracks.remove(remTrack.trackIndex());
                    if (removed != null && removed.propertyMap() != null) {
                        removed.propertyMap().remove(removed.propertyPath());
                    }
                }
            }
        }
    }

    private com.moud.api.animation.Keyframe cloneKeyframe(com.moud.api.animation.Keyframe kf) {
        return new com.moud.api.animation.Keyframe(kf.time(), kf.value(), kf.interpolation(), kf.inTangent(), kf.outTangent());
    }


    private List<com.moud.api.animation.Keyframe> ensureKeyframes(TrackView track) {
        List<com.moud.api.animation.Keyframe> list = track.propertyTrack().keyframes();
        if (list != null) {
            return list;
        }
        List<com.moud.api.animation.Keyframe> created = new ArrayList<>();
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
            List<com.moud.api.animation.EventKeyframe> events = currentClip.eventTrack() != null ? new ArrayList<>(currentClip.eventTrack()) : new ArrayList<>();
            currentClip = new AnimationClip(currentClip.id(), currentClip.name(), currentClip.duration(), currentClip.frameRate(), objects, events, currentClip.metadata());
        }
        return currentClip;
    }

    private ObjectTrack ensureObjectTrack(com.moud.client.editor.scene.SceneObject selected) {
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
            List<com.moud.api.animation.Keyframe> keyframes = track.keyframes();
            if (keyframes == null) {
                rowIndex++;
                continue;
            }
            for (com.moud.api.animation.Keyframe kf : keyframes) {
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
        if (ImGui.beginPopup("##KeyframeContext")) {
            if (!contextOnKeyframe && contextTrackIndex >= 0) {
                if (ImGui.menuItem("Add keyframe here")) {
                    TrackView track = tracks.get(contextTrackIndex);
                    addKeyframe(contextTrackIndex, (float) contextTime, defaultValueForTrack(track), com.moud.api.animation.Keyframe.Interpolation.SMOOTH);
                }
                ImGui.endPopup();
                return;
            }
            if (contextTrackIndex >= 0) {
                if (ImGui.beginMenu("Interpolation")) {
                    for (com.moud.api.animation.Keyframe.Interpolation type : com.moud.api.animation.Keyframe.Interpolation.values()) {
                        boolean sel = false;
                        if (ImGui.menuItem(type.name(), "", sel)) {
                            List<HistoryAction> undo = new ArrayList<>();
                            List<HistoryAction> redo = new ArrayList<>();
                            for (SelectedKeyframe sk : selectedKeyframes) {
                                TrackView track = tracks.get(sk.trackIndex);
                                List<com.moud.api.animation.Keyframe> keyframes = ensureKeyframes(track);
                                for (int i = 0; i < keyframes.size(); i++) {
                                    if (Math.abs(keyframes.get(i).time() - sk.time) < 1e-4) {
                                        com.moud.api.animation.Keyframe kf = keyframes.get(i);
                                        if (kf.interpolation() != type) {
                                            undo.add(new HistoryAction.ChangeInterpolation(sk.trackIndex, kf.time(), type, kf.interpolation()));
                                            redo.add(new HistoryAction.ChangeInterpolation(sk.trackIndex, kf.time(), kf.interpolation(), type));
                                            keyframes.set(i, new com.moud.api.animation.Keyframe(kf.time(), kf.value(), type, kf.inTangent(), kf.outTangent()));
                                        }
                                    }
                                }
                            }
                            if (!redo.isEmpty()) {
                                pushHistory("Change interpolation", undo, redo);
                            }
                        }
                    }
                    ImGui.endMenu();
                }
                ImGui.separator();
                if (ImGui.menuItem("Copy")) {
                    copiedKeyframes = copySelectedKeyframes();
                }
                if (ImGui.menuItem("Paste") && copiedKeyframes != null) {
                    pasteKeyframes(currentTime);
                }
                ImGui.separator();
                if (ImGui.menuItem("Delete")) {
                    deleteSelectedKeyframes();
                }
            }
            ImGui.endPopup();
        }
    }

    private List<CopiedKeyframe> copySelectedKeyframes() {
        List<CopiedKeyframe> out = new ArrayList<>();
        for (SelectedKeyframe sk : selectedKeyframes) {
            TrackView track = tracks.get(sk.trackIndex);
            List<com.moud.api.animation.Keyframe> keyframes = track.keyframes();
            if (keyframes == null) continue;
            for (com.moud.api.animation.Keyframe kf : keyframes) {
                if (Math.abs(kf.time() - sk.time) < 1e-4) {
                    out.add(new CopiedKeyframe(sk.trackIndex, kf.time(), kf.value(), kf.interpolation()));
                    break;
                }
            }
        }
        return out;
    }

    private void pasteKeyframes(float targetTime) {
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
            com.moud.api.animation.Keyframe newKf = new com.moud.api.animation.Keyframe(newTime, ck.value(), ck.interpolation(), 0f, 0f);
            undo.add(new HistoryAction.RemoveKeyframe(ck.trackIndex(), newTime));
            redo.add(new HistoryAction.AddKeyframe(ck.trackIndex(), newKf));
            applyAction(new HistoryAction.AddKeyframe(ck.trackIndex(), newKf));
        }
        if (!redo.isEmpty()) {
            pushHistory("Paste keyframes", undo, redo);
        }
    }

    private void addKeyframe(int trackIndex, float time, float value, com.moud.api.animation.Keyframe.Interpolation interp) {
        if (trackIndex < 0 || trackIndex >= tracks.size()) return;
        time = (float) snapToFrame(time);
        TrackView track = tracks.get(trackIndex);
        List<com.moud.api.animation.Keyframe> keyframes = ensureKeyframes(track);
        com.moud.api.animation.Keyframe kf = new com.moud.api.animation.Keyframe(time, value, interp, 0f, 0f);

        List<HistoryAction> undo = new ArrayList<>();
        List<HistoryAction> redo = new ArrayList<>();
        undo.add(new HistoryAction.RemoveKeyframe(trackIndex, time));
        redo.add(new HistoryAction.AddKeyframe(trackIndex, kf));

        keyframes.add(kf);
        keyframes.sort((a, b) -> Float.compare(a.time(), b.time()));
        pushHistory("Add keyframe", undo, redo);
    }

    private void deleteSelectedKeyframes() {
        if (selectedKeyframes.isEmpty()) {
            return;
        }
        List<HistoryAction> undo = new ArrayList<>();
        List<HistoryAction> redo = new ArrayList<>();
        for (SelectedKeyframe sk : new ArrayList<>(selectedKeyframes)) {
            TrackView track = tracks.get(sk.trackIndex);
            if (!trackVisibility.getOrDefault(sk.trackIndex, true)) {
                continue;
            }
            List<com.moud.api.animation.Keyframe> keyframes = track.keyframes();
            if (keyframes == null) continue;
            for (com.moud.api.animation.Keyframe kf : new ArrayList<>(keyframes)) {
                if (Math.abs(kf.time() - sk.time) < 1e-4) {
                    undo.add(new HistoryAction.AddKeyframe(sk.trackIndex, kf));
                    redo.add(new HistoryAction.RemoveKeyframe(sk.trackIndex, kf.time()));
                    keyframes.remove(kf);
                    break;
                }
            }
        }
        selectedKeyframes.clear();
        if (!redo.isEmpty()) {
            pushHistory("Delete keyframes", undo, redo);
        }
    }

    private void renderValueEditorPopup() {
        if (ImGui.beginPopup("##KeyframeValueEditor")) {
            if (editingKeyframe != null && editingTrackIndex >= 0 && editingTrackIndex < tracks.size()) {
                TrackView track = tracks.get(editingTrackIndex);
                com.moud.api.animation.Keyframe target = null;
                int idx = -1;
                List<com.moud.api.animation.Keyframe> keyframes = track.keyframes();
                if (keyframes != null) {
                    for (int i = 0; i < keyframes.size(); i++) {
                        if (Math.abs(keyframes.get(i).time() - editingKeyframe.time) < 1e-4) {
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
                            keyframes.set(idx, new com.moud.api.animation.Keyframe(target.time(), newVal, target.interpolation(), target.inTangent(), target.outTangent()));
                            pushHistory("Change value",
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
                            keyframes.set(idx, new com.moud.api.animation.Keyframe(newTime, target.value(), target.interpolation(), target.inTangent(), target.outTangent()));
                            selectedKeyframes.clear();
                            selectedKeyframes.add(new SelectedKeyframe(editingTrackIndex, newTime));
                            pushHistory("Move keyframe",
                                    List.of(new HistoryAction.MoveKeyframe(editingTrackIndex, newTime, oldTime)),
                                    List.of(new HistoryAction.MoveKeyframe(editingTrackIndex, oldTime, newTime)));
                        }
                    }
                    ImInt interpIndex = new ImInt(target.interpolation().ordinal());
                    ImGui.setNextItemWidth(150);
                    String[] interpNames = Arrays.stream(com.moud.api.animation.Keyframe.Interpolation.values()).map(Enum::name).toArray(String[]::new);
                    if (ImGui.combo("Interpolation", interpIndex, interpNames)) {
                        com.moud.api.animation.Keyframe.Interpolation chosen = com.moud.api.animation.Keyframe.Interpolation.values()[interpIndex.get()];
                        if (chosen != target.interpolation()) {
                            HistoryAction undo = new HistoryAction.ChangeInterpolation(editingTrackIndex, target.time(), chosen, target.interpolation());
                            HistoryAction redo = new HistoryAction.ChangeInterpolation(editingTrackIndex, target.time(), target.interpolation(), chosen);
                            keyframes.set(idx, new com.moud.api.animation.Keyframe(target.time(), target.value(), chosen, target.inTangent(), target.outTangent()));
                            pushHistory("Change interpolation", List.of(undo), List.of(redo));
                        }
                    }
                }
            }
            ImGui.endPopup();
        }
    }

        private void renderTrackLabels(ImDrawList drawList, float leftX, float y) {
        float rowHeight = ImGui.getTextLineHeightWithSpacing() + 6f;
        int rowIndex = 0;
        for (RowEntry row : visibleRows) {
            float rowTop = y + rowIndex * rowHeight;
            int textColor = ImGui.getColorU32(ImGuiCol.Text);
            float cursorY = rowTop + 2;
            float indentX = leftX + 4 + row.indent() * 14f;
            ImGui.setCursorScreenPos(indentX, cursorY);
            ImGui.pushID(rowIndex);

            if (row.type() == RowType.TRACK && row.trackIndex() != null) {
                int i = row.trackIndex();
                TrackView track = tracks.get(i);

                ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 2, 1);
                if (ImGui.button("##drag" + i) || (ImGui.isItemActive() && ImGui.isMouseDragging(ImGuiMouseButton.Left))) {
                    draggingTrackIndex = i;
                    dragTrackStartY = ImGui.getIO().getMousePosY();
                }
                ImGui.popStyleVar();

                ImGui.sameLine();

                boolean visible = trackVisibility.getOrDefault(i, true);
                String visIcon = visible ? "" : "";
                if (ImGui.smallButton(visIcon + "##vis" + i)) {
                    trackVisibility.put(i, !visible);
                }
                ImGui.sameLine();

                float[] color = trackColors.getOrDefault(i, null);
                int colorU32 = color != null ? ImGui.colorConvertFloat4ToU32(color[0], color[1], color[2], 1f) : textColor;
                ImGui.colorButton("##col" + i, color != null ? color : new float[]{1f, 1f, 1f});
                if (ImGui.isItemClicked()) {
                    colorPickerTrackIndex = i;
                    ImGui.openPopup("##TrackColorPicker");
                }
                ImGui.sameLine();

                if (renamingTrackIndex == i) {
                    ImGui.setNextItemWidth(140);
                    if (ImGui.inputText("##RenameTrack", renameBuffer, ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll)) {
                        tracks.set(i, new TrackView(track.objectLabel(), renameBuffer.get().trim(), track.propertyPath(), track.propertyMap(), track.propertyTrack()));
                        renamingTrackIndex = -1;
                        rebuildRowEntries();
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
                String arrow = expanded ? "" : "";
                if (ImGui.smallButton(arrow + "##exp")) {
                    groupExpanded.put(row.id(), !expanded);
                    rebuildRowEntries();
                }
                ImGui.sameLine();
                ImGui.text(row.label());
            }

            ImGui.popID();
            rowIndex++;
        }
    }

    private sealed interface HistoryAction permits HistoryAction.AddKeyframe, HistoryAction.RemoveKeyframe, HistoryAction.MoveKeyframe,
            HistoryAction.ChangeValue, HistoryAction.ChangeInterpolation, HistoryAction.AddTrack, HistoryAction.RemoveTrack {
        record AddKeyframe(int trackIndex, com.moud.api.animation.Keyframe keyframe) implements HistoryAction {
        }

        record RemoveKeyframe(int trackIndex, float time) implements HistoryAction {
        }

        record MoveKeyframe(int trackIndex, float fromTime, float toTime) implements HistoryAction {
        }

        record ChangeValue(int trackIndex, float time, float oldValue, float newValue) implements HistoryAction {
        }

        record ChangeInterpolation(int trackIndex, float time, com.moud.api.animation.Keyframe.Interpolation oldType,
                                   com.moud.api.animation.Keyframe.Interpolation newType) implements HistoryAction {
        }

        record AddTrack(int trackIndex, TrackSnapshot snapshot) implements HistoryAction {
        }

        record RemoveTrack(int trackIndex, TrackSnapshot snapshot) implements HistoryAction {
        }
    }

    private record TrackView(String objectLabel, String label, String propertyPath, Map<String, PropertyTrack> propertyMap,
                             PropertyTrack propertyTrack) {
        List<com.moud.api.animation.Keyframe> keyframes() {
            return propertyTrack.keyframes();
        }
    }

    private enum RowType { OBJECT, CATEGORY, TRACK }

    private record RowEntry(RowType type, String id, String label, int indent, Integer trackIndex) {}

    private record SelectedKeyframe(int trackIndex, double time) {
    }

    private record HistoryEntry(String description, List<HistoryAction> undoActions, List<HistoryAction> redoActions) {
    }

    private record TrackSnapshot(String objectLabel, String label, String propertyPath, Map<String, PropertyTrack> propertyMap,
                                 PropertyTrack propertyTrackCopy, List<com.moud.api.animation.Keyframe> keyframesCopy) {
    }

    private record CopiedKeyframe(int trackIndex, float time, float value,
                                  com.moud.api.animation.Keyframe.Interpolation interpolation) {
    }

    private record TransformSnapshot(float[] translation, float[] rotation, float[] scale) {
        static TransformSnapshot from(float[] t, float[] r, float[] s) {
            return new TransformSnapshot(t.clone(), r.clone(), s.clone());
        }
    }
}
