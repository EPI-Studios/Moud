package com.meekdev.moud.script.vm;

import com.meekdev.moud.script.err.ScriptError;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

// engine luau lives in files under resources, never as a string in a java class
public final class Luau {

    private Luau() {}

    public static String source(String name) {
        String path = "/moud/" + name;
        try (InputStream in = Luau.class.getResourceAsStream(path)) {
            if (in == null) throw new ScriptError(name, "not on the classpath at " + path, null);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ScriptError(name, e.getMessage(), e);
        }
    }
}
