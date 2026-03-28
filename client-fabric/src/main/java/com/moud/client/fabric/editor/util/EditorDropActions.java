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
            spawnNodeWithProperty(runtime, filename, "Model3D", Model3D.PROP_MODEL_PATH, dest.value());
        }
    }

    private static void spawnNodeWithProperty(EditorRuntime runtime,
                                              String filename,
                                              String nodeTypeId,
                                              String propertyKey,
                                              String propertyValue) {
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
            String name = filename == null ? nodeTypeId : filename;
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                name = name.substring(0, dot);
            }
            if (name.isBlank()) {
                name = nodeTypeId;
            }

            EditorHistory.CreateNodeEntry entry = new EditorHistory.CreateNodeEntry(
                    parentId,
                    name,
                    nodeTypeId,
                    List.of(Map.entry(propertyKey, propertyValue)),
                    true
            );
            runtime.history().pushEntry(entry);
            entry.redo(runtime);
        });
    }
}
