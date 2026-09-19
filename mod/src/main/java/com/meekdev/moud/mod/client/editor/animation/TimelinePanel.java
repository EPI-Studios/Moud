package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.character.ClipCurve;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.client.editor.kit.EmptyStates;
import com.meekdev.moud.mod.client.editor.kit.SegmentedControl;
import com.meekdev.moud.mod.client.editor.kit.Sections;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.kit.Toggles;
import com.meekdev.moud.mod.client.editor.kit.Toolbars;
import com.meekdev.moud.mod.client.editor.panel.Panel;
import com.meekdev.moud.mod.client.editor.style.EditorIcon;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class TimelinePanel implements Panel {

    static final String ID = "anim-timeline";

    private static final float RULER = 24.0f;
    private static final float ROW = 24.0f;
    private static final float NAMES = 240.0f;
    private static final float LEAD = 18.0f;
    private static final float KEY_RADIUS = 5.0f;
    private static final float DRAG_START = 3.0f;
    private static final float HIT_SLACK = 2.0f;
    private static final double[] STEPS = {0.05, 0.1, 0.2, 0.25, 0.5, 1, 2, 5, 10};
    private static final int[] RATES = {10, 12, 20, 24, 30, 60};
    private static final String CONTEXT = "##anim-timeline-context";

    private enum Kind { MARKERS, EVENTS, JOINT, CHANNEL }

    private record Lane(Kind kind, String joint, @Nullable Channel channel, int depth, String label) {}

    private record Hit(String joint, @Nullable Channel channel, double time) {}

    private enum Drag { NONE, SCRUB, KEYS, BOX, MARKER, EVENT, END, CURVE_POINT, CURVE_HANDLE }

    private final AnimationWorkspace workspace;
    private final AnimationSession session;
    private final IconWidgets icons;
    private final Set<String> hiddenCurves = new LinkedHashSet<>();
    private int tab;
    private float pixelsPerSecond;
    private double viewStart;
    private @Nullable Path fittedPath;
    private Drag drag = Drag.NONE;
    private float pressX;
    private float pressY;
    private boolean moved;
    private @Nullable AnimClip dragStart;
    private String dragGesture = "";
    private Set<KeyRef> dragRefs = Set.of();
    private int dragIndex = -1;
    private double dragOffset;
    private @Nullable KeyRef curveKey;
    private int curveAxis;
    private boolean curveOut;
    private double contextTime;
    private @Nullable Lane contextLane;
    private boolean contextOnKey;
    private float left;
    private float lanesLeft;
    private float right;
    private float valueTop;
    private float valueBottom;
    private double valueMin;
    private double valueMax;

    TimelinePanel(AnimationWorkspace workspace, AnimationSession session, IconWidgets icons) {
        this.workspace = workspace;
        this.session = session;
        this.icons = icons;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Timeline";
    }

    @Override
    public int windowFlags() {
        return ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
    }

    @Override
    public void render() {
        renderToolbar();
        if (!session.hasClip()) {
            EmptyStates.centered("No clip open", List.of("Pick a clip in the Clips list, or make one with the + button"));
            return;
        }
        ImGui.pushStyleColor(ImGuiCol.ChildBg, EditorStyle.COLOR_SUNKEN_BACKGROUND);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);
        ImGui.beginChild("##anim-timeline-canvas", 0, 0, false, ImGuiWindowFlags.NoScrollWithMouse);
        if (tab == 0) renderDopeSheet();
        else renderCurves();
        renderContext();
        ImGui.endChild();
        ImGui.popStyleVar();
        ImGui.popStyleColor();
    }

    private void renderToolbar() {
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, EditorStyle.itemSpacingX(), 0.0f);
        float line = ImGui.getCursorPosY();
        ImGui.setCursorPosY(line + (Toolbars.buttonHeight() - SegmentedControl.height()) * 0.5f);
        tab = SegmentedControl.render("anim-timeline-tab", List.of("Dope sheet", "Curves"), tab);
        ImGui.setCursorPosY(line);
        Toolbars.pushFlatButtons();
        Toolbars.groupSeparator();
        AnimClip clip = session.clip();
        ImGui.alignTextToFramePadding();
        Texts.muted("Snap");
        ImGui.sameLine();
        Paint.pushMono();
        boolean snapClicked = ImGui.button((session.snap() ? "1 / " + clip.fps + " s" : "off") + "##anim-snap", 0, Toolbars.buttonHeight());
        Paint.popMono();
        tip("Keys and the playhead land on frames of the clip's rate");
        if (snapClicked) ImGui.openPopup("##anim-snap-popup");
        renderSnapPopup();
        ImGui.sameLine(0, EditorScale.of(10));
        ImGui.alignTextToFramePadding();
        Texts.muted("Onion");
        ImGui.sameLine();
        Paint.pushMono();
        String onion = session.onion() ? session.onionBefore() + " · " + session.onionAfter() : "off";
        boolean onionClicked = ImGui.button(onion + "##anim-onion", 0, Toolbars.buttonHeight());
        Paint.popMono();
        tip("Ghosts of the keys before and after the playhead");
        if (onionClicked) ImGui.openPopup("##anim-onion-popup");
        renderOnionPopup();
        Toolbars.groupSeparator();
        ImGui.beginDisabled(!session.hasClip());
        if (icons.iconButton("anim-mirror", EditorIcon.MIRROR_X, EditorStyle.iconSizeToolbar())) session.mirrorSelected();
        tip("Mirror L / R: copy the selected keys to the other side, flipped (M mirrors the selected bone's pose)");
        ImGui.sameLine();
        if (icons.iconButton("anim-add-event", EditorIcon.SIGNAL, EditorStyle.iconSizeToolbar())) addEvent(session.keyTime());
        tip("Add a script event at the playhead");
        ImGui.sameLine();
        if (icons.iconButton("anim-add-marker", EditorIcon.MARKER, EditorStyle.iconSizeToolbar())) addMarker(session.keyTime());
        tip("Add a marker at the playhead");
        ImGui.endDisabled();
        Toolbars.popFlatButtons();
        String selected = selectedCount(session.keys().size());
        if (!selected.isEmpty()) {
            ImGui.sameLine(ImGui.getWindowWidth() - ImGui.calcTextSizeX(selected) - EditorStyle.windowPadding() * 2);
            ImGui.alignTextToFramePadding();
            Texts.colored(EditorStyle.COLOR_TEXT_FAINT, selected);
        } else {
            ImGui.sameLine();
            ImGui.dummy(0, Toolbars.buttonHeight());
        }
        ImGui.popStyleVar();
        ImGui.dummy(0, EditorScale.of(4));
    }

    private void renderSnapPopup() {
        if (!ImGui.beginPopup("##anim-snap-popup")) return;
        if (ImGui.menuItem("Snap to frames", "", session.snap())) session.snap(!session.snap());
        ImGui.separator();
        Texts.muted("Frame rate of the clip");
        for (int rate : RATES) {
            if (ImGui.menuItem(rate + " fps", "", session.clip().fps == rate)) session.edit("Set frame rate", changed -> changed.fps = rate);
        }
        ImGui.endPopup();
    }

    private void renderOnionPopup() {
        if (!ImGui.beginPopup("##anim-onion-popup")) return;
        if (ImGui.menuItem("Show onion skin", "", session.onion())) session.onion(!session.onion());
        ImGui.separator();
        int[] before = {session.onionBefore()};
        int[] after = {session.onionAfter()};
        ImGui.setNextItemWidth(EditorScale.of(140));
        if (ImGui.sliderInt("Keys before##anim-onion-before", before, 0, 5)) session.onionCounts(before[0], after[0]);
        ImGui.setNextItemWidth(EditorScale.of(140));
        if (ImGui.sliderInt("Keys after##anim-onion-after", after, 0, 5)) session.onionCounts(before[0], after[0]);
        ImGui.endPopup();
    }

    private List<Lane> lanes(AnimClip clip) {
        List<Lane> lanes = new ArrayList<>();
        lanes.add(new Lane(Kind.MARKERS, "", null, 0, "Markers"));
        lanes.add(new Lane(Kind.EVENTS, "", null, 0, "Script events"));
        for (Skeletons.Joint joint : workspace.skeleton()) {
            lanes.add(new Lane(Kind.JOINT, joint.name(), null, 0, joint.name()));
            if (!session.expanded().contains(joint.name())) continue;
            for (Channel channel : Channel.values()) lanes.add(new Lane(Kind.CHANNEL, joint.name(), channel, 1, channel.key()));
        }
        return lanes;
    }

    private void fit(AnimClip clip, float width) {
        Path path = session.path();
        if (path != null && path.equals(fittedPath) && pixelsPerSecond > 0) return;
        fittedPath = path;
        double span = Math.max(0.5, Math.max(clip.length, clip.lastKeyTime()) * 1.25);
        pixelsPerSecond = (float) Math.max(20, (width - EditorScale.of(LEAD) * 2) / span);
        viewStart = -EditorScale.of(LEAD) / pixelsPerSecond;
    }

    private float x(double time) {
        return lanesLeft + (float) ((time - viewStart) * pixelsPerSecond);
    }

    private double time(float screenX) {
        return viewStart + (screenX - lanesLeft) / pixelsPerSecond;
    }

    private void layout() {
        left = ImGui.getWindowPosX();
        right = left + ImGui.getWindowWidth();
        lanesLeft = left + EditorScale.of(NAMES);
    }

    private void handleZoom(boolean hovered) {
        if (!hovered) return;
        float wheel = ImGui.getIO().getMouseWheel();
        if (wheel == 0) return;
        float mouseX = ImGui.getMousePosX();
        if (ImGui.getIO().getKeyCtrl() || tab == 1 && !ImGui.getIO().getKeyShift()) {
            double anchor = time(Math.max(mouseX, lanesLeft));
            pixelsPerSecond = (float) Math.clamp(pixelsPerSecond * Math.pow(1.15, wheel), 10, 4000);
            viewStart = anchor - (Math.max(mouseX, lanesLeft) - lanesLeft) / pixelsPerSecond;
        } else if (ImGui.getIO().getKeyShift()) {
            viewStart -= wheel * 40 / pixelsPerSecond;
        } else {
            ImGui.setScrollY(Math.max(0, ImGui.getScrollY() - wheel * EditorScale.of(ROW) * 3));
        }
    }

    private void renderDopeSheet() {
        AnimClip clip = session.clip();
        layout();
        fit(clip, right - lanesLeft);
        List<Lane> lanes = lanes(clip);
        float ruler = EditorScale.of(RULER);
        float row = EditorScale.of(ROW);
        float windowTop = ImGui.getWindowPosY();
        float scroll = ImGui.getScrollY();
        float rowsTop = windowTop + ruler - scroll;
        ImGui.dummy(right - left, ruler + row * lanes.size() + EditorScale.of(8));
        boolean hovered = ImGui.isWindowHovered(ImGuiHoveredFlags.AllowWhenBlockedByActiveItem);
        handleZoom(hovered);
        ImDrawList draw = ImGui.getWindowDrawList();
        float bottom = windowTop + ImGui.getWindowHeight();
        draw.addRectFilled(x(0), windowTop + ruler, Math.max(x(0), x(clip.length)), bottom, Paint.LANE);
        Hit hoveredKey = null;
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        for (int n = 0; n < lanes.size(); n++) {
            float top = rowsTop + row * n;
            if (top + row < windowTop + ruler || top > bottom) continue;
            Lane lane = lanes.get(n);
            boolean selectedJoint = lane.kind() == Kind.JOINT && lane.joint().equals(session.joint());
            if (selectedJoint) draw.addRectFilled(left, top, right, top + row, EditorStyle.withAlpha(EditorStyle.COLOR_ACCENT, 0.07f));
            draw.addLine(left, top + row - 1, right, top + row - 1, EditorStyle.withAlpha(EditorStyle.rgb(0, 0, 0), 0.25f));
            Hit hit = drawLane(draw, clip, lane, top, row, mouseX, mouseY, hovered);
            if (hit != null) hoveredKey = hit;
        }
        drawNames(draw, clip, lanes, rowsTop, row, windowTop + ruler, bottom, hovered);
        drawEnd(draw, clip, windowTop + ruler, bottom);
        drawRuler(draw, clip, windowTop, ruler);
        drawPlayhead(draw, windowTop, ruler, bottom);
        if (drag == Drag.BOX) drawBox(draw);
        handleDopeInput(clip, lanes, rowsTop, row, windowTop, ruler, hovered, hoveredKey);
    }

    private @Nullable Hit drawLane(ImDrawList draw, AnimClip clip, Lane lane, float top, float row, float mouseX, float mouseY, boolean hovered) {
        float middle = top + row * 0.5f;
        float radius = EditorScale.of(KEY_RADIUS);
        draw.pushClipRect(lanesLeft, top, right, top + row, true);
        Hit found = null;
        switch (lane.kind()) {
            case MARKERS -> {
                for (int n = 0; n < clip.markers.size(); n++) {
                    AnimClip.Marker marker = clip.markers.get(n);
                    float at = x(marker.time());
                    boolean selected = n == session.marker();
                    int color = selected ? Paint.SELECTED : EditorStyle.COLOR_TEXT_MUTED;
                    draw.addRectFilled(at - 1, top + EditorScale.of(3), at + 1, top + row - EditorScale.of(3), color);
                    String label = marker.name().isEmpty() ? "marker" : marker.name();
                    float chipTop = top + EditorScale.of(3);
                    float chipHeight = row - EditorScale.of(6);
                    int fill = selected ? EditorStyle.withAlpha(Paint.SELECTED, 0.25f) : EditorStyle.COLOR_WIDGET_BACKGROUND;
                    draw.addRectFilled(at + 1, chipTop, at + 1 + Paint.chipWidth(label), chipTop + chipHeight, fill, EditorScale.of(3));
                    float textTop = chipTop + (chipHeight - Paint.monoSize()) * 0.5f;
                    Paint.mono(draw, at + 1 + EditorScale.of(6), textTop, color, label);
                }
            }
            case EVENTS -> {
                for (int n = 0; n < clip.events.size(); n++) {
                    AnimClip.Event event = clip.events.get(n);
                    float at = x(event.time());
                    boolean selected = n == session.event();
                    String label = "{ } " + event.name();
                    float chipTop = top + EditorScale.of(3);
                    Paint.chip(draw, at - EditorScale.of(3), chipTop, row - EditorScale.of(6), label, Paint.EVENT, selected);
                }
            }
            case JOINT -> {
                List<Double> times = jointTimes(clip, lane.joint());
                for (double time : times) {
                    float at = x(time);
                    boolean selected = anySelectedAt(lane.joint(), time);
                    boolean over = hovered && near(mouseX, mouseY, at, middle, radius);
                    Paint.diamond(draw, at, middle, radius, keyColor(selected, over, Paint.KEY_MUTED));
                    if (over) found = new Hit(lane.joint(), null, time);
                }
            }
            case CHANNEL -> {
                Channel channel = lane.channel();
                for (AnimKey key : clip.keys(lane.joint(), channel)) {
                    float at = x(key.time());
                    boolean selected = session.keys().contains(new KeyRef(lane.joint(), channel, key.time()));
                    boolean over = hovered && near(mouseX, mouseY, at, middle, radius);
                    Paint.diamond(draw, at, middle, radius, keyColor(selected, over, Paint.KEY));
                    if (key.interp() == Interp.STEP) draw.addRectFilled(at + radius + 1, middle - 1, at + radius + 4, middle + 1, Paint.KEY_MUTED);
                    if (over) found = new Hit(lane.joint(), channel, key.time());
                }
            }
        }
        draw.popClipRect();
        return found;
    }

    private void drawNames(ImDrawList draw, AnimClip clip, List<Lane> lanes, float rowsTop, float row, float clipTop, float bottom, boolean hovered) {
        draw.pushClipRect(left, clipTop, lanesLeft, bottom, true);
        draw.addRectFilled(left, clipTop, lanesLeft, bottom, EditorStyle.COLOR_PANEL_BACKGROUND);
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        for (int n = 0; n < lanes.size(); n++) {
            float top = rowsTop + row * n;
            if (top + row < clipTop || top > bottom) continue;
            Lane lane = lanes.get(n);
            boolean selected = lane.kind() == Kind.JOINT && lane.joint().equals(session.joint());
            if (selected) draw.addRectFilled(left, top, lanesLeft, top + row, EditorStyle.withAlpha(EditorStyle.COLOR_ACCENT, 0.1f));
            float x = left + EditorScale.of(12) + lane.depth() * EditorScale.of(14);
            float middle = top + row * 0.5f;
            int text = nameColor(clip, lane, selected);
            draw.addText(x, middle - ImGui.getTextLineHeight() * 0.5f, text, lane.label());
            if (lane.kind() == Kind.JOINT) {
                boolean open = session.expanded().contains(lane.joint());
                float arrowX = lanesLeft - EditorScale.of(16);
                float reach = EditorScale.of(3.5f);
                boolean overRow = hovered && mouseY >= top && mouseY < top + row && mouseX >= left && mouseX < lanesLeft;
                int arrow = overRow ? EditorStyle.COLOR_TEXT : EditorStyle.COLOR_TEXT_FAINT;
                if (open) draw.addTriangleFilled(arrowX - reach, middle - reach * 0.6f, arrowX + reach, middle - reach * 0.6f, arrowX, middle + reach, arrow);
                else draw.addTriangleFilled(arrowX - reach * 0.6f, middle - reach, arrowX - reach * 0.6f, middle + reach, arrowX + reach, middle, arrow);
            }
        }
        draw.popClipRect();
        draw.addLine(lanesLeft, clipTop, lanesLeft, bottom, EditorStyle.COLOR_OUTLINE);
    }

    private static int nameColor(AnimClip clip, Lane lane, boolean selected) {
        if (lane.kind() == Kind.CHANNEL) return EditorStyle.COLOR_TEXT_MUTED;
        if (selected) return EditorStyle.COLOR_TEXT_FOCUS;
        if (lane.kind() == Kind.JOINT && clip.keyCount(lane.joint()) == 0) return EditorStyle.COLOR_TEXT_MUTED;
        return EditorStyle.COLOR_TEXT;
    }

    private void drawRuler(ImDrawList draw, AnimClip clip, float top, float height) {
        draw.addRectFilled(left, top, right, top + height, EditorStyle.COLOR_HEADER_BACKGROUND);
        draw.addLine(left, top + height - 1, right, top + height - 1, EditorStyle.COLOR_OUTLINE);
        draw.pushClipRect(lanesLeft, top, right, top + height, true);
        double step = STEPS[STEPS.length - 1];
        for (double candidate : STEPS) {
            if (candidate * pixelsPerSecond >= EditorScale.of(56)) {
                step = candidate;
                break;
            }
        }
        double first = Math.floor(time(lanesLeft) / step) * step;
        double last = time(right);
        for (double at = Math.max(0, first); at <= last + step; at += step) {
            float x = x(at);
            draw.addLine(x, top + height - EditorScale.of(8), x, top + height - 1, EditorStyle.COLOR_TEXT_FAINT);
            float half = x(at + step * 0.5);
            draw.addLine(half, top + height - EditorScale.of(4), half, top + height - 1, EditorStyle.withAlpha(EditorStyle.COLOR_TEXT_FAINT, 0.5f));
            String label = step < 0.1 ? String.format(Locale.ROOT, "%.2f", at) : String.format(Locale.ROOT, "%.1f", at);
            Paint.small(draw, x + EditorScale.of(3), top + EditorScale.of(3), EditorStyle.COLOR_TEXT_MUTED, label);
        }
        float end = x(clip.length);
        float reach = EditorScale.of(4);
        float base = top + height - reach * 2;
        draw.addTriangleFilled(end - reach, base, end + reach, base, end, top + height - 1, EditorStyle.COLOR_TEXT_MUTED);
        draw.popClipRect();
        String length = clip.length > 0 ? Paint.seconds(clip.length) + " s" : "";
        draw.addText(left + EditorScale.of(12), top + (height - ImGui.getTextLineHeight()) * 0.5f, EditorStyle.COLOR_TEXT_FAINT, length);
    }

    private void drawEnd(ImDrawList draw, AnimClip clip, float top, float bottom) {
        float end = x(clip.length);
        if (end < lanesLeft) return;
        draw.addLine(end, top, end, bottom, EditorStyle.COLOR_WIDGET_OUTLINE, EditorScale.ofAtLeastOne(1));
    }

    private void drawPlayhead(ImDrawList draw, float top, float ruler, float bottom) {
        float at = x(session.time());
        if (at < lanesLeft - 1) return;
        draw.pushClipRect(lanesLeft, top, right, bottom, true);
        draw.addRectFilled(at - 1, top + ruler, at + 1, bottom, Paint.SELECTED);
        float half = EditorScale.of(9);
        draw.addRectFilled(at - half, top + EditorScale.of(3), at + half, top + ruler - EditorScale.of(5), Paint.SELECTED, EditorScale.of(3));
        draw.popClipRect();
    }

    private void drawBox(ImDrawList draw) {
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        draw.addRectFilled(Math.min(pressX, mouseX), Math.min(pressY, mouseY), Math.max(pressX, mouseX), Math.max(pressY, mouseY),
                EditorStyle.withAlpha(EditorStyle.COLOR_ACCENT, 0.08f));
        draw.addRect(Math.min(pressX, mouseX), Math.min(pressY, mouseY), Math.max(pressX, mouseX), Math.max(pressY, mouseY),
                EditorStyle.withAlpha(EditorStyle.COLOR_ACCENT, 0.7f));
    }

    private void handleDopeInput(AnimClip clip, List<Lane> lanes, float rowsTop, float row, float top, float ruler, boolean hovered, @Nullable Hit key) {
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        boolean inRuler = mouseY >= top && mouseY < top + ruler && mouseX >= lanesLeft;
        boolean inLanes = mouseY >= top + ruler && mouseX >= lanesLeft;
        boolean inNames = mouseY >= top + ruler && mouseX < lanesLeft && mouseX >= left;
        int laneIndex = (int) Math.floor((mouseY - rowsTop) / row);
        Lane lane = laneIndex >= 0 && laneIndex < lanes.size() && mouseY >= top + ruler ? lanes.get(laneIndex) : null;
        if (hovered && inRuler && Math.abs(mouseX - x(clip.length)) < EditorScale.of(5)) ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
        if (hovered && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            pressX = mouseX;
            pressY = mouseY;
            moved = false;
            if (inRuler && Math.abs(mouseX - x(clip.length)) < EditorScale.of(5)) beginDrag(Drag.END, "length");
            else if (inRuler) {
                drag = Drag.SCRUB;
                session.stop();
                scrubTo(time(mouseX));
            } else if (inNames && lane != null) {
                clickName(lane, mouseX);
            } else if (inLanes && lane != null) {
                pressLane(clip, lane, key, mouseX);
            }
        }
        if (hovered && ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left) && inLanes && lane != null && key == null) {
            doubleClick(lane, session.snap() ? clip.snap(time(mouseX)) : time(mouseX));
        }
        if (hovered && ImGui.isMouseClicked(ImGuiMouseButton.Right) && (inLanes || inNames) && lane != null) {
            if (key != null && !isSelected(key)) session.selectKeys(refs(clip, key));
            contextLane = lane;
            contextOnKey = key != null;
            contextTime = session.snap() ? clip.snap(time(Math.max(mouseX, lanesLeft))) : time(Math.max(mouseX, lanesLeft));
            ImGui.openPopup(CONTEXT);
        }
        updateDrag(clip, lanes, rowsTop, row, mouseX, mouseY);
    }

    private void clickName(Lane lane, float mouseX) {
        if (lane.kind() != Kind.JOINT && lane.kind() != Kind.CHANNEL) return;
        session.joint(lane.joint());
        if (lane.kind() == Kind.JOINT && (mouseX > lanesLeft - EditorScale.of(28) || ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left))) {
            if (!session.expanded().remove(lane.joint())) session.expanded().add(lane.joint());
        }
    }

    private void pressLane(AnimClip clip, Lane lane, @Nullable Hit key, float mouseX) {
        boolean additive = ImGui.getIO().getKeyCtrl() || ImGui.getIO().getKeyShift();
        if (lane.kind() == Kind.MARKERS || lane.kind() == Kind.EVENTS) {
            int index = lane.kind() == Kind.MARKERS ? markerAt(clip, mouseX) : eventAt(clip, mouseX);
            if (index >= 0) {
                if (lane.kind() == Kind.MARKERS) session.marker(index);
                else session.event(index);
                dragIndex = index;
                if (lane.kind() == Kind.MARKERS) {
                    dragOffset = time(mouseX) - clip.markers.get(index).time();
                    beginDrag(Drag.MARKER, "marker");
                } else {
                    dragOffset = time(mouseX) - clip.events.get(index).time();
                    beginDrag(Drag.EVENT, "event");
                }
                return;
            }
            if (!additive) {
                session.selectKeys(Set.of());
                session.event(-1);
                session.marker(-1);
            }
            drag = Drag.BOX;
            return;
        }
        session.joint(lane.joint());
        if (key != null) {
            Set<KeyRef> hit = refs(clip, key);
            if (additive) {
                Set<KeyRef> chosen = new LinkedHashSet<>(session.keys());
                if (chosen.containsAll(hit)) chosen.removeAll(hit);
                else chosen.addAll(hit);
                session.selectKeys(chosen);
            } else if (!session.keys().containsAll(hit)) {
                session.selectKeys(hit);
            }
            if (!session.keys().isEmpty()) {
                dragRefs = Set.copyOf(session.keys());
                beginDrag(Drag.KEYS, "keys");
            }
            return;
        }
        if (!additive) session.selectKeys(Set.of());
        drag = Drag.BOX;
    }

    private void beginDrag(Drag kind, String gesture) {
        drag = kind;
        dragStart = session.clip().copy();
        dragGesture = session.gesture(gesture);
    }

    private void updateDrag(AnimClip clip, List<Lane> lanes, float rowsTop, float row, float mouseX, float mouseY) {
        if (drag == Drag.NONE) return;
        if (Math.abs(mouseX - pressX) > EditorScale.of(DRAG_START) || Math.abs(mouseY - pressY) > EditorScale.of(DRAG_START)) moved = true;
        boolean held = ImGui.isMouseDown(ImGuiMouseButton.Left);
        switch (drag) {
            case SCRUB -> scrubTo(time(mouseX));
            case END -> {
                if (moved && dragStart != null) {
                    double length = Math.max(1.0 / Math.max(1, clip.fps), session.snap() ? clip.snap(time(mouseX)) : time(mouseX));
                    session.editFrom(dragStart, "Set length", dragGesture, changed -> changed.length = length);
                }
            }
            case KEYS -> {
                if (moved && dragStart != null) {
                    double delta = time(mouseX) - time(pressX);
                    if (session.snap()) delta = Math.round(delta * clip.fps) / (double) clip.fps;
                    double shift = delta;
                    Set<KeyRef> placed = new LinkedHashSet<>();
                    session.editFrom(dragStart, "Move keys", dragGesture, changed -> placed.addAll(KeyEdits.move(changed, dragRefs, shift)));
                    session.selectKeys(placed);
                }
            }
            case MARKER, EVENT -> {
                if (moved && dragStart != null) {
                    double at = Math.max(0, time(mouseX) - dragOffset);
                    double placed = session.snap() ? clip.snap(at) : at;
                    int index = dragIndex;
                    boolean marker = drag == Drag.MARKER;
                    session.editFrom(dragStart, marker ? "Move marker" : "Move event", dragGesture, changed -> {
                        if (marker && index < changed.markers.size()) changed.markers.set(index, changed.markers.get(index).at(placed));
                        if (!marker && index < changed.events.size()) changed.events.set(index, changed.events.get(index).at(placed));
                    });
                }
            }
            case BOX -> {
                if (!held) finishBox(clip, lanes, rowsTop, row, mouseX, mouseY);
            }
            default -> { }
        }
        if (!held) {
            if ((drag == Drag.MARKER || drag == Drag.EVENT) && moved) resortTimed(drag == Drag.MARKER);
            drag = Drag.NONE;
            dragStart = null;
        }
    }

    private void resortTimed(boolean markers) {
        AnimClip clip = session.clip();
        Object chosen = markers ? chosen(clip.markers, session.marker()) : chosen(clip.events, session.event());
        clip.sortTimed();
        if (chosen == null) return;
        if (markers) session.marker(clip.markers.indexOf(chosen));
        else session.event(clip.events.indexOf(chosen));
    }

    private static @Nullable Object chosen(List<?> items, int index) {
        if (index < 0 || index >= items.size()) return null;
        return items.get(index);
    }

    private void finishBox(AnimClip clip, List<Lane> lanes, float rowsTop, float row, float mouseX, float mouseY) {
        if (!moved) return;
        float x0 = Math.min(pressX, mouseX);
        float x1 = Math.max(pressX, mouseX);
        float y0 = Math.min(pressY, mouseY);
        float y1 = Math.max(pressY, mouseY);
        Set<KeyRef> chosen = new LinkedHashSet<>(ImGui.getIO().getKeyCtrl() || ImGui.getIO().getKeyShift() ? session.keys() : Set.of());
        for (int n = 0; n < lanes.size(); n++) {
            float middle = rowsTop + row * n + row * 0.5f;
            if (middle < y0 || middle > y1) continue;
            Lane lane = lanes.get(n);
            if (lane.kind() == Kind.JOINT) {
                for (double at : jointTimes(clip, lane.joint())) {
                    if (x(at) >= x0 && x(at) <= x1) chosen.addAll(refs(clip, new Hit(lane.joint(), null, at)));
                }
            } else if (lane.kind() == Kind.CHANNEL) {
                for (AnimKey key : clip.keys(lane.joint(), lane.channel())) {
                    if (x(key.time()) >= x0 && x(key.time()) <= x1) chosen.add(new KeyRef(lane.joint(), lane.channel(), key.time()));
                }
            }
        }
        session.selectKeys(chosen);
    }

    private void scrubTo(double to) {
        double before = session.time();
        session.seek(to);
        workspace.scrubbed(before, session.time());
    }

    private void doubleClick(Lane lane, double at) {
        switch (lane.kind()) {
            case MARKERS -> addMarker(at);
            case EVENTS -> addEvent(at);
            case JOINT -> addKey(lane.joint(), Channel.ROTATION, at);
            case CHANNEL -> addKey(lane.joint(), lane.channel(), at);
        }
    }

    void addKey(String joint, Channel channel, double at) {
        Vector3 value = Curves.sample(session.clip().keys(joint, channel), at, channel.rest());
        KeyRef[] placed = new KeyRef[1];
        session.edit("Add key", changed -> placed[0] = KeyEdits.set(changed, joint, channel, at, value));
        session.joint(joint);
        if (placed[0] != null) session.selectKeys(Set.of(placed[0]));
    }

    void addMarker(double at) {
        int[] index = {-1};
        session.edit("Add marker", changed -> {
            changed.markers.add(new AnimClip.Marker(at, "marker", ""));
            changed.sortTimed();
            for (int n = 0; n < changed.markers.size(); n++) {
                if (Math.abs(changed.markers.get(n).time() - at) < AnimClip.EPSILON) index[0] = n;
            }
        });
        session.marker(index[0]);
    }

    void addEvent(double at) {
        int[] index = {-1};
        session.edit("Add event", changed -> {
            changed.events.add(new AnimClip.Event(at, uniqueEventName(changed), AnimClip.Side.BOTH, Map.of(), "", "", false));
            changed.sortTimed();
            for (int n = 0; n < changed.events.size(); n++) {
                if (Math.abs(changed.events.get(n).time() - at) < AnimClip.EPSILON) index[0] = n;
            }
        });
        session.event(index[0]);
    }

    private static String uniqueEventName(AnimClip clip) {
        for (int n = 1; ; n++) {
            String name = n == 1 ? "event" : "event" + n;
            if (clip.events.stream().noneMatch(event -> event.name().equals(name))) return name;
        }
    }

    private int markerAt(AnimClip clip, float mouseX) {
        for (int n = clip.markers.size() - 1; n >= 0; n--) {
            AnimClip.Marker marker = clip.markers.get(n);
            float at = x(marker.time());
            String label = marker.name().isEmpty() ? "marker" : marker.name();
            if (mouseX >= at - EditorScale.of(4) && mouseX <= at + Paint.chipWidth(label)) return n;
        }
        return -1;
    }

    private int eventAt(AnimClip clip, float mouseX) {
        for (int n = clip.events.size() - 1; n >= 0; n--) {
            AnimClip.Event event = clip.events.get(n);
            float at = x(event.time()) - EditorScale.of(3);
            if (mouseX >= at && mouseX <= at + Paint.chipWidth("{ } " + event.name())) return n;
        }
        return -1;
    }

    private static List<Double> jointTimes(AnimClip clip, String joint) {
        List<Double> times = new ArrayList<>();
        for (Channel channel : Channel.values()) {
            for (AnimKey key : clip.keys(joint, channel)) {
                if (times.stream().noneMatch(known -> Math.abs(known - key.time()) < AnimClip.EPSILON)) times.add(key.time());
            }
        }
        return times;
    }

    private boolean anySelectedAt(String joint, double time) {
        for (Channel channel : Channel.values()) {
            if (session.keys().contains(new KeyRef(joint, channel, time))) return true;
        }
        return false;
    }

    private boolean isSelected(Hit hit) {
        return session.keys().containsAll(refs(session.clip(), hit));
    }

    private static Set<KeyRef> refs(AnimClip clip, Hit hit) {
        Set<KeyRef> found = new LinkedHashSet<>();
        if (hit.channel() != null) {
            found.add(new KeyRef(hit.joint(), hit.channel(), hit.time()));
            return found;
        }
        for (Channel channel : Channel.values()) {
            if (clip.keyAt(hit.joint(), channel, hit.time()) != null) found.add(new KeyRef(hit.joint(), channel, hit.time()));
        }
        return found;
    }

    private void renderContext() {
        if (!ImGui.beginPopup(CONTEXT)) return;
        Lane lane = contextLane;
        if (contextOnKey && !session.keys().isEmpty()) {
            int count = session.keys().size();
            Sections.caption(count == 1 ? "1 key" : count + " keys");
            interpolationRow();
            ImGui.separator();
            if (ImGui.menuItem("Copy", "Ctrl+C")) session.copyKeys();
            if (ImGui.menuItem("Duplicate", "Ctrl+D")) {
                session.copyKeys();
                session.pasteKeys();
            }
            if (ImGui.menuItem("Mirror to the other side")) session.mirrorSelected();
            ImGui.separator();
            if (ImGui.menuItem("Delete", "Del")) session.deleteSelected();
        } else if (lane != null) {
            Sections.caption(Paint.seconds(contextTime) + " s");
            switch (lane.kind()) {
                case MARKERS -> {
                    if (ImGui.menuItem("Add marker")) addMarker(contextTime);
                }
                case EVENTS -> {
                    if (ImGui.menuItem("Add event")) addEvent(contextTime);
                }
                default -> {
                    Channel channel = lane.channel() == null ? Channel.ROTATION : lane.channel();
                    if (ImGui.menuItem("Add " + channel.key() + " key")) addKey(lane.joint(), channel, contextTime);
                }
            }
            if (ImGui.menuItem("Paste", "Ctrl+V")) {
                session.seek(contextTime);
                session.pasteKeys();
            }
            if ((lane.kind() == Kind.EVENTS && session.event() >= 0 || lane.kind() == Kind.MARKERS && session.marker() >= 0)) {
                ImGui.separator();
                if (ImGui.menuItem("Delete", "Del")) session.deleteSelected();
            }
        }
        ImGui.endPopup();
    }

    private void interpolationRow() {
        Interp shared = null;
        boolean mixed = false;
        for (KeyRef ref : session.keys()) {
            AnimKey key = session.clip().keyAt(ref.joint(), ref.channel(), ref.time());
            if (key == null) continue;
            if (shared == null) shared = key.interp();
            else if (shared != key.interp()) mixed = true;
        }
        float width = EditorScale.of(64);
        for (Interp interp : Interp.values()) {
            if (interp.ordinal() > 0) ImGui.sameLine(0, EditorScale.of(2));
            boolean active = !mixed && interp == shared;
            if (Toggles.textSized("anim-context-" + interp.key(), interp.label(), active, width, ImGui.getFrameHeight())) {
                Set<KeyRef> chosen = Set.copyOf(session.keys());
                session.edit("Set interpolation", changed -> KeyEdits.interp(changed, chosen, interp));
            }
        }
    }

    private record Curve(String joint, Channel channel, int axis) {
        String id() {
            return joint + "/" + channel.key() + "/" + axis;
        }
    }

    private List<Curve> curves(AnimClip clip) {
        Set<String> joints = new LinkedHashSet<>();
        for (KeyRef ref : session.keys()) joints.add(ref.joint());
        if (session.joint() != null) joints.add(session.joint());
        List<Curve> curves = new ArrayList<>();
        for (String joint : joints) {
            for (Channel channel : Channel.values()) {
                if (clip.keys(joint, channel).isEmpty()) continue;
                for (int axis = 0; axis < 3; axis++) curves.add(new Curve(joint, channel, axis));
            }
        }
        return curves;
    }

    private void renderCurves() {
        AnimClip clip = session.clip();
        layout();
        fit(clip, right - lanesLeft);
        float ruler = EditorScale.of(RULER);
        float top = ImGui.getWindowPosY();
        float bottom = top + ImGui.getWindowHeight();
        ImGui.dummy(right - left, bottom - top - 1);
        boolean hovered = ImGui.isWindowHovered(ImGuiHoveredFlags.AllowWhenBlockedByActiveItem);
        handleZoom(hovered);
        ImDrawList draw = ImGui.getWindowDrawList();
        List<Curve> curves = curves(clip);
        List<Curve> shown = new ArrayList<>();
        for (Curve curve : curves) {
            if (!hiddenCurves.contains(curve.id())) shown.add(curve);
        }
        valueTop = top + ruler + EditorScale.of(14);
        valueBottom = bottom - EditorScale.of(14);
        fitValues(clip, shown);
        draw.addRectFilled(x(0), top + ruler, Math.max(x(0), x(clip.length)), bottom, Paint.LANE);
        drawValueGrid(draw, top + ruler, bottom);
        draw.pushClipRect(lanesLeft, top + ruler, right, bottom, true);
        for (Curve curve : shown) drawCurve(draw, clip, curve);
        KeyRef hoverKey = null;
        int hoverAxis = 0;
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        float radius = EditorScale.of(KEY_RADIUS);
        for (Curve curve : shown) {
            for (AnimKey key : clip.keys(curve.joint(), curve.channel())) {
                float px = x(key.time());
                float py = y(Curves.component(key.value(), curve.axis()));
                KeyRef ref = new KeyRef(curve.joint(), curve.channel(), key.time());
                boolean selected = session.keys().contains(ref);
                boolean over = hovered && near(mouseX, mouseY, px, py, radius);
                Paint.diamond(draw, px, py, radius, keyColor(selected, over, Paint.KEY));
                if (over) {
                    hoverKey = ref;
                    hoverAxis = curve.axis();
                }
            }
        }
        HandleHit handle = drawHandles(draw, clip, shown, mouseX, mouseY, hovered);
        draw.popClipRect();
        drawCurveNames(draw, curves, top + ruler, bottom, hovered);
        drawEnd(draw, clip, top + ruler, bottom);
        drawRuler(draw, clip, top, ruler);
        drawPlayhead(draw, top, ruler, bottom);
        handleCurveInput(clip, top, ruler, hovered, hoverKey, hoverAxis, handle);
        if (shown.isEmpty()) {
            String hint = session.joint() == null ? "Select a bone to see its curves" : session.joint() + " has no keys yet";
            float width = ImGui.calcTextSizeX(hint);
            draw.addText(lanesLeft + (right - lanesLeft - width) * 0.5f, (top + ruler + bottom) * 0.5f, EditorStyle.COLOR_TEXT_MUTED, hint);
        }
    }

    private void fitValues(AnimClip clip, List<Curve> shown) {
        if ((drag == Drag.CURVE_POINT || drag == Drag.CURVE_HANDLE) && valueMax > valueMin) return;
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        for (Curve curve : shown) {
            List<AnimKey> keys = clip.keys(curve.joint(), curve.channel());
            ClipCurve sampled = Curves.curve(keys);
            double span = Math.max(clip.length, clip.lastKeyTime());
            for (int n = 0; n <= 64; n++) {
                double value = Curves.component(Curves.sample(sampled, span * n / 64.0, curve.channel().rest()), curve.axis());
                low = Math.min(low, value);
                high = Math.max(high, value);
            }
            for (AnimKey key : keys) {
                double value = Curves.component(key.value(), curve.axis());
                low = Math.min(low, value);
                high = Math.max(high, value);
            }
        }
        if (low == Double.POSITIVE_INFINITY) {
            low = -1;
            high = 1;
        }
        double pad = Math.max(1e-3, (high - low) * 0.12);
        if (high - low < 1e-3) pad = Math.max(1, Math.abs(high) * 0.2);
        valueMin = low - pad;
        valueMax = high + pad;
    }

    private float y(double value) {
        return (float) (valueBottom - (value - valueMin) / (valueMax - valueMin) * (valueBottom - valueTop));
    }

    private double value(float screenY) {
        return valueMin + (valueBottom - screenY) / (valueBottom - valueTop) * (valueMax - valueMin);
    }

    private void drawValueGrid(ImDrawList draw, float top, float bottom) {
        double span = valueMax - valueMin;
        double raw = span / 5;
        double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        double step = magnitude;
        for (double factor : new double[] {1, 2, 5, 10}) {
            if (magnitude * factor >= raw) {
                step = magnitude * factor;
                break;
            }
        }
        draw.pushClipRect(lanesLeft, top, right, bottom, true);
        for (double at = Math.ceil(valueMin / step) * step; at <= valueMax; at += step) {
            float y = y(at);
            draw.addLine(lanesLeft, y, right, y, Math.abs(at) < step * 0.01 ? Paint.GRID_STRONG : Paint.GRID);
            Paint.small(draw, lanesLeft + EditorScale.of(4), y - Paint.smallSize() - 1, EditorStyle.COLOR_TEXT_FAINT, trim(at));
        }
        draw.popClipRect();
    }

    private static String trim(double value) {
        String text = ClipFile.number(Math.round(value * 1000) / 1000.0);
        return text;
    }

    private void drawCurve(ImDrawList draw, AnimClip clip, Curve curve) {
        List<AnimKey> keys = clip.keys(curve.joint(), curve.channel());
        int color = Paint.axis(curve.axis());
        boolean focused = session.keys().stream().anyMatch(ref -> ref.joint().equals(curve.joint()) && ref.channel() == curve.channel());
        float step = EditorScale.of(3);
        float previousX = lanesLeft;
        ClipCurve sampled = Curves.curve(keys);
        float previousY = y(Curves.component(Curves.sample(sampled, time(lanesLeft), curve.channel().rest()), curve.axis()));
        for (float x = lanesLeft + step; x <= right + step; x += step) {
            float y = y(Curves.component(Curves.sample(sampled, time(x), curve.channel().rest()), curve.axis()));
            draw.addLine(previousX, previousY, x, y, focused || session.keys().isEmpty() ? color : EditorStyle.withAlpha(color, 0.55f), EditorScale.of(1.6f));
            previousX = x;
            previousY = y;
        }
    }

    private record HandleHit(KeyRef key, int axis, boolean out) {}

    private @Nullable HandleHit drawHandles(ImDrawList draw, AnimClip clip, List<Curve> shown, float mouseX, float mouseY, boolean hovered) {
        if (session.keys().size() != 1) return null;
        KeyRef ref = session.keys().iterator().next();
        List<AnimKey> keys = clip.keys(ref.joint(), ref.channel());
        int index = -1;
        for (int n = 0; n < keys.size(); n++) {
            if (Math.abs(keys.get(n).time() - ref.time()) < AnimClip.EPSILON) index = n;
        }
        if (index < 0) return null;
        AnimKey key = keys.get(index);
        HandleHit found = null;
        float radius = EditorScale.of(3.5f);
        for (Curve curve : shown) {
            if (!curve.joint().equals(ref.joint()) || curve.channel() != ref.channel()) continue;
            float kx = x(key.time());
            float ky = y(Curves.component(key.value(), curve.axis()));
            if (key.interp() == Interp.BEZIER && index + 1 < keys.size()) {
                AnimKey.Handle out = Curves.outHandle(key, keys.get(index + 1));
                float hx = x(key.time() + out.dt());
                float hy = y(Curves.component(key.value().add(out.dv()), curve.axis()));
                draw.addLine(kx, ky, hx, hy, EditorStyle.COLOR_TEXT_MUTED);
                draw.addCircleFilled(hx, hy, radius, EditorStyle.COLOR_TEXT_MUTED);
                if (hovered && near(mouseX, mouseY, hx, hy, radius)) found = new HandleHit(ref, curve.axis(), true);
            }
            if (index > 0 && keys.get(index - 1).interp() == Interp.BEZIER) {
                AnimKey.Handle in = Curves.inHandle(keys.get(index - 1), key);
                float hx = x(key.time() + in.dt());
                float hy = y(Curves.component(key.value().add(in.dv()), curve.axis()));
                draw.addLine(kx, ky, hx, hy, EditorStyle.COLOR_TEXT_MUTED);
                draw.addCircleFilled(hx, hy, radius, EditorStyle.COLOR_TEXT_MUTED);
                if (hovered && near(mouseX, mouseY, hx, hy, radius)) found = new HandleHit(ref, curve.axis(), false);
            }
        }
        return found;
    }

    private void drawCurveNames(ImDrawList draw, List<Curve> curves, float top, float bottom, boolean hovered) {
        draw.addRectFilled(left, top, lanesLeft, bottom, EditorStyle.COLOR_PANEL_BACKGROUND);
        draw.addLine(lanesLeft, top, lanesLeft, bottom, EditorStyle.COLOR_OUTLINE);
        float row = EditorScale.of(ROW);
        float y = top;
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        String[] axes = {"X", "Y", "Z"};
        for (Curve curve : curves) {
            boolean hidden = hiddenCurves.contains(curve.id());
            float dot = EditorScale.of(7);
            float x = left + EditorScale.of(12);
            float middle = y + row * 0.5f;
            draw.addRectFilled(x, middle - dot * 0.5f, x + dot, middle + dot * 0.5f, hidden ? EditorStyle.COLOR_TEXT_FAINT : Paint.axis(curve.axis()), EditorScale.of(2));
            draw.addText(x + dot + EditorScale.of(7), middle - ImGui.getTextLineHeight() * 0.5f, hidden ? EditorStyle.COLOR_TEXT_FAINT : EditorStyle.COLOR_TEXT,
                    curve.joint() + "  " + curve.channel().key() + " " + axes[curve.axis()]);
            if (hovered && ImGui.isMouseClicked(ImGuiMouseButton.Left) && mouseX < lanesLeft && mouseY >= y && mouseY < y + row) {
                if (!hiddenCurves.remove(curve.id())) hiddenCurves.add(curve.id());
            }
            y += row;
        }
    }

    private void handleCurveInput(AnimClip clip, float top, float ruler, boolean hovered, @Nullable KeyRef key, int axis, @Nullable HandleHit handle) {
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        boolean inRuler = mouseY >= top && mouseY < top + ruler && mouseX >= lanesLeft;
        if (hovered && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            pressX = mouseX;
            pressY = mouseY;
            moved = false;
            if (inRuler) {
                drag = Drag.SCRUB;
                session.stop();
                scrubTo(time(mouseX));
            } else if (handle != null) {
                curveKey = handle.key();
                curveAxis = handle.axis();
                curveOut = handle.out();
                beginDrag(Drag.CURVE_HANDLE, "handle");
            } else if (key != null) {
                if (ImGui.getIO().getKeyCtrl() || ImGui.getIO().getKeyShift()) {
                    Set<KeyRef> chosen = new LinkedHashSet<>(session.keys());
                    if (!chosen.remove(key)) chosen.add(key);
                    session.selectKeys(chosen);
                } else {
                    session.selectKeys(Set.of(key));
                }
                session.joint(key.joint());
                curveKey = key;
                curveAxis = axis;
                beginDrag(Drag.CURVE_POINT, "curve");
            } else if (mouseX >= lanesLeft) {
                session.selectKeys(Set.of());
            }
        }
        if (drag == Drag.NONE) return;
        if (Math.abs(mouseX - pressX) > EditorScale.of(DRAG_START) || Math.abs(mouseY - pressY) > EditorScale.of(DRAG_START)) moved = true;
        if (drag == Drag.SCRUB) scrubTo(time(mouseX));
        if (drag == Drag.CURVE_POINT && moved && dragStart != null && curveKey != null) dragPoint(clip, mouseX, mouseY);
        if (drag == Drag.CURVE_HANDLE && moved && dragStart != null && curveKey != null) dragHandle(mouseX, mouseY);
        if (!ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            drag = Drag.NONE;
            dragStart = null;
        }
    }

    private void dragPoint(AnimClip clip, float mouseX, float mouseY) {
        KeyRef ref = curveKey;
        AnimClip start = dragStart;
        AnimKey original = start.keyAt(ref.joint(), ref.channel(), ref.time());
        if (original == null) return;
        double delta = time(mouseX) - time(pressX);
        if (session.snap()) delta = Math.round(delta * clip.fps) / (double) clip.fps;
        double changedValue = Curves.component(original.value(), curveAxis) + (value(mouseY) - value(pressY));
        double moveBy = ImGui.getIO().getKeyAlt() ? 0 : delta;
        int axis = curveAxis;
        KeyRef[] placed = new KeyRef[1];
        session.editFrom(start, "Edit curve", dragGesture, changed -> {
            changed.remove(ref.joint(), ref.channel(), ref.time());
            double time = KeyRef.clean(Math.max(0, ref.time() + moveBy));
            changed.put(ref.joint(), ref.channel(), original.at(time).with(Curves.withComponent(original.value(), axis, changedValue)));
            placed[0] = new KeyRef(ref.joint(), ref.channel(), time);
        });
        if (placed[0] != null) session.selectKeys(Set.of(placed[0]));
    }

    private void dragHandle(float mouseX, float mouseY) {
        KeyRef ref = curveKey;
        AnimClip start = dragStart;
        List<AnimKey> keys = start.keys(ref.joint(), ref.channel());
        int index = -1;
        for (int n = 0; n < keys.size(); n++) {
            if (Math.abs(keys.get(n).time() - ref.time()) < AnimClip.EPSILON) index = n;
        }
        if (index < 0) return;
        AnimKey key = keys.get(index);
        boolean out = curveOut;
        if (out && index + 1 >= keys.size() || !out && index == 0) return;
        AnimKey.Handle base = out ? Curves.outHandle(key, keys.get(index + 1)) : Curves.inHandle(keys.get(index - 1), key);
        double span = out ? keys.get(index + 1).time() - key.time() : key.time() - keys.get(index - 1).time();
        double dt = time(mouseX) - key.time();
        dt = out ? Math.clamp(dt, 0.001, span) : Math.clamp(dt, -span, -0.001);
        double dv = value(mouseY) - Curves.component(key.value(), curveAxis);
        AnimKey.Handle changedHandle = new AnimKey.Handle(dt, Curves.withComponent(base.dv(), curveAxis, dv));
        session.editFrom(start, "Edit handle", dragGesture, changed -> {
            AnimKey now = changed.keyAt(ref.joint(), ref.channel(), ref.time());
            if (now == null) return;
            changed.put(ref.joint(), ref.channel(), out ? now.handles(now.in(), changedHandle) : now.handles(changedHandle, now.out()));
        });
    }

    private static String selectedCount(int count) {
        if (count == 0) return "";
        if (count == 1) return "1 key selected";
        return count + " keys selected";
    }

    private static int keyColor(boolean selected, boolean over, int idle) {
        if (selected) return Paint.SELECTED;
        if (over) return EditorStyle.COLOR_TEXT_FOCUS;
        return idle;
    }

    private static boolean near(float mouseX, float mouseY, float x, float y, float radius) {
        float reach = radius + HIT_SLACK;
        return Math.abs(mouseX - x) <= reach && Math.abs(mouseY - y) <= reach;
    }

    private static void tip(String text) {
        if (ImGui.isItemHovered()) ImGui.setTooltip(text);
    }
}
