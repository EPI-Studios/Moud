package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.client.editor.files.CodeEditor;
import com.meekdev.moud.mod.client.editor.kit.EmptyStates;
import com.meekdev.moud.mod.client.editor.kit.Notices;
import com.meekdev.moud.mod.client.editor.kit.NumberFields;
import com.meekdev.moud.mod.client.editor.kit.Sections;
import com.meekdev.moud.mod.client.editor.kit.SegmentedControl;
import com.meekdev.moud.mod.client.editor.kit.Switches;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.kit.Toolbars;
import com.meekdev.moud.mod.client.editor.panel.Panel;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;

final class InspectorPanel implements Panel {

    static final String ID = "anim-inspector";
    private static final float PREVIEW_HEIGHT = 96.0f;
    private static final float PAD = 8.0f;
    private static final List<String> TYPES = List.of("number", "string", "boolean");
    private static final String PREVIEW_HINT = "Scrubbing past this event plays its sound and particles in the viewport, "
            + "so timing can be judged without running the place.";

    private final AnimationSession session;
    private final PreviewRig rig;
    private final ImString eventName = new ImString(64);
    private final ImString eventSound = new ImString(128);
    private final ImString eventParticle = new ImString(128);
    private final ImString markerName = new ImString(64);
    private final ImString markerValue = new ImString(128);
    private final ImString model = new ImString(256);
    private final ImString held = new ImString(128);
    private final ImString priority = new ImString(16);
    private final List<ImString> fieldKeys = new ArrayList<>();
    private final List<ImString> fieldValues = new ArrayList<>();
    private String bound = "";

    InspectorPanel(AnimationWorkspace workspace, AnimationSession session, PreviewRig rig, IconWidgets icons) {
        this.session = session;
        this.rig = rig;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Inspector";
    }

    @Override
    public void render() {
        if (!session.hasClip()) {
            EmptyStates.centered("No clip open", List.of("Open or make a clip to edit it"));
            return;
        }
        AnimClip clip = session.clip();
        bind(clip);
        if (session.event() >= 0 && session.event() < clip.events.size()) renderEvent(clip, session.event());
        else if (session.marker() >= 0 && session.marker() < clip.markers.size()) renderMarker(clip, session.marker());
        else if (!session.keys().isEmpty()) renderKeys(clip);
        else if (session.joint() != null) renderBone(clip, session.joint());
        if (clip.space == AnimClip.Space.VIEW) renderViewModel(clip);
        renderClip(clip);
    }

    private void bind(AnimClip clip) {
        String key = session.path() + "|" + session.event() + "|" + session.marker();
        if (key.equals(bound) && !(session.event() >= 0 && fieldKeys.size() != eventOrEmpty(clip).payload().size())) return;
        bound = key;
        AnimClip.Event event = eventOrEmpty(clip);
        eventName.set(event.name());
        eventSound.set(event.sound());
        eventParticle.set(event.particle());
        fieldKeys.clear();
        fieldValues.clear();
        for (Map.Entry<String, Object> field : event.payload().entrySet()) {
            fieldKeys.add(buffer(field.getKey(), 64));
            fieldValues.add(buffer(EventSnippets.shown(field.getValue()), 128));
        }
        if (session.marker() >= 0 && session.marker() < clip.markers.size()) {
            markerName.set(clip.markers.get(session.marker()).name());
            markerValue.set(clip.markers.get(session.marker()).value());
        }
        model.set(clip.view.model());
        held.set(rig.heldItem());
        priority.set(ClipFile.isNumber(clip.priority) ? clip.priority : "");
    }

    private AnimClip.Event eventOrEmpty(AnimClip clip) {
        int index = session.event();
        return index >= 0 && index < clip.events.size() ? clip.events.get(index) : new AnimClip.Event(0, "", AnimClip.Side.BOTH, Map.of(), "", "", false);
    }

    private static ImString buffer(String text, int capacity) {
        ImString value = new ImString(capacity);
        value.set(text);
        return value;
    }

    private boolean header(String title, String right) {
        boolean open = Sections.header(title);
        if (!right.isEmpty()) {
            float x = ImGui.getItemRectMaxX() - Paint.monoWidth(right) - EditorScale.of(PAD);
            float y = ImGui.getItemRectMinY() + (ImGui.getItemRectMaxY() - ImGui.getItemRectMinY() - Paint.monoSize()) * 0.5f;
            Paint.mono(ImGui.getWindowDrawList(), x, y, Paint.SELECTED, right);
        }
        if (open) ImGui.dummy(0, EditorScale.of(2));
        return open;
    }

