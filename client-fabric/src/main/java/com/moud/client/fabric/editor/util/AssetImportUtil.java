package com.moud.client.fabric.editor.util;

import com.moud.client.fabric.assets.AssetsClient;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.core.assets.AssetType;
import com.moud.core.assets.ResPath;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.nio.file.Files;
import java.util.Locale;

public final class AssetImportUtil {
    private AssetImportUtil() {
    }

    public static void importSceneFile(EditorRuntime runtime) {
        if (runtime == null) {
            return;
        }
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
            runtime.requestToast("Scene file not found", true, 4500);
            return;
        }
        if (!file.getName().endsWith(".moud.scene")) {
            runtime.requestToast("File must end with .moud.scene", true, 4500);
            return;
        }

        upload(runtime, file, inferTarget(file.getName(), true));
    }

    public static void importAssetFile(EditorRuntime runtime) {
        if (runtime == null) {
            return;
        }
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
            runtime.requestToast("Asset file not found", true, 4500);
            return;
        }

        upload(runtime, file, inferTarget(file.getName(), false));
    }

    private static void upload(EditorRuntime runtime, File file, ImportTarget target) {
        AssetsClient assets = runtime.assets();
        Session session = runtime.session();
        if (assets == null || session == null || session.state() != SessionState.CONNECTED) {
            runtime.requestToast("Import failed: not connected", true, 4500);
            return;
        }
        if (file == null || target == null) {
            runtime.requestToast("Import failed: invalid file", true, 4500);
            return;
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file.toPath());
        } catch (Exception e) {
            String msg = e.getMessage();
            runtime.requestToast("Import failed" + (msg == null || msg.isBlank() ? "" : ": " + msg), true, 6000);
            return;
        }

        String filename = file.getName();
        if (filename == null || filename.isBlank()) {
            runtime.requestToast("Invalid filename", true, 4500);
            return;
        }

        ResPath dest = safeResPath(target.destDir, filename);
        if (dest == null) {
            runtime.requestToast("Import failed: invalid destination path", true, 6000);
            return;
        }

        try {
            assets.upload(session, dest, bytes, target.type);
            runtime.requestToast("Uploading: " + dest.value(), false, 2500);
        } catch (Exception e) {
            String msg = e.getMessage();
            runtime.requestToast("Import failed" + (msg == null || msg.isBlank() ? "" : ": " + msg), true, 6000);
        }
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
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")) {
            return new ImportTarget("res://textures/", AssetType.IMAGE);
        }
        if (lower.endsWith(".ogg") || lower.endsWith(".wav") || lower.endsWith(".mp3")) {
            return new ImportTarget("res://audio/", AssetType.AUDIO);
        }
        if (lower.endsWith(".obj") || lower.endsWith(".gltf") || lower.endsWith(".glb")) {
            return new ImportTarget("res://models/", AssetType.MODEL);
        }
        if (lower.endsWith(".txt") || lower.endsWith(".json")) {
            return new ImportTarget("res://text/", AssetType.TEXT);
        }
        return new ImportTarget("res://imports/", AssetType.BINARY);
    }

    private record ImportTarget(String destDir, AssetType type) {
    }
}

