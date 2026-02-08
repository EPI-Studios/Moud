package com.moud.server.profiler.ui;

import com.moud.server.profiler.ProfilerListener;
import com.moud.server.profiler.ProfilerService;
import com.moud.server.profiler.model.ProfilerFrame;
import com.moud.server.profiler.model.ProfilerSnapshot;
import com.moud.server.profiler.model.ScriptSample;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class ProfilerDashboard extends BorderPane implements ProfilerListener {
    private final ProfilerService service;
    private final List<ProfilerFrame> frameHistory = new ArrayList<>();

    private final VBox timelineContainer;
    private final FlameGraphView flameGraph;
    private final ScriptSourceView sourceView;
    private final VBox detailBox;
    private final NetworkInspectorView networkView;

    private final TimelineTrack cpuTrack;
    private final TimelineTrack memoryTrack;
    private final TimelineTrack networkTrack;

    private int hoverIndex = -1;
    private boolean paused = false;

    public ProfilerDashboard(ProfilerService service, Stage stage) {
        this.service = service;

        getStylesheets().add(getClass().getResource("/profiler-modern.css").toExternalForm());
        getStyleClass().add("root");

        setTop(createToolbar());

        SplitPane mainSplit = new SplitPane();
        mainSplit.setOrientation(Orientation.VERTICAL);
        mainSplit.setDividerPositions(0.40);

        timelineContainer = new VBox();
        timelineContainer.getStyleClass().add("timeline-container");

        cpuTrack = new TimelineTrack("CPU USAGE", Color.web("#f48771"), f -> f.processCpuLoad() * 100.0, val -> String.format("%.1f %%", val));
        cpuTrack.setMaxValue(100);
        memoryTrack = new TimelineTrack("HEAP MEM", Color.web("#4fc1ff"), f -> f.heapUsedBytes() / 1024.0 / 1024.0, val -> String.format("%.0f MB", val));
        networkTrack = new TimelineTrack("NETWORK I/O", Color.web("#6a9955"), f -> (double)(f.inboundBytes() + f.outboundBytes()), val -> humanBytes(val.longValue()) + "/t");

        timelineContainer.getChildren().addAll(cpuTrack, memoryTrack, networkTrack);

        timelineContainer.setOnMouseMoved(e -> {
            if (frameHistory.isEmpty()) return;
            double width = timelineContainer.getWidth() - 120;
            double x = e.getX() - 120;
            if (x < 0) x = 0;
            int count = frameHistory.size();
            int idx = (int)((x / width) * count);
            idx = Math.max(0, Math.min(idx, count - 1));
            setHoverIndex(idx);
        });
        timelineContainer.setOnMouseExited(e -> setHoverIndex(-1));

        TabPane bottomTabs = new TabPane();
        bottomTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        BorderPane scriptsPane = new BorderPane();
        flameGraph = new FlameGraphView();

        TabPane detailsPane = new TabPane();
        detailBox = new VBox(10);
        detailBox.setPadding(new Insets(10));
        detailBox.setStyle("-fx-background-color: #252526;");
        Label noSel = new Label("Select a function in the Flame Graph");
        noSel.setTextFill(Color.GRAY);
        detailBox.getChildren().add(noSel);

        sourceView = new ScriptSourceView();

        detailsPane.getTabs().addAll(
                new Tab("Details", new ScrollPane(detailBox) {{ setFitToWidth(true); }}),
                new Tab("Source Code", sourceView)
        );

        SplitPane scriptSplit = new SplitPane(flameGraph, detailsPane);
        scriptSplit.setOrientation(Orientation.HORIZONTAL);
        scriptSplit.setDividerPositions(0.7);

        scriptsPane.setCenter(scriptSplit);
        bottomTabs.getTabs().add(new Tab("Scripts & Performance", scriptsPane));

        networkView = new NetworkInspectorView();
        bottomTabs.getTabs().add(new Tab("Network Inspector", networkView));

        flameGraph.setOnSampleSelected(this::updateScriptDetails);

        mainSplit.getItems().addAll(timelineContainer, bottomTabs);
        setCenter(mainSplit);

        service.addListener(this);
    }

    private Node createToolbar() {
        ToolBar bar = new ToolBar();
        bar.setStyle("-fx-background-color: #333333; -fx-padding: 5;");
        Button btnPause = new Button("Pause");
        btnPause.getStyleClass().add("button");
        btnPause.setOnAction(e -> {
            paused = !paused;
            btnPause.setText(paused ? "Resume" : "Pause");
            btnPause.setStyle(paused ? "-fx-background-color: #ce9178; -fx-text-fill: white;" : "");
        });
        Label lblInfo = new Label(" Moud Profiler 2.0");
        lblInfo.setStyle("-fx-text-fill: #858585; -fx-font-size: 11px;");
        return bar;
    }

    private void updateScriptDetails(ScriptSample sample) {
        detailBox.getChildren().clear();
        if (sample == null) return;
        addDetail("Function", sample.functionName());
        addDetail("Script", sample.scriptName() + ":" + sample.line());
        addDetail("Duration", String.format("%.4f ms", sample.durationMillis()));
        addDetail("Type", sample.type().name());
        addDetail("Label", sample.label());
        addDetail("Details", sample.detail());
        if (!sample.success()) {
            Label err = new Label("Error: " + sample.errorMessage());
            err.setTextFill(Color.web("#f44336"));
            err.setWrapText(true);
            detailBox.getChildren().add(err);
        }
        sourceView.displaySource(sample.scriptName(), sample.line());
    }

    private void addDetail(String key, String value) {
        if (value == null || value.isBlank()) return;
        HBox row = new HBox(10);
        Label k = new Label(key); k.setMinWidth(80); k.setTextFill(Color.GRAY);
        Label v = new Label(value); v.setTextFill(Color.web("#4fc1ff"));
        row.getChildren().addAll(k, v);
        detailBox.getChildren().add(row);
    }

    private void setHoverIndex(int index) {
        this.hoverIndex = index;
        cpuTrack.render(frameHistory, index);
        memoryTrack.render(frameHistory, index);
        networkTrack.render(frameHistory, index);
    }

    private String humanBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    @Override
    public void onFrame(ProfilerFrame frame) {
        if (!paused) {
            Platform.runLater(() -> {
                frameHistory.add(frame);
                if (frameHistory.size() > 600) frameHistory.remove(0);
                if (hoverIndex == -1) {
                    cpuTrack.render(frameHistory, -1);
                    memoryTrack.render(frameHistory, -1);
                    networkTrack.render(frameHistory, -1);
                }
            });
        }
    }

    @Override
    public void onSnapshot(ProfilerSnapshot snapshot) {
        if (!paused) {
            Platform.runLater(() -> {
                if (hoverIndex == -1) {
                    flameGraph.setSamples(snapshot.recentScriptSamples());
                    networkView.update();
                }
            });
        }
    }
}