    private static void label(String text) {
        float column = Math.clamp(ImGui.getContentRegionAvailX() * 0.36f, EditorScale.of(84), EditorScale.of(150));
        float start = ImGui.getCursorPosX();
        ImGui.alignTextToFramePadding();
        Texts.plain(text);
        ImGui.sameLine(start + column);
    }

    private static void readOnly(String text, String value) {
        label(text);
        ImGui.alignTextToFramePadding();
        Texts.muted(value);
    }

    private void renderKeys(AnimClip clip) {
        Set<KeyRef> keys = session.keys();
        KeyRef first = keys.iterator().next();
        AnimKey key = clip.keyAt(first.joint(), first.channel(), first.time());
        if (key == null) return;
        boolean single = keys.size() == 1;
        String title = single ? "Keyframe" : "Keyframes";
        String detail = single ? Paint.seconds(first.time()) + " s" : keys.size() + " keys";
        if (!header(title, detail)) return;
        readOnly("Bone", single ? first.joint() : joints(keys));
        if (single || sameChannel(keys)) {
            label(first.channel().label());
            float[] values = {(float) key.value().x(), (float) key.value().y(), (float) key.value().z()};
            float width = ImGui.getContentRegionAvailX() - EditorScale.of(PAD);
            if (NumberFields.vector("##anim-key-value", values, 3, width, (float) first.channel().step())) {
                Vector3 changed = new Vector3(values[0], values[1], values[2]);
                Vector3 shift = changed.sub(key.value());
                Set<KeyRef> chosen = Set.copyOf(keys);
                session.edit("Edit key", "anim-key-value", edited -> {
                    for (KeyRef ref : chosen) {
                        AnimKey one = edited.keyAt(ref.joint(), ref.channel(), ref.time());
                        if (one != null) edited.put(ref.joint(), ref.channel(), one.with(single ? changed : one.value().add(shift)));
                    }
                });
            }
        }
        else readOnly("Channel", "mixed");
        label("Easing");
        int current = key.interp().ordinal();
        for (KeyRef ref : keys) {
            AnimKey other = clip.keyAt(ref.joint(), ref.channel(), ref.time());
            if (other != null && other.interp() != key.interp()) current = -1;
        }
        int chosen = segmented("anim-key-interp", List.of("Linear", "Smooth", "Bezier", "Step"), current);
        if (chosen != current && chosen >= 0) {
            Interp interp = Interp.values()[chosen];
            Set<KeyRef> refs = Set.copyOf(keys);
            session.edit("Set interpolation", edited -> KeyEdits.interp(edited, refs, interp));
        }
        if (single) {
            ImGui.dummy(0, EditorScale.of(4));
            curvePreview(clip, first);
        }
        ImGui.dummy(0, EditorScale.of(6));
    }

