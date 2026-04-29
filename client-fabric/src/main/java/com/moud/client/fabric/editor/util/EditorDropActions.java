package com.moud.client.fabric.editor.util;

import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.editor.state.EditorHistory;
import com.moud.core.assets.AssetType;
import com.moud.core.assets.ResPath;
import com.moud.core.mesh.obj.MtlMaterial;
import com.moud.core.mesh.obj.MtlParser;
import com.moud.core.mesh.obj.ObjModel;
import com.moud.core.mesh.obj.ObjParser;
import com.moud.core.scene.Model3D;
import com.moud.net.session.Session;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
            importModelAsStaticBody(runtime, filename, dest.value(), null);
        } else if (type == AssetType.MODEL && lower.endsWith(".obj")) {
            String texPath = firstDiffuseTexture(file);
            importModelAsStaticBody(runtime, filename, dest.value(), texPath);
        }
    }

    private static String firstDiffuseTexture(File objFile) {
        if (objFile == null) return null;
        File dir = objFile.getParentFile();
        if (dir == null) return null;
        try {
            ObjModel model = ObjParser.parse(new String(Files.readAllBytes(objFile.toPath()), StandardCharsets.UTF_8));
            String mtlLib = model.mtlLibName();
            if (mtlLib == null || mtlLib.isBlank()) return null;
            File mtlFile = new File(dir, mtlLib);
            if (!mtlFile.isFile()) return null;
            Map<String, MtlMaterial> materials = MtlParser.parse(new String(Files.readAllBytes(mtlFile.toPath()), StandardCharsets.UTF_8));
            for (MtlMaterial m : materials.values()) {
                String tex = m.diffuseTexture();
                if (tex != null && !tex.isBlank()) {
                    String filename = tex;
                    int slash = filename.lastIndexOf('/');
                    if (slash >= 0) filename = filename.substring(slash + 1);
                    int back = filename.lastIndexOf('\\');
                    if (back >= 0) filename = filename.substring(back + 1);
                    return "res://textures/" + filename;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void importModelAsStaticBody(EditorRuntime runtime, String filename, String modelPath, String texturePath) {
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

            List<Map.Entry<String, String>> modelProps = texturePath != null && !texturePath.isBlank()
                    ? List.of(Map.entry(Model3D.PROP_MODEL_PATH, modelPath), Map.entry("texture", texturePath))
                    : List.of(Map.entry(Model3D.PROP_MODEL_PATH, modelPath));
            EditorHistory.DuplicateSubtreeEntry.CloneSpec visual = new EditorHistory.DuplicateSubtreeEntry.CloneSpec(
                    "Model",
                    "Model3D",
                    modelProps,
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
