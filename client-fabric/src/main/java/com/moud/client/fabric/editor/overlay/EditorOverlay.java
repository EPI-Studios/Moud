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
import com.moud.client.fabric.editor.dialogs.CreateNodeDialog;
import com.moud.client.fabric.editor.ghost.EditorGhostBlocks;
import com.moud.client.fabric.editor.net.EditorNet;
import com.moud.client.fabric.editor.panels.*;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.editor.theme.EditorTheme;
import com.moud.client.fabric.editor.tools.EditorGizmos;
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
    private com.miry.ui.layout.SplitNode rootWithBars;
    private com.miry.ui.layout.SplitNode bodyWithStatus;

    public EditorOverlay() {
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
        EditorGhostBlocks.get().onAck(ack);
    }

    public void onSchema(SchemaSnapshot schema) {
        state.onSchema(schema);
    }

    public void onSceneList(SceneList list) {
        state.onSceneList(list);
    }

    public void requestSnapshot(Session session) {
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

        applyBarRatios(h);
        dockSpace.resize(w, h);
        windowManager.update(uiContext, input, w, h);
        boolean modalOpen = createNodeDialog != null && createNodeDialog.isOpen();
        boolean blockedByWindows = windowManager.blocksInput();
        boolean blocked = modalOpen || blockedByWindows;

        processUiEvents();
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

    private void applyBarRatios(int h) {
        if (rootWithBars == null || bodyWithStatus == null) {
            return;
        }
        int toolbarPx = Math.max(32, theme.tokens.itemHeight + 10);
        int statusPx = Math.max(22, theme.tokens.itemHeight - 2);

        float topRatio = toolbarPx / (float) Math.max(1, h);
        topRatio = Math.max(0.03f, Math.min(0.18f, topRatio));
        rootWithBars.splitRatio = topRatio;

        int remaining = Math.max(1, h - toolbarPx);
        float bodyRatio = (remaining - statusPx) / (float) remaining;
        bodyRatio = Math.max(0.70f, Math.min(0.98f, bodyRatio));
        bodyWithStatus.splitRatio = bodyRatio;
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
        scenePanel = new ScenePanel(runtime);
        LeafNode scene = new LeafNode(scenePanel);
        gizmos = new EditorGizmos(runtime);
        LeafNode viewport = new LeafNode(new ViewportPanel(runtime, gizmos));
        inspectorPanel = new InspectorPanel(runtime);
        LeafNode rightDock = new LeafNode(inspectorPanel);
        rightDock.addTab(new NodeGraphPanel(runtime));
        LeafNode ops = new LeafNode(new OpsPanel(runtime));
        LeafNode toolbar = new LeafNode(new ToolbarPanel(runtime));
        LeafNode status = new LeafNode(new StatusPanel(runtime));

        for (LeafNode leaf : new LeafNode[]{scene, viewport, rightDock, ops}) {
            leaf.setHeaderButtons(LeafNode.HeaderButtons.CLOSE_ONLY);
            leaf.setHeaderButtonsOnlyOnHover(true);
            leaf.setHeaderHeight(20);
        }

        int panelBg = Theme.toArgb(theme.panelBg);
        scene.setBackgroundArgb(panelBg);
        rightDock.setBackgroundArgb(panelBg);
        ops.setBackgroundArgb(panelBg);
        viewport.setBackgroundArgb(0xFF15151A);

        toolbar.setHeaderHeight(0);
        toolbar.setHeaderButtons(LeafNode.HeaderButtons.NONE);
        status.setHeaderHeight(0);
        status.setHeaderButtons(LeafNode.HeaderButtons.NONE);

        SplitNode centerCol = new SplitNode(viewport, ops, true, 0.62f);

        SplitNode leftAndCenter = new SplitNode(scene, centerCol, false, 0.30f);
        SplitNode body = new SplitNode(leftAndCenter, rightDock, false, 0.78f);

        bodyWithStatus = new SplitNode(body, status, true, 0.96f);
        rootWithBars = new SplitNode(toolbar, bodyWithStatus, true, 0.06f);

        DockSpace ds = new DockSpace(rootWithBars);
        ds.setUi(ui);
        ds.setUiContext(uiContext);
        return ds;
    }

    private void processUiEvents() {
        if (uiContext == null) return;
        UiEvent event;
        while ((event = uiContext.pollEvent()) != null) {
            if (event instanceof KeyEvent keyEvent) {
                if (createNodeDialog != null && createNodeDialog.isOpen()) {
                    if (createNodeDialog.handleKey(uiContext, keyEvent)) {
                        continue;
                    }
                }
                if (inspectorPanel != null) {
                    inspectorPanel.handleKey(uiContext, keyEvent);
                }
                if (scenePanel != null) {
                    scenePanel.handleKey(uiContext, keyEvent);
                }
            } else if (event instanceof TextInputEvent textEvent) {
                if (inspectorPanel != null) {
                    inspectorPanel.handleTextInput(uiContext, textEvent);
                }
                if (scenePanel != null) {
                    scenePanel.handleTextInput(uiContext, textEvent);
                }
            }
        }
    }
}