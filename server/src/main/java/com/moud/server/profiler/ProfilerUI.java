package com.moud.server.profiler;

import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.profiler.ui.ProfilerApp;
import javafx.application.Application;
import javafx.application.Platform;

import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ProfilerUI {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(
            ProfilerUI.class,
            LogContext.builder().put("subsystem", "profiler").put("component", "ui").build()
    );

    private static final AtomicBoolean LAUNCHING = new AtomicBoolean(false);

    private ProfilerUI() {
    }

    public static void launchAsync() {
        if (GraphicsEnvironment.isHeadless()) {
            LOGGER.warn("Cannot launch profiler UI: graphics environment is headless");
            return;
        }

        if (ProfilerApp.isLaunched()) {
            ProfilerApp.bringToFront();
            return;
        }

        if (LAUNCHING.compareAndSet(false, true)) {
            Thread launchThread = new Thread(() -> {
                try {
                    Application.launch(ProfilerApp.class);
                } catch (IllegalStateException alreadyRunning) {
                    Platform.runLater(ProfilerApp::bringToFront);
                } catch (Exception e) {
                    LOGGER.error("Failed to launch profiler UI", e);
                } finally {
                    LAUNCHING.set(false);
                }
            }, "MoudProfilerFX");
            launchThread.setDaemon(true);
            launchThread.start();
        } else {
            LOGGER.debug("Profiler UI launch already in progress");
        }
    }
}
