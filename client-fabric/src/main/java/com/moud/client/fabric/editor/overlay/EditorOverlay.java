package com.moud.client.fabric.editor.overlay;

import com.moud.client.fabric.assets.AssetsClient;
import com.moud.client.fabric.editor.dialogs.CreateAssetDialog;
import com.moud.client.fabric.editor.net.HudEditBus;
import com.moud.client.fabric.editor.panels.CanvasNodeTypes;
import com.moud.client.fabric.editor.util.EditorUiUtil;
import com.moud.client.fabric.editor.dialogs.CreateNodeDialog;
import com.moud.client.fabric.editor.dialogs.CreateProjectDialog;
import com.moud.client.fabric.editor.dialogs.QuickSearchDialog;
import com.moud.client.fabric.editor.dialogs.ScriptEditorDialog;
import com.moud.client.fabric.editor.dialogs.TextAssetEditorDialog;
import com.moud.client.fabric.editor.dialogs.sceneimport.ImportSceneDialog;
import com.moud.client.fabric.editor.tools.EditorGizmos;
import com.moud.client.fabric.editor.tools.EditorTool;
import com.moud.client.fabric.editor.net.EditorNet;
import com.moud.client.fabric.editor.panels.AssetsPanel;
import com.moud.client.fabric.editor.panels.InspectorPanel;
import com.moud.client.fabric.editor.panels.ScenePanel;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.util.ClientDebugLog;
import com.miry.graphics.Framebuffer;
import com.miry.graphics.batch.BatchRenderer;
import com.miry.graphics.post.GaussianBlur;
import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.input.UiInput;
import com.miry.ui.font.FontAtlas;
import com.miry.ui.font.FontData;
import com.miry.ui.font.TextRenderer;
import com.miry.ui.layout.DockSpace;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.layout.LeafNode;
import com.miry.ui.layout.SplitNode;
import com.miry.ui.PanelContext;
import com.miry.ui.panels.Panel;
import com.miry.ui.theme.Theme;
import com.miry.ui.util.MathUtils;
import com.miry.ui.window.WindowManager;
import com.miry.ui.window.UiWindow;
import com.miry.ui.event.UiEvent;
import com.miry.ui.event.TextInputEvent;
import com.moud.client.fabric.platform.MinecraftGhostBlocks;
import com.moud.client.fabric.render.VeilDebugRenderer;
import com.moud.client.fabric.render.hud.HudSelectionOverlay;
import com.moud.client.fabric.render.preview.MaterialPreviewRenderer;
import com.moud.client.fabric.render.scene.util.NodePropertyUtils;
import com.moud.client.fabric.util.ClientDebugLog;
import com.moud.client.fabric.editor.theme.EditorTheme;
import com.moud.net.protocol.ProjectCreateAck;
import com.moud.net.protocol.ProjectInfo;
import com.moud.net.protocol.SceneCreateAck;
import com.moud.net.protocol.SceneDeleteAck;
import com.moud.net.protocol.SceneList;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneSaveAck;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.protocol.SchemaSnapshot;
import com.moud.net.protocol.ScriptActionInvokeAck;
import com.moud.net.protocol.ScriptActionListResponse;
import com.moud.net.protocol.ScriptFileReadResponse;
import com.moud.net.protocol.ScriptFileWriteAck;
import com.moud.core.util.ParseUtils;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import com.miry.ui.event.KeyEvent;
import com.moud.client.fabric.editor.panels.BottomPanel;
import com.moud.client.fabric.editor.panels.ToolbarPanel;
import com.moud.client.fabric.editor.panels.ViewportPanel;
import com.miry.graphics.Texture;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.ByteBuffer;

public final class EditorOverlay {
    private static final String TYPE_SCENE_INSTANCE = "SceneInstance3D";
    private static final String PROP_PREFAB_INSTANCE_ROOT = "@prefab_instance_root";

    private final Theme theme = new Theme();
    private final Ui ui = new Ui(theme);
    private final UiInput input = new UiInput();
    private UiContext uiContext;
    private final EditorState state = new EditorState();
    private final EditorNet net = new EditorNet();
    private final EditorRuntime runtime = new EditorRuntime(state, net);
    private BatchRenderer batch;
    private FontAtlas fontAtlas;
    private FontAtlas monoFontAtlas;
    private final ViewportCapture viewportCapture = new ViewportCapture();
    private Framebuffer uiFramebuffer;
    private GaussianBlur blur;
    private WindowManager windowManager;
    private int fallbackVao;
    private CreateNodeDialog createNodeDialog;
    private ImportSceneDialog importSceneDialog;
    private CreateProjectDialog createProjectDialog;
    private CreateAssetDialog createAssetDialog;
    private QuickSearchDialog quickSearchDialog;
    private ScriptEditorDialog scriptEditorDialog;
    private TextAssetEditorDialog textAssetEditorDialog;
    private UiWindow settingsWindow;
    private float appliedEditorUiScale = Float.NaN;

    private boolean open;
    private boolean prevLeft;
    private boolean pendingViewportClick;
    private boolean prevHudHidden;
    private DockSpace dockSpace;
    private InspectorPanel inspectorPanel;
    private EditorGizmos gizmos;
    private ScenePanel scenePanel;
    private AssetsPanel assetsPanel;
    private BottomPanel bottomPanel;
    private ViewportPanel viewportPanel;
    private SplitNode rootWithTop;
    private SplitNode mainWithBottom;
    private SplitNode mainRow;
    private SplitNode viewportAndRight;
    private SplitNode leftColumn;
    private boolean prevCameraCapturing;
    private boolean layoutSeeded;
    private int layoutSeedW;
    private int layoutSeedH;

    private final Map<UiWindow, Panel> floatingPanels = new HashMap<>();
    private final Map<String, UiWindow> floatingScriptWindows = new HashMap<>();
    private final List<LeafNode> dockableLeaves = new ArrayList<>();

    private String toastMessage;
    private boolean toastError;
    private long toastUntilMs;

    public EditorOverlay(AssetsClient assets) {
        runtime.setAssets(assets);
    }

    public EditorRuntime getRuntime() {
        return runtime;
    }

    public boolean isAnyTextFieldFocused() {
        return uiContext != null && uiContext.focus().hasAnyFocus();
    }

    public void onHudPickSelected(long nodeId) {
        if (state == null) return;
        state.selectedId = nodeId;
        state.selectedIds.clear();
        if (nodeId > 0L) state.selectedIds.add(nodeId);
    }

    public void saveAllOpenEditors() {
        if (scriptEditorDialog != null) scriptEditorDialog.saveIfDirty();
        if (textAssetEditorDialog != null) textAssetEditorDialog.saveIfDirty();
    }

