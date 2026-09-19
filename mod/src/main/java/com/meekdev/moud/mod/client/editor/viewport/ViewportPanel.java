package com.meekdev.moud.mod.client.editor.viewport;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.interp.PathCurve;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.query.Queries;
import com.meekdev.moud.core.render.CameraPath;
import com.meekdev.moud.core.ui.ScreenGui;
import com.meekdev.moud.core.ui.ViewportFrame;
import com.meekdev.moud.mod.adapter.physics.BlockRays;
import com.meekdev.moud.mod.adapter.render.EditorOverlay;
import com.meekdev.moud.mod.adapter.render.EditorView;
import com.meekdev.moud.mod.adapter.render.ViewportCapture;
import com.meekdev.moud.mod.adapter.ui.Ui;
import com.meekdev.moud.mod.client.EditMode;
import com.meekdev.moud.mod.client.editor.assets.AssetKind;
import com.meekdev.moud.mod.client.editor.assets.AssetsPanel;
import com.meekdev.moud.mod.client.editor.document.Batch;
import com.meekdev.moud.mod.client.editor.document.Edit;
import com.meekdev.moud.mod.client.editor.document.ReferencePick;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.kit.ToggleStyle;
import com.meekdev.moud.mod.client.editor.kit.Toolbars;
import com.meekdev.moud.mod.client.editor.panel.Panel;
import com.meekdev.moud.mod.client.editor.style.ClassIcons;
import com.meekdev.moud.mod.client.editor.style.EditorIcon;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import com.meekdev.moud.script.api.BlockRef;
import com.meekdev.moud.script.api.PluginRef;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import foundry.imgui.api.ImGuiMC;
import foundry.imgui.impl.ImGuiMCImpl;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.extension.imguizmo.ImGuizmo;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public final class ViewportPanel implements Panel, ViewTools {

    public static final String ID = "viewport";

    private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
    private static final double SPAWN_REACH = 256.0;
    private static final double SPAWN_FALLBACK = 12.0;
    private static final long HOVER_MEMORY_MILLIS = 400;
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
    private final TransformGizmo gizmo;
    private final List<float[]> lassoPoints = new ArrayList<>();
    private final ImInt arrayCount = new ImInt(3);
    private final float[] arrayOffset = {4, 0, 0};
    private final float[] arrayTurn = {0};
    private final SurfaceDrag surfaceDrag = new SurfaceDrag();
    private Runnable header = () -> {};
    private Runnable extraTools = () -> {};
    private BooleanSupplier clicksTaken = () -> false;
    private Consumer<PluginRef.Mouse> clicked = mouse -> {};
    private @Nullable CameraPath previewPath;
    private @Nullable PathCurve previewCurve;
    private double previewElapsed;
    private final OrientationCube cube = new OrientationCube();
    private final ScreenPreviews previews = new ScreenPreviews();
    private int session = -1;
    private boolean hovered;
    private boolean lookGesture;
    private boolean orbitGesture;
    private boolean boxing;
    private float pressX;
    private float pressY;
    private @Nullable Instance hoveredInstance;
    private Vector3 cameraPosition = Vector3.ZERO;
    private long hoveredAt;

    public ViewportPanel(SceneDocument document, IconWidgets icons) {
        this.document = document;
        this.icons = icons;
        this.gizmo = new TransformGizmo(document, gizmoState);
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

    public void header(Runnable drawn) {
        header = drawn;
    }

    public void plugins(Runnable tools, BooleanSupplier taken, Consumer<PluginRef.Mouse> click) {
        extraTools = tools;
        clicksTaken = taken;
        clicked = click;
    }

    public PluginRef.@Nullable Mouse mouse() {
        if (view.width() <= 0) return null;
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        Vector3 from = view.rayOrigin(mouseX, mouseY);
        Vector3 direction = view.rayDirection(mouseX, mouseY);
        Instance world = document.world();
        Queries.Cast part = world == null ? null : Queries.raycast(world, from, direction, SPAWN_REACH, document::editable);
        BlockRef.Hit block = BLOCKS.raycast(from, direction, part == null ? SPAWN_REACH : part.distance());
        if (block != null && (part == null || block.distance() < part.distance())) {
            return new PluginRef.Mouse(from, direction, block.at(), block.normal(), null, hovered);
        }
        if (part != null) return new PluginRef.Mouse(from, direction, part.at(), part.normal(), part.part(), hovered);
        return new PluginRef.Mouse(from, direction, null, null, null, hovered);
    }

    @Override
    public void render() {
        placeCameraOnEntry();
        header.run();

        renderToolbar();
        float deltaSeconds = ImGui.getIO().getDeltaTime();
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        float right = left + ImGui.getContentRegionAvailX();
        float bottom = top + ImGui.getContentRegionAvailY();
        ImGui.dummy(Math.max(1.0f, right - left), Math.max(1.0f, bottom - top));
        acceptAssetDrop();
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        hovered = ImGui.isWindowHovered() && mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
        if (hovered) hoveredAt = System.currentTimeMillis();
        ViewportCapture.show(argb(EditorStyle.COLOR_WINDOW_BACKGROUND));
        RenderTarget frame = ViewportCapture.frame();
        if (frame != null && view.capture() && placeFrame(frame, left, top, right, bottom)) {
            cameraPosition = view.cameraPosition();
            ImDrawList drawList = ImGui.getWindowDrawList();
            drawList.pushClipRect(left, top, right, bottom, true);
            updateCamera(deltaSeconds);
            if (hovered && !lookGesture) EditorOverlay.requestPick((mouseX - view.originX()) / view.width(), (mouseY - view.originY()) / view.height());
            hoveredInstance = hovered && !lookGesture ? pickAt(mouseX, mouseY) : null;
            EditorOverlay.show(document::pickable, Set.copyOf(document.selection().all()),
                    hoveredInstance instanceof Part part && !document.selection().isSelected(part.id()) ? part.id() : 0);
            previews.draw(drawList, left, top, right, bottom);
            drawBillboards(drawList);
            if (EditorView.ghosts()) HelperShapes.draw(drawList, view, document);
            boolean cubeBusy = cube.render(drawList, right, top, camera);
            boolean gizmoBusy = renderGizmo() || cubeBusy;
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
        gizmo.reset();
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
        ImGui.sameLine();
        if (Toolbars.textButton("Pivot##tool-pivot-text")) gizmoState.setTool(GizmoState.Tool.PIVOT);
        tooltip("Move a part's pivot, the point it turns about (P)");
        ImGui.sameLine();
        renderPaintButton();
        Toolbars.groupSeparator();
        String space = switch (gizmoState.space()) {
            case WORLD -> "World";
            case LOCAL -> "Local";
            case PARENT -> "Parent";
        };
        if (Toolbars.textButton(space + "##toolbar-gizmo-space")) gizmoState.toggleSpace();
        tooltip("Which axes the gizmo uses: the world's, the part's own, or its parent's (X)");
        ImGui.sameLine();
        if (Toolbars.textButton((gizmoState.individualPivots() ? "Each" : "Group") + "##toolbar-pivots")) gizmoState.toggleIndividualPivots();
        tooltip("Rotate the selection as one around the gizmo, or each part around its own centre");
        ImGui.sameLine();
        if (Toolbars.textButton((gizmoState.lasso() ? "Lasso" : "Box") + "##toolbar-lasso")) gizmoState.toggleLasso();
        tooltip("Drag on empty space to select with a box or a free lasso (L)");
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
        viewToggle("Ortho##toolbar-ortho", camera.orthographic(), camera::toggleOrthographic, "Orthographic view, no perspective (Numpad 5)");
        ImGui.sameLine();
        viewToggle("Wireframe##toolbar-wire", EditorView.wireframe(), () -> EditorView.wireframe(!EditorView.wireframe()), "Draw the world as lines (Z)");
        ImGui.sameLine();
        viewToggle("Helpers##toolbar-helpers", EditorView.ghosts(), () -> EditorView.ghosts(!EditorView.ghosts()), "Show invisible parts, zones, light and sound ranges (H)");
        ImGui.sameLine();
        if (Toolbars.textButton("Screens##toolbar-screens")) ImGui.openPopup("##screens");
        tooltip("Show or hide each ScreenGui while editing, and preview the loading, pause and disconnected screens");
        renderScreensPopup();
        Toolbars.groupSeparator();
        if (Toolbars.textButton("Arrange##toolbar-arrange")) ImGui.openPopup("##arrange");
        tooltip("Align, distribute and group the selection");
        renderStepPopup("##grid-steps", GRID_STEPS, gizmoState.gridStep(), " m", gizmoState::gridStep);
        renderStepPopup("##angle-steps", ANGLE_STEPS, gizmoState.angleStep(), "\u00b0", gizmoState::angleStep);
        renderArrangePopup();
        extraTools.run();
        Toolbars.popFlatButtons();
        ImGui.endChild();
        ImGui.popStyleVar(2);
    }

    private void renderScreensPopup() {
        if (!ImGui.beginPopup("##screens")) return;
        Instance world = document.world();
        List<ScreenGui> screens = new ArrayList<>();
        if (world != null && world.tree() != null) {
            for (ScreenGui screen : world.tree().ofClass(Classes.SCREEN_GUI)) {
                if (document.editable(screen)) screens.add(screen);
            }
        }
        Texts.muted("ScreenGuis, shown here only while editing");
        if (screens.isEmpty()) Texts.muted("  none in this scene");
        for (ScreenGui screen : screens) {
            ImGui.pushID(screen.id());
            Boolean previewed = Ui.previewed(screen);
            boolean shown = previewed != null ? previewed : screen.enabled;
            if (ImGui.checkbox(screen.name(), shown)) Ui.preview(screen, !shown);
            if (previewed != null) {
                ImGui.sameLine();
                Texts.muted("(saved " + (screen.enabled ? "on" : "off") + ")");
            }
            ImGui.sameLine();
            if (ImGui.smallButton("Only")) {
                for (ScreenGui other : screens) Ui.preview(other, other == screen);
            }
            ImGui.popID();
        }
        if (!screens.isEmpty() && ImGui.smallButton("As saved")) Ui.clearPreviews();
        ImGui.separator();
        Texts.muted("Engine screens");
        for (ScreenPreviews.Shown option : ScreenPreviews.Shown.values()) {
            String label = switch (option) {
                case NONE -> "None";
                case LOADING -> "Loading screen ([loading] in place.toml)";
                case PAUSE -> "Pause menu of an exported game";
                case DISCONNECTED -> "Disconnected screen";
            };
            if (ImGui.radioButton(label, previews.shown() == option)) previews.show(option);
        }
        ImGui.endPopup();
    }

    private static void viewToggle(String label, boolean active, Runnable toggle, String tip) {
        ToggleStyle.push(active);
        boolean clicked = Toolbars.textButton(label);
        ToggleStyle.pop(active);
        if (clicked) toggle.run();
        tooltip(tip);
    }

    private static String step(float value) {
        String text = String.format(Locale.ROOT, "%.3f", value);
        text = text.replaceAll("0+$", "");
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private static void renderStepPopup(String id, float[] steps, float current, String unit, Consumer<Float> choose) {
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
        ImGui.separator();
        if (ImGui.menuItem("Rotate 90° around Y", "Ctrl+R")) Manipulate.rotate90(document, 1);
        if (ImGui.menuItem("Rotate 90° around X", "Ctrl+T")) Manipulate.rotate90(document, 0);
        if (ImGui.beginMenu("Mirror")) {
            for (int axis = 0; axis < 3; axis++) {
                if (ImGui.menuItem("In place across " + axes[axis])) Manipulate.mirror(document, axis, false);
                if (ImGui.menuItem("Copy across " + axes[axis])) Manipulate.mirror(document, axis, true);
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Array")) {
            ImGui.setNextItemWidth(EditorScale.of(140));
            ImGui.inputInt("Copies##array-count", arrayCount);
            arrayCount.set(Math.clamp(arrayCount.get(), 1, 200));
            ImGui.setNextItemWidth(EditorScale.of(200));
            ImGui.dragFloat3("Offset##array-offset", arrayOffset, 0.1f);
            ImGui.setNextItemWidth(EditorScale.of(140));
            ImGui.dragFloat("Turn around Y##array-turn", arrayTurn, 1.0f, -360f, 360f, "%.0f°");
            if (ImGui.button("Make copies##array-go")) {
                Manipulate.array(document, arrayCount.get(), new Vector3(arrayOffset[0], arrayOffset[1], arrayOffset[2]), arrayTurn[0]);
                ImGui.closeCurrentPopup();
            }
            ImGui.endMenu();
        }
        ImGui.separator();
        if (ImGui.menuItem("Lock", "Ctrl+L")) Manipulate.lock(document, true);
        if (ImGui.menuItem("Unlock", "Ctrl+Shift+L")) Manipulate.lock(document, false);
        ImGui.endDisabled();
        ImGui.endPopup();
    }

    private void renderPaintButton() {
        boolean active = gizmoState.tool() == GizmoState.Tool.PAINT;
        ToggleStyle.push(active);
        boolean clicked = Toolbars.textButton("Paint##tool-paint");
        ToggleStyle.pop(active);
        if (clicked) gizmoState.setTool(GizmoState.Tool.PAINT);
        tooltip("Click parts to paint them with the brush colour (B)");
        if (!active) return;
        ImGui.sameLine();
        ImGui.colorEdit4("##brush", gizmoState.brush(), ImGuiColorEditFlags.NoInputs | ImGuiColorEditFlags.AlphaBar);
        tooltip("Brush colour");
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

    @Override
    public CFrame view() {
        Vector3d eye = camera.position();
        Vector3d forward = camera.forward(new Vector3d());
        Vector3 at = new Vector3(eye.x, eye.y, eye.z);
        return CFrame.lookAt(at, at.add(new Vector3(forward.x, forward.y, forward.z)));
    }

    @Override
    public void lookFrom(CFrame frame) {
        Vector3 forward = frame.vectorToWorld(Vector3.FORWARD);
        float yaw = (float) Math.toDegrees(Math.atan2(-forward.x(), forward.z()));
        float pitch = (float) Math.toDegrees(-Math.asin(Math.clamp(forward.y(), -1, 1)));
        Vector3 eye = frame.position();
        camera.placeAt(new Vec3(eye.x(), eye.y(), eye.z()), yaw, pitch);
    }

    @Override
    public void preview(CameraPath path) {
        List<CFrame> points = path.points();
        if (points.isEmpty()) return;
        previewPath = path;
        previewCurve = new PathCurve(points, path.closed);
        previewElapsed = 0;
    }

    @Override
    public boolean previewing() {
        return previewPath != null;
    }

    @Override
    public void stopPreview() {
        previewPath = null;
        previewCurve = null;
    }

    private boolean updatePreview(float deltaSeconds) {
        CameraPath path = previewPath;
        PathCurve curve = previewCurve;
        if (path == null || curve == null) return false;
        if (!path.isAlive() || ImGui.isMouseDown(ImGuiMouseButton.Right) || ImGui.isKeyPressed(ImGuiKey.Escape, false)) {
            stopPreview();
            return false;
        }
        previewElapsed += deltaSeconds;
        double duration = Math.max(0.01, path.duration);
        boolean done = !path.looped && previewElapsed >= duration;
        double progress = path.looped ? (previewElapsed % duration) / duration : Math.min(1, previewElapsed / duration);
        lookFrom(path.frameAt(curve, progress));
        if (done) stopPreview();
        return true;
    }

    private void updateCamera(float deltaSeconds) {
        if (updatePreview(deltaSeconds)) return;
        camera.updateFraming(deltaSeconds);
        camera.updateAligning(deltaSeconds);
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

    public boolean hoveredLately() {
        return System.currentTimeMillis() - hoveredAt < HOVER_MEMORY_MILLIS;
    }

    public void placeAtMouse(List<String> assets) {
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        Vector3 at = surfaceAt(view.rayOrigin(mouseX, mouseY), view.rayDirection(mouseX, mouseY));
        Instance under = pickAt(mouseX, mouseY);
        Instance world = document.world();
        if (world == null) return;
        for (String asset : assets) {
            boolean attaches = !asset.endsWith(".scene") && AssetKind.of(asset) != AssetKind.MODEL;
            int parent = attaches && under != null ? under.id() : world.id();
            document.placeAsset(asset, parent, at);
        }
    }

    private void acceptAssetDrop() {
        if (!ImGui.beginDragDropTarget()) return;
        String asset = ImGui.acceptDragDropPayload(AssetsPanel.PAYLOAD, String.class);
        if (asset != null && view.width() > 0) placeAtMouse(List.of(asset));
        ImGui.endDragDropTarget();
    }

    private Vector3 surfaceAt(Vector3 from, Vector3 direction) {
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

    public boolean flying() {
        return lookGesture || orbitGesture;
    }

    public Vector3 spawnPoint() {
        Vector3d eye = camera.position();
        Vector3d ahead = camera.forward(new Vector3d());
        return surfaceAt(new Vector3(eye.x, eye.y, eye.z), new Vector3(ahead.x, ahead.y, ahead.z));
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
        if (lookGesture || gizmo.dragging() || ImGui.getIO().getWantTextInput() || ImGui.getIO().getKeyCtrl() || !(hovered || ImGui.isWindowFocused())) return;
        if (ImGui.isKeyPressed(ImGuiKey.Q, false)) gizmoState.setTool(GizmoState.Tool.SELECT);
        if (ImGui.isKeyPressed(ImGuiKey.W, false)) gizmoState.setTool(GizmoState.Tool.TRANSLATE);
        if (ImGui.isKeyPressed(ImGuiKey.R, false)) gizmoState.setTool(GizmoState.Tool.ROTATE);
        if (ImGui.isKeyPressed(ImGuiKey.S, false)) gizmoState.setTool(GizmoState.Tool.SCALE);
        if (ImGui.isKeyPressed(ImGuiKey.X, false)) gizmoState.toggleSpace();
        if (ImGui.isKeyPressed(ImGuiKey.Space, false)) gizmoState.toggleAlternateTool();
        if (ImGui.isKeyPressed(ImGuiKey.P, false)) gizmoState.setTool(GizmoState.Tool.PIVOT);
        if (ImGui.isKeyPressed(ImGuiKey.B, false)) gizmoState.setTool(GizmoState.Tool.PAINT);
        if (ImGui.isKeyPressed(ImGuiKey.L, false)) gizmoState.toggleLasso();
        if (ImGui.isKeyPressed(ImGuiKey.Z, false)) EditorView.wireframe(!EditorView.wireframe());
        if (ImGui.isKeyPressed(ImGuiKey.H, false)) EditorView.ghosts(!EditorView.ghosts());
        if (ImGui.isKeyPressed(ImGuiKey.Keypad5, false)) camera.toggleOrthographic();
        if (ImGui.isKeyPressed(ImGuiKey.Keypad7, false)) camera.alignTo(0, -1, 0);
        if (ImGui.isKeyPressed(ImGuiKey.Keypad1, false)) camera.alignTo(0, 0, -1);
        if (ImGui.isKeyPressed(ImGuiKey.Keypad3, false)) camera.alignTo(-1, 0, 0);
    }

    private boolean renderGizmo() {
        return gizmo.render(ImGui.getWindowDrawList(), view, cameraPosition, camera.orthographic(), hovered, snapActive());
    }

    private boolean snapActive() {
        return gizmoState.snapEnabled() != ImGui.getIO().getKeyCtrl();
    }

    private void handlePicking(boolean gizmoBusy, ImDrawList drawList) {
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        if (ReferencePick.active()) {
            drawList.addText(mouseX + EditorScale.of(14), mouseY + EditorScale.of(10), EditorStyle.COLOR_HIGHLIGHT, "Pick a target");
            if (ImGui.isKeyPressed(ImGuiKey.Escape)) ReferencePick.cancel();
            if (hovered && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
                Instance picked = pickAt(mouseX, mouseY);
                if (picked != null) ReferencePick.deliver(picked);
                else ReferencePick.cancel();
                return;
            }
        }
        if (hovered && !gizmoBusy && !lookGesture && !ImGui.getIO().getKeyAlt() && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            clicked.accept(mouse());
            if (clicksTaken.getAsBoolean()) return;
            boxing = true;
            pressX = mouseX;
            pressY = mouseY;
            lassoPoints.clear();
            lassoPoints.add(new float[] {mouseX, mouseY});
            if (gizmoState.tool() != GizmoState.Tool.PAINT) armSurfaceDrag(mouseX, mouseY);
        }
        if (!boxing) return;
        if (gizmoState.tool() == GizmoState.Tool.PAINT) {
            if (ImGui.isMouseDown(ImGuiMouseButton.Left) && pickAt(mouseX, mouseY) instanceof Part part) {
                float[] c = gizmoState.brush();
                Manipulate.paint(document, part, new Color(c[0], c[1], c[2], c[3]));
            }
            if (!ImGui.isMouseDown(ImGuiMouseButton.Left)) boxing = false;
            return;
        }
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
        if (ImGui.isMouseDown(ImGuiMouseButton.Left) && gizmoState.lasso()) {
            float[] last = lassoPoints.getLast();
            if (Math.abs(last[0] - mouseX) + Math.abs(last[1] - mouseY) > 3) lassoPoints.add(new float[] {mouseX, mouseY});
            if (dragged) {
                for (int i = 1; i < lassoPoints.size(); i++) {
                    drawList.addLine(lassoPoints.get(i - 1)[0], lassoPoints.get(i - 1)[1], lassoPoints.get(i)[0], lassoPoints.get(i)[1], BOX_BORDER, 1.5f);
                }
                drawList.addLine(lassoPoints.getLast()[0], lassoPoints.getLast()[1], lassoPoints.getFirst()[0], lassoPoints.getFirst()[1], BOX_FILL, 1.0f);
            }
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
        if (dragged && gizmoState.lasso()) lassoSelect(lassoPoints);
        else if (dragged) boxSelect(Math.min(pressX, mouseX), Math.min(pressY, mouseY), Math.max(pressX, mouseX), Math.max(pressY, mouseY));
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
        Vector3 from = view.rayOrigin(mouseX, mouseY);
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

    private void lassoSelect(List<float[]> polygon) {
        if (polygon.size() < 3) return;
        boolean additive = ImGui.getIO().getKeyCtrl() || ImGui.getIO().getKeyShift();
        if (!additive) document.selection().clear();
        forEachEditableSpatial(instance -> {
            if (!document.pickable(instance)) return;
            List<Vector3> points = new ArrayList<>();
            points.add(Frames.center(instance));
            if (instance instanceof Part part) points.addAll(List.of(Frames.corners(part)));
            for (Vector3 point : points) {
                float[] screen = view.toScreen(point);
                if (screen != null && ScreenShapes.insidePolygon(polygon, screen[0], screen[1])) {
                    document.selection().add(instance.id());
                    return;
                }
            }
        });
    }

    private void boxSelect(float x0, float y0, float x1, float y1) {
        boolean additive = ImGui.getIO().getKeyCtrl() || ImGui.getIO().getKeyShift();
        if (!additive) document.selection().clear();
        forEachEditableSpatial(instance -> {
            if (!document.pickable(instance)) return;
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
        return document.pickable(picked) ? picked : null;
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

    private void forEachEditableSpatial(Consumer<Instance> action) {
        Instance world = document.world();
        if (world == null) return;
        for (Instance child : world.children()) visit(child, action);
    }

    private void visit(Instance instance, Consumer<Instance> action) {
        if (instance instanceof ViewportFrame) return;
        if (instance instanceof Spatial && document.editable(instance)) action.accept(instance);
        for (Instance child : instance.children()) visit(child, action);
    }
}
