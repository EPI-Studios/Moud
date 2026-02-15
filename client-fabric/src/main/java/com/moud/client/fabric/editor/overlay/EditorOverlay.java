package com.moud.client.fabric.editor.overlay;

import com.miry.graphics.batch.BatchRenderer;
import com.miry.graphics.Framebuffer;
import com.miry.graphics.Texture;
import com.miry.graphics.post.GaussianBlur;
import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.font.FontAtlas;
import com.miry.ui.font.FontData;
import com.miry.ui.font.TextRenderer;
import com.miry.ui.input.UiInput;
import com.miry.ui.layout.DockSpace;
import com.miry.ui.layout.LeafNode;
import com.miry.ui.layout.SplitNode;
import com.miry.ui.window.UiWindow;
import com.miry.ui.window.WindowManager;
import com.miry.ui.theme.Theme;
import com.miry.ui.event.UiEvent;
import com.miry.ui.event.KeyEvent;
import com.miry.ui.event.TextInputEvent;
import com.moud.client.fabric.assets.AssetsClient;
import com.moud.client.fabric.editor.dialogs.CreateNodeDialog;
import com.moud.client.fabric.platform.MinecraftGhostBlocks;
import com.moud.client.fabric.editor.net.EditorNet;
import com.moud.client.fabric.editor.panels.*;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.editor.theme.EditorTheme;
import com.moud.client.fabric.editor.tools.EditorGizmos;
import com.moud.net.protocol.SceneSaveAck;
import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.protocol.SceneList;
import com.moud.net.protocol.SchemaSnapshot;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

public final class EditorOverlay {
    private final Theme theme = new Theme();
    private final Ui ui = new Ui(theme);
    private final UiInput input = new UiInput();
    private UiContext uiContext;
    private final EditorState state = new EditorState();
    private final EditorNet net = new EditorNet();
    private final EditorRuntime runtime = new EditorRuntime(state, net);
    private BatchRenderer batch;
    private FontAtlas fontAtlas;
    private final ViewportCapture viewportCapture = new ViewportCapture();
    private Framebuffer uiFramebuffer;
    private GaussianBlur blur;
    private WindowManager windowManager;
    private CreateNodeDialog createNodeDialog;

    private boolean open;
    private boolean prevLeft;
    private boolean prevHudHidden;
    private DockSpace dockSpace;
    private InspectorPanel inspectorPanel;
    private EditorGizmos gizmos;
    private ScenePanel scenePanel;
    private com.miry.ui.layout.SplitNode rootWithTop;
    private com.miry.ui.layout.SplitNode mainWithBottom;
    private com.miry.ui.layout.SplitNode mainRow;
    private com.miry.ui.layout.SplitNode viewportAndRight;
    private com.miry.ui.layout.SplitNode leftColumn;
    private boolean prevCameraCapturing;
    private boolean layoutSeeded;
    private int layoutSeedW;
    private int layoutSeedH;

    private String toastMessage;
    private boolean toastError;
    private long toastUntilMs;

    public EditorOverlay(AssetsClient assets) {
        runtime.setAssets(assets);
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
    }

    public boolean isOpen() {
        return open;
    }

    public void onSnapshot(SceneSnapshot snapshot) {
        state.onSnapshot(snapshot);
    }

