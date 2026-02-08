package com.moud.server.profiler.ui;

import com.moud.server.profiler.ProfilerService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.concurrent.atomic.AtomicBoolean;

public class ProfilerApp extends Application {
    private static final AtomicBoolean LAUNCHED = new AtomicBoolean(false);
    private static ProfilerApp INSTANCE;

    private Stage primaryStage;
    private ProfilerDashboard dashboard;

    @Override
    public void start(Stage stage) {
        INSTANCE = this;
        LAUNCHED.set(true);

        this.primaryStage = stage;
        this.dashboard = new ProfilerDashboard(ProfilerService.getInstance(), stage);

        Scene scene = new Scene(dashboard, 1400, 860);
        stage.setTitle("Moud Profiler");
        stage.setScene(scene);
        stage.setMinWidth(1200);
        stage.setMinHeight(700);
        stage.show();

        stage.setOnCloseRequest(event -> {
            ProfilerService.getInstance().removeListener(dashboard);
        });
    }

    public static boolean isLaunched() {
        return LAUNCHED.get();
    }

    public static void bringToFront() {
        ProfilerApp app = INSTANCE;
        if (app != null && app.primaryStage != null) {
            Platform.runLater(() -> {
                if (!app.primaryStage.isShowing()) {
                    app.primaryStage.show();
                }
                app.primaryStage.toFront();
            });
        }
    }
}