    public void setOpen(boolean open) {
        this.open = open;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            if (open) {
                prevHudHidden = client.options.hudHidden;
                client.options.hudHidden = true;
                if (client.mouse != null) {
                    client.mouse.unlockCursor();
                }
            } else {
                client.options.hudHidden = prevHudHidden;
                if (client.mouse != null && client.currentScreen == null) {
                    client.mouse.lockCursor();
                }
            }
        }
        if (!open) {
            VeilDebugRenderer.instance().clear();
        }
    }

    public boolean isOpen() {
        return open;
    }

    public void onSnapshot(SceneSnapshot snapshot) {
        state.onSnapshot(snapshot);
        runtime.onSnapshot(snapshot);
    }

    public void onAck(SceneOpAck ack) {
        state.onAck(ack);
        runtime.onSceneOpAck(ack);
        MinecraftGhostBlocks.get().onAck(ack);
        if (ack != null && ack.results() != null) {
            int failed = 0;
            for (var r : ack.results()) {
                if (r != null && !r.ok()) {
                    failed++;
                    String msg = r.message();
                    if (msg == null || msg.isBlank()) {
                        msg = r.error() == null ? "SceneOp failed" : r.error().name();
                    }
                    ClientDebugLog.error("Scene", "SceneOp failed targetId=" + r.targetId() + " error=" + msg);
                }
            }
            if (failed > 0 && ClientDebugLog.enabled()) {
                ClientDebugLog.debug("SceneOpAck failedCount=" + failed + " batchId=" + ack.batchId() + " sceneRev=" + ack.sceneRevision());
            }
        }
    }

    public void onSchema(SchemaSnapshot schema) {
        state.onSchema(schema);
    }

    public void onSceneList(SceneList list) {
        state.onSceneList(list);
    }

    public void onSceneSaveAck(SceneSaveAck ack) {
        if (ack == null) {
            return;
        }
        if (ack.success()) {
            state.markSceneSaved(ack.sceneId(), state.currentRevisionForScene(ack.sceneId()));
            showToast("Saved scene: " + ack.sceneId(), false, 2500);
            return;
        }
        String error = ack.error();
        if (error == null || error.isBlank()) {
            error = "Unknown error";
        }
        showToast("Save failed (" + ack.sceneId() + "): " + error, true, 5000);
    }

    public void onSceneCreateAck(SceneCreateAck ack) {
        if (ack == null) {
            return;
        }
        if (ack.success()) {
            showToast("Created scene: " + ack.sceneId(), false, 2500);
            return;
        }
        String error = ack.error();
        if (error == null || error.isBlank()) {
            error = "Unknown error";
        }
        showToast("Create failed (" + ack.sceneId() + "): " + error, true, 5000);
    }

    public void onSceneDeleteAck(SceneDeleteAck ack) {
        if (ack == null) {
            return;
        }
        if (ack.success()) {
            showToast("Deleted scene: " + ack.sceneId(), false, 2500);
            return;
        }
        String error = ack.error();
        if (error == null || error.isBlank()) {
            error = "Unknown error";
        }
        showToast("Delete failed (" + ack.sceneId() + "): " + error, true, 5000);
    }

    public void onProjectInfo(ProjectInfo info) {
        if (info == null) {
            return;
        }
        state.onProjectInfo(info);
        syncProjectDialogState();
    }

    public void onProjectCreateAck(ProjectCreateAck ack) {
        if (ack == null) {
            return;
        }
        if (ack.success()) {
            state.projectExists = true;
            state.projectInfoKnown = true;
            state.projectName = ack.name() == null ? "" : ack.name();
            state.projectAuthor = ack.author() == null ? "" : ack.author();
            showToast("Created project: " + state.projectName, false, 3000);
            if (createProjectDialog != null) {
                createProjectDialog.close();
            }
            return;
        }
        String error = ack.error();
        if (error == null || error.isBlank()) {
            error = "Unknown error";
        }
        showToast("Project create failed: " + error, true, 6000);
    }

    public void onScriptActionListResponse(ScriptActionListResponse response) {
        if (response == null) {
            return;
        }
        if (!response.success()) {
            String err = response.error();
            if (err == null || err.isBlank()) {
                err = "Unknown error";
            }
            ClientDebugLog.error("Scripts", "ScriptActionList failed nodeId=" + response.nodeId() + " error=" + err);
        }
        state.onScriptActionListResponse(response, null);
    }

    public void onScriptActionInvokeAck(ScriptActionInvokeAck ack) {
        if (ack == null) {
            return;
        }
        if (ack.success()) {
            showToast("Script action ok", false, 2000);
            state.pendingSnapshot = true;
            return;
        }
        String error = ack.error();
        if (error == null || error.isBlank()) {
            error = "Unknown error";
        }
        ClientDebugLog.error("Scripts", "Script action failed error=" + error);
        showToast("Script action failed: " + error, true, 6000);
    }

    public void onScriptFileReadResponse(ScriptFileReadResponse response) {
        if (response == null) {
            return;
        }
        if (scriptEditorDialog != null && scriptEditorDialog.isOpen()) {
            scriptEditorDialog.onReadResponse(response);
        }
    }

    public void onScriptFileWriteAck(ScriptFileWriteAck ack) {
        if (ack == null) {
            return;
        }
        if (scriptEditorDialog != null && scriptEditorDialog.isOpen()) {
            scriptEditorDialog.onWriteAck(ack);
        }
    }

    public void requestSnapshot(Session session) {
        runtime.setSession(session);
        net.requestProjectInfo(session, state);
        net.requestSnapshot(session, state);
    }

    public void close() {
        if (uiContext != null) {
            uiContext.close();
            uiContext = null;
        }
        if (GLFW.glfwGetCurrentContext() == 0L) {
            fontAtlas = null;
            monoFontAtlas = null;
            batch = null;
            gizmos = null;
            MaterialPreviewRenderer.dropAll();
            return;
        }
        if (gizmos != null) {
            gizmos.close();
            gizmos = null;
        }
        if (fontAtlas != null) {
            fontAtlas.close();
            fontAtlas = null;
        }
        if (monoFontAtlas != null) {
            monoFontAtlas.close();
            monoFontAtlas = null;
        }
        viewportCapture.close();
        if (uiFramebuffer != null) {
            uiFramebuffer.close();
            uiFramebuffer = null;
        }
        if (blur != null) {
            blur.close();
            blur = null;
        }
        if (batch != null) {
            batch.close();
            batch = null;
        }
        if (fallbackVao != 0) {
            try {
                GL30.glDeleteVertexArrays(fallbackVao);
            } catch (Exception ignored) {
            }
            fallbackVao = 0;
        }
        MaterialPreviewRenderer.clear();
    }

    public void render(Session session) {
        if (!open || session == null || session.state() != SessionState.CONNECTED) {
            return;
        }

        {
            List<SceneOp> pending =
                    HudEditBus.drain();
            if (!pending.isEmpty() && runtime != null && runtime.state() != null) {
                net.sendOps(session, runtime.state(), pending);
            }
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        if (client.currentScreen != null) {
            return;
        }
        Window window = client.getWindow();
        int w = window.getWidth();
        int h = window.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float framebufferScale = window.getFramebufferWidth() / (float) w;
        framebufferScale = Math.max(0.1f, framebufferScale);

        long handle = window.getHandle();
        EditorContext ctx = EditorOverlayBus.get();
        float scrollY = ctx != null ? ctx.consumeScrollY() : 0.0f;

        float mx = (float) client.mouse.getX();
        float my = (float) client.mouse.getY();
        boolean left = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;
        boolean leftPressed = left && !prevLeft;
        boolean leftReleased = !left && prevLeft;
        prevLeft = left;
        boolean right = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_2) == GLFW.GLFW_PRESS;
        boolean rightPressed = right && !runtime.rightDown();
        boolean rightReleased = !right && runtime.rightDown();

        boolean hudGizmoConsumed = HudSelectionOverlay.handleInput(
                mx, my, left, leftPressed, leftReleased);
        if (hudGizmoConsumed || HudSelectionOverlay.isDragging()) {
            left = false;
            leftPressed = false;
            leftReleased = false;
        }
        HudSelectionOverlay.updateHoverCursor(mx, my);

        HudSelectionOverlay.handleNudgeInput(handle);
        HudSelectionOverlay.handleZOrderShortcuts(handle);

        if (ctx != null && ctx.isViewportInputFocused()) {
            left = false;
            leftPressed = false;
            leftReleased = false;
            right = false;
            rightPressed = false;
            rightReleased = false;
        }
        boolean ctrl = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        boolean shift = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        boolean alt = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
        boolean sup = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SUPER) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SUPER) == GLFW.GLFW_PRESS;

        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        ensureInitialized(window, handle, prevVao);
        applyEditorUiScale(window);
        if (prevVao == 0 && fallbackVao != 0) {
            GL30.glBindVertexArray(fallbackVao);
        }
        syncProjectDialogState();
        viewportCapture.capture(window);
        runtime.setSession(session);
        runtime.setViewportTexture(viewportCapture.texture());
        runtime.setUiSize(w, h);
        runtime.setFramebufferScale(framebufferScale, framebufferScale);
        runtime.setRightMouse(right, rightPressed, rightReleased);

        input.setMousePos(mx, my)
                .setMouseButtons(left, leftPressed, leftReleased)
                .setModifiers(ctrl, shift, alt, sup)
                .setScrollY(scrollY);

        ui.beginFrame(input, 1.0f / 60.0f);
        if (uiContext != null) {
            uiContext.dragDrop().updatePointer(
                    input.mousePos().x,
                    input.mousePos().y,
                    input.mouseDown(),
                    input.mousePressed(),
                    input.mouseReleased()
            );
            uiContext.update(1.0f / 60.0f);
        }

        boolean cameraCapturing = false;
        if (ctx != null && ctx.camera() != null) {
            cameraCapturing = ctx.camera().isCapturing();
        }
        if (uiContext != null && cameraCapturing && !prevCameraCapturing) {
            uiContext.focus().clearFocus();
        }
        prevCameraCapturing = cameraCapturing;

        if (state.pendingSnapshot) {
            state.pendingSnapshot = false;
            net.requestSnapshot(session, state);
        }

        EditorRuntime.ToastRequest toast = runtime.consumeToastRequest();
        if (toast != null) {
            showToast(toast.message, toast.error, toast.durationMs);
        }

        if (runtime.consumeFrameSelectedRequest()) {
            EditorContext editorCtx = EditorOverlayBus.get();
            if (editorCtx != null) {
                frameSelected(editorCtx);
            }
        }

        boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        if (cullWasEnabled) GL11.glDisable(GL11.GL_CULL_FACE);
        boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        if (depthWasEnabled) GL11.glDisable(GL11.GL_DEPTH_TEST);

        try {
            if (dockSpace == null || windowManager == null || uiContext == null) {
                boolean batchBegun = false;
                try {
                    bindFallbackVaoIfNeeded();
                    batch.begin(w, h, framebufferScale);
                    batchBegun = true;
                    batch.drawRect(0, 0, w, h, 0xAA000000);
                    batch.drawText("MOUD editor overlay: init failed", 12, batch.baselineForBox(8, 24), 0xFFFFFFFF);
                } finally {
                    if (batchBegun) {
                        batch.end();
                        bindFallbackVaoIfNeeded();
                    }
                }
                return;
            }

            applyBarRatios(w, h);
            dockSpace.resize(w, h);
            windowManager.update(uiContext, input, w, h);
            cleanupFloatingScriptWindows();
            runtime.clearTooltip();
            EditorUiUtil.setActiveRuntime(runtime);
            boolean modalOpen = (createNodeDialog != null && createNodeDialog.isOpen())
                    || (importSceneDialog != null && importSceneDialog.isOpen())
                    || (createProjectDialog != null && createProjectDialog.isOpen())
                    || (createAssetDialog != null && createAssetDialog.isOpen())
                    || (quickSearchDialog != null && quickSearchDialog.isOpen())
                    || (textAssetEditorDialog != null && textAssetEditorDialog.isOpen());
            boolean blockedByWindows = windowManager.blocksInput();
            boolean blockedByMenus = scenePanel != null && scenePanel.hasOpenOverlayMenus();
            boolean blocked = modalOpen || blockedByWindows || blockedByMenus;

            runtime.setUiBlocked(blocked);

            if (ctx != null && runtime.viewportMode() == EditorRuntime.ViewportMode.THREE_D) {
                boolean overVp = ctx.isMouseOverViewport(mx, my);
                if (overVp && !blocked) {
                    float ndcX = ((mx - ctx.viewportX()) / (float) ctx.viewportW()) * 2.0f - 1.0f;
                    float ndcY = ((my - ctx.viewportY()) / (float) ctx.viewportH()) * 2.0f - 1.0f;
                    ndcY = -ndcY;
                    ctx.setMouseViewportNdc(ndcX, ndcY, true);
                } else {
                    ctx.setMouseViewportNdc(0, 0, false);
                }

                pendingViewportClick = state != null && leftPressed && overVp && !blocked && !cameraCapturing;

                if (state != null) {
                    ctx.setSelectedNodeId(state.selectedId);
                }
            }

            processUiEvents(cameraCapturing, blocked);
            if (!blocked) {
                dockSpace.update(input);
            }

            Runnable overlayMenus = runtime.consumeOverlayMenuRender();

            boolean needsBackdropBlur = false;
            for (UiWindow uiWindow : windowManager.windows()) {
                if (uiWindow.backdropBlur()) {
                    needsBackdropBlur = true;
                    break;
                }
            }

            MaterialPreviewRenderer.renderRequested();
            needsBackdropBlur = false;

            if (!needsBackdropBlur) {
                boolean batchBegun = false;
                try {
                    bindFallbackVaoIfNeeded();
                    batch.begin(w, h, framebufferScale);
                    batchBegun = true;
                    dockSpace.render(batch);
                    renderDockDropZones(batch);
                    uiContext.dragDrop().enqueueOverlay(uiContext.overlay(), theme, w, h);
                    uiContext.overlay().render(batch);
                    windowManager.render(batch, uiContext, input, theme, w, h, null);

                    if (createNodeDialog != null && createNodeDialog.isOpen()) {
                        createNodeDialog.render(batch, uiContext, ui, theme, w, h);
                    }
                    if (importSceneDialog != null && importSceneDialog.isOpen()) {
                        importSceneDialog.render(batch, uiContext, ui, theme, w, h);
                    }
                    if (createProjectDialog != null && createProjectDialog.isOpen()) {
                        createProjectDialog.render(batch, uiContext, ui, theme, w, h);
                    }
                    if (createAssetDialog != null && createAssetDialog.isOpen()) {
                        createAssetDialog.render(batch, uiContext, ui, theme, w, h);
                    }
                    if (quickSearchDialog != null && quickSearchDialog.isOpen()) {
                        quickSearchDialog.render(batch, uiContext, ui, theme, w, h);
                    }
                    if (textAssetEditorDialog != null && textAssetEditorDialog.isOpen()) {
                        EditorState textAssetCheckState = runtime != null ? runtime.state() : null;
                        String activeTextAsset = textAssetCheckState != null ? textAssetCheckState.activeTextAssetPath : "";
                        boolean inlineInTab = activeTextAsset != null && !activeTextAsset.isBlank();
                        if (!inlineInTab) {
                            textAssetEditorDialog.render(batch, uiContext, ui, theme, w, h);
                        }
                    }

                    if (overlayMenus != null) {
                        overlayMenus.run();
                    }

                    uiContext.dragDrop().endFrame();
                    renderTooltip(w, h);
                    renderToast(w, h);
                } finally {
                    if (batchBegun) {
                        batch.end();
                        bindFallbackVaoIfNeeded();
                    }
                }
                applyDeferredViewportClick(ctx);
                return;
            }

            if (uiFramebuffer == null) {
                uiFramebuffer = new Framebuffer();
            }
            if (blur == null) {
                blur = new GaussianBlur();
            }

            int fbW = window.getFramebufferWidth();
            int fbH = window.getFramebufferHeight();
            uiFramebuffer.ensureSize(Math.max(1, fbW), Math.max(1, fbH));

            try (Framebuffer.Binding ignored = uiFramebuffer.bindScoped()) {
                GL11.glClearColor(theme.windowBg.getR(), theme.windowBg.getG(), theme.windowBg.getB(), theme.windowBg.getA());
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                boolean batchBegun = false;
                try {
                    bindFallbackVaoIfNeeded();
                    batch.begin(w, h, framebufferScale);
                    batchBegun = true;
                    dockSpace.render(batch);
                    uiContext.dragDrop().enqueueOverlay(uiContext.overlay(), theme, w, h);
                    uiContext.overlay().render(batch);
                } finally {
                    if (batchBegun) {
                        batch.end();
                        bindFallbackVaoIfNeeded();
                    }
                }
            }

            Texture blurred = blur.blur(uiFramebuffer.colorTexture(), fbW, fbH, 1);

            boolean batchBegun = false;
            try {
                bindFallbackVaoIfNeeded();
                batch.begin(w, h, framebufferScale);
                batchBegun = true;
                batch.drawTexturedRect(uiFramebuffer.colorTexture(), 0, 0, w, h, 0.0f, 1.0f, 1.0f, 0.0f, 0xFFFFFFFF);
                windowManager.render(batch, uiContext, input, theme, w, h, blurred);

                if (createNodeDialog != null && createNodeDialog.isOpen()) {
                    createNodeDialog.render(batch, uiContext, ui, theme, w, h);
                }
                if (importSceneDialog != null && importSceneDialog.isOpen()) {
                    importSceneDialog.render(batch, uiContext, ui, theme, w, h);
                }
                if (createProjectDialog != null && createProjectDialog.isOpen()) {
                    createProjectDialog.render(batch, uiContext, ui, theme, w, h);
                }
                if (createAssetDialog != null && createAssetDialog.isOpen()) {
                    createAssetDialog.render(batch, uiContext, ui, theme, w, h);
                }
                if (textAssetEditorDialog != null && textAssetEditorDialog.isOpen()) {
                    EditorState textCheckState2 = runtime != null ? runtime.state() : null;
                    String activeTA2 = textCheckState2 != null ? textCheckState2.activeTextAssetPath : "";
                    if (activeTA2 == null || activeTA2.isBlank()) {
                        textAssetEditorDialog.render(batch, uiContext, ui, theme, w, h);
                    }
                }

                if (overlayMenus != null) {
                    overlayMenus.run();
                }

                uiContext.dragDrop().endFrame();
                renderTooltip(w, h);
                renderToast(w, h);
            } finally {
                if (batchBegun) {
                    batch.end();
                    bindFallbackVaoIfNeeded();
                }
            }
        } finally {
            applyDeferredViewportClick(ctx);
            int restoreVao = prevVao != 0 ? prevVao : fallbackVao;
            if (restoreVao != 0) {
                GL30.glBindVertexArray(restoreVao);
            }
            if (depthWasEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST);
            if (cullWasEnabled) GL11.glEnable(GL11.GL_CULL_FACE);
        }
    }

    private void applyDeferredViewportClick(EditorContext ctx) {
        if (!pendingViewportClick) return;
        pendingViewportClick = false;

        if (gizmos != null && gizmos.isDragging()) return;
        if (HudSelectionOverlay.isDragging()) return;

        if (state == null || ctx == null) return;
        long hovered = ctx.hoveredNodeId();
        if (hovered > 0) {
            long selected = resolveViewportSelectionNodeId(hovered);
            state.selectedId = selected;
            state.selectedIds.clear();
            state.selectedIds.add(selected);
        } else {
            if (state.selectedId > 0L) {
                var node = state.scene.getNode(state.selectedId);
                if (node != null && CanvasNodeTypes.CONTROL_TYPES.contains(node.type())) {
                    return;
                }
            }
            state.selectedId = 0L;
            state.selectedIds.clear();
        }
        ctx.setSelectedNodeId(state.selectedId);
    }

    private long resolveViewportSelectionNodeId(long pickedNodeId) {
        if (state == null || state.scene == null || pickedNodeId <= 0L) {
            return pickedNodeId;
        }

        SceneSnapshot.NodeSnapshot picked = state.scene.getNode(pickedNodeId);
        if (picked == null) {
            return pickedNodeId;
        }
        if (TYPE_SCENE_INSTANCE.equals(picked.type())) {
            return picked.nodeId();
        }

        String instanceRoot = NodePropertyUtils.stringProp(picked, PROP_PREFAB_INSTANCE_ROOT);
        if (instanceRoot != null && !instanceRoot.isBlank()) {
            try {
                long rootId = Long.parseLong(instanceRoot.trim());
                SceneSnapshot.NodeSnapshot root = state.scene.getNode(rootId);
                if (root != null && TYPE_SCENE_INSTANCE.equals(root.type())) {
                    return rootId;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        long currentId = picked.parentId();
        while (currentId > 0L) {
            SceneSnapshot.NodeSnapshot parent = state.scene.getNode(currentId);
            if (parent == null) {
                break;
            }
            if (TYPE_SCENE_INSTANCE.equals(parent.type())) {
                return parent.nodeId();
            }
            currentId = parent.parentId();
        }
        return pickedNodeId;
    }

    private void syncProjectDialogState() {
        if (createProjectDialog == null) {
            return;
        }
        if (state.projectInfoKnown && !state.projectExists && !createProjectDialog.isOpen()) {
            createProjectDialog.open();
        }
        if (state.projectExists && createProjectDialog.isOpen()) {
            createProjectDialog.close();
        }
    }

    private void ensureInitialized(Window window, long handle, int prevVao) {
        if (batch != null) {
            return;
        }
        if (GLFW.glfwGetCurrentContext() == 0L) {
            return;
        }
        applyEngineEditorTheme();
        if (fallbackVao == 0) {
            fallbackVao = GL30.glGenVertexArrays();
        }
        if (prevVao == 0 && fallbackVao != 0) {
            GL30.glBindVertexArray(fallbackVao);
        }
        batch = new BatchRenderer(50_000);
        installFont(window);
        viewportCapture.ensureInitialized();
        uiContext = new UiContext(handle, UiContext.Config.MANUAL_INPUT);
        windowManager = new WindowManager();
        createNodeDialog = new CreateNodeDialog(runtime);
        runtime.setCreateNodeDialog(createNodeDialog);
        importSceneDialog = new ImportSceneDialog(runtime);
        runtime.setImportSceneDialog(importSceneDialog);
        createProjectDialog = new CreateProjectDialog(runtime);
        createAssetDialog = new CreateAssetDialog(runtime);
        runtime.setCreateAssetDialog(createAssetDialog);
        quickSearchDialog = new QuickSearchDialog(runtime);
        runtime.setQuickSearchDialog(quickSearchDialog);
        scriptEditorDialog = new ScriptEditorDialog(runtime);
        runtime.setScriptEditorDialog(scriptEditorDialog);
        textAssetEditorDialog = new TextAssetEditorDialog(runtime);
        runtime.setTextAssetEditorDialog(textAssetEditorDialog);
        runtime.setOpenEditorSettingsAction(this::openSettingsWindow);
        dockSpace = createDockSpace();
        dockSpace.setSplitterSize(5);
        dockSpace.setSplitterDrawSize(2);
        windowManager.setOnWindowDrop(win -> dockWindow(win, input.mousePos().x, input.mousePos().y));
        if (viewportPanel != null) {
            viewportPanel.setOnScriptTabTearOff(this::undockScriptTab);
        }

        if (prevVao != 0) {
            GL30.glBindVertexArray(prevVao);
        }
    }

    private void applyEngineEditorTheme() {
        EditorTheme.apply(theme, runtime.editorUiScale());
    }

    private void bindFallbackVaoIfNeeded() {
        if (fallbackVao != 0) {
            GL30.glBindVertexArray(fallbackVao);
        }
    }

    private void applyEditorUiScale(Window window) {
        float scale = runtime.editorUiScale();
        if (Math.abs(scale - appliedEditorUiScale) < 0.0001f) {
            return;
        }
        appliedEditorUiScale = scale;
        applyEngineEditorTheme();
        if (batch != null && window != null) {
            installFont(window);
        }
        layoutSeeded = false;
    }

    private void applyBarRatios(int w, int h) {
        if (rootWithTop == null || mainWithBottom == null || mainRow == null || viewportAndRight == null || leftColumn == null) {
            return;
        }
        float uiScale = runtime.editorUiScale();
        int topPx = Math.round(30.0f * uiScale);

        float topRatio = topPx / (float) Math.max(1, h);
        rootWithTop.splitRatio = MathUtils.clamp(topRatio, 0.03f, 0.20f);

        boolean reseed = !layoutSeeded || layoutSeedW != w || layoutSeedH != h;
        if (!reseed) {
            return;
        }
        layoutSeeded = true;
        layoutSeedW = w;
        layoutSeedH = h;

        int bottomPx = Math.round(156.0f * uiScale);
        int remaining = Math.max(1, h - topPx);
        float mainRatio = (remaining - bottomPx) / (float) remaining;
        mainWithBottom.splitRatio = MathUtils.clamp(mainRatio, 0.45f, 0.92f);

        int leftPx = Math.round(280.0f * uiScale);
        int rightPx = Math.round(320.0f * uiScale);
        int mainW = Math.max(1, w);
        float leftRatio = leftPx / (float) mainW;
        mainRow.splitRatio = MathUtils.clamp(leftRatio, 0.18f, 0.45f);

        int centerAndRightW = Math.max(1, mainW - leftPx);
        float centerRatio = (centerAndRightW - rightPx) / (float) centerAndRightW;
        viewportAndRight.splitRatio = MathUtils.clamp(centerRatio, 0.40f, 0.82f);

        leftColumn.splitRatio = 0.50f;
    }

    public void pushKeyEvent(int key, int scancode, int glfwAction, int mods) {
        if (!open || uiContext == null) {
            return;
        }
        KeyEvent.Action act = switch (glfwAction) {
            case GLFW.GLFW_PRESS -> KeyEvent.Action.PRESS;
            case GLFW.GLFW_RELEASE -> KeyEvent.Action.RELEASE;
            case GLFW.GLFW_REPEAT -> KeyEvent.Action.REPEAT;
            default -> null;
        };
        if (act == null) {
            return;
        }
        if (act == KeyEvent.Action.PRESS
                && key == GLFW.GLFW_KEY_P
                && ((mods & GLFW.GLFW_MOD_CONTROL) != 0 || (mods & GLFW.GLFW_MOD_SUPER) != 0)) {
            boolean noModal = (createNodeDialog == null || !createNodeDialog.isOpen())
                    && (importSceneDialog == null || !importSceneDialog.isOpen())
                    && (createProjectDialog == null || !createProjectDialog.isOpen())
                    && (createAssetDialog == null || !createAssetDialog.isOpen())
                    && (textAssetEditorDialog == null || !textAssetEditorDialog.isOpen());
            if (noModal) {
                if (quickSearchDialog != null && quickSearchDialog.isOpen()) {
                    quickSearchDialog.close();
                } else if (quickSearchDialog != null) {
                    quickSearchDialog.open();
                }
                return;
            }
        }
        if (act == KeyEvent.Action.PRESS
                && key == GLFW.GLFW_KEY_S
                && ((mods & GLFW.GLFW_MOD_CONTROL) != 0 || (mods & GLFW.GLFW_MOD_SUPER) != 0)) {
            boolean modalOpen = (createNodeDialog != null && createNodeDialog.isOpen())
                    || (importSceneDialog != null && importSceneDialog.isOpen())
                    || (createProjectDialog != null && createProjectDialog.isOpen())
                    || (createAssetDialog != null && createAssetDialog.isOpen())
                    || (quickSearchDialog != null && quickSearchDialog.isOpen())
                    || (scriptEditorDialog != null && scriptEditorDialog.isFocused(uiContext))
                    || (textAssetEditorDialog != null && textAssetEditorDialog.isOpen());
            if (!modalOpen) {
                boolean sent = runtime.saveCurrentScene();
                if (sent) {
                    showToast("Saving scene: " + state.activeSceneId + "...", false, 2000);
                } else {
                    showToast("Save failed: not connected", true, 3500);
                }
                return;
            }
        }
        if (act == KeyEvent.Action.PRESS
                && ((mods & GLFW.GLFW_MOD_CONTROL) != 0 || (mods & GLFW.GLFW_MOD_SUPER) != 0)) {
            boolean modalOpen = (createNodeDialog != null && createNodeDialog.isOpen())
                    || (importSceneDialog != null && importSceneDialog.isOpen())
                    || (createProjectDialog != null && createProjectDialog.isOpen())
                    || (createAssetDialog != null && createAssetDialog.isOpen())
                    || (quickSearchDialog != null && quickSearchDialog.isOpen())
                    || (textAssetEditorDialog != null && textAssetEditorDialog.isOpen());
            if (!modalOpen && scenePanel != null) {
                if (key == GLFW.GLFW_KEY_Z && (mods & GLFW.GLFW_MOD_SHIFT) == 0) {
                    scenePanel.performUndo();
                    return;
                }
                if (key == GLFW.GLFW_KEY_Y || (key == GLFW.GLFW_KEY_Z && (mods & GLFW.GLFW_MOD_SHIFT) != 0)) {
                    scenePanel.performRedo();
                    return;
                }
            }
        }
        uiContext.keyboard().pushKeyEvent(key, scancode, act, mods);
    }

    public void pushCharEvent(int codepoint) {
        if (!open || uiContext == null) {
            return;
        }
        if (quickSearchDialog != null && quickSearchDialog.isOpen()) {
            quickSearchDialog.handleTextInput(codepoint);
            return;
        }
        if (createProjectDialog != null && createProjectDialog.isOpen()) {
            createProjectDialog.handleTextInput(uiContext, codepoint);
            return;
        }
        if (createNodeDialog != null && createNodeDialog.isOpen()) {
            createNodeDialog.handleTextInput(codepoint);
            return;
        }
        if (importSceneDialog != null && importSceneDialog.isOpen()) {
            importSceneDialog.handleTextInput(codepoint);
            return;
        }
        if (createAssetDialog != null && createAssetDialog.isOpen()) {
            createAssetDialog.handleTextInput(uiContext, new TextInputEvent(codepoint));
            return;
        }
        if (scriptEditorDialog != null && scriptEditorDialog.isOpen() && state != null && !state.activeScriptPath.isBlank()) {
            scriptEditorDialog.handleTextInput(uiContext, codepoint);
            return;
        }
        if (textAssetEditorDialog != null && textAssetEditorDialog.isOpen()) {
            textAssetEditorDialog.handleTextInput(uiContext, codepoint);
            return;
        }
        uiContext.keyboard().pushCharEvent(codepoint);
    }

    public CreateNodeDialog getCreateNodeDialog() {
        return createNodeDialog;
    }

    private void installFont(Window window) {
        int w = Math.max(1, window.getWidth());
        float scale = window.getFramebufferWidth() / (float) w;
        scale = Math.max(0.1f, Math.min(scale, 4.0f));
        if (fontAtlas != null) {
            fontAtlas.close();
            fontAtlas = null;
        }
        if (monoFontAtlas != null) {
            monoFontAtlas.close();
            monoFontAtlas = null;
        }
        float uiScale = Math.max(0.5f, Math.min(runtime.editorUiScale(), 3.0f));
        int atlasSize = Math.min(2048, Math.max(1024, Math.round(768.0f * scale * uiScale)));
        fontAtlas = buildFontAtlas("editor", loadEditorFont(), 16.0f * uiScale, atlasSize, scale);
        monoFontAtlas = buildFontAtlas("mono", loadMonoFont(), 15.0f * uiScale, atlasSize, scale);
        if (fontAtlas != null) {
            batch.setTextRenderer(new TextRenderer(fontAtlas));
        }
        if (monoFontAtlas != null) {
            batch.setMonospaceTextRenderer(new TextRenderer(monoFontAtlas));
        }
    }

    private FontAtlas buildFontAtlas(String name, ByteBuffer fontData, float fontSize, int atlasSize, float scale) {
        int size = atlasSize;
        float pixelScale = scale;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return new FontAtlas(fontData, fontSize, size, pixelScale, FontAtlas.Mode.COVERAGE);
            } catch (RuntimeException e) {
                ClientDebugLog.error("EditorOverlay", "FontAtlas '" + name + "' build failed (attempt " + attempt + ", atlasSize=" + size + ", pixelScale=" + pixelScale + ")", e);
                size = Math.max(512, size / 2);
                pixelScale = Math.max(1.0f, pixelScale * 0.75f);
            }
        }
        ClientDebugLog.error("EditorOverlay", "FontAtlas '" + name + "' giving up; editor text will be unavailable");
        if (runtime != null) {
            runtime.requestToast("Editor font '" + name + "' failed to build; text may be missing", true, 6000);
        }
        return null;
    }

    private ByteBuffer loadEditorFont() {
        try {
            return FontData.loadFromResource("/fonts/inter.ttf");
        } catch (Exception e) {
            ClientDebugLog.warn("EditorOverlay", "inter.ttf load failed, falling back to default: " + e.getMessage());
        }
        return FontData.loadDefault();
    }

    private ByteBuffer loadMonoFont() {
        try {
            return FontData.loadFromResource("/fonts/jetbrains-mono.ttf");
        } catch (Exception e) {
            ClientDebugLog.warn("EditorOverlay", "jetbrains-mono.ttf load failed, falling back to editor font: " + e.getMessage());
        }
        return loadEditorFont();
    }

    private void openSettingsWindow() {
        if (windowManager == null) {
            return;
        }
        if (settingsWindow != null && windowManager.windows().contains(settingsWindow)) {
            windowManager.bringToFront(settingsWindow);
            return;
        }
        int ww = Math.round(420.0f * runtime.editorUiScale());
        int wh = Math.round(220.0f * runtime.editorUiScale());
        settingsWindow = windowManager.create("Editor Settings", 72, 72, ww, wh);
        settingsWindow.setBackdropBlur(true);
        settingsWindow.setResizable(false);
        settingsWindow.setContent(this::renderSettingsWindow);
        windowManager.bringToFront(settingsWindow);
    }

    private void renderSettingsWindow(UiRenderer r, UiContext ctx, UiInput input, Theme theme, int x, int y, int w, int h) {
        int text = Theme.toArgb(theme.text);
        int muted = Theme.toArgb(theme.textMuted);
        int outline = Theme.toArgb(theme.widgetOutline);
        int buttonBg = Theme.toArgb(theme.widgetBg);
        int buttonHover = Theme.toArgb(theme.widgetHover);
        int accent = Theme.toArgb(theme.widgetActive);

        int cursorY = y;
        r.drawText("Editor UI Scale", x, r.baselineForBox(cursorY, theme.design.widget_height_md), text);
        cursorY += theme.design.widget_height_md;

        String valueLabel = Math.round(runtime.editorUiScale() * 100.0f) + "%";
        r.drawText(valueLabel, x, r.baselineForBox(cursorY, theme.design.widget_height_md), accent);
        cursorY += theme.design.widget_height_md;

        r.drawText("Independent from Minecraft GUI Scale.", x, r.baselineForBox(cursorY, theme.design.widget_height_sm), muted);
        cursorY += theme.design.widget_height_sm + theme.design.space_md;

        int buttonH = theme.design.widget_height_md + theme.design.border_thin * 2;
        int minusW = Math.max(36, Math.round(40.0f * runtime.editorUiScale()));
        int plusW = minusW;
        int gap = theme.design.space_sm;
        int presetW = Math.max(52, Math.round(56.0f * runtime.editorUiScale()));
        int rowX = x;

        drawSettingsButton(r, input, theme, "-", rowX, cursorY, minusW, buttonH, buttonBg, buttonHover, outline, text, () ->
                runtime.setEditorUiScale(runtime.editorUiScale() - 0.05f));
        rowX += minusW + gap;

        drawSettingsButton(r, input, theme, "+", rowX, cursorY, plusW, buttonH, buttonBg, buttonHover, outline, text, () ->
                runtime.setEditorUiScale(runtime.editorUiScale() + 0.05f));
        rowX += plusW + gap * 2;

        for (float preset : new float[]{0.85f, 1.0f, 1.15f, 1.30f}) {
            String label = Math.round(preset * 100.0f) + "%";
            int fill = Math.abs(runtime.editorUiScale() - preset) < 0.01f ? accent : buttonBg;
            int hover = Math.abs(runtime.editorUiScale() - preset) < 0.01f ? Theme.lightenArgb(accent, 0.10f) : buttonHover;
            drawSettingsButton(r, input, theme, label, rowX, cursorY, presetW, buttonH, fill, hover, outline, text, () ->
                    runtime.setEditorUiScale(preset));
            rowX += presetW + gap;
        }
        cursorY += buttonH + theme.design.space_lg;

        int previewBg = Theme.toArgb(theme.panelBg);
        int previewH = Math.max(72, h - (cursorY - y));
        r.drawRoundedRect(x, cursorY, w, previewH, theme.design.radius_md, previewBg, theme.design.border_thin, outline);
        int previewPad = theme.design.space_md;
        int previewX = x + previewPad;
        int previewY = cursorY + previewPad;
        int pillW = Math.max(110, Math.round(120.0f * runtime.editorUiScale()));
        int pillH = theme.design.widget_height_md;
        r.drawRoundedRect(previewX, previewY, pillW, pillH, theme.design.radius_sm, buttonBg, theme.design.border_thin, outline);
        r.drawText("Preview Widget", previewX + theme.design.space_md, r.baselineForBox(previewY, pillH), text);
    }

    private static void drawSettingsButton(UiRenderer r,
                                           UiInput input,
                                           Theme theme,
                                           String label,
                                           int x,
                                           int y,
                                           int w,
                                           int h,
                                           int bg,
                                           int hover,
                                           int outline,
                                           int text,
                                           Runnable action) {
        boolean hovered = input != null && input.mousePos().x >= x && input.mousePos().y >= y
                && input.mousePos().x < x + w && input.mousePos().y < y + h;
        r.drawRoundedRect(x, y, w, h, theme.design.radius_sm, hovered ? hover : bg, theme.design.border_thin, outline);
        float baseline = r.baselineForBox(y, h);
        float textW = r.measureText(label);
        r.drawText(label, x + Math.round((w - textW) * 0.5f), baseline, text);
        if (hovered && input != null && input.mousePressed() && action != null) {
            action.run();
        }
    }

    private DockSpace createDockSpace() {
        LeafNode top = new LeafNode(new ToolbarPanel(runtime));

        scenePanel = new ScenePanel(runtime);
        LeafNode scene = new LeafNode(scenePanel);

        assetsPanel = new AssetsPanel(runtime);
        runtime.setOpenCreateSceneAction(assetsPanel::openCreateScene);
        LeafNode filesystem = new LeafNode(assetsPanel);

        leftColumn = new SplitNode(scene, filesystem, true, 0.50f);

        gizmos = new EditorGizmos(runtime);
        viewportPanel = new ViewportPanel(runtime, gizmos);
        LeafNode viewport = new LeafNode(viewportPanel);

        inspectorPanel = new InspectorPanel(runtime);
        LeafNode right = new LeafNode(inspectorPanel);

        bottomPanel = new BottomPanel(runtime);
        LeafNode bottom = new LeafNode(bottomPanel);

        for (LeafNode leaf : new LeafNode[]{top, scene, filesystem, viewport, right, bottom}) {
            leaf.setHeaderHeight(0);
            leaf.setHeaderButtons(LeafNode.HeaderButtons.NONE);
        }

        dockableLeaves.clear();
        for (LeafNode leaf : new LeafNode[]{scene, filesystem, right}) {
            leaf.setHeaderHeight(26);
            leaf.setOnUndock(this::undockPanel);
            dockableLeaves.add(leaf);
        }
        bottom.setHeaderHeight(26);
        bottom.setHeaderButtons(LeafNode.HeaderButtons.NONE);
        bottom.setOnUndock(null);

        int panelBg = Theme.toArgb(theme.panelBg);
        scene.setBackgroundArgb(panelBg);
        filesystem.setBackgroundArgb(panelBg);
        right.setBackgroundArgb(panelBg);
        bottom.setBackgroundArgb(panelBg);
        viewport.setBackgroundArgb(Theme.toArgb(theme.windowBg));
        top.setBackgroundArgb(Theme.toArgb(theme.windowBg));

        viewportAndRight = new SplitNode(viewport, right, false, 0.72f);
        mainRow = new SplitNode(leftColumn, viewportAndRight, false, 0.30f);
        mainWithBottom = new SplitNode(mainRow, bottom, true, 0.82f);
        rootWithTop = new SplitNode(top, mainWithBottom, true, 0.06f);

        DockSpace ds = new DockSpace(rootWithTop);
        ds.setUi(ui);
        ds.setUiContext(uiContext);
        return ds;
    }

    private void undockPanel(LeafNode source, Panel panel) {
        if (windowManager == null) return;
        int wx = Math.max(0, source.x() + 20);
        int wy = Math.max(0, source.y() + 20);
        int ww = Math.max(200, source.width() - 40);
        int wh = Math.max(150, source.height() - 40);
        UiWindow win = windowManager.create(panel.title(), wx, wy, ww, wh);
        win.setBackdropBlur(false);
        PanelContext pc = new PanelContext();
        win.setContent((r, ctx, inp, theme, cx, cy, cw, ch) -> {
            pc.set(r, ui, ctx, cx, cy, cw, ch);
            panel.render(pc);
        });
        floatingPanels.put(win, panel);
    }

    private void undockScriptTab(String path) {
        if (windowManager == null || path == null || path.isBlank()) return;
        UiWindow existing = floatingScriptWindows.get(path);
        if (existing != null && windowManager.windows().contains(existing)) {
            return;
        }
        float mx = input.mousePos().x;
        float my = input.mousePos().y;
        int wx = Math.max(0, (int) mx - 300);
        int wy = Math.max(0, (int) my - 15);
        String title = scriptTabLabel(path);
        UiWindow win = windowManager.create(title, wx, wy, 600, 400);
        win.setBackdropBlur(false);
        floatingScriptWindows.put(path, win);
        ScriptEditorDialog dialog = runtime.scriptEditorDialog();
        if (dialog != null) {
            dialog.open(0, path);
        }
        win.setContent((r, ctx, inp, theme, cx, cy, cw, ch) -> {
            ScriptEditorDialog d = runtime.scriptEditorDialog();
            if (d != null) {
                if (!path.equals(d.scriptPath())) {
                    d.open(0, path);
                }
                d.renderInline(r, ctx, ui, theme, cx, cy, cw, ch);
            }
        });
    }

    private void cleanupFloatingScriptWindows() {
        if (floatingScriptWindows.isEmpty() || windowManager == null) return;
        EditorState st = runtime != null ? runtime.state() : null;
        var iter = floatingScriptWindows.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            UiWindow win = entry.getValue();
            boolean stillTracked = st != null && st.openScriptPaths.contains(entry.getKey());
            boolean stillOnScreen = windowManager.windows().contains(win);
            if (!stillOnScreen) {
                iter.remove();
                if (st != null) st.closeScriptTab(entry.getKey());
            } else if (!stillTracked) {
                windowManager.windows().remove(win);
                iter.remove();
            }
        }
    }

    private static String scriptTabLabel(String path) {
        if (path == null || path.isBlank()) return "Script";
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private void dockWindow(UiWindow win, float mx, float my) {
        Panel panel = floatingPanels.get(win);
        if (panel == null) return;
        for (LeafNode leaf : dockableLeaves) {
            int hh = leaf.headerHeight();
            if (hh <= 0) hh = 26;
            if (mx >= leaf.x() && mx < leaf.x() + leaf.width()
                    && my >= leaf.y() && my < leaf.y() + hh) {
                floatingPanels.remove(win);
                windowManager.windows().remove(win);
                leaf.addTab(panel);
                leaf.setActiveTabIndex(leaf.tabCount() - 1);
                return;
            }
        }
    }

    private void renderDockDropZones(UiRenderer r) {
        if (windowManager == null || dockableLeaves.isEmpty()) return;
        UiWindow moving = windowManager.movingWindow();
        if (moving == null || !floatingPanels.containsKey(moving)) return;
        int accent = Theme.toArgb(theme.accent);
        int fill = Theme.mulAlpha(accent, 0.18f);
        int border = Theme.mulAlpha(accent, 0.70f);
        for (LeafNode leaf : dockableLeaves) {
            int hh = leaf.headerHeight();
            if (hh <= 0) hh = 26;
            r.drawRect(leaf.x(), leaf.y(), leaf.width(), hh, fill);
            r.drawRect(leaf.x(), leaf.y(), leaf.width(), 2, border);
            r.drawRect(leaf.x(), leaf.y() + hh - 2, leaf.width(), 2, border);
        }
    }

    private void processUiEvents(boolean cameraCapturing, boolean blocked) {
        if (uiContext == null) return;
        UiEvent event;
        while ((event = uiContext.pollEvent()) != null) {
            if (event instanceof KeyEvent keyEvent) {
                if (quickSearchDialog != null && quickSearchDialog.isOpen()) {
                    if (quickSearchDialog.handleKey(uiContext, keyEvent)) {
                        continue;
                    }
                }
                if (createProjectDialog != null && createProjectDialog.isOpen()) {
                    if (createProjectDialog.handleKey(uiContext, keyEvent)) {
                        continue;
                    }
                    if (blocked) {
                        continue;
                    }
                }
                if (createNodeDialog != null && createNodeDialog.isOpen()) {
                    if (createNodeDialog.handleKey(uiContext, keyEvent)) {
                        continue;
                    }
                    if (blocked) {
                        continue;
                    }
                }
                if (importSceneDialog != null && importSceneDialog.isOpen()) {
                    if (importSceneDialog.handleKey(uiContext, keyEvent)) {
                        continue;
                    }
                    if (blocked) {
                        continue;
                    }
                }
                if (createAssetDialog != null && createAssetDialog.isOpen()) {
                    if (createAssetDialog.handleKey(uiContext, keyEvent)) {
                        continue;
                    }
                    if (blocked) {
                        continue;
                    }
                }
                if (scriptEditorDialog != null && scriptEditorDialog.isOpen() && state != null && !state.activeScriptPath.isBlank()) {
                    if (scriptEditorDialog.handleKey(uiContext, keyEvent)) {
                        continue;
                    }
                    if (blocked) {
                        continue;
                    }
                }
                if (textAssetEditorDialog != null && textAssetEditorDialog.isOpen()) {
                    if (textAssetEditorDialog.handleKey(uiContext, keyEvent)) {
                        continue;
                    }
                    if (blocked) {
                        continue;
                    }
                }
                if (blocked) {
                    continue;
                }
                if (!cameraCapturing && keyEvent.isPress() && keyEvent.key() == GLFW.GLFW_KEY_F) {
                    EditorContext editorCtx = EditorOverlayBus.get();
                    if (editorCtx != null && editorCtx.isMouseOverViewport(input.mousePos().x, input.mousePos().y)) {
                        if (runtime != null && runtime.viewportMode() == EditorRuntime.ViewportMode.TWO_D) {
                            runtime.requestFrameSelected2D();
                        } else {
                            frameSelected(editorCtx);
                        }
                        continue;
                    }
                }
                if (!cameraCapturing && keyEvent.isPress() && keyEvent.mods() == 0
                        && !uiContext.focus().hasAnyFocus()) {
                    EditorTool toolSwitch = switch (keyEvent.key()) {
                        case GLFW.GLFW_KEY_Q -> EditorTool.SELECT;
                        case GLFW.GLFW_KEY_W -> EditorTool.MOVE;
                        case GLFW.GLFW_KEY_E -> EditorTool.ROTATE;
                        case GLFW.GLFW_KEY_R -> EditorTool.SCALE;
                        default -> null;
                    };
                    if (toolSwitch != null) {
                        runtime.setTool(toolSwitch);
                        continue;
                    }
                }
                if (!cameraCapturing && inspectorPanel != null) {
                    inspectorPanel.handleKey(uiContext, keyEvent);
                }
                if (!cameraCapturing && scenePanel != null) {
                    scenePanel.handleKey(uiContext, keyEvent);
                }
                if (!cameraCapturing && assetsPanel != null) {
                    assetsPanel.handleKey(uiContext, keyEvent);
                }
                if (!cameraCapturing && bottomPanel != null) {
                    bottomPanel.handleKey(uiContext, keyEvent);
                }
            } else if (event instanceof TextInputEvent textEvent) {
                if (quickSearchDialog != null && quickSearchDialog.isOpen()) {
                    quickSearchDialog.handleTextInput(textEvent.codepoint());
                    continue;
                }
                if (scriptEditorDialog != null && scriptEditorDialog.isOpen() && state != null && !state.activeScriptPath.isBlank()) {
                    scriptEditorDialog.handleTextInput(uiContext, textEvent.codepoint());
                    continue;
                }
                if (textAssetEditorDialog != null && textAssetEditorDialog.isOpen()) {
                    textAssetEditorDialog.handleTextInput(uiContext, textEvent.codepoint());
                    continue;
                }
                if (blocked) {
                    continue;
                }
                if (!cameraCapturing && inspectorPanel != null) {
                    inspectorPanel.handleTextInput(uiContext, textEvent);
                }
                if (!cameraCapturing && scenePanel != null) {
                    scenePanel.handleTextInput(uiContext, textEvent);
                }
                if (!cameraCapturing && assetsPanel != null) {
                    assetsPanel.handleTextInput(uiContext, textEvent);
                }
                if (!cameraCapturing && bottomPanel != null) {
                    bottomPanel.handleTextInput(uiContext, textEvent);
                }
            }
        }
    }

    private void frameSelected(EditorContext ctx) {
        if (ctx == null || ctx.camera() == null) {
            return;
        }
        if (gizmos == null) {
            return;
        }
        EditorState state = runtime.state();
        if (state == null || state.scene == null || state.selectedId <= 0L) {
            return;
        }
        long nodeId = state.selectedId;
        SceneSnapshot.NodeSnapshot node = state.scene.getNode(nodeId);
        if (node == null) {
            return;
        }
        Vector3f world = new Vector3f();
        if (!gizmos.tryGetWorldPos(state, nodeId, world)) {
            return;
        }
        double distance = estimateFrameDistance(node);
        ctx.camera().frameTarget(world.x, world.y, world.z, distance);
    }

    private static double estimateFrameDistance(SceneSnapshot.NodeSnapshot node) {
        if (node == null || node.properties() == null) {
            return 8.0;
        }
        float sx = 1.0f;
        float sy = 1.0f;
        float sz = 1.0f;
        boolean any = false;
        for (SceneSnapshot.Property p : node.properties()) {
            if (p == null || p.key() == null) {
                continue;
            }
            switch (p.key()) {
                case "sx" -> {
                    sx = ParseUtils.parseFloat(p.value(), sx);
                    any = true;
                }
                case "sy" -> {
                    sy = ParseUtils.parseFloat(p.value(), sy);
                    any = true;
                }
                case "sz" -> {
                    sz = ParseUtils.parseFloat(p.value(), sz);
                    any = true;
                }
                default -> {
                }
            }
        }
        if (!any) {
            return 8.0;
        }
        float max = Math.max(Math.abs(sx), Math.max(Math.abs(sy), Math.abs(sz)));
        if (!Float.isFinite(max) || max <= 0.0f) {
            return 8.0;
        }
        double dist = max * 2.5 + 2.0;
        if (dist < 3.0) {
            dist = 3.0;
        }
        if (dist > 64.0) {
            dist = 64.0;
        }
        return dist;
    }

    private void showToast(String message, boolean error, int durationMs) {
        toastMessage = message;
        toastError = error;
        toastUntilMs = System.currentTimeMillis() + Math.max(250, durationMs);
        if (error) {
            ClientDebugLog.error(message);
        } else if (ClientDebugLog.enabled()) {
            ClientDebugLog.debug("toast: " + message);
        }
    }

    private void renderTooltip(int w, int h) {
        String text = runtime.consumeTooltipText();
        if (text == null || text.isBlank() || batch == null) return;
        int padX = 8;
        int padY = 4;
        int textW = Math.max(40, text.length() * 6);
        int boxW = textW + padX * 2;
        int boxH = 20;
        int x = runtime.tooltipX() - boxW / 2;
        int y = runtime.tooltipY();
        if (x < 4) x = 4;
        if (x + boxW > w - 4) x = w - 4 - boxW;
        if (y + boxH > h - 4) y = runtime.tooltipY() - boxH - 8;
        batch.drawRect(x, y, boxW, boxH, 0xEE111111);
        batch.drawText(text, x + padX, batch.baselineForBox(y, boxH), 0xFFFFFFFF);
    }

    private void renderToast(int w, int h) {
        String message = toastMessage;
        if (message == null || batch == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= toastUntilMs) {
            toastMessage = null;
            return;
        }

        int x = 12;
        int boxH = 24;
        int y = h - 12 - boxH;
        int maxW = Math.max(120, Math.min(w - 24, 520));
        int boxW = Math.min(maxW, Math.max(160, 16 + message.length() * 7));

        int bg = toastError ? 0xCC331111 : 0xCC111111;
        int fg = toastError ? Theme.toArgb(theme.danger) : 0xFFFFFFFF;

        batch.drawRect(x, y, boxW, boxH, bg);
        batch.drawText(message, x + 10, batch.baselineForBox(y, boxH), fg);
    }
}