    public void onAck(SceneOpAck ack) {
        state.onAck(ack);
        MinecraftGhostBlocks.get().onAck(ack);
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
            showToast("Saved scene: " + ack.sceneId(), false, 2500);
            return;
        }
        String error = ack.error();
        if (error == null || error.isBlank()) {
            error = "Unknown error";
        }
        showToast("Save failed (" + ack.sceneId() + "): " + error, true, 5000);
    }

    public void requestSnapshot(Session session) {
        runtime.setSession(session);
        net.requestSnapshot(session, state);
    }

    public void close() {
        if (uiContext != null) {
            uiContext.close();
            uiContext = null;
        }
        if (GLFW.glfwGetCurrentContext() == 0L) {
            fontAtlas = null;
            batch = null;
            gizmos = null;
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
    }

    public void render(Session session) {
        if (!open || session == null || session.state() != SessionState.CONNECTED) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        if (client.currentScreen != null) {
            return;
        }
        Window window = client.getWindow();
        int w = window.getScaledWidth();
        int h = window.getScaledHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float framebufferScale = window.getFramebufferWidth() / (float) w;
        framebufferScale = Math.max(0.1f, framebufferScale);

        long handle = window.getHandle();
        EditorContext ctx = EditorOverlayBus.get();
        float scrollY = ctx != null ? ctx.consumeScrollY() : 0.0f;

        float mx = (float) (client.mouse.getX() * w / (double) Math.max(1, window.getWidth()));
        float my = (float) (client.mouse.getY() * h / (double) Math.max(1, window.getHeight()));
        boolean left = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;
        boolean leftPressed = left && !prevLeft;
        boolean leftReleased = !left && prevLeft;
        prevLeft = left;
        boolean right = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_2) == GLFW.GLFW_PRESS;
        boolean rightPressed = right && !runtime.rightDown();
        boolean rightReleased = !right && runtime.rightDown();
        boolean ctrl = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        boolean shift = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        boolean alt = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
        boolean sup = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SUPER) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SUPER) == GLFW.GLFW_PRESS;

        ensureInitialized(window, handle);
        viewportCapture.capture(window);
        runtime.setSession(session);
        runtime.setViewportTexture(viewportCapture.texture());
        runtime.setFramebufferScale(framebufferScale, framebufferScale);
        runtime.setRightMouse(right, rightPressed, rightReleased);

        input.setMousePos(mx, my)
                .setMouseButtons(left, leftPressed, leftReleased)
                .setModifiers(ctrl, shift, alt, sup)
                .setScrollY(scrollY);

        ui.beginFrame(input, 1.0f / 60.0f);
        if (uiContext != null) {
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

        boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        if (cullWasEnabled) GL11.glDisable(GL11.GL_CULL_FACE);
        boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        if (depthWasEnabled) GL11.glDisable(GL11.GL_DEPTH_TEST);

        if (dockSpace == null || windowManager == null || uiContext == null) {
            batch.begin(w, h, framebufferScale);
            batch.drawRect(0, 0, w, h, 0xAA000000);
            batch.drawText("MOUD editor overlay: init failed", 12, batch.baselineForBox(8, 24), 0xFFFFFFFF);
            batch.end();
            if (depthWasEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST);
            if (cullWasEnabled) GL11.glEnable(GL11.GL_CULL_FACE);
            return;
        }

        applyBarRatios(w, h);
        dockSpace.resize(w, h);
        windowManager.update(uiContext, input, w, h);
        boolean modalOpen = createNodeDialog != null && createNodeDialog.isOpen();
        boolean blockedByWindows = windowManager.blocksInput();
        boolean blocked = modalOpen || blockedByWindows;

        processUiEvents(cameraCapturing);
        if (!blocked) {
            dockSpace.update(input);
        }

        boolean needsBackdropBlur = false;
        for (UiWindow uiWindow : windowManager.windows()) {
            if (uiWindow.backdropBlur()) {
                needsBackdropBlur = true;
                break;
            }
        }

        if (!needsBackdropBlur) {
            batch.begin(w, h, framebufferScale);
            dockSpace.render(batch);
            uiContext.overlay().render(batch);
            windowManager.render(batch, uiContext, input, theme, w, h, null);

            if (createNodeDialog != null && createNodeDialog.isOpen()) {
                createNodeDialog.render(batch, uiContext, ui, theme, w, h);
            }

            renderToast(w, h);
            batch.end();

            if (depthWasEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST);
            if (cullWasEnabled) GL11.glEnable(GL11.GL_CULL_FACE);
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
            GL11.glClearColor(theme.windowBg.x, theme.windowBg.y, theme.windowBg.z, theme.windowBg.w);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            batch.begin(w, h, framebufferScale);
            dockSpace.render(batch);
            uiContext.overlay().render(batch);
            batch.end();
        }

        Texture blurred = null;
        blurred = blur.blur(uiFramebuffer.colorTexture(), fbW, fbH, 1);

        batch.begin(w, h, framebufferScale);
        batch.drawTexturedRect(uiFramebuffer.colorTexture(), 0, 0, w, h, 0.0f, 1.0f, 1.0f, 0.0f, 0xFFFFFFFF);
        windowManager.render(batch, uiContext, input, theme, w, h, blurred);

        if (createNodeDialog != null && createNodeDialog.isOpen()) {
            createNodeDialog.render(batch, uiContext, ui, theme, w, h);
        }

        renderToast(w, h);
        batch.end();

        if (depthWasEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST);
        if (cullWasEnabled) GL11.glEnable(GL11.GL_CULL_FACE);
    }

    private void ensureInitialized(Window window, long handle) {
        if (batch != null) {
            return;
        }
        applyEngineEditorTheme();
        batch = new BatchRenderer(50_000);
        installFont(window);
        viewportCapture.ensureInitialized();
        uiContext = new UiContext(handle, UiContext.Config.MANUAL_INPUT);
        windowManager = new WindowManager();
        createNodeDialog = new CreateNodeDialog(runtime);
        runtime.setCreateNodeDialog(createNodeDialog);
        dockSpace = createDockSpace();
        dockSpace.setSplitterSize(5);
        dockSpace.setSplitterDrawSize(2);
    }

    private void applyEngineEditorTheme() {
        EditorTheme.apply(theme);
    }

    private void applyBarRatios(int w, int h) {
        if (rootWithTop == null || mainWithBottom == null || mainRow == null || viewportAndRight == null || leftColumn == null) {
            return;
        }
        int topPx = 30;

        float topRatio = topPx / (float) Math.max(1, h);
        rootWithTop.splitRatio = clampRatio(topRatio, 0.03f, 0.20f);

        boolean reseed = !layoutSeeded || layoutSeedW != w || layoutSeedH != h;
        if (!reseed) {
            return;
        }
        layoutSeeded = true;
        layoutSeedW = w;
        layoutSeedH = h;

        // Default split sizing (user can still resize via splitters).
        int bottomPx = 32;
        int remaining = Math.max(1, h - topPx);
        float mainRatio = (remaining - bottomPx) / (float) remaining;
        mainWithBottom.splitRatio = clampRatio(mainRatio, 0.55f, 0.98f);

        // Left column sizing: approximate Godot dock widths in pixels.
        int leftPx = 280;
        int rightPx = 320;
        int mainW = Math.max(1, w);
        float leftRatio = leftPx / (float) mainW;
        mainRow.splitRatio = clampRatio(leftRatio, 0.18f, 0.45f);

        int centerAndRightW = Math.max(1, mainW - leftPx);
        float centerRatio = (centerAndRightW - rightPx) / (float) centerAndRightW;
        viewportAndRight.splitRatio = clampRatio(centerRatio, 0.40f, 0.82f);

        // Left column split between Scene and FileSystem.
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
                && key == GLFW.GLFW_KEY_S
                && ((mods & GLFW.GLFW_MOD_CONTROL) != 0 || (mods & GLFW.GLFW_MOD_SUPER) != 0)) {
            boolean sent = runtime.saveCurrentScene();
            if (sent) {
                showToast("Saving scene: " + state.activeSceneId + "...", false, 2000);
            } else {
                showToast("Save failed: not connected", true, 3500);
            }
            return;
        }
        uiContext.keyboard().pushKeyEvent(key, scancode, act, mods);
    }

    public void pushCharEvent(int codepoint) {
        if (!open || uiContext == null) {
            return;
        }
                if (createNodeDialog != null && createNodeDialog.isOpen()) {
                    createNodeDialog.handleTextInput(codepoint);
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
        scale = Math.max(0.1f, scale);
        int atlasSize = Math.min(2048, Math.max(1024, Math.round(768.0f * scale)));
        fontAtlas = new FontAtlas(FontData.loadDefault(), 16.0f, atlasSize, scale, FontAtlas.Mode.COVERAGE);
        batch.setTextRenderer(new TextRenderer(fontAtlas));
    }

    private DockSpace createDockSpace() {
        LeafNode top = new LeafNode(new ToolbarPanel(runtime));

        scenePanel = new ScenePanel(runtime);
        LeafNode scene = new LeafNode(scenePanel);

        LeafNode filesystem = new LeafNode(new AssetsPanel(runtime));

        leftColumn = new SplitNode(scene, filesystem, true, 0.50f);

        gizmos = new EditorGizmos(runtime);
        LeafNode viewport = new LeafNode(new ViewportPanel(runtime, gizmos));

        inspectorPanel = new InspectorPanel(runtime);
        LeafNode right = new LeafNode(inspectorPanel);

        LeafNode bottom = new LeafNode(new BottomPanel(runtime));

        for (LeafNode leaf : new LeafNode[]{top, scene, filesystem, viewport, right, bottom}) {
            leaf.setHeaderHeight(0);
            leaf.setHeaderButtons(LeafNode.HeaderButtons.NONE);
        }

        int panelBg = Theme.toArgb(theme.panelBg);
        scene.setBackgroundArgb(panelBg);
        filesystem.setBackgroundArgb(panelBg);
        right.setBackgroundArgb(panelBg);
        bottom.setBackgroundArgb(panelBg);
        viewport.setBackgroundArgb(0xFF15171B);
        top.setBackgroundArgb(Theme.toArgb(theme.windowBg));

        viewportAndRight = new SplitNode(viewport, right, false, 0.72f);
        mainRow = new SplitNode(leftColumn, viewportAndRight, false, 0.30f);
        mainWithBottom = new SplitNode(mainRow, bottom, true, 0.92f);
        rootWithTop = new SplitNode(top, mainWithBottom, true, 0.06f);

        DockSpace ds = new DockSpace(rootWithTop);
        ds.setUi(ui);
        ds.setUiContext(uiContext);
        return ds;
    }

    private void processUiEvents(boolean cameraCapturing) {
        if (uiContext == null) return;
        UiEvent event;
        while ((event = uiContext.pollEvent()) != null) {
            if (event instanceof KeyEvent keyEvent) {
                if (createNodeDialog != null && createNodeDialog.isOpen()) {
                    if (createNodeDialog.handleKey(uiContext, keyEvent)) {
                        continue;
                    }
                }
                if (!cameraCapturing && inspectorPanel != null) {
                    inspectorPanel.handleKey(uiContext, keyEvent);
                }
                if (!cameraCapturing && scenePanel != null) {
                    scenePanel.handleKey(uiContext, keyEvent);
                }
            } else if (event instanceof TextInputEvent textEvent) {
                if (!cameraCapturing && inspectorPanel != null) {
                    inspectorPanel.handleTextInput(uiContext, textEvent);
                }
            }
        }
    }

    private static float clampRatio(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private void showToast(String message, boolean error, int durationMs) {
        toastMessage = message;
        toastError = error;
        toastUntilMs = System.currentTimeMillis() + Math.max(250, durationMs);
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
        int fg = toastError ? 0xFFFFB3B3 : 0xFFFFFFFF;

        batch.drawRect(x, y, boxW, boxH, bg);
        batch.drawText(message, x + 10, batch.baselineForBox(y, boxH), fg);
    }
}
