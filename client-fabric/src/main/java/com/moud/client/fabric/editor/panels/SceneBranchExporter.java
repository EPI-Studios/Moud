package com.moud.client.fabric.editor.panels;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.ContextMenu;
import com.miry.ui.widgets.TextField;
import com.moud.client.fabric.assets.AssetsClient;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.editor.util.EditorUiUtil;
import com.moud.core.assets.AssetType;
import com.moud.core.assets.ResPath;
import com.moud.core.scene.SceneFile;
import com.moud.net.protocol.AssetTransferStatus;
import com.moud.net.protocol.AssetUploadAck;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.session.Session;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.moud.client.fabric.editor.panels.SceneNodeOps.isRuntimePlayerNode;

class SceneBranchExporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final EditorRuntime runtime;

    boolean saveBranchOpen;
    boolean saveBranchFocusRequested;
    long saveBranchRootId;
    String saveBranchError;
    final TextField saveBranchSceneIdField = new TextField();
    final TextField saveBranchDisplayNameField = new TextField();

    private ContextMenu nodeMenu;
    private ContextMenu addChildMenu;
    private Runnable closeAddChildCategoryMenus = () -> {};

    SceneBranchExporter(EditorRuntime runtime) {
        this.runtime = runtime;
    }

    void setMenus(ContextMenu nodeMenu, ContextMenu addChildMenu, Runnable closeAddChildCategoryMenus) {
        this.nodeMenu = nodeMenu;
        this.addChildMenu = addChildMenu;
        this.closeAddChildCategoryMenus = closeAddChildCategoryMenus != null ? closeAddChildCategoryMenus : () -> {};
    }

    private void closeAddChildCategoryMenus() {
        closeAddChildCategoryMenus.run();
    }

    int renderSaveBranchRow(Ui ui, UiRenderer r, UiContext uiContext, Theme theme, int x, int y, int w, boolean interactive) {
        int pad = theme.design.space_sm;
        int rowH = 34;
        int fieldH = theme.design.widget_height_sm;
        int fieldY = y + (rowH - fieldH) / 2;

        int btnW = 72;
        int cancelW = 72;
        int cancelX = x + w - pad - cancelW;
        int saveX = cancelX - pad - btnW;

        int idW = 120;
        int idX = x + pad;
        int nameX = idX + idW + pad;
        int nameW = Math.max(60, saveX - pad - nameX);

        var input = interactive ? ui.input() : null;
        saveBranchSceneIdField.render(r, uiContext, input, theme, idX, fieldY, idW, fieldH, true);
        if ((saveBranchSceneIdField.text() == null || saveBranchSceneIdField.text().isEmpty()) && (uiContext == null || !saveBranchSceneIdField.isFocused(uiContext))) {
            r.drawText("scene_id", idX + 6, r.baselineForBox(fieldY, fieldH), Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.65f));
        }

        saveBranchDisplayNameField.render(r, uiContext, input, theme, nameX, fieldY, nameW, fieldH, true);
        if ((saveBranchDisplayNameField.text() == null || saveBranchDisplayNameField.text().isEmpty()) && (uiContext == null || !saveBranchDisplayNameField.isFocused(uiContext))) {
            r.drawText("Display name (optional)", nameX + 6, r.baselineForBox(fieldY, fieldH), Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.65f));
        }

        EditorUiUtil.textButton(ui, r, theme, saveX, fieldY, btnW, fieldH, "Save", true, this::commitSaveBranch);
        EditorUiUtil.textButton(ui, r, theme, cancelX, fieldY, cancelW, fieldH, "Cancel", true, () -> {
            saveBranchOpen = false;
            saveBranchError = null;
        });

        if (saveBranchFocusRequested && uiContext != null) {
            saveBranchFocusRequested = false;
            saveBranchSceneIdField.focus(uiContext);
        }

        int cursorY = y + rowH + pad;
        if (saveBranchError != null && !saveBranchError.isBlank()) {
            r.drawText(saveBranchError, x + pad, r.baselineForBox(cursorY, 18), Theme.toArgb(theme.danger));
            cursorY += 18 + pad;
        }
        return cursorY;
    }

    void openSaveBranch(SceneSnapshot.NodeSnapshot node) {
        nodeMenu.close();
        closeAddChildCategoryMenus();
        addChildMenu.close();
        saveBranchRootId = node != null ? node.nodeId() : 0L;
        saveBranchOpen = true;
        saveBranchFocusRequested = true;
        saveBranchError = null;

        String suggested = node != null ? normalizeSceneId(node.name()) : "";
        if (suggested == null || !isValidSceneId(suggested)) {
            suggested = "branch_" + saveBranchRootId;
        }
        saveBranchSceneIdField.setText(suggested);
        saveBranchSceneIdField.setCursorPos(saveBranchSceneIdField.text().length());
        saveBranchDisplayNameField.setText(node != null ? node.name() : "");
        saveBranchDisplayNameField.setCursorPos(saveBranchDisplayNameField.text().length());
    }

    void commitSaveBranch() {
        EditorState state = runtime.state();
        if (state == null || state.scene == null) {
            saveBranchError = "No scene loaded";
            return;
        }

        String sid = normalizeSceneId(saveBranchSceneIdField.text());
        if (!isValidSceneId(sid)) {
            saveBranchError = "Invalid id (use [a-z0-9_-], max 64 chars)";
            return;
        }

        SceneSnapshot.NodeSnapshot selectedRoot = state.scene.getNode(saveBranchRootId);
        if (selectedRoot == null) {
            saveBranchError = "Branch root not found";
            return;
        }

        List<SceneFile.NodeEntry> nodes = buildBranchSceneFileNodes(state, selectedRoot);
        if (nodes.isEmpty()) {
            saveBranchError = "Branch is empty";
            return;
        }

        String displayName = saveBranchDisplayNameField.text();
        SceneFile file = new SceneFile(SceneFile.FORMAT_V1, sid, displayName, nodes);
        String json = GSON.toJson(file);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        if (!uploadSceneFile(sid, bytes)) {
            return;
        }

        saveBranchOpen = false;
        saveBranchError = null;
    }

    static List<SceneFile.NodeEntry> buildBranchSceneFileNodes(EditorState state, SceneSnapshot.NodeSnapshot selectedRoot) {
        if (state == null || state.scene == null || selectedRoot == null) {
            return List.of();
        }

        boolean includeRoot = selectedRoot.parentId() != 0L;
        boolean includeRuntimePlayers = isRuntimePlayerNode(selectedRoot);

        List<SceneSnapshot.NodeSnapshot> roots = includeRoot
                ? List.of(selectedRoot)
                : state.scene.childrenOf(selectedRoot.nodeId());

        ArrayList<SceneSnapshot.NodeSnapshot> branchNodes = new ArrayList<>();
        HashSet<Long> includedIds = new HashSet<>();
        collectBranchNodes(state, roots, includeRuntimePlayers, branchNodes, includedIds);
        if (branchNodes.isEmpty()) {
            return List.of();
        }

        long selectedRootId = selectedRoot.nodeId();
        ArrayList<SceneFile.NodeEntry> out = new ArrayList<>(branchNodes.size());
        for (SceneSnapshot.NodeSnapshot node : branchNodes) {
            out.add(toSceneFileNodeEntry(node, selectedRootId, includeRoot, includedIds));
        }
        return List.copyOf(out);
    }

    static void collectBranchNodes(EditorState state,
                                   List<SceneSnapshot.NodeSnapshot> roots,
                                   boolean includeRuntimePlayers,
                                   List<SceneSnapshot.NodeSnapshot> out,
                                   Set<Long> includedIds) {
        if (state == null || state.scene == null || roots == null || roots.isEmpty()) {
            return;
        }

        ArrayDeque<SceneSnapshot.NodeSnapshot> stack = new ArrayDeque<>(roots.size());
        for (int i = roots.size() - 1; i >= 0; i--) {
            SceneSnapshot.NodeSnapshot node = roots.get(i);
            if (node != null) {
                stack.push(node);
            }
        }

        while (!stack.isEmpty()) {
            SceneSnapshot.NodeSnapshot node = stack.pop();
            if (!includeRuntimePlayers && isRuntimePlayerNode(node)) {
                continue;
            }
            long id = node.nodeId();
            if (!includedIds.add(id)) {
                continue;
            }
            out.add(node);

            List<SceneSnapshot.NodeSnapshot> children = state.scene.childrenOf(id);
            for (int i = children.size() - 1; i >= 0; i--) {
                SceneSnapshot.NodeSnapshot child = children.get(i);
                if (child != null) {
                    stack.push(child);
                }
            }
        }
    }

    static SceneFile.NodeEntry toSceneFileNodeEntry(SceneSnapshot.NodeSnapshot node,
                                                    long selectedRootId,
                                                    boolean includeRoot,
                                                    Set<Long> includedIds) {
        long parent = remapParentId(node, selectedRootId, includeRoot, includedIds);
        String name = exportNodeName(node);
        String type = node.type() == null || node.type().isBlank() ? "Node" : node.type();
        Map<String, String> props = exportNodeProps(node);
        return new SceneFile.NodeEntry(node.nodeId(), parent, name, type, props);
    }

    static long remapParentId(SceneSnapshot.NodeSnapshot node,
                              long selectedRootId,
                              boolean includeRoot,
                              Set<Long> includedIds) {
        if (node == null) {
            return 0L;
        }
        long parent = node.parentId();
        if (includeRoot && node.nodeId() == selectedRootId) {
            return 0L;
        }
        if (!includeRoot && parent == selectedRootId) {
            return 0L;
        }
        if (includedIds != null && includedIds.contains(parent)) {
            return parent;
        }
        return 0L;
    }

    static String exportNodeName(SceneSnapshot.NodeSnapshot node) {
        if (node == null) {
            return "Node";
        }
        String name = node.name() == null ? "" : node.name().trim();
        if (!name.isEmpty()) {
            return name;
        }
        String type = node.type() == null || node.type().isBlank() ? "Node" : node.type();
        return type + "_" + node.nodeId();
    }

    static Map<String, String> exportNodeProps(SceneSnapshot.NodeSnapshot node) {
        if (node == null || node.properties() == null || node.properties().isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> props = new LinkedHashMap<>();
        for (SceneSnapshot.Property prop : node.properties()) {
            if (prop == null || prop.key() == null || prop.key().isBlank() || prop.value() == null) {
                continue;
            }
            if ("@type".equals(prop.key())) {
                continue;
            }
            props.put(prop.key(), prop.value());
        }
        return Map.copyOf(props);
    }

    boolean uploadSceneFile(String sceneId, byte[] bytes) {
        AssetsClient assets = runtime.assets();
        Session session = runtime.session();
        if (assets == null || session == null) {
            saveBranchError = "Not connected";
            return false;
        }

        ResPath path;
        try {
            path = new ResPath("res://scenes/" + sceneId + ".moud.scene");
        } catch (Exception e) {
            saveBranchError = "Invalid path: " + e.getMessage();
            return false;
        }

        assets.addListener(new AssetsClient.Listener() {
            @Override
            public void onUploadAck(AssetUploadAck ack) {
                if (ack == null || !path.equals(ack.path())) {
                    return;
                }
                if (ack.status() == AssetTransferStatus.OK || ack.status() == AssetTransferStatus.ALREADY_PRESENT) {
                    assets.requestManifest(session);
                }
                assets.removeListener(this);
            }
        });

        assets.upload(session, path, bytes, AssetType.BINARY);
        return true;
    }

    static String normalizeSceneId(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    static boolean isValidSceneId(String sceneId) {
        if (sceneId == null) {
            return false;
        }
        String id = sceneId.trim();
        if (id.isEmpty() || id.length() > 64) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == '_' || c == '-') {
                continue;
            }
            if (c >= 'a' && c <= 'z') {
                continue;
            }
            if (c >= '0' && c <= '9') {
                continue;
            }
            return false;
        }
        return true;
    }
}
