package com.moud.client.editor.ui.panel.timeline;

import com.moud.api.animation.AnimationClip;
import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.network.MoudPackets;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ToolbarView {
    private ToolbarView() {
    }

    public record Result(
            AnimationClip currentClip,
            String selectedPath,
            float currentTime,
            boolean playing,
            boolean loop,
            float speed,
            boolean recording,
            boolean listRequested
    ) {
    }

    public static Result render(
            SceneEditorOverlay overlay,
            ImString savePathBuffer,
            ImString filterBuffer,
            List<MoudPackets.AnimationFileInfo> availableAnimations,
            List<String> recentEvents,
            History history,
            Map<String, TransformSnapshot> lastRecordedTransforms,
            Runnable rebuildTrackViews,
            Runnable insertKeyframeAtCurrentTime,
            Consumer<String> pushEventIndicator,
            Supplier<String> resolveAnimationId,
            Consumer<Float> applyAnimationAtTime,
            Consumer<Float> dispatchEventTrack,
            AnimationClip currentClip,
            String selectedPath,
            float currentTime,
            boolean playing,
            boolean loop,
            float speed,
            boolean recording,
            boolean listRequested
    ) {
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 6, 4);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 8, 6);

        ImGui.setNextItemWidth(180f);
        if (ImGui.beginCombo("##AnimSelector", currentClip != null ? currentClip.name() : "<none>")) {
            String filter = filterBuffer.get().trim().toLowerCase(Locale.ROOT);
            for (MoudPackets.AnimationFileInfo info : availableAnimations) {
                if (!filter.isEmpty()
                        && !info.path().toLowerCase(Locale.ROOT).contains(filter)
                        && !info.name().toLowerCase(Locale.ROOT).contains(filter)) {
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
                String path = filterBuffer.get().isBlank()
                        ? "animations/" + name.replace(' ', '_').toLowerCase(Locale.ROOT) + ".an"
                        : filterBuffer.get();
                currentClip = new AnimationClip(UUID.randomUUID().toString(), name, 8f, 60f, new ArrayList<>(), new ArrayList<>(), Map.of());
                selectedPath = path;
                savePathBuffer.set(path);
                rebuildTrackViews.run();
                history.clear();
                lastRecordedTransforms.clear();
                currentTime = 0f;
                playing = false;
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }

        ImGui.sameLine();
        if (ImGui.button("\ue161 Save") && currentClip != null) {
            String path = savePathBuffer.get().isBlank()
                    ? (selectedPath != null ? selectedPath : currentClip.name() + ".an")
                    : savePathBuffer.get();
            overlay.saveAnimation(path, currentClip);
            pushEventIndicator.accept("Saved " + path);
            listRequested = false;
            overlay.requestAnimationList();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Save current animation [Ctrl+S]");
        }

        ImGui.sameLine();
        if (ImGui.button("\ue5d5 Refresh")) {
            listRequested = false;
            overlay.requestAnimationList();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Refresh animation list from server");
        }
        ImGui.sameLine();
        ImGui.textDisabled(" | ");
        ImGui.sameLine();

        String playIcon = playing ? "\ue034" : "\ue037";
        if (ImGui.button(playIcon + " Play")) {
            playing = !playing;
            if (playing && currentClip != null) {
                overlay.seekAnimation(resolveAnimationId.get(), currentTime);
                applyAnimationAtTime.accept(currentTime);
                overlay.playAnimation(resolveAnimationId.get(), loop, speed);
                dispatchEventTrack.accept(currentTime);
            } else {
                overlay.stopAnimation(resolveAnimationId.get());
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Play/Pause animation [Space]");
        }
        ImGui.sameLine();
        if (ImGui.button("\ue047 Stop")) {
            playing = false;
            currentTime = 0f;
            overlay.seekAnimation(resolveAnimationId.get(), currentTime);
            applyAnimationAtTime.accept(currentTime);
            overlay.stopAnimation(resolveAnimationId.get());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Stop and reset to start");
        }
        ImGui.sameLine();
        int loopColor = loop ? ImGui.getColorU32(ImGuiCol.ButtonActive) : ImGui.getColorU32(ImGuiCol.Button);
        ImGui.pushStyleColor(ImGuiCol.Button, loopColor);
        if (ImGui.button("\ue040")) {
            loop = !loop;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(loop ? "Loop enabled (click to disable)" : "Loop disabled (click to enable)");
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
        if (recording) {
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
            lastRecordedTransforms.clear();
            pushEventIndicator.accept(recording ? "Recording started - move objects to record" : "Recording stopped");
        }
        ImGui.popStyleColor(3);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(recording
                    ? "Recording active [R to toggle]\nKeyframes auto-added when you transform objects"
                    : "Start recording mode [R]\nKeyframes will be added automatically");
        }

        ImGui.sameLine();
        if (ImGui.button("\ue145 Key")) {
            insertKeyframeAtCurrentTime.run();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Insert keyframe at current time for selected object (K)");
        }

        ImGui.sameLine();
        ImGui.text(String.format("Time %s / %s",
                Playback.formatTime(currentTime),
                currentClip != null ? Playback.formatTime(currentClip.duration()) : "00:00:00"));

        if (recording) {
            ImGui.sameLine();
            ImGui.textColored(0xFF0000FF, "\u25cf REC");
        }

        if (!recentEvents.isEmpty()) {
            ImGui.sameLine();
            ImGui.textColored(ImGui.getColorU32(ImGuiCol.PlotHistogramHovered), "\ue7f7 Events: " + recentEvents.getLast());
            if (recentEvents.size() > 4) {
                recentEvents.remove(0);
            }
        }

        ImGui.popStyleVar(2);

        return new Result(currentClip, selectedPath, currentTime, playing, loop, speed, recording, listRequested);
    }
}
