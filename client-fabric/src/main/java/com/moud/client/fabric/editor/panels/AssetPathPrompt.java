package com.moud.client.fabric.editor.panels;

import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class AssetPathPrompt {
    private AssetPathPrompt() {
    }

    public static String promptName(String title, String defaultValue) {
        String input = TinyFileDialogs.tinyfd_inputBox(title, title, defaultValue == null ? "" : defaultValue);
        if (input == null) return null;
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.contains("..") || trimmed.startsWith("/") || trimmed.contains("\\")) return null;
        return trimmed;
    }

    public static boolean confirmDelete(String path, int sceneRefs) {
        String title = "Delete " + path;
        String msg = "Delete " + path + "?" + (sceneRefs > 0
                ? "\n" + sceneRefs + " scene reference(s) will be cleared."
                : "");
        boolean ok = TinyFileDialogs.tinyfd_messageBox(title, msg, "yesno", "warning", false);
        return ok;
    }

    public static String dirnameOf(String resPath) {
        if (resPath == null) return "";
        String stripped = resPath.startsWith("res://") ? resPath.substring("res://".length()) : resPath;
        int slash = stripped.lastIndexOf('/');
        return slash < 0 ? "" : stripped.substring(0, slash);
    }

    public static String joinRes(String parent, String name) {
        StringBuilder sb = new StringBuilder("res://");
        if (parent != null && !parent.isBlank()) sb.append(parent).append('/');
        sb.append(name);
        return sb.toString();
    }
}
