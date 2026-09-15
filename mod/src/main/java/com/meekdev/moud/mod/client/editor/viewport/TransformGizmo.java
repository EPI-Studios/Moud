package com.meekdev.moud.mod.client.editor.viewport;

import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.ui.ViewportFrame;
import com.meekdev.moud.mod.client.editor.document.Batch;
import com.meekdev.moud.mod.client.editor.document.Edit;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.document.SetProperty;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.extension.imguizmo.ImGuizmo;
import imgui.extension.imguizmo.flag.Mode;
import imgui.flag.ImGuiKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

final class TransformGizmo {

    private static final float GIZMO_SIZE_CLIP_SPACE = 0.14f;
    private static final float SNAP_RADIUS = 18.0f;
    private static final int[] DIGIT_KEYS = {ImGuiKey._0, ImGuiKey._1, ImGuiKey._2, ImGuiKey._3, ImGuiKey._4, ImGuiKey._5, ImGuiKey._6, ImGuiKey._7, ImGuiKey._8, ImGuiKey._9};
    private static final int[] PAD_KEYS = {ImGuiKey.Keypad0, ImGuiKey.Keypad1, ImGuiKey.Keypad2, ImGuiKey.Keypad3, ImGuiKey.Keypad4, ImGuiKey.Keypad5, ImGuiKey.Keypad6, ImGuiKey.Keypad7, ImGuiKey.Keypad8, ImGuiKey.Keypad9};

    private record Start(Matrix4f matrix, CFrame world, Vector3 size, Vector3 position) {}

    private final SceneDocument document;
    private final GizmoState state;
    private final FaceHandles faceHandles = new FaceHandles();
    private final float[] model = new float[16];
    private final float[] snap = new float[3];
    private final Map<Integer, Start> starts = new LinkedHashMap<>();
    private final StringBuilder typed = new StringBuilder();
    private @Nullable Matrix4f gizmoStart;
    private @Nullable Vector3 groupCentre;
    private @Nullable Vector3 snapOffset;
    private @Nullable Vector3 snapTarget;

    TransformGizmo(SceneDocument document, GizmoState state) {
        this.document = document;
        this.state = state;
    }

    void reset() {
        starts.clear();
        gizmoStart = null;
        typed.setLength(0);
        snapOffset = null;
    }

    boolean dragging() {
        return gizmoStart != null;
    }

    boolean render(ImDrawList draw, SceneView view, Vector3 camera, boolean orthographic, boolean hovered, boolean snapping) {
        Integer operation = state.operation().orElse(null);
        Instance leader = document.primary();
        if (operation == null || !(leader instanceof Spatial) || !document.editable(leader)) {
            reset();
            return false;
        }
        List<Instance> roots = Manipulate.spatialRoots(document);
        boolean groupScale = state.tool() == GizmoState.Tool.SCALE && (roots.size() > 1 || !(leader instanceof Part));
        if (state.tool() == GizmoState.Tool.SCALE && !groupScale && leader instanceof Part part) {
            return faceHandles.render(draw, view, document, part, hovered, snapping, state.gridStep());
        }
        ImGuizmo.setOrthographic(orthographic);
        ImGuizmo.setDrawList();
        ImGuizmo.setGizmoSizeClipSpace(GIZMO_SIZE_CLIP_SPACE);
        ImGuizmo.setRect(view.originX(), view.originY(), view.width(), view.height());

        if (gizmoStart == null || state.tool() == GizmoState.Tool.PIVOT) handle(leader, roots, camera, groupScale).get(model);
        int mode = state.tool() == GizmoState.Tool.PIVOT || state.space() != GizmoState.Space.WORLD ? Mode.LOCAL : Mode.WORLD;
        if (groupScale) mode = Mode.WORLD;
        if (snapping) {
            float step = state.snapStep();
            snap[0] = step;
            snap[1] = step;
            snap[2] = step;
            ImGuizmo.manipulate(view.view, view.projection, operation, mode, model, null, snap);
        } else {
            ImGuizmo.manipulate(view.view, view.projection, operation, mode, model);
        }
        if (state.tool() == GizmoState.Tool.PIVOT) drawPivot(draw, view, leader);
        if (!ImGuizmo.isUsing()) {
            if (gizmoStart != null) finish();
            return ImGuizmo.isOver();
        }
        if (gizmoStart == null) begin(leader, roots, camera, groupScale);
        Matrix4f moved = new Matrix4f().set(model);
        readTyping();
        if (state.tool() == GizmoState.Tool.PIVOT) {
            movePivot(leader, moved, camera);
            return true;
        }
        if (state.tool() == GizmoState.Tool.TRANSLATE) moved = vertexSnap(draw, view, leader, moved, camera);
        moved = typedValue(draw, moved);
        apply(moved, camera, groupScale);
        return true;
    }

