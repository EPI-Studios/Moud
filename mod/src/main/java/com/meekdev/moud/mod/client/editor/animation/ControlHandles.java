package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.character.IKControl;
import com.meekdev.moud.core.character.JointSpring;
import com.meekdev.moud.core.character.JointSprings;
import com.meekdev.moud.core.character.Rigs;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.document.SetProperty;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.client.editor.viewport.SceneView;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class ControlHandles {

    private static final float GRAB = 9.0f;
    private static final float SIZE = 6.0f;

    private record Handle(Instance control, @Nullable Instance moved, @Nullable PropertyDef property, Vector3 at, String label) {}

    private final AnimationWorkspace workspace;
    private final AnimationSession session;
    private @Nullable Handle held;
    private Vector3 grabbed = Vector3.ZERO;
    private int hovered = -1;

    ControlHandles(AnimationWorkspace workspace, AnimationSession session) {
        this.workspace = workspace;
        this.session = session;
    }

    boolean dragging() {
        return held != null;
    }

    boolean hovering() {
        return hovered >= 0;
    }

    boolean draw(ImDrawList draw, SceneView view, boolean hover) {
        List<Handle> handles = new ArrayList<>();
        Instance selected = session.control();
        for (Instance control : workspace.controls()) {
            boolean chosen = control == selected;
            if (control instanceof IKControl ik) ik(draw, view, ik, chosen, handles);
            else if (control instanceof JointSpring spring) spring(draw, view, spring, chosen, handles);
        }
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        if (held == null) {
            hovered = -1;
            double closest = EditorScale.of(GRAB);
            for (int n = 0; hover && n < handles.size(); n++) {
                float[] at = view.toScreen(handles.get(n).at());
                if (at == null) continue;
                double distance = Math.hypot(at[0] - mouseX, at[1] - mouseY);
                if (distance < closest) {
                    closest = distance;
                    hovered = n;
                }
            }
        }
        for (int n = 0; n < handles.size(); n++) {
            Handle handle = handles.get(n);
            boolean lit = n == hovered || held != null && held.control() == handle.control() && held.label().equals(handle.label());
            mark(draw, view, handle, lit || handle.control() == selected);
        }
        if (held == null && hovered >= 0 && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            Handle picked = handles.get(hovered);
            session.joint(null);
            session.control(picked.control());
            if (picked.moved() != null && picked.property() != null && workspace.document().editable(picked.moved())) {
                held = picked;
                grabbed = picked.at().sub(onPlane(view, picked.at(), mouseX, mouseY));
            }
            return true;
        }
        if (held == null) return hovered >= 0;
        if (!ImGui.isMouseDown(ImGuiMouseButton.Left) || !held.moved().isAlive()) {
            held = null;
            return true;
        }
        Vector3 to = onPlane(view, held.at(), mouseX, mouseY).add(grabbed);
        move(workspace.document(), held, to);
        held = new Handle(held.control(), held.moved(), held.property(), to, held.label());
        return true;
    }

    private void ik(ImDrawList draw, SceneView view, IKControl control, boolean chosen, List<Handle> handles) {
        Vector3 end = frame(control.endEffector);
        int color = chosen ? Paint.SELECTED : EditorStyle.withAlpha(EditorStyle.COLOR_HIGHLIGHT, 0.8f);
        Handle target = target(control);
        handles.add(target);
        if (end != null) dashed(draw, view, end, target.at(), color);
        if (alive(control.pole)) {
            CFrame at = Rigs.world(control.pole);
            Handle pole = new Handle(control, movable(control.pole) ? control.pole : null, cframeOf(control.pole), at == null ? Vector3.ZERO : at.position(), "pole");
            handles.add(pole);
            Vector3 root = frame(Rigs.posable(control.chainRoot) ? control.chainRoot : control.endEffector);
            if (root != null) dashed(draw, view, root, pole.at(), EditorStyle.withAlpha(color, 0.6f));
        }
    }

    private void spring(ImDrawList draw, SceneView view, JointSpring spring, boolean chosen, List<Handle> handles) {
        Instance joint = JointSprings.joint(spring);
        if (joint == null) return;
        CFrame frame = Rigs.frame(joint);
        Vector3 axis = spring.axis.lengthSq() < 1e-12 ? new Vector3(0, -1, 0) : spring.axis.normalize();
        Vector3 tip = frame.position().add(frame.rotation().rotate(axis).mul(spring.length));
        float[] from = view.toScreen(frame.position());
        float[] to = view.toScreen(tip);
        int color = chosen ? Paint.SELECTED : EditorStyle.withAlpha(EditorStyle.COLOR_AXIS_Y, 0.85f);
        if (from != null && to != null) draw.addLine(from[0], from[1], to[0], to[1], color, EditorScale.of(chosen ? 2.4f : 1.6f));
        handles.add(new Handle(spring, null, null, tip, "spring"));
    }

    private void mark(ImDrawList draw, SceneView view, Handle handle, boolean lit) {
        float[] at = view.toScreen(handle.at());
        if (at == null) return;
        float size = EditorScale.of(SIZE);
        int color = lit ? Paint.SELECTED : handle.label().equals("spring") ? EditorStyle.COLOR_AXIS_Y : EditorStyle.COLOR_HIGHLIGHT;
        switch (handle.label()) {
            case "target" -> {
                Paint.diamondOutline(draw, at[0], at[1], size, color, EditorScale.of(1.8f));
                if (handle.moved() != null) Paint.diamond(draw, at[0], at[1], size * 0.45f, color);
            }
            case "pole" -> draw.addCircle(at[0], at[1], size * 0.8f, color, 16, EditorScale.of(1.8f));
            default -> draw.addCircleFilled(at[0], at[1], size * 0.55f, color);
        }
        if (lit) Paint.small(draw, at[0] + size + EditorScale.of(6), at[1] - Paint.smallSize() * 0.5f, color, handle.control().name() + " " + handle.label());
    }

    static void nudge(SceneDocument document, IKControl control, Vector3 by) {
        Handle target = target(control);
        if (target.moved() != null && document.editable(target.moved())) move(document, target, target.at().add(by));
    }

    private static Handle target(IKControl control) {
        if (!alive(control.target)) return new Handle(control, control, control.def().property("targetCframe"), control.targetCframe.position(), "target");
        CFrame at = Rigs.world(control.target);
        return new Handle(control, movable(control.target) ? control.target : null, cframeOf(control.target), at == null ? Vector3.ZERO : at.position(), "target");
    }

    private static void move(SceneDocument document, Handle handle, Vector3 to) {
        Instance moved = handle.moved();
        PropertyDef property = handle.property();
        if (moved == null || property == null) return;
        Object value;
        if (moved instanceof IKControl control) {
            value = control.targetCframe.withPosition(to);
        } else if (moved instanceof Spatial spatial) {
            CFrame parent = spatial.parent() instanceof Spatial above ? Transforms.world(above) : CFrame.IDENTITY;
            CFrame world = Transforms.world(spatial);
            value = parent.inverse().mul(new CFrame(to, world.rotation()));
        } else {
            return;
        }
        document.history().execute(new SetProperty(document.ref(moved.id()), property.index(), value, "Move " + moved.name()));
    }

    private static Vector3 onPlane(SceneView view, Vector3 through, float mouseX, float mouseY) {
        Vector3 origin = view.rayOrigin(mouseX, mouseY);
        Vector3 direction = view.rayDirection(mouseX, mouseY);
        Vector3 normal = through.sub(view.cameraPosition()).normalize();
        double facing = direction.dot(normal);
        if (Math.abs(facing) < 1e-4) return through;
        return origin.add(direction.mul(through.sub(origin).dot(normal) / facing));
    }

    private static boolean alive(@Nullable Instance instance) {
        return instance != null && instance.isAlive();
    }

    private static boolean movable(Instance instance) {
        return instance instanceof Spatial && !Rigs.posable(instance) && instance.id() > 0;
    }

    private static @Nullable PropertyDef cframeOf(Instance instance) {
        return instance instanceof Spatial ? instance.def().property("cframe") : null;
    }

    private static @Nullable Vector3 frame(@Nullable Instance joint) {
        if (!alive(joint) || !Rigs.posable(joint)) return null;
        return Rigs.frame(joint).position();
    }

    private static void dashed(ImDrawList draw, SceneView view, Vector3 from, Vector3 to, int color) {
        float[] a = view.toScreen(from);
        float[] b = view.toScreen(to);
        if (a == null || b == null) return;
        float length = (float) Math.hypot(b[0] - a[0], b[1] - a[1]);
        float dash = EditorScale.of(4);
        for (float at = 0; at < length; at += dash * 2) {
            float s = at / length;
            float e = Math.min(1, (at + dash) / length);
            draw.addLine(a[0] + (b[0] - a[0]) * s, a[1] + (b[1] - a[1]) * s, a[0] + (b[0] - a[0]) * e, a[1] + (b[1] - a[1]) * e, color, EditorScale.of(1.4f));
        }
    }
}
