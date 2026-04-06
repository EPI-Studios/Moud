package com.moud.server.minestom.scripting.typescript;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TypeScriptPipeline {

    private static final Pattern DEFAULT_CLASS_PATTERN =
            Pattern.compile("export\\s+default\\s+class\\s+(\\w+)");

    private final TypeScriptTranspiler transpiler;
    private final MoudRuntimeShim shim;

    public TypeScriptPipeline(TypeScriptTranspiler transpiler, MoudRuntimeShim shim) {
        this.transpiler = transpiler;
        this.shim = shim;
    }

    public String prepareScript(Path scriptFile) {
        String rawSource;
        try {
            rawSource = Files.readString(scriptFile, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read script: " + scriptFile, e);
        }
        return process(rawSource);
    }

    String process(String rawSource) {
        String transpiledJs = transpiler.transpile(rawSource);
        return wrap(transpiledJs);
    }

    private String wrap(String transpiledJs) {
        return "(function() {\n" +
               "  'use strict';\n" +
               "  var exports = {};\n" +
               "  var module = { exports: exports };\n" +
               "  var require = function(mod) {\n" +
               "    if (mod === 'moud' || mod.startsWith('moud/')) {\n" +
               "      return globalThis.__MoudRuntimeExports;\n" +
               "    }\n" +
               "    throw new Error('Unknown module: ' + mod);\n" +
               "  };\n" +
               transpiledJs + "\n" +
               "  return exports['default'] || module.exports['default'];\n" +
               "})()";
    }
}
