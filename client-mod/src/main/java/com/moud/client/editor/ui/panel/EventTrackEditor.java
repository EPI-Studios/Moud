package com.moud.client.editor.ui.panel;

import com.moud.api.animation.EventKeyframe;
import com.moud.api.animation.AnimationClip;
import imgui.ImGui;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImFloat;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class EventTrackEditor {
    private final ImString payloadBuffer = new ImString("{}", 512);
    private final ImString nameBuffer = new ImString("particle_burst", 128);
    private final ImString templatePicker = new ImString("particle_burst", 64);

    public void render(AnimationClip clip, Runnable onChange) {
        if (clip == null) {
            ImGui.textDisabled("No animation loaded.");
            return;
        }
        List<EventKeyframe> events = ensureEventList(clip);

        ImGui.textDisabled("Event Track");
        if (ImGui.beginTable("event_table", 3, ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg | ImGuiTableFlags.SizingStretchProp)) {
            ImGui.tableSetupColumn("Time");
            ImGui.tableSetupColumn("Name");
            ImGui.tableSetupColumn("Payload");
            ImGui.tableHeadersRow();
            Iterator<EventKeyframe> it = events.iterator();
            int idx = 0;
            while (it.hasNext()) {
                EventKeyframe ev = it.next();
                ImGui.pushID(idx++);
                ImGui.tableNextRow();
                ImGui.tableSetColumnIndex(0);
                ImFloat timeBuf = new ImFloat(ev.time());
                if (ImGui.inputFloat("##time", timeBuf, 0.01f, 0.1f, "%.3f")) {
                    events.set(idx - 1, new EventKeyframe(Math.max(0f, timeBuf.get()), ev.name(), ev.payload()));
                    onChange.run();
                }
                ImGui.tableSetColumnIndex(1);
                ImString nameBuf = new ImString(ev.name(), 128);
                if (ImGui.inputText("##name", nameBuf)) {
                    events.set(idx - 1, new EventKeyframe(ev.time(), nameBuf.get(), ev.payload()));
                    onChange.run();
                }
                ImGui.tableSetColumnIndex(2);
                ImString payloadBuf = new ImString(ev.payload(), 512);
                boolean payloadChanged = ImGui.inputTextMultiline("##payload", payloadBuf, 400, 60);
                if (payloadChanged) {
                    events.set(idx - 1, new EventKeyframe(ev.time(), ev.name(), payloadBuf.get()));
                    onChange.run();
                }
                ImGui.sameLine();
                if (ImGui.button("Delete")) {
                    it.remove();
                    onChange.run();
                }
                ImGui.popID();
            }
            ImGui.endTable();
        }

        ImGui.separator();
        ImGui.textDisabled("Add Event");
        ImFloat timeNew = new ImFloat(0f);
        ImGui.inputFloat("Time", timeNew, 0.01f, 0.1f, "%.3f");
        ImGui.inputText("Name", nameBuffer);
        ImGui.inputTextMultiline("Payload", payloadBuffer, 400, 60);
        ImGui.sameLine();
        if (ImGui.beginCombo("Template", templatePicker.get())) {
            if (ImGui.selectable("particle_burst", "particle_burst".equals(templatePicker.get()))) {
                templatePicker.set("particle_burst");
                payloadBuffer.set("{\"type\":\"burst\",\"texture\":\"minecraft:particle/generic_0\",\"position\":{\"x\":0,\"y\":0,\"z\":0},\"velocity\":{\"x\":0,\"y\":1,\"z\":0},\"lifetime\":1.0}");
            }
            ImGui.endCombo();
        }
        if (ImGui.button("Add Event##add_event")) {
            events.add(new EventKeyframe(Math.max(0f, timeNew.get()), nameBuffer.get(), payloadBuffer.get()));
            events.sort((a, b) -> Float.compare(a.time(), b.time()));
            onChange.run();
        }

        writeBack(clip, events);
        renderValidation(payloadBuffer.get());
    }

    private List<EventKeyframe> ensureEventList(AnimationClip clip) {
        if (clip.eventTrack() != null) {
            return new ArrayList<>(clip.eventTrack());
        }
        return new ArrayList<>();
    }

    private void writeBack(AnimationClip clip, List<EventKeyframe> events) {
        if (clip.eventTrack() == events) {
            return;
        }
        events.sort((a, b) -> Float.compare(a.time(), b.time()));
        // replace clip contents via withEventTrack helper
        try {
            java.lang.reflect.Field f = clip.getClass().getDeclaredField("eventTrack");
            f.setAccessible(true);
            f.set(clip, events);
        } catch (Exception ignored) {
        }
    }

    private void renderValidation(String json) {
        try {
            com.google.gson.JsonParser.parseString(json);
        } catch (Exception e) {
            ImGui.textColored(0xFFAA0000, "Invalid JSON payload");
        }
    }
}
