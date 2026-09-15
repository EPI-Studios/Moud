package com.meekdev.moud.mod.client.editor.files;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.place.Output;
import com.meekdev.moud.mod.place.PlaceToml;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class CodeEditor {

    private static final List<String> ZED = List.of("zed", System.getProperty("user.home") + "/.local/bin/zed", "zeditor");

    private CodeEditor() {}

    public static void open(Path file, int line) {
        String root = PlaceToml.root().toAbsolutePath().toString();
        String target = file.toAbsolutePath() + ":" + Math.max(1, line);
        for (String zed : ZED) {
            if (start(List.of(zed, root, target))) return;
        }
        if (start(List.of("code", root, "--goto", target))) return;
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
