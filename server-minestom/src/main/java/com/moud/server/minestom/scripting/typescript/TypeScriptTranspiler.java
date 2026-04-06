package com.moud.server.minestom.scripting.typescript;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class TypeScriptTranspiler {

    private static final String COMPILER_RESOURCE = "/scripting/typescript.js";

    private final Context ctx;
    private final Value transpileFn;

    public TypeScriptTranspiler(Engine graalEngine) {
        this.ctx = Context.newBuilder("js")
                .engine(graalEngine)
                .allowHostAccess(HostAccess.newBuilder(HostAccess.EXPLICIT).allowArrayAccess(true).build())
                .allowHostClassLookup(ignored -> false)
                .build();

        String compilerSource = loadCompiler();
        ctx.eval("js", compilerSource);

        this.transpileFn = ctx.eval("js",
                "(function(src) {" +
                "  return ts.transpileModule(src, {" +
                "    compilerOptions: {" +
                "      target: ts.ScriptTarget.ES2022," +
                "      module: ts.ModuleKind.CommonJS," +
                "      experimentalDecorators: true" +
                "    }" +
                "  }).outputText;" +
                "})");
    }

    public String transpile(String tsSource) {
        try {
            return transpileFn.execute(tsSource).asString();
        } catch (PolyglotException e) {
            throw new TranspileException("TypeScript transpilation failed: " + e.getMessage(), e);
        }
    }

    public void close() {
        try {
            ctx.close(true);
        } catch (Exception ignored) {
        }
    }

    private static String loadCompiler() {
        try (InputStream in = TypeScriptTranspiler.class.getResourceAsStream(COMPILER_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                        "typescript.js not found on classpath at " + COMPILER_RESOURCE +
                        ". Run './gradlew downloadTypeScriptCompiler' to fetch it.");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load typescript.js", e);
        }
    }

    public static final class TranspileException extends RuntimeException {
        public TranspileException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
