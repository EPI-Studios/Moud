package com.moud.client.fabric.editor.state;


import com.miry.graphics.Texture;
import com.miry.ui.input.UiInput;
import com.moud.client.fabric.assets.AssetsClient;
import com.moud.client.fabric.editor.dialogs.CreateNodeDialog;
import com.moud.client.fabric.editor.dialogs.ScriptEditorDialog;
import com.moud.client.fabric.editor.net.EditorNet;
import com.moud.client.fabric.editor.tools.EditorTool;
import com.moud.core.assets.ResPath;
import com.moud.net.protocol.AssetManifestResponse;
import com.moud.net.protocol.SceneList;
import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.protocol.SchemaSnapshot;
import com.moud.net.protocol.ScriptActionListResponse;
import com.moud.net.protocol.ScriptFileReadResponse;
import com.moud.net.protocol.ScriptFileWriteAck;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class EditorRuntime {
    private static final float SCENE_DRAG_THRESHOLD_PX = 6.0f;

    public static final class ToastRequest {
        public final String message;
        public final boolean error;
        public final int durationMs;

        public ToastRequest(String message, boolean error, int durationMs) {
            this.message = message == null ? "" : message;
            this.error = error;
            this.durationMs = durationMs;
        }
    }

    private final EditorState state;
    private final EditorNet net;
    private CreateNodeDialog createNodeDialog;
    private ScriptEditorDialog scriptEditorDialog;
    private Runnable openCreateSceneAction;
    private AssetsClient assets;
    private Session session;
    private Texture viewportTexture;
    private EditorTool tool = EditorTool.SELECT;
    private boolean gridSnapEnabled;
    private float gridSnapStep = 1.0f;
    private float framebufferScaleX = 1.0f;
    private float framebufferScaleY = 1.0f;
    private int uiWidth;
    private int uiHeight;
    private boolean rightDown;
    private boolean rightPressed;
    private boolean rightReleased;
    private boolean uiBlocked;
    private ToastRequest pendingToast;

    // Deferred rendering: panels register menus here; EditorOverlay renders them after dockspace
    // so they appear on top of all panels and are not scissored to the panel bounds.
    private Runnable overlayMenuRender;

    private String sceneDragId;
    private float sceneDragStartX;
    private float sceneDragStartY;
    private boolean sceneDragActive;

    public EditorRuntime(EditorState state, EditorNet net) {
        this.state = state;
        this.net = net;
    }

    public EditorState state() {
        return state;
    }

    public EditorNet net() {
        return net;
    }

    public Session session() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public Texture viewportTexture() {
        return viewportTexture;
    }

    public void setViewportTexture(Texture viewportTexture) {
        this.viewportTexture = viewportTexture;
    }

    public EditorTool tool() {
        return tool;
    }

    public void setTool(EditorTool tool) {
        this.tool = tool == null ? EditorTool.SELECT : tool;
    }

    public boolean gridSnapEnabled() {
        return gridSnapEnabled;
    }

    public void setGridSnapEnabled(boolean enabled) {
        gridSnapEnabled = enabled;
    }

    public float gridSnapStep() {
        return gridSnapStep;
    }

    public void setGridSnapStep(float step) {
        if (!Float.isFinite(step) || step <= 0.0f) {
            return;
        }
        gridSnapStep = step;
    }

    public void cycleGridSnapStep() {
        float s = gridSnapStep;
        if (Math.abs(s - 1.0f) < 1e-6f) {
            gridSnapStep = 0.5f;
        } else if (Math.abs(s - 0.5f) < 1e-6f) {
            gridSnapStep = 0.1f;
        } else {
            gridSnapStep = 1.0f;
        }
    }

    public float framebufferScaleX() {
        return framebufferScaleX;
    }

    public float framebufferScaleY() {
        return framebufferScaleY;
    }

    public void setFramebufferScale(float framebufferScaleX, float framebufferScaleY) {
        this.framebufferScaleX = Math.max(0.1f, framebufferScaleX);
        this.framebufferScaleY = Math.max(0.1f, framebufferScaleY);
    }

    public int uiWidth() {
        return uiWidth;
    }

    public int uiHeight() {
        return uiHeight;
    }

    public void setUiSize(int width, int height) {
        uiWidth = Math.max(0, width);
        uiHeight = Math.max(0, height);
    }

    public boolean rightDown() {
        return rightDown;
    }

    public boolean rightPressed() {
        return rightPressed;
    }

    public boolean rightReleased() {
        return rightReleased;
    }

    public void setRightMouse(boolean down, boolean pressed, boolean released) {
        rightDown = down;
        rightPressed = pressed;
        rightReleased = released;
    }

    public boolean uiBlocked() {
        return uiBlocked;
    }

    public void setUiBlocked(boolean uiBlocked) {
        this.uiBlocked = uiBlocked;
    }

    public void requestToast(String message, boolean error, int durationMs) {
        if (message == null || message.isBlank()) {
            return;
        }
        pendingToast = new ToastRequest(message, error, durationMs);
    }

    public ToastRequest consumeToastRequest() {
        ToastRequest toast = pendingToast;
        pendingToast = null;
        return toast;
    }

    public CreateNodeDialog getCreateNodeDialog() {
        return createNodeDialog;
    }

    public void setCreateNodeDialog(CreateNodeDialog dialog) {
        this.createNodeDialog = dialog;
    }

    public void setScriptEditorDialog(ScriptEditorDialog dialog) {
        this.scriptEditorDialog = dialog;
    }

    public ScriptEditorDialog scriptEditorDialog() {
        return scriptEditorDialog;
    }

    public void openScriptEditor(long nodeId, String scriptPath) {
        ScriptEditorDialog dialog = scriptEditorDialog;
        if (dialog == null) {
            return;
        }
        dialog.open(nodeId, scriptPath);
    }

    public void setOpenCreateSceneAction(Runnable action) {
        this.openCreateSceneAction = action;
    }

    public void openCreateScene() {
        Runnable action = openCreateSceneAction;
        if (action != null) {
            action.run();
        }
    }

    public AssetsClient assets() {
        return assets;
    }

    public void setAssets(AssetsClient assets) {
        this.assets = assets;
    }

    public boolean saveCurrentScene() {
        Session session = this.session;
        if (session == null || session.state() != SessionState.CONNECTED) {
            return false;
        }
        String sceneId = state.activeSceneId;
        if (sceneId == null || sceneId.isBlank()) {
            return false;
        }
        net.saveScene(session, sceneId);
        return true;
    }

    public void setOverlayMenuRender(Runnable r) {
        this.overlayMenuRender = r;
    }

    public Runnable consumeOverlayMenuRender() {
        Runnable r = this.overlayMenuRender;
        this.overlayMenuRender = null;
        return r;
    }

    public void beginSceneDrag(String sceneId, float mouseX, float mouseY) {
        if (sceneId == null || sceneId.isBlank()) {
            return;
        }
        sceneDragId = sceneId;
        sceneDragStartX = mouseX;
        sceneDragStartY = mouseY;
        sceneDragActive = false;
    }

    public void updateSceneDrag(UiInput input) {
        if (sceneDragId == null || sceneDragActive || input == null || !input.mouseDown()) {
            return;
        }
        float mx = input.mousePos().x;
        float my = input.mousePos().y;
        float dx = mx - sceneDragStartX;
        float dy = my - sceneDragStartY;
        float threshold = SCENE_DRAG_THRESHOLD_PX;
        if (dx * dx + dy * dy >= threshold * threshold) {
            sceneDragActive = true;
        }
    }

    public String sceneDragId() {
        return sceneDragId;
    }

    public boolean sceneDragActive() {
        return sceneDragActive;
    }

    public void clearSceneDrag() {
        sceneDragId = null;
        sceneDragActive = false;
    }
}
