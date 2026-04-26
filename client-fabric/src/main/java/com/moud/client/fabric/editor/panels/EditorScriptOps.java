package com.moud.client.fabric.editor.panels;

import com.moud.client.fabric.editor.net.EditorNet;
import com.moud.client.fabric.editor.state.EditorHistory;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.editor.util.ScriptAssetTypes;
import com.moud.core.assets.ResPath;
import com.moud.core.scripts.ScriptFilenames;
import com.moud.core.scripts.ScriptSlot;
import com.moud.net.protocol.SceneOp;
import com.moud.net.session.Session;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

final class EditorScriptOps {
    private final EditorRuntime runtime;

    EditorScriptOps(EditorRuntime runtime) {
        this.runtime = runtime;
    }

    void newServerScriptFor(long nodeId) {
        var dialog = runtime.createAssetDialog();
        if (dialog == null) {
            runtime.requestToast("Create file dialog unavailable", true, 3000);
            return;
        }
        dialog.openScript("node_" + nodeId + ".server.luau", path -> useServerScript(nodeId, path, true));
    }

    void newClientScriptFor(long nodeId) {
        var dialog = runtime.createAssetDialog();
        if (dialog == null) {
            runtime.requestToast("Create file dialog unavailable", true, 3000);
            return;
        }
        dialog.openScript("node_" + nodeId + ".client.luau", path -> useClientScript(nodeId, path, true));
    }

    void importServerScriptFor(long nodeId) {
        importScript(nodeId, ScriptSlot.SCRIPT);
    }

    void importClientScriptFor(long nodeId) {
        importScript(nodeId, ScriptSlot.CLIENT_SCRIPT);
    }

    void useServerScript(long nodeId, String scriptPath, boolean openEditor) {
        useScript(nodeId, "script", scriptPath, openEditor);
    }

    void useClientScript(long nodeId, String scriptPath, boolean openEditor) {
        useScript(nodeId, "client_script", scriptPath, openEditor);
    }

    List<String> scriptAssets(boolean clientOnly) {
        EditorState state = runtime.state();
        ArrayList<String> paths = new ArrayList<>();
        if (state == null) {
            return paths;
        }
        for (var entry : state.manifestEntries) {
            if (entry == null || entry.path() == null) continue;
            String path = entry.path().value();
            if (!isScriptAssetPath(path)) continue;
            if (clientOnly && !ScriptFilenames.isLuau(path)) continue;
            paths.add(path);
        }
        paths.sort(String::compareToIgnoreCase);
        return paths;
    }

    private void importScript(long nodeId, ScriptSlot slot) {
        EditorState state = runtime.state();
        EditorNet net = runtime.net();
        Session session = runtime.session();
        if (state == null || net == null || session == null) {
            runtime.requestToast("Attach failed: not connected", true, 3500);
            return;
        }

        try {
            String selectedPath = TinyFileDialogs.tinyfd_openFileDialog(
                    slot == ScriptSlot.CLIENT_SCRIPT ? "Import Client Script (.luau)" : "Import Script " + ScriptAssetTypes.FILTER_LABEL,
                    "", null,
                    slot == ScriptSlot.CLIENT_SCRIPT ? "Luau Script (.luau)" : ScriptAssetTypes.FILTER_LABEL,
                    false);
            if (selectedPath == null || selectedPath.isBlank()) return;

            File file = new File(selectedPath);
            if (!file.exists() || !file.isFile()) {
                runtime.requestToast("Script file not found", true, 4500);
                return;
            }

            String filename = file.getName();
            if (filename == null || filename.isBlank()) {
                runtime.requestToast("Invalid filename", true, 4500);
                return;
            }

            if (slot == ScriptSlot.CLIENT_SCRIPT) {
                if (!ScriptFilenames.isLuau(filename)) {
                    runtime.requestToast("Client scripts must be Luau (.luau / .client.luau)", true, 5000);
                    return;
                }
                filename = ScriptFilenames.ensureSuffixFor(filename, ScriptSlot.CLIENT_SCRIPT);
            } else {
                filename = ScriptAssetTypes.ensureExtension(filename);
                ScriptAssetTypes.Kind kind = ScriptAssetTypes.kindOrDefault(filename);
                if (kind == ScriptAssetTypes.Kind.LUAU) {
                    filename = ScriptFilenames.ensureSuffixFor(filename, ScriptSlot.SCRIPT);
                }
            }

            String scriptPath = "res://scripts/" + filename;
            try {
                new ResPath(scriptPath);
            } catch (Exception ignored) {
                String ext = slot == ScriptSlot.CLIENT_SCRIPT ? ".client.luau" : ".server.luau";
                scriptPath = "res://scripts/node_" + nodeId + ext;
            }

            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            net.writeScriptFile(session, state, scriptPath, content);

            if (slot == ScriptSlot.CLIENT_SCRIPT) {
                useClientScript(nodeId, scriptPath, true);
            } else {
                useServerScript(nodeId, scriptPath, true);
            }
        } catch (Exception exception) {
            String message = exception.getMessage();
            runtime.requestToast("Attach failed" + (message == null || message.isBlank() ? "" : ": " + message), true, 6000);
        }
    }

    private void useScript(long nodeId, String property, String scriptPath, boolean openEditor) {
        if (scriptPath == null || scriptPath.isBlank()) {
            return;
        }
        sendOpsRecorded(List.of(new SceneOp.SetProperty(nodeId, property, scriptPath)));
        runtime.requestToast("Attached script: " + scriptPath, false, 2500);
        if (openEditor) {
            runtime.openScriptEditor("client_script".equals(property) ? 0L : nodeId, scriptPath);
        }
    }

    private void sendOpsRecorded(List<SceneOp> operations) {
        EditorState state = runtime.state();
        Session session = runtime.session();
        EditorNet net = runtime.net();
        if (state == null || session == null || net == null) return;

        List<SceneOp> inverseOperations = EditorHistory.buildInverseOps(state.scene, operations);
        net.sendOps(session, state, operations);
        if (!inverseOperations.isEmpty()) {
            runtime.history().push(inverseOperations, operations);
        }
    }

    private static boolean isScriptAssetPath(String path) {
        return path != null && path.startsWith("res://scripts/") && ScriptAssetTypes.isScriptPath(path);
    }
}
