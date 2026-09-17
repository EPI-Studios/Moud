package com.meekdev.moud.mod.client.editor.document;

import com.meekdev.moud.mod.MoudMod;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public record ScriptTemplate(String label, String name, String code) {

    private static final String ROOT = "/assets/moud/editor/scripts/";

    public static final List<ScriptTemplate> SERVER = List.of(
            load("Empty", "Script", "script.luau"),
            load("On touched", "Touched", "touched.luau"),
            load("Spin", "Spin", "spin.luau"),
            load("Kill on touch", "KillOnTouch", "kill-on-touch.luau"));

    public static final List<ScriptTemplate> CLIENT = List.of(
            load("Empty", "LocalScript", "local-script.luau"));

    public static final ScriptTemplate MODULE = load("Module script", "Module", "module.luau");

    private static ScriptTemplate load(String label, String name, String file) {
        try (InputStream in = ScriptTemplate.class.getResourceAsStream(ROOT + file)) {
            if (in == null) throw new IOException(ROOT + file + " is missing");
            return new ScriptTemplate(label, name, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            MoudMod.LOG.warn("script template {} could not be read", file, e);
            return new ScriptTemplate(label, name, "");
        }
    }
}
