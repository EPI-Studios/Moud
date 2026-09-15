package com.meekdev.moud.mod.client.editor.viewport;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.query.Queries;
import com.meekdev.moud.mod.adapter.physics.BlockRays;
import com.meekdev.moud.mod.adapter.render.EditorOverlay;
import com.meekdev.moud.script.api.BlockRef;
import com.meekdev.moud.mod.adapter.render.ViewportCapture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import foundry.imgui.api.ImGuiMC;
import foundry.imgui.impl.ImGuiMCImpl;
import com.meekdev.moud.mod.client.editor.EditMode;
import com.meekdev.moud.mod.client.editor.document.Batch;
import com.meekdev.moud.mod.client.editor.document.Edit;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.kit.Toolbars;
import com.meekdev.moud.mod.client.editor.panel.Panel;
import com.meekdev.moud.mod.client.editor.style.ClassIcons;
import com.meekdev.moud.mod.client.editor.style.EditorIcon;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.extension.imguizmo.ImGuizmo;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public final class ViewportPanel implements Panel {

    public static final String ID = "viewport";

    private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
    private static final float GIZMO_SIZE_CLIP_SPACE = 0.14f;
    private static final double SPAWN_REACH = 256.0;
    private static final double SPAWN_FALLBACK = 12.0;
    private static final BlockRays BLOCKS = new BlockRays(() -> Minecraft.getInstance().level, false);
    private static final float TOOLBAR_MARGIN_X = 6.0f;
    private static final float TOOLBAR_MARGIN_Y = 4.0f;
    private static final float BILLBOARD_HALF_SIZE = 11.0f;
    private static final float BILLBOARD_CLICK_RADIUS = 14.0f;
    private static final float BILLBOARD_SHADOW_RADIUS = 14.0f;
    private static final int BILLBOARD_SHADOW_COLOR = 0x78000000;
    private static final float DRAG_THRESHOLD = 4.0f;
    private static final float OUTLINE_THICKNESS = 2.0f;
    private static final int BOX_FILL = EditorStyle.withAlpha(EditorStyle.COLOR_ACCENT, 0.08f);
    private static final int BOX_BORDER = EditorStyle.withAlpha(EditorStyle.COLOR_ACCENT, 0.7f);
    private static final float[] GRID_STEPS = {0.125f, 0.25f, 0.5f, 1.0f, 2.0f, 4.0f};
    private static final float[] ANGLE_STEPS = {5.0f, 15.0f, 45.0f, 90.0f};

    private final SceneDocument document;
    private final IconWidgets icons;
    private final GizmoState gizmoState = new GizmoState();
    private final EditorCamera camera = new EditorCamera();
    private final SceneView view = new SceneView();
    private final float[] model = new float[16];
    private final float[] snap = new float[3];
    private final Map<Integer, Matrix4f> dragStart = new LinkedHashMap<>();
    private final FaceHandles faceHandles = new FaceHandles();
    private final SurfaceDrag surfaceDrag = new SurfaceDrag();
    private int session = -1;
    private boolean hovered;
    private boolean lookGesture;
    private boolean orbitGesture;
    private boolean boxing;
    private float pressX;
    private float pressY;
    private @Nullable Instance hoveredInstance;
    private Vector3 cameraPosition = Vector3.ZERO;

    public ViewportPanel(SceneDocument document, IconWidgets icons) {
        this.document = document;
        this.icons = icons;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Viewport";
    }

    @Override
    public int windowFlags() {
        return WINDOW_FLAGS;
    }

    @Override
    public void render() {
        placeCameraOnEntry();
        renderToolbar();
        float deltaSeconds = ImGui.getIO().getDeltaTime();
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        float right = left + ImGui.getContentRegionAvailX();
        float bottom = top + ImGui.getContentRegionAvailY();
        ImGui.dummy(Math.max(1.0f, right - left), Math.max(1.0f, bottom - top));
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        hovered = ImGui.isWindowHovered() && mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
        ViewportCapture.show(argb(EditorStyle.COLOR_WINDOW_BACKGROUND));
        RenderTarget frame = ViewportCapture.frame();
        if (frame != null && view.capture() && placeFrame(frame, left, top, right, bottom)) {
            cameraPosition = view.cameraPosition();
            ImDrawList drawList = ImGui.getWindowDrawList();
            drawList.pushClipRect(left, top, right, bottom, true);
            updateCamera(deltaSeconds);
            if (hovered && !lookGesture) EditorOverlay.requestPick((mouseX - view.originX()) / view.width(), (mouseY - view.originY()) / view.height());
            hoveredInstance = hovered && !lookGesture ? pickAt(mouseX, mouseY) : null;
            EditorOverlay.show(document::editable, Set.copyOf(document.selection().all()),
                    hoveredInstance instanceof Part part && !document.selection().isSelected(part.id()) ? part.id() : 0);
            drawBillboards(drawList);
            boolean gizmoBusy = renderGizmo();
            handlePicking(gizmoBusy, drawList);
            drawList.popClipRect();
            handleFrameShortcut();
            handleToolHotkeys();
        } else {
            Texts.muted("Waiting for the camera");
        }
        camera.apply();
    }

    private boolean placeFrame(RenderTarget frame, float left, float top, float right, float bottom) {
        float viewWidth = right - left;
        float viewHeight = bottom - top;
        if (viewWidth < 1.0f || viewHeight < 1.0f || frame.width <= 0 || frame.height <= 0) return false;
        float viewAspect = viewWidth / viewHeight;
        float frameAspect = (float) frame.width / frame.height;
        float cropU = viewAspect > frameAspect ? 1.0f : viewAspect / frameAspect;
        float cropV = viewAspect > frameAspect ? frameAspect / viewAspect : 1.0f;
        float u0 = (1.0f - cropU) * 0.5f;
        float v0 = (1.0f - cropV) * 0.5f;
        float fullWidth = viewWidth / cropU;
        float fullHeight = viewHeight / cropV;
        view.frame(left - u0 * fullWidth, top - v0 * fullHeight, fullWidth, fullHeight);
        long texture = ImGuiMCImpl.handler.getRenderer().getImGuiId(ImGuiMC.getColorTexture(frame), null);
        ImGui.getWindowDrawList().addImage(texture, left, top, right, bottom, u0, 1.0f - v0, u0 + cropU, 1.0f - v0 - cropV);
        return true;
    }

    private static int argb(int abgr) {
        int a = (abgr >>> 24) & 0xFF;
        int b = (abgr >>> 16) & 0xFF;
        int g = (abgr >>> 8) & 0xFF;
        int r = abgr & 0xFF;
        return a << 24 | r << 16 | g << 8 | b;
    }

    private void placeCameraOnEntry() {
        if (session == EditMode.session()) return;
        session = EditMode.session();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) camera.placeAt(player.getEyePosition(), player.getYRot(), player.getXRot());
        dragStart.clear();
    }

    private void renderToolbar() {
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, EditorScale.of(TOOLBAR_MARGIN_X), EditorScale.of(TOOLBAR_MARGIN_Y));
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, EditorStyle.itemSpacingX(), 0.0f);
        ImGui.beginChild("##viewport-toolbar", 0.0f, ImGui.getFrameHeight() + EditorScale.of(TOOLBAR_MARGIN_Y) * 2.0f, false);
        Toolbars.pushFlatButtons();
        renderToolButton("tool-select", EditorIcon.TOOL_SELECT, GizmoState.Tool.SELECT, "Select (Q)");
        ImGui.sameLine();
        renderToolButton("tool-move", EditorIcon.TOOL_MOVE, GizmoState.Tool.TRANSLATE, "Move (W)");
        ImGui.sameLine();
        renderToolButton("tool-rotate", EditorIcon.TOOL_ROTATE, GizmoState.Tool.ROTATE, "Rotate (R)");
        ImGui.sameLine();
        renderToolButton("tool-scale", EditorIcon.TOOL_SCALE, GizmoState.Tool.SCALE, "Scale (S)");
        Toolbars.groupSeparator();
        if (Toolbars.textButton(gizmoState.worldSpace() ? "World##toolbar-gizmo-space" : "Local##toolbar-gizmo-space")) gizmoState.toggleSpace();
        tooltip("Toggle the gizmo between world and local space (X)");
        Toolbars.groupSeparator();
        if (icons.toggleButton("toolbar-snap", EditorIcon.SNAP, EditorStyle.iconSizeToolbar(), gizmoState.snapEnabled())) gizmoState.toggleSnap();
        tooltip("Snap, hold Ctrl to invert");
        ImGui.sameLine();
        if (Toolbars.textButton(step(gizmoState.gridStep()) + " m##toolbar-grid")) ImGui.openPopup("##grid-steps");
        tooltip("Grid step for moving, surface dragging and resizing");
        ImGui.sameLine();
        if (Toolbars.textButton(step(gizmoState.angleStep()) + "\u00b0##toolbar-angle")) ImGui.openPopup("##angle-steps");
        tooltip("Angle step for rotating");
        Toolbars.groupSeparator();
        if (Toolbars.textButton("Arrange##toolbar-arrange")) ImGui.openPopup("##arrange");
        tooltip("Align, distribute and group the selection");
        renderStepPopup("##grid-steps", GRID_STEPS, gizmoState.gridStep(), " m", gizmoState::gridStep);
        renderStepPopup("##angle-steps", ANGLE_STEPS, gizmoState.angleStep(), "\u00b0", gizmoState::angleStep);
        renderArrangePopup();
        Toolbars.popFlatButtons();
        ImGui.endChild();
        ImGui.popStyleVar(2);
    }

    private static String step(float value) {
        String text = String.format(java.util.Locale.ROOT, "%.3f", value);
        text = text.replaceAll("0+$", "");
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private static void renderStepPopup(String id, float[] steps, float current, String unit, java.util.function.Consumer<Float> choose) {
        if (!ImGui.beginPopup(id)) return;
        for (float value : steps) {
            if (ImGui.menuItem(step(value) + unit, "", value == current)) choose.accept(value);
        }
        ImGui.endPopup();
    }

    private void renderArrangePopup() {
        if (!ImGui.beginPopup("##arrange")) return;
        boolean several = document.selectedRoots().size() > 1;
        String[] axes = {"X", "Y", "Z"};
        ImGui.beginDisabled(!several);
        for (int axis = 0; axis < 3; axis++) {
            ImGui.alignTextToFramePadding();
            ImGui.text(axes[axis]);
            ImGui.sameLine();
            if (ImGui.button("Min##align-min-" + axis)) Arrange.align(document, axis, Arrange.Edge.MIN);
            ImGui.sameLine();
            if (ImGui.button("Centre##align-mid-" + axis)) Arrange.align(document, axis, Arrange.Edge.CENTRE);
            ImGui.sameLine();
            if (ImGui.button("Max##align-max-" + axis)) Arrange.align(document, axis, Arrange.Edge.MAX);
            ImGui.sameLine();
            ImGui.beginDisabled(document.selectedRoots().size() < 3);
            if (ImGui.button("Distribute##distribute-" + axis)) Arrange.distribute(document, axis);
            ImGui.endDisabled();
        }
        ImGui.endDisabled();
        ImGui.separator();
        ImGui.beginDisabled(document.selectedRoots().isEmpty());
        if (ImGui.menuItem("Group", "Ctrl+G")) document.group();
        if (ImGui.menuItem("Ungroup", "Ctrl+U")) document.ungroup();
        ImGui.endDisabled();
        ImGui.endPopup();
    }

    private void renderToolButton(String id, EditorIcon icon, GizmoState.Tool tool, String tip) {
        if (icons.toggleButton(id, icon, EditorStyle.iconSizeToolbar(), gizmoState.tool() == tool)) gizmoState.setTool(tool);
        tooltip(tip);
    }

    private static void tooltip(String text) {
        if (ImGui.isItemHovered()) ImGui.setTooltip(text);
    }

    private boolean heldGesture(boolean alreadyRunning, boolean buttonHeld) {
        if (!buttonHeld) return false;
        return alreadyRunning || hovered;
    }

    private void updateCamera(float deltaSeconds) {
        camera.updateFraming(deltaSeconds);
        boolean rightHeld = heldGesture(lookGesture, ImGui.isMouseDown(ImGuiMouseButton.Right));
        lookGesture = rightHeld;
        boolean orbitHeld = heldGesture(orbitGesture, !rightHeld && ImGui.getIO().getKeyAlt() && ImGui.isMouseDown(ImGuiMouseButton.Left));
        orbitGesture = orbitHeld;
        camera.updateLook(ImGui.getMousePosX(), ImGui.getMousePosY(), rightHeld);
        camera.updateOrbit(ImGui.getMousePosX(), ImGui.getMousePosY(), orbitHeld);
        applyScrollNavigation(rightHeld);
        if (!rightHeld) return;
        ImGui.setWindowFocus();
        camera.updateMovement(keyDown(GLFW.GLFW_KEY_W), keyDown(GLFW.GLFW_KEY_S), keyDown(GLFW.GLFW_KEY_A), keyDown(GLFW.GLFW_KEY_D),
                keyDown(GLFW.GLFW_KEY_SPACE), keyDown(GLFW.GLFW_KEY_LEFT_SHIFT), keyDown(GLFW.GLFW_KEY_LEFT_CONTROL), deltaSeconds);
    }

    private static boolean keyDown(int key) {
        Window window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, key);
    }

    private void applyScrollNavigation(boolean rightHeld) {
        if (!hovered) return;
        float wheel = ImGui.getIO().getMouseWheel();
        if (wheel == 0.0f) return;
        if (rightHeld) camera.applyZoom(wheel);
        else camera.applyDolly(wheel);
    }

    private void handleFrameShortcut() {
        if (!hovered || ImGui.getIO().getWantTextInput() || !ImGui.isKeyPressed(ImGuiKey.F, false)) return;
        frameSelection();
    }

    public boolean flying() {
        return lookGesture || orbitGesture;
    }

    public Vector3 spawnPoint() {
        Vector3d eye = camera.position();
        Vector3d ahead = camera.forward(new Vector3d());
        Vector3 from = new Vector3(eye.x, eye.y, eye.z);
        Vector3 direction = new Vector3(ahead.x, ahead.y, ahead.z);
        double best = SPAWN_REACH;
        Instance world = document.world();
        if (world != null) {
            Queries.Cast part = Queries.raycast(world, from, direction, SPAWN_REACH, document::editable);
            if (part != null) best = part.distance();
        }
        BlockRef.Hit block = BLOCKS.raycast(from, direction, best);
        if (block != null) best = Math.min(best, block.distance());
        return best < SPAWN_REACH ? from.add(direction.mul(best)) : from.add(direction.mul(SPAWN_FALLBACK));
    }

    public void frameSelection() {
        List<Instance> selected = new ArrayList<>();
        for (int id : document.selection().all()) {
            Instance instance = document.find(id);
            if (instance != null && Frames.center(instance) != null) selected.add(instance);
        }
        if (selected.isEmpty()) return;
        Vector3 sum = Vector3.ZERO;
        for (Instance instance : selected) sum = sum.add(Frames.center(instance));
        Vector3 center = sum.mul(1.0 / selected.size());
        double radius = 1.0;
        for (Instance instance : selected) radius = Math.max(radius, center.distance(Frames.center(instance)) + Frames.radius(instance));
        camera.frame(new Vector3d(center.x(), center.y(), center.z()), radius);
    }

    private void handleToolHotkeys() {
        if (lookGesture || ImGui.getIO().getWantTextInput() || ImGui.getIO().getKeyCtrl() || !(hovered || ImGui.isWindowFocused())) return;
        if (ImGui.isKeyPressed(ImGuiKey.Q, false)) gizmoState.setTool(GizmoState.Tool.SELECT);
        if (ImGui.isKeyPressed(ImGuiKey.W, false)) gizmoState.setTool(GizmoState.Tool.TRANSLATE);
        if (ImGui.isKeyPressed(ImGuiKey.R, false)) gizmoState.setTool(GizmoState.Tool.ROTATE);
        if (ImGui.isKeyPressed(ImGuiKey.S, false)) gizmoState.setTool(GizmoState.Tool.SCALE);
        if (ImGui.isKeyPressed(ImGuiKey.X, false)) gizmoState.toggleSpace();
        if (ImGui.isKeyPressed(ImGuiKey.Space, false)) gizmoState.toggleAlternateTool();
    }

    private boolean renderGizmo() {
        Integer operation = gizmoState.operation().orElse(null);
        Instance leader = document.primary();
        if (operation == null || !(leader instanceof Spatial) || !document.editable(leader)) {
            dragStart.clear();
            return false;
        }
        if (gizmoState.tool() == GizmoState.Tool.SCALE && leader instanceof Part part) {
            return faceHandles.render(ImGui.getWindowDrawList(), view, document, part, hovered, snapActive(), gizmoState.gridStep());
        }
        ImGuizmo.setOrthographic(false);
        ImGuizmo.setDrawList();
        ImGuizmo.setGizmoSizeClipSpace(GIZMO_SIZE_CLIP_SPACE);
        ImGuizmo.setRect(view.originX(), view.originY(), view.width(), view.height());
        Matrix4f current = Frames.matrix(leader, cameraPosition);
        current.get(model);
        if (snapActive()) {
            float step = gizmoState.snapStep();
            snap[0] = step;
            snap[1] = step;
            snap[2] = step;
            ImGuizmo.manipulate(view.view, view.projection, operation, gizmoState.mode(), model, null, snap);
        } else {
            ImGuizmo.manipulate(view.view, view.projection, operation, gizmoState.mode(), model);
        }
        boolean using = ImGuizmo.isUsing();
        if (!using) {
            dragStart.clear();
            return ImGuizmo.isOver();
        }
        if (dragStart.isEmpty()) captureDragStart(leader);
        Matrix4f moved = new Matrix4f().set(model);
        if (!moved.equals(current, 1.0e-6f)) applyDrag(leader, moved);
        return true;
    }

    private boolean snapActive() {
        return gizmoState.snapEnabled() != ImGui.getIO().getKeyCtrl();
    }

    private void captureDragStart(Instance leader) {
        dragStart.put(leader.id(), Frames.matrix(leader, cameraPosition));
        if (gizmoState.tool() == GizmoState.Tool.SCALE) return;
        List<Instance> selected = new ArrayList<>();
        for (int id : document.selection().all()) {
            Instance instance = document.find(id);
            if (instance != leader && instance instanceof Spatial && document.editable(instance)) selected.add(instance);
        }
        for (Instance instance : selected) {
            if (!hasSelectedAncestor(instance, selected, leader)) dragStart.put(instance.id(), Frames.matrix(instance, cameraPosition));
        }
    }

    private static boolean hasSelectedAncestor(Instance instance, List<Instance> selected, Instance leader) {
        for (Instance at = instance.parent(); at != null; at = at.parent()) {
            if (at == leader || selected.contains(at)) return true;
        }
        return false;
    }

    private void applyDrag(Instance leader, Matrix4f moved) {
        boolean resize = gizmoState.tool() == GizmoState.Tool.SCALE;
        Matrix4f leaderStart = dragStart.get(leader.id());
        Matrix4f delta = new Matrix4f(moved).mul(new Matrix4f(leaderStart).invert());
        List<Edit> edits = new ArrayList<>(Frames.writes(document, leader, moved, cameraPosition, resize));
        for (Map.Entry<Integer, Matrix4f> entry : dragStart.entrySet()) {
            if (entry.getKey() == leader.id()) continue;
            Instance follower = document.find(entry.getKey());
            if (follower == null) continue;
            edits.addAll(Frames.writes(document, follower, new Matrix4f(delta).mul(entry.getValue()), cameraPosition, false));
        }
        String label = switch (gizmoState.tool()) {
            case ROTATE -> "Rotate";
            case SCALE -> "Scale";
            default -> "Move";
        };
        document.history().execute(new Batch(label, edits));
    }

    private void handlePicking(boolean gizmoBusy, ImDrawList drawList) {
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        if (hovered && !gizmoBusy && !lookGesture && !ImGui.getIO().getKeyAlt() && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            boxing = true;
            pressX = mouseX;
            pressY = mouseY;
            armSurfaceDrag(mouseX, mouseY);
        }
        if (!boxing) return;
        boolean dragged = Math.abs(mouseX - pressX) > DRAG_THRESHOLD || Math.abs(mouseY - pressY) > DRAG_THRESHOLD;
        if (surfaceDrag.armed()) {
            if (ImGui.isMouseDown(ImGuiMouseButton.Left)) {
                if (dragged || surfaceDrag.started()) surfaceDrag.update(document, view, mouseX, mouseY, snapActive(), gizmoState.gridStep());
                return;
            }
            boxing = false;
            if (!surfaceDrag.started()) applyPick(pickAt(mouseX, mouseY));
            surfaceDrag.cancel();
            return;
        }
        if (ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            if (dragged) {
                float x0 = Math.min(pressX, mouseX);
                float y0 = Math.min(pressY, mouseY);
                float x1 = Math.max(pressX, mouseX);
                float y1 = Math.max(pressY, mouseY);
                drawList.addRectFilled(x0, y0, x1, y1, BOX_FILL);
                drawList.addRect(x0, y0, x1, y1, BOX_BORDER);
            }
            return;
        }
        boxing = false;
        if (dragged) boxSelect(Math.min(pressX, mouseX), Math.min(pressY, mouseY), Math.max(pressX, mouseX), Math.max(pressY, mouseY));
        else applyPick(pickAt(mouseX, mouseY));
    }

    private void armSurfaceDrag(float mouseX, float mouseY) {
        surfaceDrag.cancel();
        GizmoState.Tool tool = gizmoState.tool();
        if (tool != GizmoState.Tool.SELECT && tool != GizmoState.Tool.TRANSLATE) return;
        if (ImGui.getIO().getKeyCtrl() || ImGui.getIO().getKeyShift()) return;
        if (!(pickAt(mouseX, mouseY) instanceof Part part)) return;
        Instance world = document.world();
        if (world == null) return;
        Vector3 from = view.cameraPosition();
        Queries.Cast cast = Queries.raycast(world, from, view.rayDirection(mouseX, mouseY), SPAWN_REACH, candidate -> candidate == part);
        if (!document.selection().isSelected(part.id())) document.selection().select(part.id());
        surfaceDrag.arm(document, part, cast == null ? Frames.center(part) : cast.at());
    }

    private void applyPick(@Nullable Instance picked) {
        boolean additive = ImGui.getIO().getKeyCtrl() || ImGui.getIO().getKeyShift();
        if (picked == null) {
            if (!additive) document.selection().clear();
            return;
        }
        if (additive) document.selection().toggle(picked.id());
        else document.selection().select(picked.id());
    }

    private void boxSelect(float x0, float y0, float x1, float y1) {
        boolean additive = ImGui.getIO().getKeyCtrl() || ImGui.getIO().getKeyShift();
        if (!additive) document.selection().clear();
        forEachEditableSpatial(instance -> {
            if (instance instanceof Part part) {
                List<float[]> projected = new ArrayList<>(8);
                for (Vector3 corner : Frames.corners(part)) {
                    float[] screen = view.toScreen(corner);
                    if (screen != null) projected.add(screen);
                }
                if (ScreenShapes.overlapsRect(ScreenShapes.hull(projected), x0, y0, x1, y1)) document.selection().add(instance.id());
                return;
            }
            float[] screen = view.toScreen(Frames.center(instance));
            if (screen != null && screen[0] >= x0 && screen[0] <= x1 && screen[1] >= y0 && screen[1] <= y1) {
                document.selection().add(instance.id());
            }
        });
    }

    private @Nullable Instance pickAt(float mouseX, float mouseY) {
        Instance billboard = billboardAt(mouseX, mouseY);
        if (billboard != null) return billboard;
        Instance picked = document.find(EditorOverlay.picked());
        return document.editable(picked) ? picked : null;
    }

    private @Nullable Instance billboardAt(float mouseX, float mouseY) {
        Instance[] best = {null};
        double[] closest = {BILLBOARD_CLICK_RADIUS * BILLBOARD_CLICK_RADIUS};
        forEachEditableSpatial(instance -> {
            if (instance instanceof Part) return;
            float[] screen = view.toScreen(Frames.center(instance));
            if (screen == null) return;
            double dx = screen[0] - mouseX;
            double dy = screen[1] - mouseY;
            double distance = dx * dx + dy * dy;
            if (distance <= closest[0]) {
                closest[0] = distance;
                best[0] = instance;
            }
        });
        return best[0];
    }

    private void drawBillboards(ImDrawList drawList) {
        float half = EditorScale.of(BILLBOARD_HALF_SIZE);
        forEachEditableSpatial(instance -> {
            if (instance instanceof Part) return;
            float[] screen = view.toScreen(Frames.center(instance));
            if (screen == null) return;
            boolean selected = document.selection().isSelected(instance.id());
            drawList.addCircleFilled(screen[0], screen[1], EditorScale.of(BILLBOARD_SHADOW_RADIUS), BILLBOARD_SHADOW_COLOR);
            if (selected) drawList.addCircle(screen[0], screen[1], EditorScale.of(BILLBOARD_SHADOW_RADIUS), EditorStyle.COLOR_HIGHLIGHT, 0, OUTLINE_THICKNESS);
            drawList.addImage(icons.textureId(ClassIcons.of(instance.def())), screen[0] - half, screen[1] - half, screen[0] + half, screen[1] + half);
        });
    }

    private void forEachEditableSpatial(java.util.function.Consumer<Instance> action) {
        Instance world = document.world();
        if (world == null) return;
        for (Instance child : world.children()) visit(child, action);
    }

    private void visit(Instance instance, java.util.function.Consumer<Instance> action) {
        if (instance instanceof Spatial && document.editable(instance)) action.accept(instance);
        for (Instance child : instance.children()) visit(child, action);
    }
}
