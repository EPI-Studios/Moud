package com.meekdev.moud.script.luau;

import com.meekdev.moud.core.scene.Json;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class LuauProject {

    private static final String LUAURC = LuauResource.text("project/luaurc.json");
    private static final List<String> OUTDATED_LUAURCS = List.of(
            LuauResource.text("project/luaurc-without-plugins.json"),
            LuauResource.text("project/luaurc-with-plugins.json"));
    private static final String ZED_SETTINGS = LuauResource.text("project/zed-settings.json");
    private static final String VSCODE_SETTINGS = LuauResource.text("project/vscode-settings.json");
    private static final String VSCODE_EXTENSIONS = LuauResource.text("project/vscode-extensions.json");
    private static final String LAUNCHER = LuauResource.text("project/luau-lsp.sh");
    private static final String WINDOWS_LAUNCHER = LuauResource.text("project/luau-lsp.cmd");
    private static final boolean WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final byte[] EXE = WINDOWS ? LuauResource.bytesOrNull("project/luau-lsp-"
            + (System.getProperty("os.arch").toLowerCase().contains("aarch64") ? "arm64" : "x64") + ".exe") : null;
    private static final String MARK = ZED_SETTINGS.substring(0, ZED_SETTINGS.indexOf('\n'));
    private static final String DEFINITIONS = "luau-lsp.types.definitionFiles";
    private static final String RECOMMENDATIONS = "recommendations";

    private LuauProject() {}

    static void write(Path place, Path types) throws IOException {
        Path luaurc = place.resolve(".luaurc");
        if (!Files.exists(luaurc) || OUTDATED_LUAURCS.contains(Files.readString(luaurc))) Files.writeString(luaurc, LUAURC);
        Path server = server(place.resolve(".moud"));
        Path zed = place.resolve(".zed").resolve("settings.json");
        owned(zed, zed(types.toAbsolutePath(), server));
        Path vscode = place.resolve(".vscode");
        String code = VSCODE_SETTINGS.formatted(server.toString().endsWith(".cmd") ? ""
                : "\n    \"luau-lsp.server.path\": \".moud/" + server.getFileName() + "\",");
        if (!owned(vscode.resolve("settings.json"), code)) merge(vscode.resolve("settings.json"), code);
        if (!owned(vscode.resolve("extensions.json"), VSCODE_EXTENSIONS)) merge(vscode.resolve("extensions.json"), VSCODE_EXTENSIONS);
    }

    private static String zed(Path types, Path server) {
        String definitions = Json.write(types.toString()).strip();
        return ZED_SETTINGS.formatted(definitions, Json.write(server.toString()).strip());
    }

    private static Path server(Path directory) throws IOException {
        Path jar = directory.resolve("luau-lsp.jar").toAbsolutePath();
        replace(jar, jar());
        String java = java();
        String main = LuauLsp.class.getName();
        if (EXE != null) {
            List<String> config = List.of(java, "-Xmx128m", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-cp", jar.toString(), main);
            writeIfChanged(directory.resolve("luau-lsp.cfg"), String.join("\r\n", config) + "\r\n");
            Path exe = directory.resolve("luau-lsp.exe").toAbsolutePath();
            replace(exe, EXE);
            return exe;
        }
        if (WINDOWS) {
            Path batch = directory.resolve("luau-lsp.cmd").toAbsolutePath();
            String windows = "\"" + java + "\"";
            writeIfChanged(batch, WINDOWS_LAUNCHER.formatted(windows, "\"" + jar + "\"", main).replace("\r\n", "\n").replace("\n", "\r\n"));
            return batch;
        }
        Path shell = directory.resolve("luau-lsp").toAbsolutePath();
        writeIfChanged(shell, LAUNCHER.formatted(quoted(java), quoted(jar.toString()), main));
        try {
            Files.setPosixFilePermissions(shell, PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (UnsupportedOperationException ignored) {
        }
        return shell;
    }

    private static void replace(Path file, byte[] bytes) throws IOException {
        Path aside = file.resolveSibling(file.getFileName() + ".old");
        try {
            Files.deleteIfExists(aside);
        } catch (IOException ignored) {
        }
        if (Files.isRegularFile(file) && Arrays.equals(Files.readAllBytes(file), bytes)) return;
        try {
            Files.write(file, bytes);
        } catch (IOException e) {
            try {
                Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
                Files.write(file, bytes);
            } catch (IOException again) {
                if (!Files.isRegularFile(file)) throw again;
            }
        }
    }

    private static byte[] jar() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Class<?> type : LuauLsp.class.getNestMembers()) {
                String name = type.getName().replace('.', '/') + ".class";
                try (InputStream in = LuauLsp.class.getResourceAsStream("/" + name)) {
                    if (in == null) throw new IOException(name + " is missing");
                    ZipEntry entry = new ZipEntry(name);
                    entry.setTime(0);
                    zip.putNextEntry(entry);
                    in.transferTo(zip);
                    zip.closeEntry();
                }
            }
        }
        return bytes.toByteArray();
    }

    private static String java() {
        String command = ProcessHandle.current().info().command()
                .orElse(Path.of(System.getProperty("java.home"), "bin", WINDOWS ? "java.exe" : "java").toString());
        Path console = Path.of(command).resolveSibling("java.exe");
        if (command.toLowerCase().endsWith("javaw.exe") && Files.isRegularFile(console)) return console.toString();
        return command;
    }

    private static String quoted(String text) {
        return "'" + text.replace("'", "'\\''") + "'";
    }

    private static void writeIfChanged(Path file, String text) throws IOException {
        if (Files.isRegularFile(file) && Files.readString(file).equals(text)) return;
        Files.writeString(file, text);
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
