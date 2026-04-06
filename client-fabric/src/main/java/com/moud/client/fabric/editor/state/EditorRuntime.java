package com.moud.client.fabric.editor.state;


import com.miry.graphics.Texture;
import com.moud.client.fabric.assets.AssetsClient;
import com.moud.client.fabric.editor.dialogs.CreateAssetDialog;
import com.moud.client.fabric.editor.dialogs.CreateNodeDialog;
import com.moud.client.fabric.editor.dialogs.QuickSearchDialog;
import com.moud.client.fabric.editor.dialogs.ScriptEditorDialog;
import com.moud.client.fabric.editor.dialogs.TextAssetEditorDialog;
import com.moud.client.fabric.editor.net.EditorNet;
import com.moud.client.fabric.editor.tools.EditorTool;
import com.moud.core.assets.AssetHash;
import com.moud.core.assets.ResPath;
import com.moud.net.protocol.AssetManifestResponse;
import com.moud.net.protocol.SceneList;
import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.protocol.SchemaSnapshot;
import com.moud.net.protocol.ScriptActionListResponse;
import com.moud.net.protocol.ScriptFileReadResponse;
import com.moud.net.protocol.ScriptFileWriteAck;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongConsumer;

public final class EditorRuntime {
    private static final String PROP_SCENE_MODE = "scene_mode";

    public enum ViewportMode {
        THREE_D,
        TWO_D
    }

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
    private CreateAssetDialog createAssetDialog;
    private ScriptEditorDialog scriptEditorDialog;
    private TextAssetEditorDialog textAssetEditorDialog;
    private QuickSearchDialog quickSearchDialog;
    private Runnable openCreateSceneAction;
    private Runnable openEditorSettingsAction;
    private AssetsClient assets;
    private Session session;
    private Texture viewportTexture;
    private EditorTool tool = EditorTool.SELECT;
    private ViewportMode viewportMode = ViewportMode.THREE_D;
    private boolean gridSnapEnabled;
    private float gridSnapStep = 1.0f;
    private boolean rotationSnapEnabled = true;
    private float rotationSnapDeg = 15.0f;
    private boolean gizmoLocalSpace;
    private boolean frameSelectedRequested;
    private boolean frameSelected2DRequested;
    private float framebufferScaleX = 1.0f;
    private float framebufferScaleY = 1.0f;
    private int uiWidth;
    private int uiHeight;
    private boolean rightDown;
    private boolean rightPressed;
    private boolean rightReleased;
    private boolean uiBlocked;
    private float editorUiScale = 1.0f;
    private ToastRequest pendingToast;
    private final HashMap<Long, LongConsumer> afterCreateByBatchId = new HashMap<>();
    private final EditorHistory history = new EditorHistory();

    private Runnable overlayMenuRender;

    private final HashMap<String, ViewportMode> pendingSceneModes = new HashMap<>();

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

    public ViewportMode viewportMode() {
        return viewportMode;
    }

    public void setViewportMode(ViewportMode mode) {
        viewportMode = mode == null ? ViewportMode.THREE_D : mode;
    }

    public void markSceneMode(String sceneId, ViewportMode mode) {
        if (sceneId == null || sceneId.isBlank()) {
            return;
        }
        ViewportMode m = mode == null ? ViewportMode.THREE_D : mode;
        if (m != ViewportMode.TWO_D) {
            return;
        }
        pendingSceneModes.put(sceneId, m);
    }

    public void onSnapshot(SceneSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        EditorState st = state;
        if (st == null) {
            return;
        }
        String sceneId = st.activeSceneId;
        if (sceneId == null || sceneId.isBlank()) {
            return;
        }
        ViewportMode pending = pendingSceneModes.remove(sceneId);
        if (pending == null) {
            return;
        }
        applySceneModeTemplate(sceneId, pending);
    }

    private void applySceneModeTemplate(String sceneId, ViewportMode mode) {
        EditorState st = state;
        Session sess = session;
        if (st == null || sess == null || net == null || st.scene == null) {
            return;
        }

        long rootId = findRootNodeId(st);
        if (rootId <= 0L) {
            st.pendingSnapshot = true;
            return;
        }

        if (mode != ViewportMode.TWO_D) {
            return;
        }
        String modeValue = "2d";
        ArrayList<SceneOp> ops = new ArrayList<>();
        ops.add(new SceneOp.SetProperty(rootId, PROP_SCENE_MODE, modeValue));

        for (var node : st.scene.nodes()) {
            if (node == null) {
                continue;
            }
            if (node.parentId() != rootId) {
                continue;
            }
            String type = node.type();
            String name = node.name();
            if ("WorldEnvironment".equals(type) || "WorldEnvironment".equals(name)
                    || "Ticker".equals(type) || "ticker".equalsIgnoreCase(name)) {
                ops.add(new SceneOp.QueueFree(node.nodeId()));
            }
        }
        setViewportMode(ViewportMode.TWO_D);

        net.sendOps(sess, st, ops);
        st.pendingSnapshot = true;
    }