    private Matrix4f handle(Instance leader, List<Instance> roots, Vector3 camera, boolean groupScale) {
        if (groupScale) {
            Vector3 centre = Manipulate.centre(roots).sub(camera);
            return new Matrix4f().translation((float) centre.x(), (float) centre.y(), (float) centre.z());
        }
        CFrame world = Transforms.world(leader);
        Vector3 at = world.position();
        Quat turn = world.rotation();
        if (state.tool() == GizmoState.Tool.PIVOT) {
            at = world.pointToWorld(((Spatial) leader).pivot);
        } else if (state.space() == GizmoState.Space.PARENT) {
            turn = leader.parent() instanceof Spatial ? Transforms.world(leader.parent()).rotation() : Quat.IDENTITY;
        }
        Vector3 local = at.sub(camera);
        return new Matrix4f().translation((float) local.x(), (float) local.y(), (float) local.z()).rotate(quaternion(turn));
    }

    private void begin(Instance leader, List<Instance> roots, Vector3 camera, boolean groupScale) {
        gizmoStart = new Matrix4f().set(model);
        typed.setLength(0);
        starts.clear();
        List<Instance> moving = new ArrayList<>();
        if (!groupScale && state.tool() != GizmoState.Tool.PIVOT && !roots.contains(leader)) moving.add(leader);
        moving.addAll(roots);
        for (Instance instance : moving) {
            CFrame world = Transforms.world(instance);
            Vector3 size = instance instanceof Part part ? part.size : Vector3.ONE;
            starts.put(instance.id(), new Start(Frames.matrix(instance, camera), world, size, world.position()));
        }
        groupCentre = Manipulate.centre(roots);
        snapOffset = null;
        if (state.tool() != GizmoState.Tool.PIVOT && ImGui.getIO().getKeyShift()) {
            document.pasteTransformed(document.selectedRoots(), frame -> frame, "Duplicate", false);
        }
    }

    private void finish() {
        reset();
    }

    private void apply(Matrix4f moved, Vector3 camera, boolean groupScale) {
        Matrix4f delta = new Matrix4f(moved).mul(new Matrix4f(gizmoStart).invert());
        List<Edit> edits = new ArrayList<>();
        if (groupScale) {
            Vector3f scale = moved.getScale(new Vector3f());
            double factor = dominant(scale);
            for (Map.Entry<Integer, Start> entry : starts.entrySet()) {
                Instance instance = document.find(entry.getKey());
                if (!(instance instanceof Spatial)) continue;
                Start start = entry.getValue();
                Vector3 position = groupCentre.add(start.position().sub(groupCentre).mul(factor));
                edits.addAll(Frames.worldWrites(document, instance, start.world().withPosition(position),
                        instance instanceof Part ? start.size().mul(factor) : null));
                scaleDescendants(instance, factor, edits);
            }
            document.history().execute(new Batch("Scale", edits));
            return;
        }
        Quaternionf turn = delta.getNormalizedRotation(new Quaternionf());
        Vector3f shift = new Vector3f();
        new Matrix4f(delta).transformPosition(new Vector3f(gizmoStart.m30(), gizmoStart.m31(), gizmoStart.m32()), shift)
                .sub(gizmoStart.m30(), gizmoStart.m31(), gizmoStart.m32());
        boolean individual = state.individualPivots() && state.tool() == GizmoState.Tool.ROTATE;
        for (Map.Entry<Integer, Start> entry : starts.entrySet()) {
            Instance instance = document.find(entry.getKey());
            if (instance == null) continue;
            Start start = entry.getValue();
            Matrix4f target;
            if (individual) {
                Vector3f origin = start.matrix().getTranslation(new Vector3f());
                target = new Matrix4f().translation(origin).rotate(turn).mul(new Matrix4f(start.matrix()).setTranslation(0, 0, 0));
            } else {
                target = new Matrix4f(delta).mul(start.matrix());
            }
            edits.addAll(Frames.writes(document, instance, target, camera, false));
        }
        String label = state.tool() == GizmoState.Tool.ROTATE ? "Rotate" : "Move";
        document.history().execute(new Batch(label, edits));
    }

