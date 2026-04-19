package com.moud.client.fabric.editor.settings;

import com.moud.client.fabric.util.ClientDebugLog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ExternalEditorLauncher {
    private ExternalEditorLauncher() {
    }

    public static boolean launch(String commandTemplate, String absolutePath, int line) {
        if (commandTemplate == null || commandTemplate.isBlank() || absolutePath == null) return false;
        String expanded = commandTemplate
                .replace("{path}", absolutePath)
                .replace("{line}", Integer.toString(Math.max(1, line)));
        List<String> argv = tokenize(expanded);
        if (argv.isEmpty()) return false;
        try {
            new ProcessBuilder(argv).inheritIO().start();
            return true;
        } catch (IOException e) {
            ClientDebugLog.error("ExternalEditorLauncher", "failed to launch: " + e.getMessage(), e);
            return false;
        }
    }

    private static List<String> tokenize(String command) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c == '"' && !inSingle) { inDouble = !inDouble; continue; }
            if (c == '\'' && !inDouble) { inSingle = !inSingle; continue; }
            if (Character.isWhitespace(c) && !inSingle && !inDouble) {
                if (!cur.isEmpty()) { out.add(cur.toString()); cur.setLength(0); }
                continue;
            }
            cur.append(c);
        }
        if (!cur.isEmpty()) out.add(cur.toString());
        return out;
    }
}