    private static long findRootNodeId(EditorState st) {
        if (st == null || st.scene == null) {
            return 0L;
        }
        var roots = st.scene.childrenOf(0L);
        if (roots != null) {
            for (var node : roots) {
                if (node != null && "Root".equals(node.type())) {
                    return node.nodeId();
                }
            }
            for (var node : roots) {
                if (node != null) {
                    return node.nodeId();
                }
            }
        }
        return 0L;
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

    public boolean rotationSnapEnabled() { return rotationSnapEnabled; }
    public void setRotationSnapEnabled(boolean v) { rotationSnapEnabled = v; }
    public float rotationSnapDeg() { return rotationSnapDeg; }
    public boolean gizmoLocalSpace() { return gizmoLocalSpace; }
    public void setGizmoLocalSpace(boolean v) { gizmoLocalSpace = v; }

    public void requestFrameSelected() { frameSelectedRequested = true; }
    public boolean consumeFrameSelectedRequest() {
        boolean v = frameSelectedRequested;
        frameSelectedRequested = false;
        return v;
    }

    public void requestFrameSelected2D() { frameSelected2DRequested = true; }
    public boolean consumeFrameSelected2DRequest() {
        boolean v = frameSelected2DRequested;
        frameSelected2DRequested = false;
        return v;
    }

    public void cycleRotationSnapDeg() {
        if (Math.abs(rotationSnapDeg - 15.0f) < 1e-3f) {
            rotationSnapDeg = 45.0f;
        } else if (Math.abs(rotationSnapDeg - 45.0f) < 1e-3f) {
            rotationSnapDeg = 90.0f;
        } else {
            rotationSnapDeg = 15.0f;
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

    public float editorUiScale() {
        return editorUiScale;
    }

    public void setEditorUiScale(float scale) {
        if (!Float.isFinite(scale)) {
            return;
        }
        editorUiScale = Math.max(0.75f, Math.min(1.75f, scale));
    }

    public void requestToast(String message, boolean error, int durationMs) {
        if (message == null || message.isBlank()) {
            return;
        }
        pendingToast = new ToastRequest(message, error, durationMs);
    }

    public EditorHistory history() {
        return history;
    }

    public void afterCreateNode(long batchId, LongConsumer callback) {
        if (batchId <= 0L || callback == null) {
            return;
        }
        afterCreateByBatchId.put(batchId, callback);
    }

    public void onSceneOpAck(SceneOpAck ack) {
        if (ack == null || ack.batchId() <= 0L) {
            return;
        }
        LongConsumer cb = afterCreateByBatchId.remove(ack.batchId());
        if (cb == null || ack.results() == null) {
            return;
        }
        for (var r : ack.results()) {
            if (r != null && r.createdId() > 0L) {
                cb.accept(r.createdId());
                return;
            }
        }
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

    public void setCreateAssetDialog(CreateAssetDialog dialog) {
        this.createAssetDialog = dialog;
    }

    public CreateAssetDialog createAssetDialog() {
        return createAssetDialog;
    }

    public void setScriptEditorDialog(ScriptEditorDialog dialog) {
        this.scriptEditorDialog = dialog;
    }

    public ScriptEditorDialog scriptEditorDialog() {
        return scriptEditorDialog;
    }

    public void setTextAssetEditorDialog(TextAssetEditorDialog dialog) {
        this.textAssetEditorDialog = dialog;
    }

    public TextAssetEditorDialog textAssetEditorDialog() {
        return textAssetEditorDialog;
    }

    public void openScriptEditor(long nodeId, String scriptPath) {
        if (scriptPath != null && !scriptPath.isBlank()) {
            state.ensureScriptOpen(scriptPath);
        }
        ScriptEditorDialog dialog = scriptEditorDialog;
        if (dialog == null) {
            return;
        }
        dialog.open(nodeId, scriptPath);
    }

    public void openTextAssetEditor(String resPath, AssetHash hash) {
        TextAssetEditorDialog dialog = textAssetEditorDialog;
        if (dialog == null) return;
        dialog.open(resPath, hash);
        EditorState st = state;
        if (st != null) {
            st.activeScriptPath = "";
            st.ensureTextAssetOpen(resPath);
        }
    }

    public void openTextAssetEditor(String resPath, AssetHash hash, String initialText) {
        TextAssetEditorDialog dialog = textAssetEditorDialog;
        if (dialog == null) return;
        dialog.open(resPath, hash, initialText);
        EditorState st = state;
        if (st != null) {
            st.activeScriptPath = "";
            st.ensureTextAssetOpen(resPath);
        }
    }

    public void openCreateAsset() {
        CreateAssetDialog dialog = createAssetDialog;
        if (dialog != null) {
            dialog.open();
        }
    }

    public void setQuickSearchDialog(QuickSearchDialog dialog) {
        this.quickSearchDialog = dialog;
    }

    public QuickSearchDialog quickSearchDialog() {
        return quickSearchDialog;
    }

    public void openQuickSearch() {
        QuickSearchDialog dialog = quickSearchDialog;
        if (dialog != null) {
            dialog.open();
        }
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

    public void setOpenEditorSettingsAction(Runnable action) {
        this.openEditorSettingsAction = action;
    }

    public void openEditorSettings() {
        Runnable action = openEditorSettingsAction;
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
}