    private void scaleDescendants(Instance root, double factor, List<Edit> edits) {
        for (Instance child : root.children()) {
            if (child instanceof Spatial spatial && document.editable(child)) {
                PropertyDef frame = child.def().property("cframe");
                CFrame local = spatial.cframe;
                edits.add(new SetProperty(document.ref(child.id()), frame.index(), local.withPosition(local.position().mul(factor)), "Transform"));
                if (child instanceof Part part) {
                    edits.add(new SetProperty(document.ref(child.id()), child.def().property("size").index(), part.size.mul(factor), "Transform"));
                }
            }
            scaleDescendants(child, factor, edits);
        }
    }

    private static double dominant(Vector3f scale) {
        double best = 1;
        for (double s : new double[] {scale.x, scale.y, scale.z}) {
            if (Math.abs(s - 1) > Math.abs(best - 1)) best = s;
        }
        return Math.max(0.01, best);
    }

    private void movePivot(Instance leader, Matrix4f moved, Vector3 camera) {
        Start start = starts.get(leader.id());
        if (start == null || !(leader instanceof Spatial spatial)) return;
        Vector3f at = moved.getTranslation(new Vector3f());
        Vector3 world = new Vector3(at.x + camera.x(), at.y + camera.y(), at.z + camera.z());
        Vector3 pivot = start.world().pointToObject(world);
        CFrame local = Transforms.localFor(leader, start.world()).mul(CFrame.at(pivot));
        List<Edit> edits = List.of(
                new SetProperty(document.ref(leader.id()), leader.def().property("pivot").index(), pivot, "Pivot"),
                new SetProperty(document.ref(leader.id()), leader.def().property("cframe").index(), local, "Pivot"));
        document.history().execute(new Batch("Move pivot", edits));
    }

    private void drawPivot(ImDrawList draw, SceneView view, Instance leader) {
        CFrame world = Transforms.world(leader);
        float[] at = view.toScreen(world.pointToWorld(((Spatial) leader).pivot));
        if (at == null) return;
        float r = EditorScale.of(6.0f);
        draw.addQuadFilled(at[0], at[1] - r, at[0] + r, at[1], at[0], at[1] + r, at[0] - r, at[1], EditorStyle.COLOR_HIGHLIGHT);
    }

    private Matrix4f vertexSnap(ImDrawList draw, SceneView view, Instance leader, Matrix4f moved, Vector3 camera) {
        if (!ImGui.isKeyDown(ImGuiKey.V)) {
            snapOffset = null;
            return moved;
        }
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        Start start = starts.get(leader.id());
        if (start == null) return moved;
        if (snapOffset == null && leader instanceof Part part) {
            Vector3 nearest = nearestPoint(view, List.of(part), mouseX, mouseY, Double.MAX_VALUE);
            snapOffset = nearest == null ? Vector3.ZERO : nearest.sub(Transforms.world(part).position());
        }
        List<Part> others = new ArrayList<>();
        Instance world = document.world();
        if (world != null) collectParts(world, others);
        others.removeIf(part -> starts.containsKey(part.id()) || document.selection().isSelected(part.id()));
        Vector3 target = nearestPoint(view, others, mouseX, mouseY, EditorScale.of(SNAP_RADIUS));
        snapTarget = target;
        if (target == null) return moved;
        float[] mark = view.toScreen(target);
        if (mark != null) draw.addCircle(mark[0], mark[1], EditorScale.of(7.0f), EditorStyle.COLOR_HIGHLIGHT, 16, 2.0f);
        Vector3 centre = target.sub(snapOffset == null ? Vector3.ZERO : snapOffset).sub(camera);
        return new Matrix4f(moved).setTranslation((float) centre.x(), (float) centre.y(), (float) centre.z());
    }

    private void collectParts(Instance at, List<Part> into) {
        for (Instance child : at.children()) {
            if (child instanceof ViewportFrame) continue;
            if (child instanceof Part part && part.visible) into.add(part);
            collectParts(child, into);
        }
    }

