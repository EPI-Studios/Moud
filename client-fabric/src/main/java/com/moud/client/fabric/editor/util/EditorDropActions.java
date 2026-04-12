package com.moud.client.fabric.editor.util;

import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.editor.state.EditorHistory;
import com.moud.core.assets.AssetType;
import com.moud.core.assets.ResPath;
import com.moud.core.scene.Model3D;
import com.moud.net.session.Session;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.MinecraftClient;

public final class EditorDropActions {
    private EditorDropActions() {
    }

    public static void afterImport(EditorRuntime runtime, File file, ResPath dest, AssetType type) {
        if (runtime == null || file == null || dest == null) {
            return;
        }
        String filename = file.getName();
        if (filename == null || filename.isBlank()) {
            return;
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        if (type == AssetType.MODEL && lower.endsWith(".bbmodel")) {
            importModelAsStaticBody(runtime, filename, dest.value());
        }
    }

    private static void importModelAsStaticBody(EditorRuntime runtime, String filename, String modelPath) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return;
        }
        mc.execute(() -> {
            EditorState state = runtime.state();
            Session session = runtime.session();
            if (state == null || session == null || runtime.net() == null) {
                return;
            }

            long parentId = state.selectedId > 0L ? state.selectedId : 0L;
            String name = filename == null ? "StaticBody3D" : filename;
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                name = name.substring(0, dot);
            }
            if (name.isBlank()) {
                name = "StaticBody3D";
            }

            EditorHistory.DuplicateSubtreeEntry.CloneSpec visual = new EditorHistory.DuplicateSubtreeEntry.CloneSpec(
                    "Model",
                    "Model3D",
                    List.of(Map.entry(Model3D.PROP_MODEL_PATH, modelPath)),
                    List.of()
            );
            EditorHistory.DuplicateSubtreeEntry.CloneSpec root = new EditorHistory.DuplicateSubtreeEntry.CloneSpec(
                    name,
                    "StaticBody3D",
                    List.of(Map.entry("shape", "auto")),
                    List.of(visual)
            );
            EditorHistory.DuplicateSubtreeEntry entry = new EditorHistory.DuplicateSubtreeEntry(
                    parentId,
                    name,
                    root,
                    true
            );
            runtime.history().pushEntry(entry);
            entry.redo(runtime);
        });
    }
}
