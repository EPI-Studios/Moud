package com.moud.bootstrap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class LauncherAgentWriter {

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    enum WriteResult {
        MODIFIED,
        UNCHANGED,
        NO_TARGET
    }

    WriteResult write(LauncherProfileLocator.LauncherProfile profile, Path agentJar) throws IOException {
        if (profile == null || profile.type() == LauncherProfileLocator.LauncherType.UNKNOWN || profile.file() == null) {
            return WriteResult.NO_TARGET;
        }

        return switch (profile.type()) {
            case VANILLA, CURSEFORGE -> writeVanillaFormat(profile.file(), profile.gameDir(), agentJar);
            case PRISM, MULTIMC -> writePrismFormat(profile.file(), agentJar);
            case MODRINTH_THESEUS -> writeTheseusFormat(profile.file(), agentJar);
            case UNKNOWN -> WriteResult.NO_TARGET;
        };
    }

    WriteResult writeVanillaFormat(Path file, Path gameDir, Path agentJar) throws IOException {
        JsonObject root = parseJsonObject(file);
        JsonObject profiles = root.has("profiles") && root.get("profiles").isJsonObject()
                ? root.getAsJsonObject("profiles")
                : new JsonObject();
        root.add("profiles", profiles);

        String selectedProfile = stringValue(root.get("selectedProfile"));
        List<Map.Entry<String, JsonElement>> targetProfiles = selectVanillaTargets(profiles, selectedProfile, gameDir);
        if (targetProfiles.isEmpty()) {
            return WriteResult.NO_TARGET;
        }

        boolean modified = false;
        String agentArg = agentArg(agentJar);

        for (Map.Entry<String, JsonElement> entry : targetProfiles) {
            if (!(entry.getValue() instanceof JsonObject profile)) {
                continue;
            }
            String current = stringValue(profile.get("javaArgs"));
            String updated = appendArgIfMissing(current, agentArg, agentJar);
            if (!updated.equals(current)) {
                profile.addProperty("javaArgs", updated);
                modified = true;
            }
        }

        if (modified) {
            writeJson(file, root);
        }
        return modified ? WriteResult.MODIFIED : WriteResult.UNCHANGED;
    }

    WriteResult writePrismFormat(Path file, Path agentJar) throws IOException {
        List<String> lines = Files.exists(file)
                ? Files.readAllLines(file, StandardCharsets.UTF_8)
                : new ArrayList<>();

        IniSectionEditor editor = new IniSectionEditor(lines);
        boolean changed = false;
        changed |= editor.upsert("General", "OverrideJavaArgs", "true");
        String currentArgs = unquote(editor.value("General", "JvmArgs"));
        String updatedArgs = appendArgIfMissing(currentArgs, agentArg(agentJar), agentJar);
        changed |= editor.upsert("General", "JvmArgs", quotePrismArgs(updatedArgs));

        if (changed) {
            Files.write(file, editor.lines(), StandardCharsets.UTF_8);
        }
        return changed ? WriteResult.MODIFIED : WriteResult.UNCHANGED;
    }

    WriteResult writeTheseusFormat(Path file, Path agentJar) throws IOException {
        JsonObject root = parseJsonObject(file);
        String current = stringValue(root.get("java_args"));
        String updated = appendArgIfMissing(current, agentArg(agentJar), agentJar);
        if (updated.equals(current)) {
            return WriteResult.UNCHANGED;
        }
        root.addProperty("java_args", updated);
        writeJson(file, root);
        return WriteResult.MODIFIED;
    }

    private static JsonObject parseJsonObject(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return new JsonObject();
        }
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        JsonElement element = JsonParser.parseString(raw);
        if (element == null || !element.isJsonObject()) {
            return new JsonObject();
        }
        return element.getAsJsonObject();
    }

    private static void writeJson(Path file, JsonObject root) throws IOException {
        Files.writeString(file, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
    }

    private static List<Map.Entry<String, JsonElement>> selectVanillaTargets(JsonObject profiles, String selectedProfile, Path gameDir) {
        ArrayList<Map.Entry<String, JsonElement>> targets = new ArrayList<>();

        if (selectedProfile != null && !selectedProfile.isBlank() && profiles.has(selectedProfile)) {
            targets.add(Map.entry(selectedProfile, profiles.get(selectedProfile)));
        }

        String normalizedGameDir = gameDir.toAbsolutePath().normalize().toString();
        for (Map.Entry<String, JsonElement> entry : profiles.entrySet()) {
            if (!(entry.getValue() instanceof JsonObject profile)) {
                continue;
            }
            String profileGameDir = stringValue(profile.get("gameDir"));
            if (!profileGameDir.isBlank()) {
                try {
                    if (Path.of(profileGameDir).toAbsolutePath().normalize().toString().equals(normalizedGameDir)
                            && targets.stream().noneMatch(existing -> existing.getKey().equals(entry.getKey()))) {
                        targets.add(entry);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (targets.isEmpty() && profiles.entrySet().size() == 1) {
            targets.add(profiles.entrySet().iterator().next());
        }

        return List.copyOf(targets);
    }

    private static String appendArgIfMissing(String currentArgs, String agentArg, Path agentJar) {
        String current = currentArgs == null ? "" : currentArgs.trim();
        String jarPath = agentJar.toAbsolutePath().normalize().toString();
        if (!current.isBlank() && (current.contains(agentArg) || current.contains(jarPath))) {
            return current;
        }
        return current.isBlank() ? agentArg : current + " " + agentArg;
    }

    private static String agentArg(Path agentJar) {
        String path = agentJar.toAbsolutePath().normalize().toString();
        if (path.contains(" ")) {
            return "-javaagent:\"" + path + "\"";
        }
        return "-javaagent:" + path;
    }

    private static String quotePrismArgs(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.isEmpty()) {
            normalized = normalized + " ";
        }
        return "\"" + normalized.replace("\"", "\\\"") + "\"";
    }

    private static String unquote(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized.replace("\\\"", "\"").trim();
    }

    private static String stringValue(JsonElement element) {
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }
}
