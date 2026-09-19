package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.instance.Joint;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.mod.adapter.render.EditorOverlay;
import com.meekdev.moud.mod.client.editor.kit.ToggleStyle;
import com.meekdev.moud.mod.client.editor.kit.Toolbars;
import com.meekdev.moud.mod.client.editor.style.EditorIcon;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import com.meekdev.moud.mod.client.editor.viewport.SceneView;
import com.meekdev.moud.mod.client.editor.viewport.ViewportPanel;
import com.meekdev.moud.mod.client.editor.viewport.ViewportTakeover;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class AnimationViewport implements ViewportTakeover {

    private static final float PANEL_PAD = 8.0f;
    private static final double GHOST_CHANGE = 0.5;

    private final AnimationWorkspace workspace;
    private final AnimationSession session;
    private final PreviewRig rig;
    private final IconWidgets icons;
    private final ViewportPanel viewport;
    private final BoneGizmo gizmo = new BoneGizmo();
    private @Nullable AnimClip editStart;
    private String gesture = "";
    private Vector3 startValue = Vector3.ZERO;
    private boolean aiming;
    private boolean pressed;

    AnimationViewport(AnimationWorkspace workspace, AnimationSession session, PreviewRig rig, IconWidgets icons, ViewportPanel viewport) {
        this.workspace = workspace;
        this.session = session;
        this.rig = rig;
        this.icons = icons;
        this.viewport = viewport;
    }

    private boolean view() {
        return session.clip().space == AnimClip.Space.VIEW;
    }

    @Override
    public void toolbar() {
        if (icons.toggleButton("anim-tool-rotate", EditorIcon.TOOL_ROTATE, EditorStyle.iconSizeToolbar(), session.tool() == AnimationSession.Tool.ROTATE)) {
            session.tool(AnimationSession.Tool.ROTATE);
        }
        tip("Rotate the selected bone (R)");
        ImGui.sameLine();
        if (icons.toggleButton("anim-tool-move", EditorIcon.TOOL_MOVE, EditorStyle.iconSizeToolbar(), session.tool() == AnimationSession.Tool.MOVE)) {
            session.tool(AnimationSession.Tool.MOVE);
        }
        tip("Move the selected bone (G)");
        Toolbars.groupSeparator();
        if (Toolbars.textButton((session.local() ? "Local" : "World") + "##anim-space")) session.local(!session.local());
        tip("The gizmo turns with the bone, or stays on the world axes");
        if (view()) {
            ImGui.sameLine();
            if (icons.toggleButton("anim-through", EditorIcon.CAMERA_3D, EditorStyle.iconSizeToolbar(), workspace.through())) workspace.through(!workspace.through());
            tip("Look through the camera bone, as the player sees the view model (V)");
        } else {
            Toolbars.groupSeparator();
            if (icons.toggleButton("anim-onion", EditorIcon.ONION, EditorStyle.iconSizeToolbar(), session.onion())) session.onion(!session.onion());
            tip("Onion skin, the poses of the keys around the playhead");
            ImGui.sameLine();
            if (icons.toggleButton("anim-ik", EditorIcon.SKELETON_IK_3D, EditorStyle.iconSizeToolbar(), session.ikDrag())) session.ikDrag(!session.ikDrag());
            tip("Drag an arm to aim it, the same as holding Alt");
        }
    }

    private static void toggle(String label, boolean active, Runnable action, String tipText) {
        ToggleStyle.push(active);
        boolean clicked = Toolbars.textButton(label);
        ToggleStyle.pop(active);
        if (clicked) action.run();
        tip(tipText);
    }

    private static void tip(String text) {
        if (ImGui.isItemHovered()) ImGui.setTooltip(text);
    }

    @Override
    public boolean holdsLeftDrag() {
        return aiming || gizmo.dragging() || ImGui.getIO().getKeyAlt() && viewport.hovered() && aimable(session.joint());
    }

    @Override
    public void frameRequested() {
        workspace.frameRig();
    }

    private boolean aimable(@Nullable String joint) {
        return !view() && ("rightArm".equals(joint) || "leftArm".equals(joint));
    }

    @Override
    public void draw(ImDrawList draw, SceneView sceneView, boolean hovered) {
        Map<String, JointPose> pose = workspace.pose();
        Map<Integer, String> limbs = rig.limbIds();
        int hoveredId = 0;
        if (hovered && !gizmo.dragging() && !gizmo.hovering()) {
            int picked = EditorOverlay.picked();
            if (limbs.containsKey(picked)) hoveredId = picked;
        }
        Set<Integer> selected = new HashSet<>();
        for (Map.Entry<Integer, String> limb : limbs.entrySet()) {
            if (limb.getValue().equals(session.joint())) selected.add(limb.getKey());
        }
        EditorOverlay.show(instance -> limbs.containsKey(instance.id()), selected, hoveredId);
        if (session.onion() && session.hasClip() && !view()) drawOnion(draw, sceneView, pose);
        if (view()) drawCameraBone(draw, sceneView, pose);
        boolean busy = handleAim(draw, sceneView, pose, hovered);
        if (!busy && session.hasClip()) busy = renderGizmo(draw, sceneView, pose, hovered);
        if (hovered && !busy && ImGui.isMouseClicked(ImGuiMouseButton.Left) && !ImGui.getIO().getKeyAlt()) pressed = true;
        if (pressed && !ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            pressed = false;
            if (hovered && !busy) {
                String joint = limbs.get(hoveredId);
                if (joint != null) session.joint(joint);
            }
        }
        drawReadout(draw, sceneView, pose);
        handleKeys(hovered);
    }

    private void handleKeys(boolean hovered) {
        if (!hovered && !ImGui.isWindowFocused() || ImGui.getIO().getWantTextInput() || ImGui.getIO().getKeyCtrl()) return;
        if (ImGui.isKeyPressed(ImGuiKey.R, false)) session.tool(AnimationSession.Tool.ROTATE);
        if (ImGui.isKeyPressed(ImGuiKey.G, false)) session.tool(AnimationSession.Tool.MOVE);
        if (ImGui.isKeyPressed(ImGuiKey.M, false)) session.mirrorPose();
        if (ImGui.isKeyPressed(ImGuiKey.X, false)) session.local(!session.local());
        if (view() && ImGui.isKeyPressed(ImGuiKey.V, false)) workspace.through(!workspace.through());
        if (ImGui.isKeyPressed(ImGuiKey.Keypad5, false)) viewport.toggleOrthographic();
    }

    private @Nullable CFrame parentFrame(String joint, Map<String, JointPose> pose) {
        if (view()) return rig.viewParent(joint, pose, rig.eye());
        return rig.parentFrame(joint, pose);
    }

    private boolean renderGizmo(ImDrawList draw, SceneView sceneView, Map<String, JointPose> pose, boolean hovered) {
        String joint = session.joint();
        CFrame parent = joint == null ? null : parentFrame(joint, pose);
        if (joint == null || parent == null) {
            gizmo.cancel();
            return false;
        }
        JointPose current = pose.getOrDefault(joint, JointPose.REST);
        CFrame pivot = parent.mul(current.transform());
        boolean rotating = session.tool() == AnimationSession.Tool.ROTATE;
        boolean wasDragging = gizmo.dragging();
        BoneGizmo.Result moved = gizmo.draw(draw, sceneView, viewport.view(), pivot, session.local(), rotating ? BoneGizmo.Mode.ROTATE : BoneGizmo.Mode.MOVE, hovered);
        if (moved == null) return gizmo.hovering() || gizmo.dragging();
        if (!wasDragging) {
            editStart = session.clip().copy();
            gesture = session.gesture(rotating ? "rotate" : "move");
            startValue = rotating ? current.rotation() : current.position();
        }
        double at = session.keyTime();
        AnimClip start = editStart;
        if (start == null) return true;
        if (rotating) {
            Quat local = parent.rotation().inverse().mul(moved.rotation());
            Vector3 degrees = round(Euler.nearest(local, startValue), 1000);
            session.editFrom(start, "Rotate " + joint, gesture, changed -> KeyEdits.set(changed, joint, Channel.ROTATION, at, degrees));
        } else {
            Vector3 local = round(parent.pointToObject(moved.position()), 1e5);
            session.editFrom(start, "Move " + joint, gesture, changed -> KeyEdits.set(changed, joint, Channel.POSITION, at, local));
        }
        selectKeyAt(joint, rotating ? Channel.ROTATION : Channel.POSITION, at);
        return true;
    }

    private void selectKeyAt(String joint, Channel channel, double at) {
        KeyRef ref = new KeyRef(joint, channel, at);
        if (!session.keys().equals(Set.of(ref))) session.selectKeys(Set.of(ref));
    }

    private static Vector3 round(Vector3 value, double scale) {
        return new Vector3(Math.round(value.x() * scale) / scale, Math.round(value.y() * scale) / scale, Math.round(value.z() * scale) / scale);
    }

    private boolean handleAim(ImDrawList draw, SceneView sceneView, Map<String, JointPose> pose, boolean hovered) {
        String joint = session.joint();
        boolean wanted = aimable(joint) && (ImGui.getIO().getKeyAlt() || session.ikDrag()) && !gizmo.hovering();
        if (!aiming && wanted && hovered && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            aiming = true;
            editStart = session.clip().copy();
            gesture = session.gesture("aim");
            startValue = pose.getOrDefault(joint, JointPose.REST).rotation();
        }
        if (!aiming) return false;
        if (!ImGui.isMouseDown(ImGuiMouseButton.Left) || joint == null || editStart == null) {
            aiming = false;
            return true;
        }
        CFrame parent = parentFrame(joint, pose);
        if (parent == null) return true;
        Vector3 pivot = parent.position();
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        Vector3 origin = sceneView.rayOrigin(mouseX, mouseY);
        Vector3 direction = sceneView.rayDirection(mouseX, mouseY);
        Vector3 normal = pivot.sub(sceneView.cameraPosition()).normalize();
        double facing = direction.dot(normal);
        if (Math.abs(facing) < 1e-4) return true;
        Vector3 target = origin.add(direction.mul(pivot.sub(origin).dot(normal) / facing));
        Vector3 reach = parent.rotation().inverse().rotate(target.sub(pivot));
        if (reach.lengthSq() < 1e-8) return true;
        Vector3 degrees = round(Euler.nearest(between(new Vector3(0, -1, 0), reach.normalize()), startValue), 1000);
        double at = session.keyTime();
        session.editFrom(editStart, "Aim " + joint, gesture, changed -> KeyEdits.set(changed, joint, Channel.ROTATION, at, degrees));
        selectKeyAt(joint, Channel.ROTATION, at);
        float[] from = sceneView.toScreen(pivot);
        float[] to = sceneView.toScreen(target);
        if (from != null && to != null) {
            dashed(draw, from[0], from[1], to[0], to[1], EditorStyle.COLOR_AXIS_Z);
            float size = EditorScale.of(6);
            Paint.diamondOutline(draw, to[0], to[1], size, EditorStyle.COLOR_AXIS_Z, EditorScale.of(1.6f));
            Paint.small(draw, to[0] + size + EditorScale.of(6), to[1] - Paint.smallSize() * 0.5f, EditorStyle.COLOR_AXIS_Z, "aim");
        }
        return true;
    }

    private static Quat between(Vector3 from, Vector3 to) {
        double dot = Math.clamp(from.dot(to), -1, 1);
        if (dot > 0.999999) return Quat.IDENTITY;
        if (dot < -0.999999) return Quat.axisAngle(new Vector3(1, 0, 0), Math.PI);
        return Quat.axisAngle(from.cross(to).normalize(), Math.acos(dot));
    }

    private static void dashed(ImDrawList draw, float x0, float y0, float x1, float y1, int color) {
        float length = (float) Math.hypot(x1 - x0, y1 - y0);
        float dash = EditorScale.of(4);
        for (float at = 0; at < length; at += dash * 2) {
            float a = at / length;
            float b = Math.min(1, (at + dash) / length);
            draw.addLine(x0 + (x1 - x0) * a, y0 + (y1 - y0) * a, x0 + (x1 - x0) * b, y0 + (y1 - y0) * b, color, EditorScale.of(1.4f));
        }
    }

    private void drawOnion(ImDrawList draw, SceneView sceneView, Map<String, JointPose> pose) {
        List<Double> times = session.keyTimes();
        double now = session.time();
        List<String> joints = Rigs.names(session.clip());
        int before = 0;
        for (int n = times.size() - 1; n >= 0 && before < session.onionBefore(); n--) {
            if (times.get(n) >= now - 1e-6) continue;
            before++;
            ghost(draw, sceneView, JointPose.all(session.clip(), joints, times.get(n)), pose, EditorStyle.COLOR_AXIS_Z, before);
        }
        int after = 0;
        for (double at : times) {
            if (after >= session.onionAfter()) break;
            if (at <= now + 1e-6) continue;
            after++;
            ghost(draw, sceneView, JointPose.all(session.clip(), joints, at), pose, EditorStyle.COLOR_AXIS_Y, after);
        }
    }

    private void ghost(ImDrawList draw, SceneView sceneView, Map<String, JointPose> ghost, Map<String, JointPose> now, int tint, int distance) {
        float strength = 1.0f / distance;
        int fill = EditorStyle.withAlpha(tint, 0.16f * strength);
        int edge = EditorStyle.withAlpha(tint, 0.45f * strength);
        for (Map.Entry<String, Joint> entry : rig.joints().entrySet()) {
            if (!(entry.getValue().part1 instanceof Part part) || !part.visible) continue;
            PreviewRig.Box was = rig.box(entry.getKey(), ghost);
            PreviewRig.Box is = rig.box(entry.getKey(), now);
            if (was == null || is == null) continue;
            if (was.frame().position().distance(is.frame().position()) < 0.01 && angle(was.frame().rotation(), is.frame().rotation()) < GHOST_CHANGE) continue;
            silhouette(draw, sceneView, was, fill, edge);
        }
    }

    private static double angle(Quat a, Quat b) {
        double dot = Math.abs(a.x() * b.x() + a.y() * b.y() + a.z() * b.z() + a.w() * b.w());
        return Math.toDegrees(2 * Math.acos(Math.min(1, dot)));
    }

    private static void silhouette(ImDrawList draw, SceneView sceneView, PreviewRig.Box box, int fill, int edge) {
        Vector3 half = box.size().mul(0.5);
        List<float[]> points = new ArrayList<>(8);
        for (int n = 0; n < 8; n++) {
            Vector3 local = new Vector3((n & 1) == 0 ? -half.x() : half.x(), (n & 2) == 0 ? -half.y() : half.y(), (n & 4) == 0 ? -half.z() : half.z());
            float[] screen = sceneView.toScreen(box.frame().pointToWorld(local));
            if (screen == null) return;
            points.add(screen);
        }
        List<float[]> hull = hull(points);
        if (hull.size() < 3) return;
        for (int n = 1; n + 1 < hull.size(); n++) {
            draw.addTriangleFilled(hull.get(0)[0], hull.get(0)[1], hull.get(n)[0], hull.get(n)[1], hull.get(n + 1)[0], hull.get(n + 1)[1], fill);
        }
        for (int n = 0; n < hull.size(); n++) {
            float[] a = hull.get(n);
            float[] b = hull.get((n + 1) % hull.size());
            draw.addLine(a[0], a[1], b[0], b[1], edge, EditorScale.of(1.2f));
        }
    }

    private static List<float[]> hull(List<float[]> points) {
        List<float[]> sorted = new ArrayList<>(points);
        sorted.sort((a, b) -> a[0] != b[0] ? Float.compare(a[0], b[0]) : Float.compare(a[1], b[1]));
        List<float[]> out = new ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            int start = out.size();
            for (float[] point : sorted) {
                while (out.size() >= start + 2 && cross(out.get(out.size() - 2), out.getLast(), point) <= 0) out.removeLast();
                out.add(point);
            }
            out.removeLast();
            sorted = sorted.reversed();
        }
        return out;
    }

    private static float cross(float[] o, float[] a, float[] b) {
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
    }

    private void drawCameraBone(ImDrawList draw, SceneView sceneView, Map<String, JointPose> pose) {
        if (workspace.through()) {
            float centreX = sceneView.originX() + sceneView.width() * 0.5f;
            float centreY = sceneView.originY() + sceneView.height() * 0.5f;
            float cross = EditorScale.of(8);
            int crosshair = EditorStyle.withAlpha(EditorStyle.COLOR_TEXT_FOCUS, 0.7f);
            draw.addLine(centreX - cross, centreY, centreX + cross, centreY, crosshair, EditorScale.of(1.5f));
            draw.addLine(centreX, centreY - cross, centreX, centreY + cross, crosshair, EditorScale.of(1.5f));
            return;
        }
        CFrame bone = rig.viewPivot("camera", pose, rig.eye());
        float[] at = sceneView.toScreen(bone.position());
        if (at == null) return;
        double reach = 0.35;
        Vector3 forward = bone.lookVector();
        float[] ahead = sceneView.toScreen(bone.position().add(forward.mul(0.8)));
        if (ahead != null) dashed(draw, at[0], at[1], ahead[0], ahead[1], EditorStyle.withAlpha(EditorStyle.COLOR_TEXT_FOCUS, 0.45f));
        Vector3[] axes = {bone.rightVector(), bone.upVector(), forward.neg()};
        for (int n = 0; n < 3; n++) {
            float[] tip = sceneView.toScreen(bone.position().add(axes[n].mul(reach)));
            if (tip != null) draw.addLine(at[0], at[1], tip[0], tip[1], Paint.axis(n), EditorScale.of(2));
        }
        boolean selected = "camera".equals(session.joint());
        int color = selected ? Paint.SELECTED : EditorStyle.COLOR_TEXT_MUTED;
        float box = EditorScale.of(4);
        draw.addRect(at[0] - box, at[1] - box, at[0] + box, at[1] + box, color, 0, 0, EditorScale.of(1.5f));
        Paint.small(draw, at[0] + EditorScale.of(10), at[1] + EditorScale.of(6), color, "camera bone");
        float grab = EditorScale.of(10);
        if (!gizmo.hovering() && ImGui.isMouseClicked(ImGuiMouseButton.Left) && ImGui.isMouseHoveringRect(at[0] - grab, at[1] - grab, at[0] + grab + Paint.smallWidth("camera bone"), at[1] + grab * 2)) {
            session.joint("camera");
        }
    }

    private void drawReadout(ImDrawList draw, SceneView sceneView, Map<String, JointPose> pose) {
        float pad = EditorScale.of(PANEL_PAD);
        float clipBottom = Math.min(sceneView.originY() + sceneView.height(), ImGui.getWindowPosY() + ImGui.getWindowHeight());
        float clipLeft = Math.max(sceneView.originX(), ImGui.getWindowPosX());
        float clipRight = Math.min(sceneView.originX() + sceneView.width(), ImGui.getWindowPosX() + ImGui.getWindowWidth());
        String hint = view() ? "R rotate  ·  G move  ·  M mirror" : "R rotate  ·  G move  ·  Alt drag aims an arm  ·  M mirror";
        Paint.small(draw, clipRight - Paint.smallWidth(hint) - pad * 1.5f, clipBottom - Paint.smallSize() - pad * 1.5f, EditorStyle.COLOR_TEXT_MUTED, hint);
        String joint = session.joint();
        if (joint == null) return;
        Channel channel = session.tool() == AnimationSession.Tool.MOVE ? Channel.POSITION : Channel.ROTATION;
        Vector3 value = pose.getOrDefault(joint, JointPose.REST).value(channel);
        String unit = channel == Channel.ROTATION ? "°" : "";
        String[] parts = {joint, channel.key(), "X " + Paint.number(value.x()) + unit, "Y " + Paint.number(value.y()) + unit, "Z " + Paint.number(value.z()) + unit};
        int[] colors = {EditorStyle.COLOR_TEXT, EditorStyle.COLOR_TEXT_MUTED, EditorStyle.COLOR_AXIS_X, EditorStyle.COLOR_AXIS_Y, EditorStyle.COLOR_AXIS_Z};
        float gap = EditorScale.of(12);
        float width = pad * 2 - gap;
        for (String part : parts) width += Paint.smallWidth(part) + gap;
        float height = Paint.smallSize() + pad * 1.4f;
        float x = clipLeft + pad * 1.5f;
        float y = clipBottom - height - pad * 1.5f;
        draw.addRectFilled(x, y, x + width, y + height, EditorStyle.withAlpha(EditorStyle.COLOR_PANEL_BACKGROUND, 0.9f), EditorStyle.frameRounding());
        float at = x + pad;
        for (int n = 0; n < parts.length; n++) {
            Paint.small(draw, at, y + (height - Paint.smallSize()) * 0.5f, colors[n], parts[n]);
            at += Paint.smallWidth(parts[n]) + gap;
        }
    }
}
