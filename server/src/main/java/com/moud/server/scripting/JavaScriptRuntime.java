package com.moud.server.scripting;

import com.moud.server.MoudEngine;
import com.moud.server.logging.MoudLogger;
import com.moud.server.typescript.TypeScriptTranspiler;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;

public class JavaScriptRuntime {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(JavaScriptRuntime.class);

    private Context jsContext;
    private final ExecutorService executor;
    private final ScheduledExecutorService timerExecutor;
    private final MoudEngine engine;
    private volatile boolean isShuttingDown = false;

    public JavaScriptRuntime(MoudEngine engine) {
        this.engine = engine;
        this.executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "JavaScriptRuntime-Main"));
        this.timerExecutor = Executors.newScheduledThreadPool(1, r -> new Thread(r, "JavaScript-Timer"));
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
        Value bindings = jsContext.getBindings("js");

        bindings.putMember("setTimeout", new ProxyExecutable() {
            @Override
            public Object execute(Value... arguments) {
                if (arguments.length < 2) return null;
                Value callback = arguments[0];
                long delay = arguments[1].asLong();
                timerExecutor.schedule(() -> executeCallback(callback), delay, TimeUnit.MILLISECONDS);
                return null;
            }
        });

        bindings.putMember("setInterval", new ProxyExecutable() {
            @Override
            public Object execute(Value... arguments) {
                if (arguments.length < 2) return null;
                Value callback = arguments[0];
                long period = arguments[1].asLong();
                if (period <= 0) return null;
                timerExecutor.scheduleAtFixedRate(() -> executeCallback(callback), period, period, TimeUnit.MILLISECONDS);
                return null;
            }
        });

        bindings.putMember("clearInterval", (ProxyExecutable) (arguments) -> null);
        bindings.putMember("clearTimeout", (ProxyExecutable) (arguments) -> null);
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

                        moudObj.putMember("server", scriptingAPI.server);
                        moudObj.putMember("lighting", scriptingAPI.lighting);
                        moudObj.putMember("zones", scriptingAPI.zones);
                        moudObj.putMember("math", scriptingAPI.math);
                        moudObj.putMember("worlds", scriptingAPI.worlds);
                        moudObj.putMember("commands", scriptingAPI.commands);
                        moudObj.putMember("async", scriptingAPI.getAsync());

                    } else if (api instanceof com.moud.server.proxy.AssetProxy) {
                        moudObj.putMember("assets", api);
                    } else if (api instanceof com.moud.server.ConsoleAPI) {
                        bindings.putMember("console", api);
                    }
                }

                bindings.putMember("Moud", moudObj);
                bindings.putMember("api", moudObj);

            } finally {
                jsContext.leave();
            }
        }, executor).join();
    }

    public CompletableFuture<Void> executeScript(Path scriptPath) {
        return CompletableFuture.runAsync(() -> {
            if (isShuttingDown) return;

            try {
                String scriptContent;
                String fileName = scriptPath.getFileName().toString();

                if (fileName.endsWith(".ts")) {
                    scriptContent = TypeScriptTranspiler.transpile(scriptPath).get();
                } else {
                    scriptContent = Files.readString(scriptPath);
                }

                jsContext.enter();
                try {
                    jsContext.eval("js", scriptContent);
                } finally {
                    jsContext.leave();
                }
            } catch (PolyglotException e) {
                if (e.isGuestException()) {
                    LOGGER.scriptError("Execution failed in {} at line {}, column {}",
                            scriptPath.getFileName(), e.getSourceLocation().getStartLine(), e.getSourceLocation().getStartColumn());
                    LOGGER.error("└─> {}: {}", e.getMessage().split(":")[0], e.getMessage().substring(e.getMessage().indexOf(":") + 1).trim());
                } else {
                    LOGGER.error("Host error during script execution", e);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to execute script: {}", scriptPath.getFileName(), e);
            }
        }, executor);
    }

    public void executeCallback(Value callback, Object... args) {
        if (isShuttingDown || callback == null || !callback.canExecute()) return;

        executor.execute(() -> {
            if (isShuttingDown) return;

            jsContext.enter();
            try {
                callback.execute(args);
            } catch (PolyglotException e) {
                if (e.isGuestException() && e.getSourceLocation() != null) {
                    LOGGER.scriptError("Error in callback execution at {} (line {}, column {}): {}",
                            e.getSourceLocation().getSource().getName(),
                            e.getSourceLocation().getStartLine(),
                            e.getSourceLocation().getStartColumn(),
                            e.getMessage());
                } else {
                    LOGGER.error("Error executing callback: {}", e.getMessage());
                }
            } catch (Exception e) {
                LOGGER.error("Unexpected error in callback execution", e);
            } finally {
                jsContext.leave();
            }
        });
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public void shutdown() {
        isShuttingDown = true;

        timerExecutor.shutdown();

        executor.execute(() -> {
            if (jsContext != null) {
                try {
                    jsContext.close(true);
                } catch (Exception e) {
                    LOGGER.error("Error closing JavaScript context", e);
                }
            }
        });

        executor.shutdown();
        try {
            if (!timerExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                timerExecutor.shutdownNow();
            }
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            timerExecutor.shutdownNow();
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}