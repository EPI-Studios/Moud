package com.moud.client.editor.ui.panel;

import com.moud.api.animation.AnimatableRegistry;
import com.moud.api.animation.AnimationClip;
import com.moud.api.animation.ObjectTrack;
import com.moud.api.animation.PropertyTrack;
import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.network.MoudPackets;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.*;


public final class AnimationTimelinePanel {
    private final SceneEditorOverlay overlay;
    private final ImString savePathBuffer = new ImString(256);
    private final ImString filterBuffer = new ImString(64);
    private final List<MoudPackets.AnimationFileInfo> availableAnimations = new ArrayList<>();
    private final List<String> recentEvents = new ArrayList<>();
    private final List<SelectedKeyframe> selectedKeyframes = new ArrayList<>();
    private final List<TrackView> tracks = new ArrayList<>();
    private final Deque<HistoryEntry> undoStack = new ArrayDeque<>(50);
    private final Deque<HistoryEntry> redoStack = new ArrayDeque<>(50);
    private final Map<Integer, Boolean> trackVisibility = new LinkedHashMap<>();
    private final Map<Integer, float[]> trackColors = new LinkedHashMap<>();
    private final Map<String, TransformSnapshot> lastRecordedTransforms = new java.util.HashMap<>();
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

    public AnimationTimelinePanel(SceneEditorOverlay overlay) {
        this.overlay = overlay;
        AnimatableRegistry.registerDefaults();
        this.currentClip = buildTestClip();
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
                overlay.playAnimation(resolveAnimationId(), loop, speed);
            } else {
                overlay.stopAnimation(resolveAnimationId());
            }
        }
        ImGui.sameLine();
        if (ImGui.button("\ue047 Stop")) {
            playing = false;
            currentTime = 0f;
            overlay.seekAnimation(resolveAnimationId(), currentTime);
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
        int recColor = recording ? ImGui.getColorU32(ImGuiCol.ButtonActive) : ImGui.getColorU32(ImGuiCol.Button);
        ImGui.pushStyleColor(ImGuiCol.Button, recColor);
        if (ImGui.button("\ue061 Rec")) {
            recording = !recording;
        }
        ImGui.popStyleColor();
        ImGui.sameLine();
        ImGui.text(String.format("Time %s / %s", formatTime(currentTime), currentClip != null ? formatTime(currentClip.duration()) : "00:00:00"));

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
        ObjectTrack objTrack = ensureObjectTrack(object);
        float t = currentTime;
        String cacheKey = object.getId() + "|" + limbKey;

        TransformSnapshot last = lastRecordedTransforms.get(cacheKey);
        boolean firstRecord = last == null;
        float eps = 1e-4f;
        if (firstRecord) {
            last = TransformSnapshot.from(translation, rotation, scale);
        }

        boolean changed = false;
        String prefix = limbKey + ".";
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
        }
        ImGui.text(String.format("Time: %.3fs / %.3fs", currentTime, duration));
    }

    private void renderTimelineArea() {
        if (currentClip == null) {
            return;
        }
        float contentWidth = Math.max(1f, ImGui.getContentRegionAvailX());
        float rowHeight = ImGui.getTextLineHeightWithSpacing() + 6f;
        float contentHeight = Math.max(rowHeight * Math.max(1, tracks.size()) + 48f, ImGui.getContentRegionAvailY() - 50f);
        float leftWidth = 240f;
        float rulerHeight = 24f;
        float zoomBarHeight = 14f;
        float trackAreaHeight = Math.max(rowHeight * Math.max(1, tracks.size()), contentHeight - rulerHeight - zoomBarHeight);

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

        for (int i = 0; i < tracks.size(); i++) {
            TrackView track = tracks.get(i);
            if (!trackVisibility.getOrDefault(i, true)) {
                continue;
            }
            float rowTop = y + i * rowHeight;
            float rowCenter = rowTop + rowHeight * 0.5f;

            int bg = (i % 2 == 0) ? ImGui.getColorU32(ImGuiCol.FrameBg) : ImGui.getColorU32(ImGuiCol.FrameBgHovered);
            drawList.addRectFilled(rightX, rowTop, rightX + keyWidth, rowTop + rowHeight, bg);

            List<com.moud.api.animation.Keyframe> keyframes = track.keyframes();
            if (keyframes != null) {
                for (com.moud.api.animation.Keyframe kf : keyframes) {
                    double t = kf.time();
                    if (t < visibleStart || t > visibleEnd) continue;
                    float px = (float) (rightX + (t - visibleStart) / visibleSpan * keyWidth);
                    float size = 6f;
                    int color = ImGui.getColorU32(ImGuiCol.PlotLines);
                    float[] custom = trackColors.get(i);
                    if (custom != null) {
                        color = ImGui.colorConvertFloat4ToU32(custom[0], custom[1], custom[2], 1f);
                    }
                    if (isSelected(i, kf.time())) {
                        color = ImGui.getColorU32(ImGuiCol.PlotHistogram);
                    }
                    drawKeyframeShape(drawList, px, rowCenter, (int) size, color, kf.interpolation());
                }
            }
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

        for (int i = 0; i < tracks.size(); i++) {
            TrackView track = tracks.get(i);
            if (!trackVisibility.getOrDefault(i, true)) {
                continue;
            }
            float rowTop = keyY + i * rowHeight;
            float rowBottom = rowTop + rowHeight;
            if (mouseY < rowTop || mouseY > rowBottom) {
                continue;
            }
            List<com.moud.api.animation.Keyframe> keyframes = track.keyframes();
            if (keyframes == null) {
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
                    toggleSelection(i, kf.time(), additive);
                    return;
                }
            }
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
        for (int i = 0; i < tracks.size(); i++) {
            TrackView track = tracks.get(i);
            if (!trackVisibility.getOrDefault(i, true)) {
                continue;
            }
            float rowTop = keyY + i * rowHeight;
            float rowBottom = rowTop + rowHeight;
            if (rowBottom < minY || rowTop > maxY) {
                continue;
            }
            List<com.moud.api.animation.Keyframe> keyframes = track.keyframes();
            if (keyframes == null) continue;
            for (com.moud.api.animation.Keyframe kf : keyframes) {
                double t = kf.time();
                if (t < visibleStart || t > visibleEnd) continue;
                float px = (float) (timelineX + (t - visibleStart) / visibleSpan * keyWidth);
                float py = rowTop + rowHeight * 0.5f;
                if (px >= minX && px <= maxX && py >= minY && py <= maxY) {
                    selectedKeyframes.add(new SelectedKeyframe(i, kf.time()));
                }
            }
        }
    }

    private boolean attemptBeginKeyframeDrag(float mouseX, float mouseY, float timelineX, float timelineWidth, float keyY, float keyHeight, double visibleStart, double visibleSpan) {
        float rowHeight = ImGui.getTextLineHeightWithSpacing() + 6f;
        float keyWidth = this.timelineWidth;
        double visibleEnd = visibleStart + visibleSpan;
        for (int i = 0; i < tracks.size(); i++) {
            TrackView track = tracks.get(i);
            if (!trackVisibility.getOrDefault(i, true)) {
                continue;
            }
            float rowTop = keyY + i * rowHeight;
            float rowBottom = rowTop + rowHeight;
            if (mouseY < rowTop || mouseY > rowBottom) {
                continue;
            }
            List<com.moud.api.animation.Keyframe> keyframes = track.keyframes();
            if (keyframes == null) continue;
            for (com.moud.api.animation.Keyframe kf : keyframes) {
                double t = kf.time();
                if (t < visibleStart || t > visibleEnd) continue;
                float px = (float) (timelineX + (t - visibleStart) / visibleSpan * keyWidth);
                float py = rowTop + rowHeight * 0.5f;
                float size = 8f;
                if (Math.abs(mouseX - px) <= size && Math.abs(mouseY - py) <= size) {
                    if (!isSelected(i, t)) {
                        selectedKeyframes.clear();
                        selectedKeyframes.add(new SelectedKeyframe(i, t));
                    }
                    draggingKeyframes = true;
                    dragKeyStartX = mouseX;
                    dragOriginalTimes = snapshotSelectedTimes();
                    dragSelectedSnapshot = new ArrayList<>(selectedKeyframes);
                    snapDuringDrag = false;
                    return true;
                }
            }
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
                    contextTime = pixelXToTime(mouseX);
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
        if (!wantKeyboard && ImGui.isKeyPressed(ImGuiKey.Space, false) && currentClip != null) {
            playing = !playing;
            if (playing) {
                overlay.seekAnimation(resolveAnimationId(), currentTime);
                overlay.playAnimation(resolveAnimationId(), loop, speed);
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

    private void rebuildTrackViews() {
        tracks.clear();
        if (currentClip == null || currentClip.objectTracks() == null) {
            return;
        }
        for (ObjectTrack obj : currentClip.objectTracks()) {
            if (obj.propertyTracks() == null) continue;
            for (Map.Entry<String, PropertyTrack> entry : obj.propertyTracks().entrySet()) {
                PropertyTrack track = entry.getValue();
                String label = (obj.targetObjectName() != null ? obj.targetObjectName() : obj.targetObjectId()) + " / " + track.propertyPath();
                tracks.add(new TrackView(label, entry.getKey(), obj.propertyTracks(), track));
            }
        }
        trackVisibility.clear();
        trackColors.clear();
        for (int i = 0; i < tracks.size(); i++) {
            trackVisibility.put(i, true);
        }
    }

    private AnimationClip buildTestClip() {
        List<ObjectTrack> objects = new ArrayList<>();
        objects.add(buildTestObject("obj-1", "Camera Rig"));
        objects.add(buildTestObject("obj-2", "Light A"));
        return new AnimationClip("test-clip", "Test Clip", 8f, 60f, objects, new ArrayList<>(), Map.of());
    }

    private ObjectTrack buildTestObject(String id, String name) {
        Map<String, PropertyTrack> props = new LinkedHashMap<>();
        props.put("position.x", new PropertyTrack(
                "position.x",
                PropertyTrack.PropertyType.FLOAT,
                -10f,
                10f,
                testKeys(new float[]{0f, 2f, 4f}, new float[]{0f, 4f, -2f})
        ));
        props.put("rotation.y", new PropertyTrack(
                "rotation.y",
                PropertyTrack.PropertyType.ANGLE,
                -180f,
                180f,
                testKeys(new float[]{1f, 3f, 6f}, new float[]{0f, 90f, 45f})
        ));
        return new ObjectTrack(id, name, props);
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
    }

    public AnimationClip getCurrentClip() {
        return currentClip;
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
        // hierachy for fake players limbq
        String selectedType = selected.getType() == null ? "" : selected.getType().toLowerCase(Locale.ROOT);
        boolean isFakePlayer = selectedType.contains("fakeplayer") || selectedType.contains("fake_player") || selectedType.contains("fake player");
        if (isFakePlayer) {
            if (ImGui.treeNode("Fake Player")) {
                String preferredLimb = overlay.getSelectedLimbType();
                boolean showRootProps = preferredLimb == null || preferredLimb.isEmpty() || preferredLimb.contains("torso");
                if (showRootProps) {
                    renderPropertyMenuItems(targetTrack, props, p -> true);
                }
                if (ImGui.treeNodeEx("Head", preferredLimb != null && preferredLimb.contains("head") ? ImGuiTreeNodeFlags.DefaultOpen : 0)) {
                    renderLimbMenu(targetTrack, "fakeplayer:head");
                    ImGui.treePop();
                }
                if (ImGui.treeNodeEx("Torso", preferredLimb != null && preferredLimb.contains("torso") ? ImGuiTreeNodeFlags.DefaultOpen : 0)) {
                    renderLimbMenu(targetTrack, "fakeplayer:torso");
                    ImGui.treePop();
                }
                if (ImGui.treeNodeEx("Left Arm", preferredLimb != null && preferredLimb.contains("left_arm") ? ImGuiTreeNodeFlags.DefaultOpen : 0)) {
                    renderLimbMenu(targetTrack, "fakeplayer:left_arm");
                    ImGui.treePop();
                }
                if (ImGui.treeNodeEx("Right Arm", preferredLimb != null && preferredLimb.contains("right_arm") ? ImGuiTreeNodeFlags.DefaultOpen : 0)) {
                    renderLimbMenu(targetTrack, "fakeplayer:right_arm");
                    ImGui.treePop();
                }
                if (ImGui.treeNodeEx("Left Leg", preferredLimb != null && preferredLimb.contains("left_leg") ? ImGuiTreeNodeFlags.DefaultOpen : 0)) {
                    renderLimbMenu(targetTrack, "fakeplayer:left_leg");
                    ImGui.treePop();
                }
                if (ImGui.treeNodeEx("Right Leg", preferredLimb != null && preferredLimb.contains("right_leg") ? ImGuiTreeNodeFlags.DefaultOpen : 0)) {
                    renderLimbMenu(targetTrack, "fakeplayer:right_leg");
                    ImGui.treePop();
                }
                ImGui.treePop();
            }
        } else {
            if (ImGui.treeNode("Properties")) {
                renderPropertyMenuItems(targetTrack, props, p -> true);
                ImGui.treePop();
            }
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
                TrackView trackView = new TrackView((targetTrack.targetObjectName() != null ? targetTrack.targetObjectName() : targetTrack.targetObjectId()) + " / " + fullPath, fullPath, targetTrack.propertyTracks(), newTrack);
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
        List<com.moud.api.animation.Keyframe> copy = new ArrayList<>();
        List<com.moud.api.animation.Keyframe> src = track.keyframes();
        if (src != null) {
            for (com.moud.api.animation.Keyframe kf : src) {
                copy.add(new com.moud.api.animation.Keyframe(kf.time(), kf.value(), kf.interpolation(), kf.inTangent(), kf.outTangent()));
            }
        }
        PropertyTrack copyTrack = new PropertyTrack(track.propertyPath(), track.propertyTrack().propertyType(), track.propertyTrack().minValue(), track.propertyTrack().maxValue(), copy);
        return new TrackSnapshot(track.label(), track.propertyPath(), track.propertyMap(), copyTrack, copy);
    }

    private TrackView restoreTrack(TrackSnapshot snapshot) {
        snapshot.propertyMap().put(snapshot.propertyPath(), snapshot.propertyTrackCopy());
        return new TrackView(snapshot.label(), snapshot.propertyPath(), snapshot.propertyMap(), snapshot.propertyTrackCopy());
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
            tracks.set(idx, new TrackView(track.label(), track.propertyPath(), track.propertyMap(), newTrack));
        }
        return newTrack.keyframes();
    }

    private AnimationClip ensureMutableClip() {
        if (currentClip == null) return null;
        List<ObjectTrack> objects = currentClip.objectTracks();
        if (!(objects instanceof ArrayList<ObjectTrack>)) {
            objects = new ArrayList<>(objects != null ? objects : List.of());
            currentClip = new AnimationClip(currentClip.id(), currentClip.name(), currentClip.duration(), currentClip.frameRate(), objects, currentClip.eventTrack(), currentClip.metadata());
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
        for (int i = 0; i < tracks.size(); i++) {
            TrackView track = tracks.get(i);
            if (!trackVisibility.getOrDefault(i, true)) {
                continue;
            }
            float rowTop = keyY + i * rowHeight;
            float rowBottom = rowTop + rowHeight;
            if (mouseY < rowTop || mouseY > rowBottom) continue;
            List<com.moud.api.animation.Keyframe> keyframes = track.keyframes();
            if (keyframes == null) continue;
            for (com.moud.api.animation.Keyframe kf : keyframes) {
                double t = kf.time();
                if (t < visibleStart || t > visibleEnd) continue;
                float px = (float) (timelineX + (t - visibleStart) / visibleSpan * keyWidth);
                float py = rowTop + rowHeight * 0.5f;
                float size = 8f;
                if (Math.abs(mouseX - px) <= size && Math.abs(mouseY - py) <= size) {
                    return new SelectedKeyframe(i, kf.time());
                }
            }
        }
        return null;
    }

    private int rowIndexFromY(float mouseY, float keyY) {
        float rowHeight = ImGui.getTextLineHeightWithSpacing() + 6f;
        int idx = (int) Math.floor((mouseY - keyY) / rowHeight);
        return idx;
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
        for (int i = 0; i < tracks.size(); i++) {
            TrackView track = tracks.get(i);
            float rowTop = y + i * rowHeight;
            int textColor = ImGui.getColorU32(ImGuiCol.Text);
            float cursorY = rowTop + 2;
            ImGui.setCursorScreenPos(leftX + 4, cursorY);
            ImGui.pushID(i);

            // drag handle
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 2, 1);
            if (ImGui.button("\ue945##drag" + i) || (ImGui.isItemActive() && ImGui.isMouseDragging(ImGuiMouseButton.Left))) {
                draggingTrackIndex = i;
                dragTrackStartY = ImGui.getIO().getMousePosY();
            }
            ImGui.popStyleVar();

            ImGui.sameLine();

            // visibility toggle
            boolean visible = trackVisibility.getOrDefault(i, true);
            String visIcon = visible ? "\ue8f4" : "\ue8f5";
            if (ImGui.smallButton(visIcon + "##vis" + i)) {
                trackVisibility.put(i, !visible);
            }
            ImGui.sameLine();

            // color swatch
            float[] color = trackColors.getOrDefault(i, null);
            int colorU32 = color != null ? ImGui.colorConvertFloat4ToU32(color[0], color[1], color[2], 1f) : textColor;
            ImGui.colorButton("##col" + i, color != null ? color : new float[]{1f, 1f, 1f});
            if (ImGui.isItemClicked()) {
                colorPickerTrackIndex = i;
                ImGui.openPopup("##TrackColorPicker");
            }
            ImGui.sameLine();

            // label / rename
            if (renamingTrackIndex == i) {
                ImGui.setNextItemWidth(140);
                if (ImGui.inputText("##RenameTrack", renameBuffer, ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll)) {
                    tracks.set(i, new TrackView(renameBuffer.get().trim(), track.propertyPath(), track.propertyMap(), track.propertyTrack()));
                    renamingTrackIndex = -1;
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

            if (ImGui.beginPopup("##TrackContext")) {
                if (ImGui.menuItem("Rename")) {
                    renamingTrackIndex = i;
                    renameBuffer.set(track.label());
                }
                if (ImGui.menuItem("Delete")) {
                    removeTrackWithHistory(i);
                    ImGui.closeCurrentPopup();
                }
                if (ImGui.menuItem("Set Color")) {
                    colorPickerTrackIndex = i;
                }
                ImGui.endPopup();
            }

            if (ImGui.beginPopup("##TrackColorPicker")) {
                if (colorPickerTrackIndex >= 0 && colorPickerTrackIndex < tracks.size()) {
                    float[] picked = trackColors.getOrDefault(colorPickerTrackIndex, new float[]{1f, 1f, 1f});
                    if (ImGui.colorPicker3("Track Color", picked)) {
                        trackColors.put(colorPickerTrackIndex, picked);
                    }
                    if (ImGui.button("Reset")) {
                        trackColors.remove(colorPickerTrackIndex);
                    }
                }
                ImGui.endPopup();
            }

            ImGui.popID();
        }

        // add track button
        ImGui.setCursorScreenPos(leftX + 4, y + tracks.size() * rowHeight + 6);
        if (ImGui.button("+ Add Track")) {
            ImGui.openPopup("##AddTrackPopup");
        }
        if (ImGui.beginPopup("##AddTrackPopup")) {
            renderAddTrackPopup();
            ImGui.endPopup();
        }

        handleTrackReorder(y, rowHeight);
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

    private record TrackView(String label, String propertyPath, Map<String, PropertyTrack> propertyMap,
                             PropertyTrack propertyTrack) {
        List<com.moud.api.animation.Keyframe> keyframes() {
            return propertyTrack.keyframes();
        }
    }

    private record SelectedKeyframe(int trackIndex, double time) {
    }

    private record HistoryEntry(String description, List<HistoryAction> undoActions, List<HistoryAction> redoActions) {
    }

    private record TrackSnapshot(String label, String propertyPath, Map<String, PropertyTrack> propertyMap,
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
