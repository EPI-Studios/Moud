package com.moud.client.fabric.editor.util;


import com.moud.client.fabric.assets.AssetsClient;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.core.assets.AssetType;
import com.moud.core.assets.ResPath;
import com.moud.core.material.TresMaterialConverter;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

public final class AssetImportUtil {
    private AssetImportUtil() {
    }

    public static void importSceneFile(EditorRuntime runtime) {
        if (runtime == null) {
            return;
        }
        Thread.ofVirtual().start(() -> {
            String path = TinyFileDialogs.tinyfd_openFileDialog(
                    "Import Scene File",
                    "",
                    null,
                    "Scene Files",
                    false
            );
            if (path == null || path.isBlank()) {
                return;
            }
            File file = new File(path);
            if (!file.exists() || !file.isFile()) {
                toast(runtime, "Scene file not found", true, 4500);
                return;
            }
            if (!file.getName().endsWith(".moud.scene")) {
                toast(runtime, "File must end with .moud.scene", true, 4500);
                return;
            }

            upload(runtime, file, inferTarget(file.getName(), true));
        });
    }

    public static void importAssetFile(EditorRuntime runtime) {
        if (runtime == null) {
            return;
        }
        Thread.ofVirtual().start(() -> {
            String path = TinyFileDialogs.tinyfd_openFileDialog(
                    "Import Asset File",
                    "",
                    null,
                    "All Files",
                    false
            );
            if (path == null || path.isBlank()) {
                return;
            }
            File file = new File(path);
            if (!file.exists() || !file.isFile()) {
                toast(runtime, "Asset file not found", true, 4500);
                return;
            }

            upload(runtime, file, inferTarget(file.getName(), false));
        });
    }

