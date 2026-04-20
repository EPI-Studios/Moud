package com.moud.client.launcher;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

final class UriSchemeRegistrar {
    private static final String SCHEME = "moud";
    private static final String LINUX_HANDLER_NAME = "moud-uri-handler";
    private static final String LINUX_DESKTOP_FILE = LINUX_HANDLER_NAME + ".desktop";
    private static final String MAC_APP_NAME = "Moud URI Handler.app";
    private static final String MAC_EXECUTABLE_NAME = "moud-uri-handler";
    private static final String WINDOWS_REG_KEY = "HKCU\\Software\\Classes\\" + SCHEME;
    private static final String WINDOWS_ICON = "\"%SystemRoot%\\System32\\shell32.dll\",1";

    private UriSchemeRegistrar() {
    }

    static Result ensureRegistered() {
        Platform platform = Platform.current();
        if (platform == Platform.OTHER) {
            return new Result(Status.SKIPPED, "unsupported platform");
        }

        Optional<Path> launcherJar = currentLauncherJar();
        if (launcherJar.isEmpty()) {
            return new Result(Status.SKIPPED, "launcher is not running from a jar");
        }

        Path javaCommand = resolveJavaCommand(platform);
        Path homeDir = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        try {
            return switch (platform) {
                case LINUX -> ensureLinux(homeDir, launcherJar.get(), javaCommand);
                case MACOS -> ensureMacos(homeDir, launcherJar.get(), javaCommand);
                case WINDOWS -> ensureWindows(launcherJar.get(), javaCommand);
                case OTHER -> new Result(Status.SKIPPED, "unsupported platform");
            };
        } catch (Exception e) {
            return new Result(Status.FAILED, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    static String linuxWrapperContent(Path javaCommand, Path launcherJar) {
        return """
                #!/usr/bin/env bash
                exec %s -jar %s --uri "$1"
                """.formatted(shellQuote(javaCommand.toString()), shellQuote(launcherJar.toString()));
    }

    static String linuxDesktopEntryContent(Path wrapperPath) {
        return """
                [Desktop Entry]
                Type=Application
                Name=Moud URI Handler
                Exec=%s %%u
                Terminal=false
                NoDisplay=true
                MimeType=x-scheme-handler/moud;
                Categories=Game;
                """.formatted(desktopQuote(wrapperPath.toString()));
    }

    static String macExecutableContent(Path javaCommand, Path launcherJar) {
        return """
                #!/usr/bin/env bash
                exec %s -jar %s --uri "$1"
                """.formatted(shellQuote(javaCommand.toString()), shellQuote(launcherJar.toString()));
    }

    static String macInfoPlistContent() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                  <key>CFBundleDevelopmentRegion</key>
                  <string>en</string>
                  <key>CFBundleExecutable</key>
                  <string>moud-uri-handler</string>
                  <key>CFBundleIdentifier</key>
                  <string>com.moud.urihandler</string>
                  <key>CFBundleName</key>
                  <string>Moud URI Handler</string>
                  <key>CFBundlePackageType</key>
                  <string>APPL</string>
                  <key>CFBundleShortVersionString</key>
                  <string>1.0</string>
                  <key>CFBundleVersion</key>
                  <string>1</string>
                  <key>LSMinimumSystemVersion</key>
                  <string>10.13</string>
                  <key>CFBundleURLTypes</key>
                  <array>
                    <dict>
                      <key>CFBundleURLName</key>
                      <string>com.moud.uri</string>
                      <key>CFBundleURLSchemes</key>
                      <array>
                        <string>moud</string>
                      </array>
                    </dict>
                  </array>
                </dict>
                </plist>
                """;
    }

    static String windowsCommandValue(Path javaCommand, Path launcherJar) {
        return "\"%s\" -jar \"%s\" --uri \"%%1\"".formatted(javaCommand, launcherJar);
    }

    private static Result ensureLinux(Path homeDir, Path launcherJar, Path javaCommand) throws IOException, InterruptedException {
        Path wrapper = homeDir.resolve(".local").resolve("bin").resolve(LINUX_HANDLER_NAME);
        Path desktopFile = homeDir.resolve(".local").resolve("share").resolve("applications").resolve(LINUX_DESKTOP_FILE);

        boolean changed = false;
        changed |= writeIfChanged(wrapper, linuxWrapperContent(javaCommand, launcherJar), true);
        changed |= writeIfChanged(desktopFile, linuxDesktopEntryContent(wrapper), false);

        boolean mimeChanged = false;
        if (commandAvailable("xdg-mime")) {
            String current = capture("xdg-mime", "query", "default", "x-scheme-handler/" + SCHEME).trim();
            if (!LINUX_DESKTOP_FILE.equalsIgnoreCase(current)) {
                runChecked("xdg-mime", "default", LINUX_DESKTOP_FILE, "x-scheme-handler/" + SCHEME);
                mimeChanged = true;
            }
        }

        if (changed || mimeChanged) {
            return new Result(Status.REGISTERED, "registered moud:// for Linux");
        }
        return new Result(Status.ALREADY_REGISTERED, "moud:// already registered");
    }

    private static Result ensureMacos(Path homeDir, Path launcherJar, Path javaCommand) throws IOException, InterruptedException {
        Path appDir = homeDir.resolve("Applications").resolve(MAC_APP_NAME);
        Path contentsDir = appDir.resolve("Contents");
        Path macosDir = contentsDir.resolve("MacOS");
        Path plist = contentsDir.resolve("Info.plist");
        Path executable = macosDir.resolve(MAC_EXECUTABLE_NAME);

        boolean changed = false;
        changed |= writeIfChanged(plist, macInfoPlistContent(), false);
        changed |= writeIfChanged(executable, macExecutableContent(javaCommand, launcherJar), true);

        Path lsregister = Path.of("/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister");
        if (Files.isExecutable(lsregister)) {
            runChecked(lsregister.toString(), "-f", appDir.toString());
        }

        if (changed) {
            return new Result(Status.REGISTERED, "registered moud:// for macOS");
        }
        return new Result(Status.ALREADY_REGISTERED, "moud:// already registered");
    }

    private static Result ensureWindows(Path launcherJar, Path javaCommand) throws IOException, InterruptedException {
        String desiredCommand = windowsCommandValue(javaCommand, launcherJar);
        String currentCommand = queryWindowsDefaultValue(WINDOWS_REG_KEY + "\\shell\\open\\command");
        if (desiredCommand.equals(currentCommand)) {
            return new Result(Status.ALREADY_REGISTERED, "moud:// already registered");
        }

        runChecked("reg", "add", WINDOWS_REG_KEY, "/ve", "/d", "URL:Moud Protocol", "/f");
        runChecked("reg", "add", WINDOWS_REG_KEY, "/v", "URL Protocol", "/d", "", "/f");
        runChecked("reg", "add", WINDOWS_REG_KEY + "\\DefaultIcon", "/ve", "/d", WINDOWS_ICON, "/f");
        runChecked("reg", "add", WINDOWS_REG_KEY + "\\shell\\open\\command", "/ve", "/d", desiredCommand, "/f");
        return new Result(Status.REGISTERED, "registered moud:// for Windows");
    }

    private static Optional<Path> currentLauncherJar() {
        try {
            URI location = ClientLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path path = Path.of(location).toAbsolutePath().normalize();
            if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar")) {
                return Optional.of(path);
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private static Path resolveJavaCommand(Platform platform) {
        String currentCommand = ProcessHandle.current().info().command().orElse(null);
        if (currentCommand != null && !currentCommand.isBlank()) {
            Path currentPath = Path.of(currentCommand).toAbsolutePath().normalize();
            if (platform == Platform.WINDOWS) {
                Path javaw = currentPath.resolveSibling("javaw.exe");
                if (Files.isExecutable(javaw)) {
                    return javaw;
                }
            }
            return currentPath;
        }

        Path javaHome = Path.of(System.getProperty("java.home")).toAbsolutePath().normalize();
        if (platform == Platform.WINDOWS) {
            Path javaw = javaHome.resolve("bin").resolve("javaw.exe");
            if (Files.isExecutable(javaw)) {
                return javaw;
            }
            Path java = javaHome.resolve("bin").resolve("java.exe");
            if (Files.exists(java)) {
                return java;
            }
            return Path.of("javaw.exe");
        }

        Path java = javaHome.resolve("bin").resolve("java");
        if (Files.exists(java)) {
            return java;
        }
        return Path.of("java");
    }

    private static boolean writeIfChanged(Path path, String content, boolean executable) throws IOException {
        Files.createDirectories(path.getParent());
        String normalized = content.endsWith("\n") ? content : content + "\n";
        if (Files.isRegularFile(path)) {
            String existing = Files.readString(path, StandardCharsets.UTF_8);
            if (existing.equals(normalized)) {
                if (executable) {
                    path.toFile().setExecutable(true, false);
                }
                return false;
            }
        }
        Files.writeString(path, normalized, StandardCharsets.UTF_8);
        if (executable) {
            path.toFile().setExecutable(true, false);
        }
        return true;
    }

    private static boolean commandAvailable(String command) {
        try {
            Process process = new ProcessBuilder(command, "--help")
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().readAllBytes();
            process.waitFor();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String capture(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        return exit == 0 ? output : "";
    }

    private static void runChecked(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IOException(output.isEmpty() ? "command failed: " + String.join(" ", command) : output);
        }
    }

    private static String queryWindowsDefaultValue(String key) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("reg", "query", key, "/ve")
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        if (exit != 0) {
            return null;
        }
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("(Default)")) {
                continue;
            }
            String[] parts = trimmed.split("\\s{2,}");
            if (parts.length >= 3) {
                return parts[2].trim();
            }
        }
        return null;
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static String desktopQuote(String value) {
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    enum Platform {
        WINDOWS,
        MACOS,
        LINUX,
        OTHER;

        static Platform current() {
            String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (osName.contains("win")) {
                return WINDOWS;
            }
            if (osName.contains("mac")) {
                return MACOS;
            }
            if (osName.contains("linux")) {
                return LINUX;
            }
            return OTHER;
        }
    }

    enum Status {
        REGISTERED,
        ALREADY_REGISTERED,
        SKIPPED,
        FAILED
    }

    record Result(Status status, String message) {
    }
}
