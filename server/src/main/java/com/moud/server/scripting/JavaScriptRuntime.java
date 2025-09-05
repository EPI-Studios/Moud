package com.moud.server.scripting;

import com.moud.server.typescript.TypeScriptTranspiler;
import org.graalvm.polyglot.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JavaScriptRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaScriptRuntime.class);
    private static final String LANGUAGE_ID = "js";

    private final Context context;
    private final ExecutorService executor;

    public JavaScriptRuntime() {
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "JavaScriptRuntime");
            thread.setDaemon(true);
            return thread;
        });

        this.context = Context.newBuilder(LANGUAGE_ID)
                .allowHostAccess(HostAccess.newBuilder()
                        .allowPublicAccess(true)
                        .allowAllImplementations(true)
                        .allowArrayAccess(true)
                        .allowListAccess(true)
                        .allowMapAccess(true)
                        .build())
                .allowIO(false)
                .allowNativeAccess(false)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .allowPolyglotAccess(PolyglotAccess.NONE)
                .option("engine.WarnInterpreterOnly", "false")
                .build();
    }

    public void bindGlobal(String name, Object value) {
        context.getBindings(LANGUAGE_ID).putMember(name, value);
    }

    public CompletableFuture<Value> executeScript(Path scriptPath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String content;

                if (scriptPath.toString().endsWith(".ts")) {
                    LOGGER.info("Transpiling TypeScript file: {}", scriptPath);
                    content = TypeScriptTranspiler.transpile(scriptPath).get();
                } else {
                    content = Files.readString(scriptPath);
                }

                Source source = Source.newBuilder(LANGUAGE_ID, content, scriptPath.getFileName().toString())
                        .build();
                return context.eval(source);
            } catch (Exception e) {
                LOGGER.error("Script execution failed for: {}", scriptPath, e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    public void shutdown() {
        try {
            context.close();
        } catch (Exception ignored) {}
        executor.shutdown();
    }
}