package com.meekdev.moud.script.luau;

import com.meekdev.moud.core.scene.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class LuauProject {

    private static final String LUAURC = LuauResource.text("project/luaurc.json");
    private static final List<String> OUTDATED_LUAURCS = List.of(
            LuauResource.text("project/luaurc-without-plugins.json"),
            LuauResource.text("project/luaurc-with-plugins.json"));
    private static final String ZED_SETTINGS = LuauResource.text("project/zed-settings.json");
    private static final String VSCODE_SETTINGS = LuauResource.text("project/vscode-settings.json");
    private static final String VSCODE_EXTENSIONS = LuauResource.text("project/vscode-extensions.json");
    private static final String MARK = ZED_SETTINGS.substring(0, ZED_SETTINGS.indexOf('\n'));
    private static final String DEFINITIONS = "luau-lsp.types.definitionFiles";
    private static final String RECOMMENDATIONS = "recommendations";

    private LuauProject() {}

    static void write(Path place, Path types) throws IOException {
        Path luaurc = place.resolve(".luaurc");
        if (!Files.exists(luaurc) || OUTDATED_LUAURCS.contains(Files.readString(luaurc))) Files.writeString(luaurc, LUAURC);
        Path zed = place.resolve(".zed").resolve("settings.json");
        owned(zed, zed(types.toAbsolutePath()));
        Path vscode = place.resolve(".vscode");
        if (!owned(vscode.resolve("settings.json"), VSCODE_SETTINGS)) merge(vscode.resolve("settings.json"), VSCODE_SETTINGS);
        if (!owned(vscode.resolve("extensions.json"), VSCODE_EXTENSIONS)) merge(vscode.resolve("extensions.json"), VSCODE_EXTENSIONS);
    }

    private static String zed(Path types) {
        String definitions = Json.write(types.toString()).strip();
        return ZED_SETTINGS.formatted(definitions);
    }

    private static boolean owned(Path file, String ours) throws IOException {
        String text = Files.exists(file) ? Files.readString(file) : null;
        if (text != null && !text.startsWith(MARK)) return false;
        if (ours.equals(text)) return true;
        Files.createDirectories(file.getParent());
        Files.writeString(file, ours);
        return true;
    }

    private static void merge(Path file, String ours) throws IOException {
        String text = Files.readString(file);
        Object theirs;
        try {
            theirs = Json.parse(text);
        } catch (RuntimeException e) {
            return;
        }
        if (!(theirs instanceof Map<?, ?> map)) return;
        @SuppressWarnings("unchecked")
        Map<String, Object> settings = (Map<String, Object>) map;
        if (merge(settings, withoutMark(ours))) Files.writeString(file, Json.write(settings));
    }

    private static boolean merge(Map<String, Object> settings, Map<?, ?> ours) {
        boolean changed = false;
        for (Map.Entry<?, ?> entry : ours.entrySet()) {
            String key = (String) entry.getKey();
            Object existing = settings.get(key);
            if (existing == null) {
                settings.put(key, entry.getValue());
                changed = true;
            } else if (key.equals(DEFINITIONS) && existing instanceof Map<?, ?> files && entry.getValue() instanceof Map<?, ?> wanted) {
                @SuppressWarnings("unchecked")
                Map<String, Object> theirs = (Map<String, Object>) files;
                for (Map.Entry<?, ?> file : wanted.entrySet()) {
                    if (theirs.containsKey((String) file.getKey())) continue;
                    theirs.put((String) file.getKey(), file.getValue());
                    changed = true;
                }
            } else if (key.equals(RECOMMENDATIONS) && existing instanceof List<?> list && entry.getValue() instanceof List<?> wanted) {
                @SuppressWarnings("unchecked")
                List<Object> theirs = (List<Object>) list;
                for (Object id : wanted) {
                    if (theirs.contains(id)) continue;
                    theirs.add(id);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static Map<?, ?> withoutMark(String text) {
        String body = text.startsWith(MARK) ? text.substring(MARK.length()) : text;
        return (Map<?, ?>) Json.parse(body);
    }
}