    public static void importDroppedFile(EditorRuntime runtime, String path) {
        if (runtime == null) {
            return;
        }
        if (path == null || path.isBlank()) {
            return;
        }
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            toast(runtime, "File not found", true, 4500);
            return;
        }
        ImportTarget target = inferTarget(file.getName(), false);
        String filename = file.getName();
        if (filename == null || filename.isBlank()) {
            toast(runtime, "Invalid filename", true, 4500);
            return;
        }
        ResPath dest = safeResPath(target.destDir, filename);
        if (dest == null) {
            toast(runtime, "Import failed: invalid destination path", true, 6000);
            return;
        }
        upload(runtime, file, target, dest);
        EditorDropActions.afterImport(runtime, file, dest, target.type);
    }

    private static void upload(EditorRuntime runtime, File file, ImportTarget target) {
        if (runtime == null || file == null || target == null) {
            toast(runtime, "Import failed: invalid file", true, 4500);
            return;
        }
        String filename = file.getName();
        if (filename == null || filename.isBlank()) {
            toast(runtime, "Invalid filename", true, 4500);
            return;
        }
        ResPath dest = safeResPath(target.destDir, filename);
        if (dest == null) {
            toast(runtime, "Import failed: invalid destination path", true, 6000);
            return;
        }
        upload(runtime, file, target, dest);
    }

    private static void upload(EditorRuntime runtime, File file, ImportTarget target, ResPath dest) {
        AssetsClient assets = runtime.assets();
        Session session = runtime.session();
        if (assets == null || session == null || session.state() != SessionState.CONNECTED) {
            toast(runtime, "Import failed: not connected", true, 4500);
            return;
        }
        if (file == null || target == null) {
            toast(runtime, "Import failed: invalid file", true, 4500);
            return;
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file.toPath());
        } catch (Exception e) {
            String msg = e.getMessage();
            toast(runtime, "Import failed" + (msg == null || msg.isBlank() ? "" : ": " + msg), true, 6000);
            return;
        }

        String fileName = file.getName().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".tres")) {
            String baseName = file.getName();
            if (baseName.toLowerCase(Locale.ROOT).endsWith(".tres")) {
                baseName = baseName.substring(0, baseName.length() - 5);
            }
            String shaderResPath = "res://shaders/" + baseName + ".moudshader";

            String tresContent = new String(bytes, StandardCharsets.UTF_8);
            TresMaterialConverter.ConvertResult result = TresMaterialConverter.convert(tresContent, shaderResPath);
            if (result == null) {
                toast(runtime, "Not a supported material type", true, 4500);
                return;
            }
            bytes = result.moudmatJson().getBytes(StandardCharsets.UTF_8);
            String destStr = dest.value();
            if (destStr.endsWith(".tres")) {
                destStr = destStr.substring(0, destStr.length() - 5) + ".moudmat";
                dest = new ResPath(destStr);
            }

            String shaderContent = loadTemplate("pbr_shader.moudshader");
            byte[] shaderBytes = shaderContent.getBytes(StandardCharsets.UTF_8);
            ResPath shaderDest = new ResPath(shaderResPath);
            MinecraftClient shaderMc = MinecraftClient.getInstance();
            Runnable shaderUpload = () -> {
                try {
                    assets.upload(session, shaderDest, shaderBytes, AssetType.TEXT);
                } catch (Exception e) {
                    System.err.println("[Moud] Shader upload failed: " + e.getMessage());
                }
            };
            if (shaderMc != null && !shaderMc.isOnThread()) {
                shaderMc.execute(shaderUpload);
            } else {
                shaderUpload.run();
            }

            File tresDir = file.getParentFile();
            if (tresDir != null && !result.referencedTextures().isEmpty()) {
                int imported = 0;
                for (String texPath : result.referencedTextures()) {
                    String texFilename = texPath;
                    int slash = texFilename.lastIndexOf('/');
                    if (slash >= 0) texFilename = texFilename.substring(slash + 1);
                    File texFile = new File(tresDir, texFilename);
                    if (texFile.isFile()) {
                        ImportTarget texTarget = new ImportTarget("res://textures/", AssetType.IMAGE);
                        upload(runtime, texFile, texTarget);
                        imported++;
                    }
                }
                if (imported > 0) {
                    toast(runtime, "Auto-imported " + imported + " texture(s)", false, 2500);
                }
            }

            if (!result.warnings().isEmpty()) {
                toast(runtime, "Material converted with warnings", false, 3000);
            } else {
                toast(runtime, "MaterialMaker .tres converted to .moudmat", false, 2500);
            }
        }

        if (dest == null) {
            toast(runtime, "Import failed: invalid destination path", true, 6000);
            return;
        }

        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            ResPath finalDest = dest;
            if (mc != null && !mc.isOnThread()) {
                byte[] finalBytes = bytes;
                mc.execute(() -> assets.upload(session, finalDest, finalBytes, target.type));
            } else {
                assets.upload(session, finalDest, bytes, target.type);
            }
            toast(runtime, "Uploading: " + finalDest.value(), false, 2500);
        } catch (Exception e) {
            String msg = e.getMessage();
            toast(runtime, "Import failed" + (msg == null || msg.isBlank() ? "" : ": " + msg), true, 6000);
        }
    }

    private static void toast(EditorRuntime runtime, String message, boolean error, int durationMs) {
        if (runtime == null || message == null || message.isBlank()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && !mc.isOnThread()) {
            mc.execute(() -> runtime.requestToast(message, error, durationMs));
            return;
        }
        runtime.requestToast(message, error, durationMs);
    }

    private static ResPath safeResPath(String destDir, String filename) {
        String dir = (destDir == null || destDir.isBlank()) ? "res://imports/" : destDir.trim();
        if (!dir.startsWith(ResPath.SCHEME)) {
            dir = ResPath.SCHEME + dir;
        }
        if (!dir.endsWith("/")) {
            dir = dir + "/";
        }

        try {
            return new ResPath(dir + filename);
        } catch (Exception ignored) {
        }

        String ext = "";
        int dot = filename.lastIndexOf('.');
        if (dot >= 0 && dot < filename.length() - 1) {
            ext = filename.substring(dot);
        }
        String fallback = "import_" + System.currentTimeMillis() + ext;
        try {
            return new ResPath(dir + fallback);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ImportTarget inferTarget(String filename, boolean forceScene) {
        String name = filename == null ? "" : filename;
        String lower = name.toLowerCase(Locale.ROOT);
        if (forceScene || lower.endsWith(".moud.scene")) {
            return new ImportTarget("res://scenes/", AssetType.BINARY);
        }
        if (lower.endsWith(".moudshader") || lower.endsWith(".glsl")) {
            return new ImportTarget("res://shaders/", AssetType.TEXT);
        }
        if (lower.endsWith(".moudmat")) {
            return new ImportTarget("res://materials/", AssetType.TEXT);
        }
        if (lower.endsWith(".tres")) {
            return new ImportTarget("res://materials/", AssetType.TEXT);
        }
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")) {
            return new ImportTarget("res://textures/", AssetType.IMAGE);
        }
        if (lower.endsWith(".ogg") || lower.endsWith(".wav") || lower.endsWith(".mp3")) {
            return new ImportTarget("res://audio/", AssetType.AUDIO);
        }
        if (lower.endsWith(".bbmodel") || lower.endsWith(".obj") || lower.endsWith(".gltf") || lower.endsWith(".glb")) {
            return new ImportTarget("res://models/", AssetType.MODEL);
        }
        if (lower.endsWith(".txt") || lower.endsWith(".json")) {
            return new ImportTarget("res://text/", AssetType.TEXT);
        }
        return new ImportTarget("res://imports/", AssetType.BINARY);
    }

    private static String loadTemplate(String fileName) {
        String path = "/assets/moud/templates/" + fileName;
        try (var in = AssetImportUtil.class.getResourceAsStream(path)) {
            if (in == null) return "";
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private record ImportTarget(String destDir, AssetType type) {
    }
}