    private int segmented(String id, List<String> labels, int selected) {
        float width = ImGui.getContentRegionAvailX() - EditorScale.of(PAD);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0, 0);
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        float height = SegmentedControl.height();
        float cell = width / labels.size();
        ImDrawList draw = ImGui.getWindowDrawList();
        draw.addRectFilled(left, top, left + width, top + height, EditorStyle.COLOR_SUNKEN_BACKGROUND, EditorStyle.frameRounding());
        int chosen = selected;
        for (int n = 0; n < labels.size(); n++) {
            float cellLeft = left + cell * n;
            float cellRight = left + cell * (n + 1);
            ImGui.setCursorScreenPos(cellLeft, top);
            if (ImGui.invisibleButton(id + "-" + n, cell, height)) chosen = n;
            boolean hovered = ImGui.isItemHovered();
            float inset = EditorScale.of(2);
            if (n == selected || hovered) {
                int fill = n == selected ? EditorStyle.COLOR_WIDGET_ACTIVE : EditorStyle.withAlpha(EditorStyle.COLOR_WIDGET_HOVER, 0.7f);
                draw.addRectFilled(cellLeft + inset, top + inset, cellRight - inset, top + height - inset, fill, EditorStyle.frameRounding());
            }
            String text = labels.get(n);
            int color = n == selected ? EditorStyle.COLOR_TEXT : EditorStyle.COLOR_TEXT_MUTED;
            if (ImGui.calcTextSizeX(text) + inset * 4 <= cell) {
                float textLeft = cellLeft + (cell - ImGui.calcTextSizeX(text)) * 0.5f;
                draw.addText(textLeft, top + (height - ImGui.getTextLineHeight()) * 0.5f, color, text);
            } else {
                draw.pushClipRect(cellLeft, top, cellRight, top + height, true);
                float textLeft = cellLeft + Math.max(inset, (cell - Paint.smallWidth(text)) * 0.5f);
                Paint.small(draw, textLeft, top + (height - Paint.smallSize()) * 0.5f, color, text);
                draw.popClipRect();
            }
        }
        ImGui.setCursorScreenPos(left, top);
        ImGui.dummy(width, height);
        ImGui.popStyleVar();
        return chosen;
    }

    private static String joints(Set<KeyRef> keys) {
        Set<String> names = new LinkedHashSet<>();
        for (KeyRef ref : keys) names.add(ref.joint());
        return names.size() == 1 ? names.iterator().next() : names.size() + " bones";
    }

    private static boolean sameChannel(Set<KeyRef> keys) {
        Channel channel = keys.iterator().next().channel();
        return keys.stream().allMatch(ref -> ref.channel() == channel);
    }

    private void curvePreview(AnimClip clip, KeyRef ref) {
        List<AnimKey> keys = clip.keys(ref.joint(), ref.channel());
        int index = -1;
        for (int n = 0; n < keys.size(); n++) {
            if (Math.abs(keys.get(n).time() - ref.time()) < AnimClip.EPSILON) index = n;
        }
        if (index < 0) return;
        float width = ImGui.getContentRegionAvailX() - EditorScale.of(PAD);
        float height = EditorScale.of(PREVIEW_HEIGHT);
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        ImGui.dummy(width, height);
        ImDrawList draw = ImGui.getWindowDrawList();
        draw.addRectFilled(left, top, left + width, top + height, EditorStyle.COLOR_SUNKEN_BACKGROUND, EditorStyle.frameRounding());
        draw.addLine(left, top + height * 0.5f, left + width, top + height * 0.5f, Paint.GRID);
        draw.addLine(left + width / 3, top, left + width / 3, top + height, Paint.GRID);
        draw.addLine(left + width * 2 / 3, top, left + width * 2 / 3, top + height, Paint.GRID);
        AnimKey key = keys.get(index);
        double from = index > 0 ? keys.get(index - 1).time() : key.time() - 0.25;
        double to = index + 1 < keys.size() ? keys.get(index + 1).time() : key.time() + 0.25;
        int axis = dominantAxis(keys, index);
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        for (int n = 0; n <= 48; n++) {
            double value = Curves.component(Curves.sample(keys, from + (to - from) * n / 48.0, ref.channel().rest()), axis);
            low = Math.min(low, value);
            high = Math.max(high, value);
        }
        if (high - low < 1e-6) {
            low -= 1;
            high += 1;
        }
        double pad = (high - low) * 0.2;
        double min = low - pad;
        double max = high + pad;
        float inset = EditorScale.of(10);
        DoubleUnaryOperator sx = time -> left + inset + (float) ((time - from) / (to - from) * (width - inset * 2));
        DoubleUnaryOperator sy = value -> top + height - (float) ((value - min) / (max - min) * height);
        float previousX = 0;
        float previousY = 0;
        for (int n = 0; n <= 64; n++) {
            double time = from + (to - from) * n / 64.0;
            float px = (float) sx.applyAsDouble(time);
            float py = (float) sy.applyAsDouble(Curves.component(Curves.sample(keys, time, ref.channel().rest()), axis));
            if (n > 0) draw.addLine(previousX, previousY, px, py, Paint.axis(axis), EditorScale.of(1.8f));
            previousX = px;
            previousY = py;
        }
        float kx = (float) sx.applyAsDouble(key.time());
        float ky = (float) sy.applyAsDouble(Curves.component(key.value(), axis));
        if (key.interp() == Interp.BEZIER && index + 1 < keys.size()) {
            AnimKey.Handle out = Curves.outHandle(key, keys.get(index + 1));
            handle(draw, kx, ky, (float) sx.applyAsDouble(key.time() + out.dt()), (float) sy.applyAsDouble(Curves.component(key.value().add(out.dv()), axis)));
        }
        if (index > 0 && keys.get(index - 1).interp() == Interp.BEZIER) {
            AnimKey.Handle in = Curves.inHandle(keys.get(index - 1), key);
            handle(draw, kx, ky, (float) sx.applyAsDouble(key.time() + in.dt()), (float) sy.applyAsDouble(Curves.component(key.value().add(in.dv()), axis)));
        }
        Paint.diamond(draw, kx, ky, EditorScale.of(5), Paint.SELECTED);
        String axisName = new String[] {"X", "Y", "Z"}[axis];
        Paint.small(draw, left + width - Paint.smallWidth(axisName) - EditorScale.of(6), top + EditorScale.of(4), Paint.axis(axis), axisName);
    }

    private static void handle(ImDrawList draw, float kx, float ky, float hx, float hy) {
        draw.addLine(kx, ky, hx, hy, EditorStyle.COLOR_TEXT_MUTED);
        draw.addCircleFilled(hx, hy, EditorScale.of(3), EditorStyle.COLOR_TEXT_MUTED);
    }

    private static int dominantAxis(List<AnimKey> keys, int index) {
        int best = 0;
        double spread = -1;
        for (int axis = 0; axis < 3; axis++) {
            double low = Double.POSITIVE_INFINITY;
            double high = Double.NEGATIVE_INFINITY;
            for (int n = Math.max(0, index - 1); n <= Math.min(keys.size() - 1, index + 1); n++) {
                double value = Curves.component(keys.get(n).value(), axis);
                low = Math.min(low, value);
                high = Math.max(high, value);
            }
            if (high - low > spread + 1e-9) {
                spread = high - low;
                best = axis;
            }
        }
        return best;
    }

    private void renderBone(AnimClip clip, String joint) {
        if (!header("Bone", Paint.seconds(session.keyTime()) + " s")) return;
        readOnly("Bone", joint + "  \u00b7  " + clip.keyCount(joint) + " keys");
        JointPose pose = JointPose.of(clip, joint, session.keyTime());
        for (Channel channel : Channel.values()) {
            label(channel.label());
            Vector3 value = pose.value(channel);
            float[] values = {(float) value.x(), (float) value.y(), (float) value.z()};
            if (NumberFields.vector("##anim-bone-" + channel.key(), values, 3, ImGui.getContentRegionAvailX() - EditorScale.of(PAD), (float) channel.step())) {
                Vector3 changed = new Vector3(values[0], values[1], values[2]);
                double at = session.keyTime();
                session.edit("Key " + joint, "anim-bone-" + channel.key(), edited -> KeyEdits.set(edited, joint, channel, at, changed));
            }
        }
        Sections.caption("Editing a value keys it at the playhead");
        ImGui.dummy(0, EditorScale.of(6));
    }

    private void renderMarker(AnimClip clip, int index) {
        AnimClip.Marker marker = clip.markers.get(index);
        if (!header("Marker", Paint.seconds(marker.time()) + " s")) return;
        label("Name");
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - EditorScale.of(PAD));
        if (ImGui.inputText("##anim-marker-name", markerName)) {
            String name = markerName.get();
            session.edit("Rename marker", "anim-marker-name", edited -> {
                AnimClip.Marker old = edited.markers.get(index);
                edited.markers.set(index, new AnimClip.Marker(old.time(), name, old.value()));
            });
        }
        label("Value");
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - EditorScale.of(PAD));
        if (ImGui.inputText("##anim-marker-value", markerValue)) {
            String value = markerValue.get();
            session.edit("Set marker value", "anim-marker-value", edited -> {
                AnimClip.Marker old = edited.markers.get(index);
                edited.markers.set(index, new AnimClip.Marker(old.time(), old.name(), value));
            });
        }
        label("Time");
        float time = NumberFields.scalar("##anim-marker-time", (float) marker.time(), 1f / clip.fps, ImGui.getContentRegionAvailX() - EditorScale.of(PAD));
        if (Math.abs(time - marker.time()) > 1e-6) {
            double at = Math.max(0, session.snap() ? clip.snap(time) : time);
            session.edit("Move marker", "anim-marker-time", edited -> edited.markers.set(index, edited.markers.get(index).at(at)));
        }
        Sections.caption("Scripts hear it with getMarkerReachedSignal(\"" + marker.name() + "\")");
        ImGui.dummy(0, EditorScale.of(6));
    }

    private void renderEvent(AnimClip clip, int index) {
        AnimClip.Event event = clip.events.get(index);
        if (!header("Event", Paint.seconds(event.time()) + " s")) return;
        label("Name");
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - EditorScale.of(PAD));
        Paint.pushMono();
        ImGui.pushStyleColor(ImGuiCol.Text, EditorStyle.COLOR_WARNING);
        boolean renamed = ImGui.inputText("##anim-event-name", eventName, ImGuiInputTextFlags.CharsNoBlank);
        ImGui.popStyleColor();
        Paint.popMono();
        if (renamed && !eventName.get().isBlank()) {
            String name = eventName.get();
            session.edit("Rename event", "anim-event-name", edited -> edited.events.set(index, edited.events.get(index).named(name)));
        }
        label("Fires on");
        int side = segmented("anim-event-side", List.of("Server", "Client", "Both"), event.on().ordinal());
        if (side != event.on().ordinal()) {
            AnimClip.Side chosen = AnimClip.Side.values()[side];
            session.edit("Set event side", edited -> edited.events.set(index, edited.events.get(index).firing(chosen)));
        }
        Texts.plain("Payload");
        renderPayload(event, index);
        ImGui.dummy(0, EditorScale.of(4));
        renderPreviewCard(event, index);
        ImGui.dummy(0, EditorScale.of(4));
        renderHandler(clip, event);
        renderListeners(event);
        ImGui.dummy(0, EditorScale.of(6));
    }

    private void renderPayload(AnimClip.Event event, int index) {
        List<Map.Entry<String, Object>> fields = new ArrayList<>(event.payload().entrySet());
        float width = ImGui.getContentRegionAvailX() - EditorScale.of(PAD);
        float column = (width - ImGui.getFrameHeight()) / 3;
        int removed = -1;
        Paint.pushMono();
        for (int n = 0; n < fields.size() && n < fieldKeys.size(); n++) {
            ImGui.pushID("anim-field-" + n);
            Object value = fields.get(n).getValue();
            ImGui.setNextItemWidth(column - EditorStyle.itemSpacingX());
            if (ImGui.inputText("##key", fieldKeys.get(n), ImGuiInputTextFlags.CharsNoBlank) && !fieldKeys.get(n).get().isBlank()) {
                writePayload(index, fields, n, fieldKeys.get(n).get(), value);
            }
            ImGui.sameLine();
            String type = typeOf(value);
            ImGui.setNextItemWidth(column - EditorStyle.itemSpacingX());
            if (ImGui.beginCombo("##type", type)) {
                for (String option : TYPES) {
                    if (ImGui.selectable(option, option.equals(type))) {
                        Object converted = convert(fieldValues.get(n).get(), option);
                        fieldValues.get(n).set(EventSnippets.shown(converted));
                        writePayload(index, fields, n, fields.get(n).getKey(), converted);
                    }
                }
                ImGui.endCombo();
            }
            ImGui.sameLine();
            ImGui.setNextItemWidth(column - EditorStyle.itemSpacingX());
            if (ImGui.inputText("##value", fieldValues.get(n))) {
                writePayload(index, fields, n, fields.get(n).getKey(), convert(fieldValues.get(n).get(), type));
            }
            ImGui.sameLine();
            if (ImGui.button("×##remove", ImGui.getFrameHeight(), ImGui.getFrameHeight())) removed = n;
            ImGui.popID();
        }
        Paint.popMono();
        if (removed >= 0) {
            int gone = removed;
            session.edit("Remove payload field", edited -> {
                Map<String, Object> payload = new LinkedHashMap<>(edited.events.get(index).payload());
                payload.remove(fields.get(gone).getKey());
                edited.events.set(index, edited.events.get(index).carrying(payload));
            });
            bound = "";
        }
        Toolbars.pushFlatButtons();
        if (ImGui.button("+ field##anim-add-field")) {
            session.edit("Add payload field", edited -> {
                Map<String, Object> payload = new LinkedHashMap<>(edited.events.get(index).payload());
                String name = "field";
                for (int n = 2; payload.containsKey(name); n++) name = "field" + n;
                payload.put(name, 0.0);
                edited.events.set(index, edited.events.get(index).carrying(payload));
            });
            bound = "";
        }
        Toolbars.popFlatButtons();
    }

    private void writePayload(int index, List<Map.Entry<String, Object>> fields, int position, String key, Object value) {
        session.edit("Edit payload", "anim-payload-" + index + "-" + position, edited -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            for (int n = 0; n < fields.size(); n++) {
                if (n == position) payload.put(key, value);
                else payload.put(fields.get(n).getKey(), fields.get(n).getValue());
            }
            edited.events.set(index, edited.events.get(index).carrying(payload));
        });
    }

    private static String typeOf(Object value) {
        if (value instanceof Number) return "number";
        if (value instanceof Boolean) return "boolean";
        return "string";
    }

    private static Object convert(String text, String type) {
        return switch (type) {
            case "number" -> {
                try {
                    yield Double.parseDouble(text.strip());
                } catch (NumberFormatException e) {
                    yield 0.0;
                }
            }
            case "boolean" -> text.strip().equalsIgnoreCase("true");
            default -> text;
        };
    }

    private void renderPreviewCard(AnimClip.Event event, int index) {
        float width = ImGui.getContentRegionAvailX() - EditorScale.of(PAD);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, EditorStyle.COLOR_ELEVATED_BACKGROUND);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, EditorScale.of(10), EditorScale.of(10));
        float height = event.preview() ? EditorScale.of(168) : EditorScale.of(92);
        ImGui.beginChild("##anim-event-preview", width, height, ImGuiChildFlags.AlwaysUseWindowPadding, ImGuiWindowFlags.NoScrollbar);
        ImGui.alignTextToFramePadding();
        ImGui.textUnformatted("Preview in editor");
        ImGui.sameLine(ImGui.getContentRegionMaxX() - Switches.width());
        boolean on = Switches.draw("##anim-event-preview-switch", event.preview());
        if (on != event.preview()) session.edit(on ? "Preview event" : "Stop previewing event", edited -> {
            AnimClip.Event one = edited.events.get(index);
            edited.events.set(index, one.previewing(one.sound(), one.particle(), on));
        });
        ImGui.pushTextWrapPos(ImGui.getContentRegionMaxX());
        Paint.pushSmall();
        Texts.colored(EditorStyle.COLOR_TEXT_MUTED, PREVIEW_HINT);
        Paint.popSmall();
        ImGui.popTextWrapPos();
        if (event.preview()) {
            ImGui.dummy(0, EditorScale.of(2));
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.inputTextWithHint("##anim-event-sound", "sound, like block.amethyst_block.chime", eventSound)) {
                String sound = eventSound.get().strip();
                session.edit("Set preview sound", "anim-event-sound", edited -> {
                    AnimClip.Event one = edited.events.get(index);
                    edited.events.set(index, one.previewing(sound, one.particle(), one.preview()));
                });
            }
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.inputTextWithHint("##anim-event-particle", "particle, like end_rod", eventParticle)) {
                String particle = eventParticle.get().strip();
                session.edit("Set preview particle", "anim-event-particle", edited -> {
                    AnimClip.Event one = edited.events.get(index);
                    edited.events.set(index, one.previewing(one.sound(), particle, one.preview()));
                });
            }
        }
        ImGui.endChild();
        ImGui.popStyleVar();
        ImGui.popStyleColor();
    }

    private void renderHandler(AnimClip clip, AnimClip.Event event) {
        Path path = session.path();
        String res = path == null ? "" : "res://" + session.library().folder().getParent().relativize(path).toString().replace('\\', '/');
        String snippet = EventSnippets.handler(session.name(), res, event);
        Texts.muted("Handler");
        ImGui.sameLine(ImGui.getContentRegionMaxX() - ImGui.calcTextSizeX("Copy snippet") - EditorStyle.framePaddingX() * 2 - EditorScale.of(PAD));
        if (ImGui.smallButton("Copy snippet##anim-copy-snippet")) {
            ImGui.setClipboardText(snippet);
            session.say("handler for " + event.name() + " copied");
        }
        float width = ImGui.getContentRegionAvailX() - EditorScale.of(PAD);
        String[] lines = snippet.split("\n");
        float height = Paint.monoSize() * 1.35f * lines.length + EditorScale.of(12);
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        ImGui.dummy(width, height);
        ImDrawList draw = ImGui.getWindowDrawList();
        draw.addRectFilled(left, top, left + width, top + height, EditorStyle.COLOR_SUNKEN_BACKGROUND, EditorStyle.frameRounding());
        draw.pushClipRect(left, top, left + width, top + height, true);
        float y = top + EditorScale.of(6);
        for (String line : lines) {
            int comment = line.indexOf(" -- ");
            String code = comment < 0 ? line : line.substring(0, comment);
            Paint.mono(draw, left + EditorScale.of(8), y, EditorStyle.COLOR_TEXT, code);
            if (comment >= 0) Paint.mono(draw, left + EditorScale.of(8) + Paint.monoWidth(code), y, EditorStyle.COLOR_TEXT_FAINT, line.substring(comment));
            y += Paint.monoSize() * 1.35f;
        }
        draw.popClipRect();
    }

    private void renderListeners(AnimClip.Event event) {
        List<EventSnippets.Listener> listeners = EventSnippets.listeners(event.name());
        ImGui.dummy(0, EditorScale.of(4));
        Texts.muted("Listeners in this place");
        if (listeners.isEmpty()) {
            Paint.pushSmall();
            Texts.colored(EditorStyle.COLOR_TEXT_FAINT, "No script listens for " + event.name() + " yet");
            Paint.popSmall();
            return;
        }
        Paint.pushMono();
        for (EventSnippets.Listener listener : listeners) {
            float lineWidth = ImGui.calcTextSizeX("line " + listener.line());
            String label = listener.shown() + "##listener-" + listener.shown() + listener.line();
            float width = ImGui.getContentRegionAvailX() - lineWidth - EditorScale.of(PAD * 2);
            if (ImGui.selectable(label, false, 0, width, 0)) {
                CodeEditor.open(listener.file(), listener.line());
            }
            ImGui.sameLine(ImGui.getContentRegionMaxX() - lineWidth - EditorScale.of(PAD));
            Texts.colored(EditorStyle.COLOR_TEXT_FAINT, "line " + listener.line());
        }
        Paint.popMono();
    }

    private void renderViewModel(AnimClip clip) {
        if (!header("View model", "")) return;
        label("Model");
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - EditorScale.of(PAD));
        Paint.pushMono();
        boolean changed = ImGui.inputTextWithHint("##anim-view-model-path", "res://models/arms.bbmodel", model);
        Paint.popMono();
        if (changed) {
            String path = model.get().strip();
            session.edit("Set view model", "anim-view-model-path", edited -> {
                AnimClip.ViewModel old = edited.view;
                edited.view = new AnimClip.ViewModel(path, old.sway(), old.bob(), old.recoil(), old.inspect());
            });
        }
        Sections.caption("Empty uses the player's own skin arms.");
        ImGui.dummy(0, EditorScale.of(2));
        label("Held item");
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - EditorScale.of(PAD));
        if (ImGui.inputTextWithHint("##anim-view-held", "minecraft:lantern", held)) rig.heldItem(held.get().strip());
        ImGui.dummy(0, EditorScale.of(4));
        Sections.caption("PROCEDURAL LAYERS");
        AnimClip.ViewModel view = clip.view;
        double sway = slider("Sway", "anim-view-sway", view.sway());
        double bob = slider("Bob", "anim-view-bob", view.bob());
        double recoil = slider("Recoil on camera bone", "anim-view-recoil", view.recoil());
        double inspect = slider("Inspect idle", "anim-view-inspect", view.inspect());
        if (sway != view.sway() || bob != view.bob() || recoil != view.recoil() || inspect != view.inspect()) {
            session.edit("Set view layers", "anim-view-layers", edited -> {
                edited.view = new AnimClip.ViewModel(edited.view.model(), sway, bob, recoil, inspect);
            });
        }
        ImGui.dummy(0, EditorScale.of(4));
        Notices.info("View clips play on body.viewModel. Other players see the body clip with the same name.");
        ImGui.dummy(0, EditorScale.of(6));
    }

    private static double slider(String text, String id, double value) {
        float width = ImGui.getContentRegionAvailX() - EditorScale.of(PAD);
        Texts.plain(text);
        ImGui.sameLine(ImGui.getContentRegionMaxX() - Paint.monoWidth("0.00") - EditorScale.of(PAD));
        Paint.pushMono();
        Texts.muted(String.format(Locale.ROOT, "%.2f", value));
        Paint.popMono();
        float updated = NumberFields.ranged("##" + id, (float) value, 0.01f, width, 0, 1);
        return Math.clamp(Math.round(updated * 100) / 100.0, 0, 1);
    }

    private void renderClip(AnimClip clip) {
        if (!header("Clip", "")) return;
        label("Length");
        float length = NumberFields.scalar("##anim-clip-length", (float) clip.length, 1f / clip.fps, ImGui.getContentRegionAvailX() - EditorScale.of(PAD));
        if (Math.abs(length - clip.length) > 1e-6) {
            double changed = Math.max(1.0 / clip.fps, session.snap() ? clip.snap(length) : length);
            session.edit("Set length", "anim-clip-length", edited -> edited.length = changed);
        }
        label("Frame rate");
        float fps = NumberFields.scalar("##anim-clip-fps", clip.fps, 1, ImGui.getContentRegionAvailX() - EditorScale.of(PAD));
        if (Math.round(fps) != clip.fps && fps >= 1) {
            int rate = Math.clamp(Math.round(fps), 1, 240);
            session.edit("Set frame rate", "anim-clip-fps", edited -> edited.fps = rate);
        }
        label("Playback");
        int loop = segmented("anim-clip-loop", List.of("Loop", "Once", "Hold"), clip.loop.ordinal());
        if (loop != clip.loop.ordinal()) {
            AnimClip.Loop chosen = AnimClip.Loop.values()[loop];
            session.edit("Set playback", edited -> edited.loop = chosen);
        }
        label("Priority");
        renderPriority(clip);
        label("Blend");
        int blend = segmented("anim-clip-blend", List.of("Normal", "Additive"), clip.blend.ordinal());
        if (blend != clip.blend.ordinal()) {
            AnimClip.Blend chosen = AnimClip.Blend.values()[blend];
            session.edit("Set blend", edited -> edited.blend = chosen);
        }
        label("Mask");
        renderMask(clip);
    }

    private void renderPriority(AnimClip clip) {
        String shown = ClipFile.isNumber(clip.priority) ? clip.priority : Character.toUpperCase(clip.priority.charAt(0)) + clip.priority.substring(1);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - EditorScale.of(PAD));
        if (!ImGui.beginCombo("##anim-clip-priority", shown)) return;
        for (String name : AnimClip.PRIORITIES) {
            if (ImGui.selectable(Character.toUpperCase(name.charAt(0)) + name.substring(1), name.equals(clip.priority))) {
                session.edit("Set priority", edited -> edited.priority = name);
            }
        }
        ImGui.separator();
        ImGui.setNextItemWidth(EditorScale.of(120));
        if (ImGui.inputTextWithHint("##anim-clip-priority-number", "number", priority, ImGuiInputTextFlags.CharsDecimal | ImGuiInputTextFlags.EnterReturnsTrue)
                && ClipFile.isNumber(priority.get().strip())) {
            String number = ClipFile.number(Double.parseDouble(priority.get().strip()));
            session.edit("Set priority", edited -> edited.priority = number);
            ImGui.closeCurrentPopup();
        }
        ImGui.endCombo();
    }

    private void renderMask(AnimClip clip) {
        float available = ImGui.getContentRegionAvailX() - EditorScale.of(PAD);
        float start = ImGui.getCursorPosX();
        float used = 0;
        String removed = null;
        ImDrawList draw = ImGui.getWindowDrawList();
        List<String> pills = new ArrayList<>(clip.mask);
        if (pills.isEmpty()) {
            ImGui.alignTextToFramePadding();
            Texts.colored(EditorStyle.COLOR_TEXT_FAINT, "every joint");
            ImGui.sameLine();
            used = ImGui.calcTextSizeX("every joint") + EditorStyle.itemSpacingX();
        }
        for (String joint : pills) {
            float width = ImGui.calcTextSizeX(joint) + EditorScale.of(22);
            if (used > 0 && used + width > available) {
                ImGui.newLine();
                used = 0;
            }
            if (used > 0) ImGui.sameLine(start + used);
            float left = ImGui.getCursorScreenPosX();
            float top = ImGui.getCursorScreenPosY();
            float height = ImGui.getFrameHeight();
            if (ImGui.invisibleButton("##mask-" + joint, width, height)) removed = joint;
            boolean hovered = ImGui.isItemHovered();
            int fill = hovered ? EditorStyle.COLOR_WIDGET_HOVER : EditorStyle.COLOR_WIDGET_BACKGROUND;
            draw.addRectFilled(left, top, left + width, top + height, fill, height * 0.5f);
            String shown = hovered ? joint + " ×" : joint;
            draw.addText(left + EditorScale.of(10), top + (height - ImGui.getTextLineHeight()) * 0.5f, EditorStyle.COLOR_TEXT, shown);
            if (hovered) ImGui.setTooltip("Remove " + joint + " from the mask");
            used += width + EditorStyle.itemSpacingX();
        }
        String add = "+ joint";
        float width = ImGui.calcTextSizeX(add) + EditorScale.of(20);
        if (used > 0 && used + width > available) {
            ImGui.newLine();
            used = 0;
        }
        if (used > 0) ImGui.sameLine(start + used);
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        float height = ImGui.getFrameHeight();
        if (ImGui.invisibleButton("##mask-add", width, height)) ImGui.openPopup("##anim-mask-add");
        boolean hovered = ImGui.isItemHovered();
        draw.addRect(left, top, left + width, top + height, hovered ? EditorStyle.COLOR_TEXT_MUTED : EditorStyle.COLOR_WIDGET_OUTLINE, height * 0.5f);
        draw.addText(left + EditorScale.of(10), top + (height - ImGui.getTextLineHeight()) * 0.5f, EditorStyle.COLOR_TEXT_MUTED, add);
        if (ImGui.beginPopup("##anim-mask-add")) {
            for (String joint : Rigs.names(clip)) {
                if (clip.mask.contains(joint)) continue;
                if (ImGui.menuItem(joint)) session.edit("Mask " + joint, edited -> edited.mask.add(joint));
            }
            ImGui.endPopup();
        }
        if (removed != null) {
            String gone = removed;
            session.edit("Unmask " + gone, edited -> edited.mask.remove(gone));
        }
        Sections.caption(clip.mask.isEmpty() ? "The clip moves every joint it has keys for" : "Only these joints are moved when the clip plays");
    }
}
