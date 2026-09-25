package com.meekdev.moud.mod.client.editor.files;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.place.Output;
import com.meekdev.moud.mod.place.PlaceToml;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class CodeEditor {

    private static final String SYSTEM = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final boolean MAC = SYSTEM.contains("mac");
    private static final boolean WINDOWS = SYSTEM.contains("win");
    private static final String PROGRAMS = Path.of(System.getProperty("user.home"), "AppData", "Local", "Programs").toString();
    private static final List<String> ZED = MAC
            ? List.of("zed", "/usr/local/bin/zed", "/opt/homebrew/bin/zed", "/Applications/Zed.app/Contents/MacOS/cli")
            : WINDOWS
            ? List.of("zed", Path.of(PROGRAMS, "Zed", "bin", "zed.exe").toString(), Path.of(PROGRAMS, "Zed", "Zed.exe").toString())
            : List.of("zed", System.getProperty("user.home") + "/.local/bin/zed", "zeditor");
    private static final List<String> CODE = MAC
            ? List.of("code", "/usr/local/bin/code", "/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code")
            : WINDOWS
            ? List.of("code.cmd", Path.of(PROGRAMS, "Microsoft VS Code", "bin", "code.cmd").toString())
            : List.of("code");

    private CodeEditor() {}

    public static void open(Path file, int line) {
        String root = PlaceToml.root().toAbsolutePath().toString();
        String target = file.toAbsolutePath() + ":" + Math.max(1, line);
        for (String zed : ZED) {
            if (start(List.of(zed, root, target))) return;
        }
        for (String code : CODE) {
            if (start(List.of(code, root, "--goto", target))) return;
        }
        if (MAC && start(List.of("open", "-t", file.toAbsolutePath().toString()))) return;
        if (WINDOWS && start(List.of("notepad.exe", file.toAbsolutePath().toString()))) return;
        MoudMod.LOG.warn("no code editor found to open {}", target);
        Output.add(Output.Level.WARN, "editor", "No code editor found to open " + target);
    }

    private static boolean start(List<String> command) {
        try {
            new ProcessBuilder(command).start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
