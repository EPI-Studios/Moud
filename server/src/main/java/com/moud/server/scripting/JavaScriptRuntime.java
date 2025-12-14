package com.moud.server.scripting;

import com.moud.server.MoudEngine;
import com.moud.server.logging.MoudLogger;
import com.moud.server.profiler.ProfilerService;
import com.moud.server.profiler.model.ScriptExecutionMetadata;
import com.moud.server.profiler.model.ScriptExecutionType;
import com.moud.server.profiler.script.ScriptProfiler;
import com.moud.server.typescript.TypeScriptTranspiler;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class JavaScriptRuntime {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(JavaScriptRuntime.class);
    private static final long CALLBACK_TIMEOUT_MS = Long.getLong("moud.script.timeout", 30000);

    private Context jsContext;
    private final ExecutorService executor;
    private final ScheduledExecutorService timeoutExecutor;
    private final MoudEngine engine;
    private volatile boolean isShuttingDown = false;
    private final Map<Long, ScheduledFuture<?>> intervals = new ConcurrentHashMap<>();
    private final AtomicLong intervalIdCounter = new AtomicLong(0);

    public JavaScriptRuntime(MoudEngine engine) {
        this.engine = engine;
        this.executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "JavaScriptRuntime-Main"));
        this.timeoutExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "JavaScriptRuntime-Timeout");
            t.setDaemon(true);
            return t;
        });
        initializeContext();
    }

    private void initializeContext() {
        HostAccess hostAccess = HostAccess.newBuilder()
                .allowAccessAnnotatedBy(HostAccess.Export.class)
                .allowAllImplementations(true)
                .allowAllClassImplementations(true)
                .allowArrayAccess(true)
                .allowListAccess(true)
                .allowMapAccess(true)
                .build();

        this.jsContext = Context.newBuilder("js")
                .allowHostAccess(hostAccess)
                .allowIO(true)
                .option("engine.WarnInterpreterOnly", "false")
                .build();

        bindTimerFunctions();
    }

    private void bindTimerFunctions() {
        jsContext.enter();
        try {
            jsContext.getBindings("js").putMember("setTimeout", new ProxyExecutable() {
                @Override
                public Object execute(Value... arguments) {
                    if (arguments.length < 2) return -1;
                    Value callback = arguments[0];
                    long delay = arguments[1].asLong();
                    ScriptExecutionMetadata metadata = ScriptExecutionMetadata.of(
                            ScriptExecutionType.TIMEOUT,
                            "setTimeout",
                            "delay=" + delay
                    );

                    CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS, executor)
                            .execute(() -> executeCallbackSafe(callback, metadata));
                    return null;
                }
            });

            jsContext.getBindings("js").putMember("setInterval", new ProxyExecutable() {
                @Override
                public Object execute(Value... arguments) {
                    if (arguments.length < 2) return -1;
                    Value callback = arguments[0];
                    long delay = arguments[1].asLong();
                    long intervalId = intervalIdCounter.incrementAndGet();
                    ScriptExecutionMetadata metadata = ScriptExecutionMetadata.of(
                            ScriptExecutionType.INTERVAL,
                            "setInterval",
                            "id=" + intervalId
                    );

                    ScheduledFuture<?> future = timeoutExecutor.scheduleAtFixedRate(() -> {
                        if (!isShuttingDown && jsContext != null) {
                            try {
                                executeCallbackSafe(callback, metadata);
                            } catch (IllegalStateException e) {
                                // cotext was closed during execute
                                intervals.remove(intervalId);
                            }
                        }
                    }, delay, delay, TimeUnit.MILLISECONDS);

                    intervals.put(intervalId, future);
                    return intervalId;
                }
            });

            jsContext.getBindings("js").putMember("clearInterval", new ProxyExecutable() {
                @Override
                public Object execute(Value... arguments) {
                    if (arguments.length < 1) return null;
                    long intervalId = arguments[0].asLong();
                    ScheduledFuture<?> future = intervals.remove(intervalId);
                    if (future != null) {
                        future.cancel(false);
                    }
                    return null;
                }
            });
        } finally {
            jsContext.leave();
        }
    }

    public void bindAPIs(Object... apis) {
        CompletableFuture.runAsync(() -> {
            jsContext.enter();
            try {
                Value bindings = jsContext.getBindings("js");
                Value moudObj = jsContext.eval("js", "({})");

                for (Object api : apis) {
                    if (api instanceof com.moud.server.api.ScriptingAPI) {
                        com.moud.server.api.ScriptingAPI scriptingAPI = (com.moud.server.api.ScriptingAPI) api;

                        moudObj.putMember("on", new ProxyExecutable() {
                            @Override
                            public Object execute(Value... arguments) {
                                if (arguments.length >= 2) {
                                    scriptingAPI.on(arguments[0].asString(), arguments[1]);
                                }
                                return null;
                            }
                        });

                        moudObj.putMember("once", new ProxyExecutable() {
                            @Override
                            public Object execute(Value... arguments) {
                                if (arguments.length >= 2) {
                                    scriptingAPI.once(arguments[0].asString(), arguments[1]);
                                }
                                return null;
                            }
                        });

                        moudObj.putMember("off", new ProxyExecutable() {
                            @Override
                            public Object execute(Value... arguments) {
                                if (arguments.length >= 2) {
                                    scriptingAPI.off(arguments[0].asString(), arguments[1]);
                                }
                                return null;
                            }
                        });

                        moudObj.putMember("server", scriptingAPI.server);
                        moudObj.putMember("world", scriptingAPI.world);
                        moudObj.putMember("lighting", scriptingAPI.lighting);
                    moudObj.putMember("zones", scriptingAPI.zones);
                    moudObj.putMember("math", scriptingAPI.math);
                    moudObj.putMember("commands", scriptingAPI.commands);
                    moudObj.putMember("scene", scriptingAPI.scene);
                    moudObj.putMember("async", scriptingAPI.getAsync());
                    moudObj.putMember("particles", scriptingAPI.particles);
                    moudObj.putMember("primitives", scriptingAPI.primitives);
                    moudObj.putMember("ik", scriptingAPI.ik);

                } else if (api instanceof com.moud.server.proxy.AssetProxy) {
                    moudObj.putMember("assets", api);
                } else if (api instanceof com.moud.server.ConsoleAPI) {
                    bindings.putMember("console", api);
                    } else if (api instanceof com.moud.server.api.CameraAPI) {
                        moudObj.putMember("camera", api);
                    }
                }

                bindings.putMember("Moud", moudObj);
                bindings.putMember("api", moudObj);

            } finally {
                jsContext.leave();
            }
        }, executor);
    }

    public CompletableFuture<Void> executeScript(Path scriptPath) {
        return CompletableFuture.runAsync(() -> {
            if (isShuttingDown) {
                return;
            }

            try {
                String scriptContent;
                String fileName = scriptPath.getFileName().toString();

                if (fileName.endsWith(".ts")) {
                    scriptContent = TypeScriptTranspiler.transpile(scriptPath).get();
                } else {
                    scriptContent = Files.readString(scriptPath);
                }

                evaluateSource(scriptContent, fileName);
            } catch (PolyglotException e) {
                if (e.isGuestException() && e.getSourceLocation() != null) {
                    LOGGER.scriptError("Execution failed in {} at line {}, column {}",
                            scriptPath.getFileName(),
                            e.getSourceLocation().getStartLine(),
                            e.getSourceLocation().getStartColumn());
                    LOGGER.error("└─> {}: {}", e.getMessage().split(":")[0], e.getMessage().substring(e.getMessage().indexOf(":") + 1).trim());
                } else {
                    LOGGER.error("Host error during script execution", e);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to execute script: {}", scriptPath.getFileName(), e);
            }
        }, executor);
    }

    public CompletableFuture<Void> executeSource(String scriptContent, String virtualFileName) {
        return CompletableFuture.runAsync(() -> evaluateSource(scriptContent, virtualFileName), executor);
    }

    private void evaluateSource(String scriptContent, String virtualFileName) {
        if (isShuttingDown) {
            return;
        }

        try {
            jsContext.enter();
            try {
                Source source = Source.newBuilder("js", scriptContent, virtualFileName).buildLiteral();
                jsContext.eval(source);
            } finally {
                jsContext.leave();
            }
        } catch (PolyglotException e) {
            if (e.isGuestException() && e.getSourceLocation() != null) {
                LOGGER.scriptError("Execution failed in {} at line {}, column {}",
                        e.getSourceLocation().getSource().getName(),
                        e.getSourceLocation().getStartLine(),
                        e.getSourceLocation().getStartColumn());
                LOGGER.error("└─> {}", e.getMessage());
            } else {
                LOGGER.error("Host error during script execution", e);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to evaluate script source {}", virtualFileName, e);
        }
    }

    public void executeCallback(Value callback, Object... args) {
        executeCallback(callback, ScriptExecutionMetadata.unnamed(ScriptExecutionType.OTHER), args);
    }

    public void executeCallback(Value callback, ScriptExecutionMetadata metadata, Object... args) {
        if (isShuttingDown) {
            return;
        }
        executor.submit(() -> runCallback(callback, metadata, args));
    }

    private void executeCallbackSafe(Value callback) {
        executeCallbackSafe(callback, ScriptExecutionMetadata.unnamed(ScriptExecutionType.TIMEOUT));
    }

    private void executeCallbackSafe(Value callback, ScriptExecutionMetadata metadata, Object... args) {
        if (isShuttingDown) return;

        Future<?> callbackTask = executor.submit(() -> runCallback(callback, metadata, args));

        timeoutExecutor.schedule(() -> {
            if (!callbackTask.isDone()) {
                callbackTask.cancel(true);
                LOGGER.error("Callback execution timed out after {}ms [{}]", CALLBACK_TIMEOUT_MS, metadata.label());
            }
        }, CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void runCallback(Value callback, ScriptExecutionMetadata metadata, Object... args) {
        if (callback == null || !callback.canExecute()) {
            return;
        }

        ScriptProfiler.ActiveSpan span = ProfilerService.getInstance()
                .scriptProfiler()
                .open(callback, metadata);
        long start = System.nanoTime();

        boolean success = false;
        String errorMessage = null;
        boolean entered = false;

        try {
            jsContext.enter();
            entered = true;
            callback.execute(args);
            success = true;
        } catch (PolyglotException e) {
            errorMessage = e.getMessage();
            handlePolyglotException(e);
        } catch (CancellationException e) {
            errorMessage = "Cancelled";
        } catch (RuntimeException e) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                errorMessage = "Interrupted";
            } else {
                errorMessage = e.getMessage();
                LOGGER.error("Unexpected error in callback execution", e);
            }
        } catch (Exception e) {
            errorMessage = e.getMessage();
            LOGGER.error("Unexpected error in callback execution", e);
        } finally {
            if (entered) {
                try {
                    jsContext.leave();
                } catch (Exception ignored) {
                }
            }
            ProfilerService.getInstance().scriptProfiler()
                    .close(span, System.nanoTime() - start, success, errorMessage);
        }
    }

    private void handlePolyglotException(PolyglotException e) {
        if (e.isGuestException() && e.getSourceLocation() != null) {
            LOGGER.scriptError("Error in callback execution at {} (line {}, column {}): {}",
                    e.getSourceLocation().getSource().getName(),
                    e.getSourceLocation().getStartLine(),
                    e.getSourceLocation().getStartColumn(),
                    e.getMessage());
        } else {
            LOGGER.error("Error executing callback: {}", e.getMessage());
        }
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public void shutdown() {
        isShuttingDown = true;

        LOGGER.debug("Cancelling {} active intervals before context shutdown", intervals.size());
        intervals.values().forEach(future -> future.cancel(true)); // Use true for immediate interrupt
        intervals.clear();

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        executor.execute(() -> {
            if (jsContext != null) {
                try {
                    jsContext.close(true);
                    LOGGER.debug("JavaScript context closed successfully");
                } catch (Exception e) {
                    LOGGER.error("Error closing JavaScript context", e);
                }
            }
        });

        executor.shutdown();
        timeoutExecutor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
            if (!timeoutExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                timeoutExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            timeoutExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