    private static @Nullable Vector3 nearestPoint(SceneView view, List<Part> parts, float mouseX, float mouseY, double radius) {
        Vector3 best = null;
        double bestDistance = radius * radius;
        for (Part part : parts) {
            CFrame world = Transforms.world(part);
            Vector3 half = part.size.mul(0.5);
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Vector3 point = world.pointToWorld(new Vector3(half.x() * x, half.y() * y, half.z() * z));
                        float[] screen = view.toScreen(point);
                        if (screen == null) continue;
                        double dx = screen[0] - mouseX;
                        double dy = screen[1] - mouseY;
                        double distance = dx * dx + dy * dy;
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = point;
                        }
                    }
                }
            }
        }
        return best;
    }

    private void readTyping() {
        for (int n = 0; n < 10; n++) {
            if (ImGui.isKeyPressed(DIGIT_KEYS[n], false) || ImGui.isKeyPressed(PAD_KEYS[n], false)) typed.append(n);
        }
        if (ImGui.isKeyPressed(ImGuiKey.Period, false) || ImGui.isKeyPressed(ImGuiKey.KeypadDecimal, false) || ImGui.isKeyPressed(ImGuiKey.Comma, false)) {
            if (typed.indexOf(".") < 0) typed.append('.');
        }
        if (ImGui.isKeyPressed(ImGuiKey.Minus, false) || ImGui.isKeyPressed(ImGuiKey.KeypadSubtract, false)) {
            if (typed.length() > 0 && typed.charAt(0) == '-') typed.deleteCharAt(0);
            else typed.insert(0, '-');
        }
        if (ImGui.isKeyPressed(ImGuiKey.Backspace, false) && typed.length() > 0) typed.setLength(typed.length() - 1);
    }

    private Matrix4f typedValue(ImDrawList draw, Matrix4f moved) {
        if (typed.length() == 0 || typed.toString().equals("-") || typed.toString().equals(".")) return moved;
        double value;
        try {
            value = Double.parseDouble(typed.toString());
        } catch (NumberFormatException e) {
            return moved;
        }
        Matrix4f result;
        String text;
        if (state.tool() == GizmoState.Tool.ROTATE) {
            Matrix4f delta = new Matrix4f(moved).mul(new Matrix4f(gizmoStart).invert());
            AxisAngle4f turn = new AxisAngle4f().set(delta.getNormalizedRotation(new Quaternionf()));
            Vector3f axis = new Vector3f(turn.x, turn.y, turn.z);
            if (axis.lengthSquared() < 1e-6f) axis.set(0, 1, 0);
            if (turn.angle < 0) axis.negate();
            Vector3f origin = gizmoStart.getTranslation(new Vector3f());
            result = new Matrix4f().translation(origin).rotate((float) Math.toRadians(value), axis.normalize())
                    .mul(new Matrix4f(gizmoStart).setTranslation(0, 0, 0));
            text = String.format(Locale.ROOT, "%s°", typed);
        } else {
            Vector3f from = gizmoStart.getTranslation(new Vector3f());
            Vector3f to = moved.getTranslation(new Vector3f());
            Vector3f change = to.sub(from, new Vector3f());
            Vector3f[] axes = {gizmoStart.getColumn(0, new Vector3f()).normalize(), gizmoStart.getColumn(1, new Vector3f()).normalize(), gizmoStart.getColumn(2, new Vector3f()).normalize()};
            if (state.space() == GizmoState.Space.WORLD) axes = new Vector3f[] {new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)};
            int major = 0;
            for (int i = 1; i < 3; i++) {
                if (Math.abs(change.dot(axes[i])) > Math.abs(change.dot(axes[major]))) major = i;
            }
            double sign = change.dot(axes[major]) < 0 ? -1 : 1;
            Vector3f offset = new Vector3f(axes[major]).mul((float) (value * sign));
            result = new Matrix4f(gizmoStart).setTranslation(from.add(offset, new Vector3f()));
            text = String.format(Locale.ROOT, "%s m along %s", typed, "XYZ".charAt(major));
        }
        float x = ImGui.getMousePosX() + EditorScale.of(16);
        float y = ImGui.getMousePosY() + EditorScale.of(12);
        float width = ImGui.calcTextSize(text).x;
        draw.addRectFilled(x - 4, y - 2, x + width + 4, y + ImGui.getTextLineHeight() + 2, EditorStyle.withAlpha(EditorStyle.COLOR_WINDOW_BACKGROUND, 0.9f), 3);
        draw.addText(x, y, EditorStyle.COLOR_TEXT, text);
        return result;
    }

    private static Quaternionf quaternion(Quat q) {
        return new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w());
    }
}
