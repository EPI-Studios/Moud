package com.moud.server.scripting;

import com.moud.server.api.exception.APIException;
import com.moud.server.logging.MoudLogger;
import com.moud.server.typescript.TypeScriptTranspiler;
import org.graalvm.polyglot.*;
import org.graalvm.polyglot.io.IOAccess;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class JavaScriptRuntime {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(JavaScriptRuntime.class);
    private static final String LANGUAGE_ID = "js";
    private static final long SCRIPT_TIMEOUT_SECONDS = 30;
    private static final int MAX_HEAP_SIZE_MB = 256;

    private final Context context;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Instant lastExecutionTime;

    public JavaScriptRuntime() {
        LOGGER.info("Initializing JavaScript runtime with GraalVM");

        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "JavaScriptRuntime");
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler(this::handleUncaughtException);
            return thread;
        });

        try {
            this.context = createSecureContext();
            this.running.set(true);
            LOGGER.success("JavaScript runtime initialized successfully");

        } catch (Exception e) {
            LOGGER.critical("Failed to initialize JavaScript runtime", e);
            throw new APIException("RUNTIME_INIT_FAILED", "Failed to initialize JavaScript runtime", e);
        }
    }

    private Context createSecureContext() {
        return Context.newBuilder(LANGUAGE_ID)
                .allowHostAccess(HostAccess.newBuilder()
                        .allowPublicAccess(true)
                        .allowAllImplementations(true)
                        .allowArrayAccess(true)
                        .allowListAccess(true)
                        .allowMapAccess(true)
                        .allowAccessAnnotatedBy(HostAccess.Export.class)
                        .denyAccess(Class.class)
                        .denyAccess(ClassLoader.class)
                        .build())
                .allowIO(IOAccess.NONE)
                .allowNativeAccess(false)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .allowPolyglotAccess(PolyglotAccess.NONE)
                .allowExperimentalOptions(true)
                .option("engine.WarnInterpreterOnly", "false")
                .option("js.ecmascript-version", "2022")
                .option("js.strict", "true")
                .resourceLimits(ResourceLimits.newBuilder()
                        .statementLimit(1000000, null)
                        .build())
                .build();
    }

    public void bindGlobal(String name, Object value) {
        if (!running.get()) {
            throw new APIException("RUNTIME_NOT_RUNNING", "Runtime is not running");
        }

        try {
            if (name == null || name.trim().isEmpty()) {
                throw new APIException("INVALID_GLOBAL_NAME", "Global name cannot be null or empty");
            }

            if (value == null) {
                LOGGER.warn("Binding null value to global: {}", name);
            }

            context.getBindings(LANGUAGE_ID).putMember(name, value);
            LOGGER.debug("Global bound successfully: {} -> {}", name,
                    value != null ? value.getClass().getSimpleName() : "null");

        } catch (Exception e) {
            LOGGER.error("Failed to bind global '{}': {}", name, e.getMessage(), e);
            throw new APIException("GLOBAL_BINDING_FAILED", "Failed to bind global: " + name, e);
        }
    }

    public CompletableFuture<Value> executeScript(Path scriptPath) {
        if (!running.get()) {
            return CompletableFuture.failedFuture(
                    new APIException("RUNTIME_NOT_RUNNING", "Runtime is not running"));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                validateScriptPath(scriptPath);

                String content = loadScriptContent(scriptPath);
                Source source = createSource(content, scriptPath);

                LOGGER.info("Executing script: {}", scriptPath.getFileName());
                Instant startTime = Instant.now();

                Value result = executeWithTimeout(source);

                Duration executionTime = Duration.between(startTime, Instant.now());
                lastExecutionTime = Instant.now();

                LOGGER.success("Script executed successfully in {}ms: {}",
                        executionTime.toMillis(), scriptPath.getFileName());

                return result;

            } catch (APIException e) {
                throw e;
            } catch (PolyglotException e) {
                handlePolyglotException(scriptPath, e);
                throw new APIException("SCRIPT_EXECUTION_FAILED", "Script execution failed", e);
            } catch (Exception e) {
                LOGGER.error("Unexpected error executing script: {}", scriptPath, e);
                throw new APIException("SCRIPT_EXECUTION_ERROR", "Unexpected script execution error", e);
            }
        }, executor);
    }

    private void validateScriptPath(Path scriptPath) {
        if (scriptPath == null) {
            throw new APIException("INVALID_SCRIPT_PATH", "Script path cannot be null");
        }

        if (!Files.exists(scriptPath)) {
            throw new APIException("SCRIPT_NOT_FOUND", "Script file not found: " + scriptPath);
        }

        if (!Files.isRegularFile(scriptPath)) {
            throw new APIException("INVALID_SCRIPT_FILE", "Not a regular file: " + scriptPath);
        }
    }

    private String loadScriptContent(Path scriptPath) throws Exception {
        try {
            if (scriptPath.toString().endsWith(".ts")) {
                LOGGER.debug("Transpiling TypeScript file: {}", scriptPath.getFileName());
                return TypeScriptTranspiler.transpile(scriptPath).get();
            } else {
                return Files.readString(scriptPath);
            }
        } catch (Exception e) {
            throw new APIException("SCRIPT_LOAD_FAILED", "Failed to load script content", e);
        }
    }

    private Source createSource(String content, Path scriptPath) {
        try {
            return Source.newBuilder(LANGUAGE_ID, content, scriptPath.getFileName().toString())
                    .mimeType("application/javascript")
                    .interactive(false)
                    .internal(false)
                    .build();
        } catch (Exception e) {
            throw new APIException("SOURCE_CREATION_FAILED", "Failed to create script source", e);
        }
    }

    private Value executeWithTimeout(Source source) {
        try {

            return context.eval(source);
        } catch (PolyglotException e) {
            if (e.isCancelled()) {
                throw new APIException("SCRIPT_TIMEOUT", "Script execution timed out");
            }
            throw e;
        }
    }

    private void handlePolyglotException(Path scriptPath, PolyglotException e) {
        String errorType = "Unknown";
        String errorMessage = e.getMessage();
        String location = "unknown";

        if (e.isGuestException()) {
            errorType = "Script Error";
        } else if (e.isHostException()) {
            errorType = "Host Error";
        } else if (e.isInternalError()) {
            errorType = "Internal Error";
        }

        if (e.getSourceLocation() != null) {
            location = String.format("line %d, column %d",
                    e.getSourceLocation().getStartLine(),
                    e.getSourceLocation().getStartColumn());
        }

        LOGGER.scriptError("Script execution failed in {} at {}: [{}] {}",
                scriptPath.getFileName(), location, errorType, errorMessage);
    }

    private void handleUncaughtException(Thread thread, Throwable throwable) {
        LOGGER.critical("Uncaught exception in JavaScript runtime thread: {}",
                throwable.getMessage(), throwable);
    }

    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            LOGGER.debug("Runtime already shutdown");
            return;
        }

        LOGGER.info("Shutting down JavaScript runtime");

        try {

            if (context != null) {
                context.close(true);
                LOGGER.debug("Polyglot context closed");
            }
        } catch (Exception e) {
            LOGGER.error("Error closing polyglot context", e);
        }

        try {

            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warn("Executor did not terminate gracefully, forcing shutdown");
                executor.shutdownNow();
            }
            LOGGER.debug("Executor shutdown completed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Shutdown interrupted");
            executor.shutdownNow();
        } catch (Exception e) {
            LOGGER.error("Error shutting down executor", e);
        }

        LOGGER.success("JavaScript runtime shutdown completed");
    }

    public boolean isRunning() {
        return running.get();
    }

    public Instant getLastExecutionTime() {
        return lastExecutionTime;
    }

    public boolean isHealthy() {
        return running.get();
    }

    public RuntimeStats getStats() {
        return new RuntimeStats(
                running.get(),
                lastExecutionTime,
                context != null
        );
    }

    public static class RuntimeStats {
        private final boolean running;
        private final Instant lastExecution;
        private final boolean contextActive;

        public RuntimeStats(boolean running, Instant lastExecution, boolean contextActive) {
            this.running = running;
            this.lastExecution = lastExecution;
            this.contextActive = contextActive;
        }

        public boolean isRunning() { return running; }
        public Instant getLastExecution() { return lastExecution; }
        public boolean isContextActive() { return contextActive; }

        @Override
        public String toString() {
            return String.format("RuntimeStats{running=%s, contextActive=%s, lastExecution=%s}",
                    running, contextActive, lastExecution);
        }
    }
}