package com.moud.server.profiler.ui;

import com.moud.server.network.diagnostics.NetworkProbe;
import com.moud.server.profiler.ProfilerListener;
import com.moud.server.profiler.ProfilerService;
import com.moud.server.profiler.model.ProfilerCapture;
import com.moud.server.profiler.model.ProfilerFrame;
import com.moud.server.profiler.model.ProfilerSnapshot;
import com.moud.server.profiler.model.ScriptAggregate;
import com.moud.server.profiler.model.ScriptSample;
import com.moud.server.profiler.model.ScriptExecutionType;
import com.moud.server.shared.diagnostics.SharedStoreSnapshot;
import com.moud.server.shared.diagnostics.SharedValueSnapshot;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class ProfilerDashboard extends BorderPane implements ProfilerListener {
    private static final int MAX_POINTS = 300;
    private static final int MAX_SAMPLES = 512;
    private static final DecimalFormat CPU_FORMAT = new DecimalFormat("0.0");
    private static final DecimalFormat MEMORY_FORMAT = new DecimalFormat("0.00");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final ProfilerService profilerService;
    private final Stage stage;

    private final boolean flameChartEnabled = false;
    private final XYChart.Series<Number, Number> processCpuSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> systemCpuSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> heapSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> outboundSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> inboundSeries = new XYChart.Series<>();

    private final ObservableList<ScriptAggregateRow> scriptAggregateRows = FXCollections.observableArrayList();
    private final ObservableList<ScriptSampleRow> scriptSampleRows = FXCollections.observableArrayList();
    private final ObservableList<NetworkRow> networkRows = FXCollections.observableArrayList();

    private final TableView<ScriptAggregateRow> scriptAggregateTable = new TableView<>();
    private final TableView<ScriptSampleRow> scriptSampleTable = new TableView<>();
    private final TableView<NetworkRow> networkTable = new TableView<>();
    private final TreeView<String> sharedValuesTree = new TreeView<>();
    private final Canvas flameChartCanvas = new Canvas(960, 220);
    private boolean flameChartArmed = false;

    private final Label cpuStatus = new Label("CPU: --");
    private final Label memoryStatus = new Label("Heap: --");
    private final Label captureStatus = new Label("Capture: idle");
    private final Label alertStatus = new Label("");

    private final ToggleButton darkModeToggle = new ToggleButton("Dark Theme");

    private final AtomicReference<ProfilerCapture> lastCapture = new AtomicReference<>();

    public ProfilerDashboard(ProfilerService profilerService, Stage stage) {
        this.profilerService = profilerService;
        this.stage = stage;
        setPadding(new Insets(0, 0, 0, 0));

        setTop(buildToolbar());
        setCenter(buildTabs());
        setBottom(buildStatusBar());

        configureTables();

        // Enable dark mode by default (Unity-style)
        darkModeToggle.setSelected(true);
        applyTheme(true);

        profilerService.addListener(this);
    }

    private Node buildToolbar() {
        Button startCapture = new Button("Start Capture");
        startCapture.setOnAction(event -> {
            profilerService.startCapture("Manual Capture");
            captureStatus.setText("Capture: recording…");
        });

        Button stopCapture = new Button("Stop Capture");
        stopCapture.setOnAction(event -> {
            ProfilerCapture capture = profilerService.stopCapture();
            if (capture != null) {
                lastCapture.set(capture);
                captureStatus.setText(String.format(Locale.US,
                        "Capture: %s (%d frames / %d samples)",
                        capture.name(),
                        capture.frames().size(),
                        capture.scriptSamples().size()));
            } else {
                captureStatus.setText("Capture: idle");
            }
        });

        Button exportCapture = new Button("Export");
        exportCapture.setOnAction(event -> {
            ProfilerCapture capture = lastCapture.get();
            if (capture == null) {
                capture = profilerService.stopCapture();
                if (capture == null) {
                    showAlert(Alert.AlertType.INFORMATION, "No capture to export.");
                    return;
                }
            }
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Export Profiler Capture");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Moud Profiler (*.mprof)", "*.mprof"));
            java.io.File file = chooser.showSaveDialog(stage);
            Path path = file != null ? file.toPath() : null;
            if (path != null) {
                try {
                    profilerService.exportCapture(path, capture);
                    showAlert(Alert.AlertType.INFORMATION, "Capture exported to " + path);
                } catch (IOException e) {
                    showAlert(Alert.AlertType.ERROR, "Failed to export capture: " + e.getMessage());
                }
            }
        });

        Button loadCapture = new Button("Load Capture");
        loadCapture.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Open Profiler Capture");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Moud Profiler (*.mprof)", "*.mprof"));
            java.io.File file = chooser.showOpenDialog(stage);
            if (file != null) {
                try {
                    ProfilerCapture capture = profilerService.importCapture(file.toPath());
                    displayCapture(capture);
                    lastCapture.set(capture);
                    captureStatus.setText(String.format(Locale.US,
                            "Capture: Loaded %s (%d frames)",
                            capture.name(), capture.frames().size()));
                } catch (IOException e) {
                    showAlert(Alert.AlertType.ERROR, "Failed to load capture: " + e.getMessage());
                }
            }
        });

        darkModeToggle.setOnAction(event -> applyTheme(darkModeToggle.isSelected()));

        ToolBar toolBar = new ToolBar(
                startCapture,
                stopCapture,
                new Separator(),
                exportCapture,
                loadCapture,
                new Separator(),
                darkModeToggle
        );
        toolBar.setPadding(new Insets(8, 12, 8, 12));
        toolBar.setPrefHeight(48);

        return toolBar;
    }

    private Node buildTabs() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        tabPane.getTabs().add(new Tab("Overview", buildOverviewTab()));
        tabPane.getTabs().add(new Tab("Scripts", buildScriptsTab()));
        tabPane.getTabs().add(new Tab("Network", buildNetworkTab()));
        tabPane.getTabs().add(new Tab("Shared Values", buildSharedValuesTab()));

        return tabPane;
    }

    private Node buildOverviewTab() {
        NumberAxis xAxisCpu = new NumberAxis();
        xAxisCpu.setLabel("Frame");
        NumberAxis yAxisCpu = new NumberAxis();
        yAxisCpu.setLabel("CPU %");

        LineChart<Number, Number> cpuChart = new LineChart<>(xAxisCpu, yAxisCpu);
        cpuChart.setAnimated(false);
        cpuChart.setTitle("CPU Load");
        processCpuSeries.setName("Process");
        systemCpuSeries.setName("System");
        cpuChart.getData().addAll(processCpuSeries, systemCpuSeries);

        NumberAxis xAxisHeap = new NumberAxis();
        xAxisHeap.setLabel("Frame");
        NumberAxis yAxisHeap = new NumberAxis();
        yAxisHeap.setLabel("Heap (MiB)");

        LineChart<Number, Number> heapChart = new LineChart<>(xAxisHeap, yAxisHeap);
        heapChart.setAnimated(false);
        heapChart.setTitle("Heap Usage");
        heapSeries.setName("Heap Used");
        heapChart.getData().add(heapSeries);

        HBox charts = new HBox(16, cpuChart, heapChart);
        charts.setPadding(new Insets(16));
        charts.setPrefHeight(340);
        HBox.setHgrow(cpuChart, Priority.ALWAYS);
        HBox.setHgrow(heapChart, Priority.ALWAYS);

        GridPane summary = new GridPane();
        summary.setPadding(new Insets(16, 16, 12, 16));
        summary.setHgap(32);
        summary.setVgap(10);

        summary.add(new Label("Process CPU"), 0, 0);
        summary.add(cpuStatus, 1, 0);
        summary.add(new Label("Heap Usage"), 0, 1);
        summary.add(memoryStatus, 1, 1);
        summary.add(new Label("Capture"), 0, 2);
        summary.add(captureStatus, 1, 2);
        summary.add(new Label("Alerts"), 0, 3);
        summary.add(alertStatus, 1, 3);

        VBox overview = new VBox(charts, summary);
        VBox.setVgrow(charts, Priority.ALWAYS);

        return overview;
    }

    private Node buildScriptsTab() {
        scriptAggregateTable.setItems(scriptAggregateRows);
        scriptAggregateTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ScriptAggregateRow, String> functionCol = new TableColumn<>("Function");
        functionCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getFunction()));

        TableColumn<ScriptAggregateRow, String> scriptCol = new TableColumn<>("Script");
        scriptCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getScript()));

        TableColumn<ScriptAggregateRow, Number> lineCol = new TableColumn<>("Line");
        lineCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getLine()));

        TableColumn<ScriptAggregateRow, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getType()));

        TableColumn<ScriptAggregateRow, Number> countCol = new TableColumn<>("Calls");
        countCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getCount()));

        TableColumn<ScriptAggregateRow, Number> totalCol = new TableColumn<>("Total (ms)");
        totalCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getTotalMillis()));

        TableColumn<ScriptAggregateRow, Number> avgCol = new TableColumn<>("Avg (ms)");
        avgCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getAvgMillis()));

        TableColumn<ScriptAggregateRow, Number> maxCol = new TableColumn<>("Max (ms)");
        maxCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getMaxMillis()));

        scriptAggregateTable.getColumns().addAll(functionCol, scriptCol, lineCol, typeCol, countCol, totalCol, avgCol, maxCol);

        scriptSampleTable.setItems(scriptSampleRows);
        scriptSampleTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ScriptSampleRow, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getTimestamp()));
        TableColumn<ScriptSampleRow, String> sampleTypeCol = new TableColumn<>("Type");
        sampleTypeCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getType()));
        TableColumn<ScriptSampleRow, String> sampleLabelCol = new TableColumn<>("Label");
        sampleLabelCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getLabel()));
        TableColumn<ScriptSampleRow, String> sampleFunctionCol = new TableColumn<>("Function");
        sampleFunctionCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getFunction()));
        TableColumn<ScriptSampleRow, Number> sampleDurationCol = new TableColumn<>("Duration (ms)");
        sampleDurationCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getDurationMillis()));
        TableColumn<ScriptSampleRow, String> sampleResultCol = new TableColumn<>("Result");
        sampleResultCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getResult()));

        scriptSampleTable.getColumns().addAll(timeCol, sampleTypeCol, sampleLabelCol, sampleFunctionCol, sampleDurationCol, sampleResultCol);

        scriptAggregateTable.setRowFactory(tv -> {
            TableRow<ScriptAggregateRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    ScriptAggregateRow selected = row.getItem();
                    filterSamplesForAggregate(selected);
                }
            });
            return row;
        });

        SplitPane split = new SplitPane(scriptAggregateTable, scriptSampleTable);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.55);
        SplitPane.setResizableWithParent(scriptAggregateTable, Boolean.TRUE);
        SplitPane.setResizableWithParent(scriptSampleTable, Boolean.TRUE);

        Node flameNode;
        if (flameChartEnabled) {
            StackPane flameContainer = new StackPane(flameChartCanvas);
            flameContainer.setPadding(new Insets(8));
            flameContainer.setPrefHeight(220);

            flameChartCanvas.widthProperty().bind(flameContainer.widthProperty());
            flameChartCanvas.heightProperty().bind(flameContainer.heightProperty());
            flameChartCanvas.widthProperty().addListener((obs, oldVal, newVal) ->
                    renderFlameChart(scriptSampleRows.stream().map(ScriptSampleRow::sample).toList()));
            flameChartCanvas.heightProperty().addListener((obs, oldVal, newVal) ->
                    renderFlameChart(scriptSampleRows.stream().map(ScriptSampleRow::sample).toList()));
            flameChartCanvas.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    flameChartArmed = true;
                    Platform.runLater(() -> renderFlameChart(scriptSampleRows.stream().map(ScriptSampleRow::sample).toList()));
                }
            });
            flameNode = flameContainer;
        } else {
            Label placeholder = new Label("Flame chart disabled");
            placeholder.setPadding(new Insets(16));
            flameNode = placeholder;
        }

        VBox container = new VBox(flameNode, split);
        VBox.setVgrow(split, Priority.ALWAYS);
        return container;
    }

    private Node buildNetworkTab() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Frame");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Bytes");

        LineChart<Number, Number> networkChart = new LineChart<>(xAxis, yAxis);
        networkChart.setAnimated(false);
        networkChart.setTitle("Network Bandwidth");
        outboundSeries.setName("Outbound bytes");
        inboundSeries.setName("Inbound bytes");
        networkChart.getData().addAll(outboundSeries, inboundSeries);

        networkTable.setItems(networkRows);
        networkTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<NetworkRow, String> packetCol = new TableColumn<>("Packet / Channel");
        packetCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getIdentifier()));
        TableColumn<NetworkRow, Number> packetCountCol = new TableColumn<>("Count");
        packetCountCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getCount()));
        TableColumn<NetworkRow, String> payloadCol = new TableColumn<>("Payload");
        payloadCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getPayload()));
        TableColumn<NetworkRow, Number> failureCol = new TableColumn<>("Failures");
        failureCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getFailures()));
        TableColumn<NetworkRow, String> playersCol = new TableColumn<>("Top Players");
        playersCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getPlayers()));

        networkTable.getColumns().addAll(packetCol, packetCountCol, payloadCol, failureCol, playersCol);

        VBox container = new VBox(networkChart, networkTable);
        VBox.setVgrow(networkChart, Priority.ALWAYS);
        VBox.setVgrow(networkTable, Priority.ALWAYS);
        container.setSpacing(16);
        container.setPadding(new Insets(16));

        return container;
    }

    private Node buildSharedValuesTab() {
        sharedValuesTree.setShowRoot(false);
        sharedValuesTree.setRoot(new TreeItem<>("root"));
        VBox container = new VBox(sharedValuesTree);
        container.setPadding(new Insets(16));
        VBox.setVgrow(sharedValuesTree, Priority.ALWAYS);
        return container;
    }

    private Node buildStatusBar() {
        HBox statusBar = new HBox(24, cpuStatus, memoryStatus, captureStatus, alertStatus);
        statusBar.setPadding(new Insets(6, 12, 6, 12));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.getStyleClass().add("profiler-status-bar");
        return statusBar;
    }

    private void configureTables() {
        scriptSampleTable.setPlaceholder(new Label("No script samples"));
        scriptAggregateTable.setPlaceholder(new Label("No script data"));
        networkTable.setPlaceholder(new Label("No network data"));
    }

    private void applyTheme(boolean dark) {
        if (stage.getScene() == null) {
            return;
        }
        if (dark) {
            if (!stage.getScene().getStylesheets().contains("/profiler-dark.css")) {
                stage.getScene().getStylesheets().add("/profiler-dark.css");
            }
        } else {
            stage.getScene().getStylesheets().remove("/profiler-dark.css");
        }
    }

    private void filterSamplesForAggregate(ScriptAggregateRow aggregate) {
        if (aggregate == null) {
            return;
        }
        List<ScriptSampleRow> filtered = scriptSampleRows.stream()
                .filter(sample -> aggregate.getFunction().equals(sample.getFunction())
                        && aggregate.getScript().equals(sample.getScript()))
                .toList();
        scriptSampleRows.setAll(filtered);
        scriptSampleTable.scrollTo(0);
    }

    private void updateCpuChart(ProfilerFrame frame) {
        addPoint(processCpuSeries, frame.index(), frame.processCpuLoad() * 100);
        addPoint(systemCpuSeries, frame.index(), frame.systemCpuLoad() * 100);
    }

    private void updateHeapChart(ProfilerFrame frame) {
        addPoint(heapSeries, frame.index(), frame.heapUsedBytes() / 1024.0 / 1024.0);
    }

    private void updateNetworkChart(ProfilerSnapshot snapshot) {
        NetworkProbe.NetworkSnapshot net = snapshot.networkSnapshot();
        addPoint(outboundSeries, snapshot.frame().index(), net.outboundBytes());
        addPoint(inboundSeries, snapshot.frame().index(), net.inboundBytes());
    }

    private void addPoint(XYChart.Series<Number, Number> series, long x, double y) {
        series.getData().add(new XYChart.Data<>(x, y));
        if (series.getData().size() > MAX_POINTS) {
            series.getData().remove(0, series.getData().size() - MAX_POINTS);
        }
    }

    private void updateStatus(ProfilerFrame frame) {
        double cpu = frame.processCpuLoad() * 100.0;
        cpuStatus.setText(String.format(Locale.US, "CPU: %s %%", CPU_FORMAT.format(cpu)));

        double heapMiB = frame.heapUsedBytes() / 1024.0 / 1024.0;
        double heapCommitted = frame.heapCommittedBytes() / 1024.0 / 1024.0;
        memoryStatus.setText(String.format(Locale.US, "Heap: %s / %s MiB",
                MEMORY_FORMAT.format(heapMiB),
                MEMORY_FORMAT.format(heapCommitted)));

        boolean alert = cpu > 85.0 || (heapCommitted > 0 && (heapMiB / heapCommitted) > 0.85);
        alertStatus.setText(alert ? "Budget exceeded" : "");
        alertStatus.getStyleClass().removeAll("profiler-alert");
        if (alert) {
            alertStatus.getStyleClass().add("profiler-alert");
        }
    }

    private void updateScriptAggregates(List<ScriptAggregate> aggregates) {
        scriptAggregateRows.setAll(aggregates.stream()
                .map(ScriptAggregateRow::new)
                .limit(150)
                .toList());
    }

    private void updateScriptSamples(List<ScriptSample> samples) {
        List<ScriptSampleRow> rows = samples.stream()
                .sorted(Comparator.comparingLong(ScriptSample::startEpochMillis).reversed())
                .limit(MAX_SAMPLES)
                .map(ScriptSampleRow::new)
                .toList();
        scriptSampleRows.setAll(rows);
        renderFlameChart(samples);
    }

    private void updateNetworkTable(NetworkProbe.NetworkSnapshot snapshot) {
        List<NetworkRow> rows = new ArrayList<>();
        snapshot.outbound().stream()
                .filter(NetworkProbe.PacketStatSnapshot::hasTraffic)
                .limit(50)
                .forEach(stat -> rows.add(NetworkRow.from(stat, "Outbound")));
        snapshot.inbound().stream()
                .filter(NetworkProbe.PacketStatSnapshot::hasTraffic)
                .limit(50)
                .forEach(stat -> rows.add(NetworkRow.from(stat, "Inbound")));
        networkRows.setAll(rows);
    }

    private void updateSharedValuesTree(List<SharedStoreSnapshot> stores) {
        TreeItem<String> root = new TreeItem<>("root");
        for (SharedStoreSnapshot store : stores) {
            TreeItem<String> playerNode = root.getChildren().stream()
                    .filter(node -> node.getValue().equals(store.playerId()))
                    .findFirst()
                    .orElseGet(() -> {
                        TreeItem<String> item = new TreeItem<>(store.playerId());
                        root.getChildren().add(item);
                        return item;
                    });
            TreeItem<String> storeNode = new TreeItem<>(store.storeName() + " (" + store.totalKeys() + ")");
            playerNode.getChildren().add(storeNode);
            for (Map.Entry<String, SharedValueSnapshot> entry : store.values().entrySet()) {
                SharedValueSnapshot value = entry.getValue();
                String label = String.format(Locale.US,
                        "%s = %s (last %s:%s)",
                        entry.getKey(),
                        stringifyValue(value.value()),
                        value.lastWriter(),
                        value.lastWriterId());
                storeNode.getChildren().add(new TreeItem<>(label));
            }
        }
        root.setExpanded(true);
        sharedValuesTree.setRoot(root);
        sharedValuesTree.setShowRoot(false);
    }

    private String stringifyValue(Object value) {
        if (value == null) {
            return "null";
        }
        String str = value.toString();
        if (str.length() > 80) {
            return str.substring(0, 80) + "…";
        }
        return str;
    }

    private void renderFlameChart(List<ScriptSample> samples) {
        if (!flameChartEnabled) {
            return;
        }
        if (!flameChartArmed || flameChartCanvas.getScene() == null) {
            return;
        }

        double width = flameChartCanvas.getWidth();
        double height = flameChartCanvas.getHeight();
        if (width <= 1 || height <= 1) {
            return;
        }

        try {
            GraphicsContext gc = flameChartCanvas.getGraphicsContext2D();
            gc.clearRect(0, 0, width, height);

            if (samples == null || samples.isEmpty()) {
                return;
            }

            Map<Long, List<ScriptSample>> children = new HashMap<>();
            long earliest = Long.MAX_VALUE;
            long latest = Long.MIN_VALUE;

            for (ScriptSample sample : samples) {
                children.computeIfAbsent(sample.parentSpanId(), id -> new ArrayList<>()).add(sample);
                earliest = Math.min(earliest, sample.startEpochMillis());
                long sampleEnd = sample.startEpochMillis() + Math.max(1L, (long) sample.durationMillis());
                latest = Math.max(latest, sampleEnd);
            }

            double total = Math.max(1, latest - earliest);
            int depth = computeDepth(children, -1L);
            double rowHeight = Math.max(18, height / Math.max(1, depth));

            List<ScriptSample> roots = new ArrayList<>(children.getOrDefault(-1L, Collections.emptyList()));
            roots.sort(Comparator.comparingLong(ScriptSample::startEpochMillis));
            drawSamplesRecursive(gc, children, roots, earliest, total, rowHeight, 0, width);

            gc.setStroke(Color.web("#444444"));
            for (int i = 1; i < depth; i++) {
                double y = i * rowHeight;
                gc.strokeLine(0, y, width, y);
            }
        } catch (NullPointerException ex) {
            // Ignore rare native canvas initialization issues; a later pulse will redraw.
        }
    }

    private int computeDepth(Map<Long, List<ScriptSample>> children, long parentId) {
        List<ScriptSample> childList = children.get(parentId);
        if (childList == null || childList.isEmpty()) {
            return 1;
        }
        int max = 1;
        for (ScriptSample child : childList) {
            max = Math.max(max, 1 + computeDepth(children, child.spanId()));
        }
        return max;
    }

    private void drawSamplesRecursive(GraphicsContext gc,
                                      Map<Long, List<ScriptSample>> children,
                                      List<ScriptSample> samples,
                                      long earliest,
                                      double total,
                                      double rowHeight,
                                      int depth,
                                      double canvasWidth) {
        double y = depth * rowHeight;
        for (ScriptSample sample : samples) {
            double startOffset = sample.startEpochMillis() - earliest;
            double x = (startOffset / total) * canvasWidth;
            double w = Math.max(1, (sample.durationMillis() / total) * canvasWidth);

            gc.setFill(colorForType(sample.type()));
            gc.fillRect(x, y + 1, w, Math.max(2, rowHeight - 2));
            gc.setStroke(Color.web("#2c2d31"));
            gc.strokeRect(x, y + 1, w, Math.max(2, rowHeight - 2));

            if (w > 60) {
                gc.setFill(Color.WHITE);
                gc.fillText(sample.label().isBlank() ? sample.functionName() : sample.label(), x + 6, y + (rowHeight / 2));
            }

            List<ScriptSample> childList = new ArrayList<>(children.getOrDefault(sample.spanId(), Collections.emptyList()));
            childList.sort(Comparator.comparingLong(ScriptSample::startEpochMillis));
            if (!childList.isEmpty()) {
                drawSamplesRecursive(gc, children, childList, earliest, total, rowHeight, depth + 1, canvasWidth);
            }
        }
    }

    private Color colorForType(ScriptExecutionType type) {
        return switch (type) {
            case EVENT -> Color.web("#4FC3F7");
            case ASYNC_TASK -> Color.web("#BA68C8");
            case TIMEOUT -> Color.web("#FF7043");
            case INTERVAL -> Color.web("#FFA726");
            case RUNTIME_TICK -> Color.web("#66BB6A");
            case COMMAND -> Color.web("#9575CD");
            case OTHER -> Color.web("#90A4AE");
        };
    }

    private void displayCapture(ProfilerCapture capture) {
        processCpuSeries.getData().clear();
        systemCpuSeries.getData().clear();
        heapSeries.getData().clear();
        outboundSeries.getData().clear();
        inboundSeries.getData().clear();

        capture.frames().forEach(frame -> {
            updateCpuChart(frame);
            updateHeapChart(frame);
            addPoint(outboundSeries, frame.index(), frame.outboundBytes());
            addPoint(inboundSeries, frame.index(), frame.inboundBytes());
            updateStatus(frame);
        });

        List<ScriptSample> samples = capture.scriptSamples();
        updateScriptSamples(samples);
        updateScriptAggregates(aggregateSamples(samples));
    }

    private List<ScriptAggregate> aggregateSamples(List<ScriptSample> samples) {
        return samples.stream()
                .collect(Collectors.groupingBy(sample ->
                                sample.functionName() + "|" + sample.scriptName() + "|" + sample.line() + "|" + sample.type().name(),
                        Collectors.collectingAndThen(Collectors.toList(), list -> {
                            ScriptSample first = list.get(0);
                            long total = list.stream().mapToLong(ScriptSample::durationNanos).sum();
                            long max = list.stream().mapToLong(ScriptSample::durationNanos).max().orElse(0);
                            return new ScriptAggregate(
                                    first.functionName(),
                                    first.scriptName(),
                                    first.line(),
                                    first.type(),
                                    first.label(),
                                    list.size(),
                                    total,
                                    max
                            );
                        })))
                .values()
                .stream()
                .sorted(ScriptAggregate::compareTo)
                .toList();
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.initOwner(stage);
        alert.showAndWait();
    }

    @Override
    public void onFrame(ProfilerFrame frame) {
        Platform.runLater(() -> {
            updateCpuChart(frame);
            updateHeapChart(frame);
            updateStatus(frame);
        });
    }

    @Override
    public void onScriptSample(ScriptSample sample) {
        Platform.runLater(() -> {
            scriptSampleRows.add(0, new ScriptSampleRow(sample));
            if (scriptSampleRows.size() > MAX_SAMPLES) {
                scriptSampleRows.remove(scriptSampleRows.size() - 1);
            }
            renderFlameChart(scriptSampleRows.stream().map(ScriptSampleRow::sample).toList());
        });
    }

    @Override
    public void onSnapshot(ProfilerSnapshot snapshot) {
        Platform.runLater(() -> {
            updateNetworkChart(snapshot);
            updateScriptAggregates(snapshot.scriptAggregates());
            updateScriptSamples(snapshot.recentScriptSamples());
            updateNetworkTable(snapshot.networkSnapshot());
            updateSharedValuesTree(snapshot.sharedStores());
        });
    }

    public static final class ScriptAggregateRow {
        private final SimpleStringProperty function = new SimpleStringProperty();
        private final SimpleStringProperty script = new SimpleStringProperty();
        private final SimpleLongProperty line = new SimpleLongProperty();
        private final SimpleStringProperty type = new SimpleStringProperty();
        private final SimpleLongProperty count = new SimpleLongProperty();
        private final SimpleDoubleProperty totalMillis = new SimpleDoubleProperty();
        private final SimpleDoubleProperty avgMillis = new SimpleDoubleProperty();
        private final SimpleDoubleProperty maxMillis = new SimpleDoubleProperty();

        ScriptAggregateRow(ScriptAggregate aggregate) {
            function.set(aggregate.functionName());
            script.set(aggregate.scriptName());
            line.set(aggregate.line());
            type.set(aggregate.type().displayName());
            count.set(aggregate.invocationCount());
            totalMillis.set(aggregate.totalDurationMillis());
            avgMillis.set(aggregate.averageDurationMillis());
            maxMillis.set(aggregate.maxDurationMillis());
        }

        public String getFunction() {
            return function.get();
        }

        public String getScript() {
            return script.get();
        }

        public long getLine() {
            return line.get();
        }

        public String getType() {
            return type.get();
        }

        public long getCount() {
            return count.get();
        }

        public double getTotalMillis() {
            return totalMillis.get();
        }

        public double getAvgMillis() {
            return avgMillis.get();
        }

        public double getMaxMillis() {
            return maxMillis.get();
        }
    }

    public static final class ScriptSampleRow {
        private final SimpleStringProperty timestamp = new SimpleStringProperty();
        private final SimpleStringProperty type = new SimpleStringProperty();
        private final SimpleStringProperty label = new SimpleStringProperty();
        private final SimpleStringProperty function = new SimpleStringProperty();
        private final SimpleDoubleProperty durationMillis = new SimpleDoubleProperty();
        private final SimpleStringProperty result = new SimpleStringProperty();
        private final SimpleStringProperty script = new SimpleStringProperty();
        private final ScriptSample sample;

        ScriptSampleRow(ScriptSample sample) {
            this.sample = sample;
            timestamp.set(TIME_FORMAT.format(Instant.ofEpochMilli(sample.startEpochMillis())));
            type.set(sample.type().displayName());
            label.set(sample.label());
            function.set(sample.functionName());
            durationMillis.set(sample.durationMillis());
            script.set(sample.scriptName());
            result.set(sample.success() ? "OK" : sample.errorMessage());
        }

        public String getTimestamp() {
            return timestamp.get();
        }

        public String getType() {
            return type.get();
        }

        public String getLabel() {
            return label.get();
        }

        public String getFunction() {
            return function.get();
        }

        public double getDurationMillis() {
            return durationMillis.get();
        }

        public String getResult() {
            return result.get();
        }

        public String getScript() {
            return script.get();
        }

        public ScriptSample sample() {
            return sample;
        }
    }

    public static final class NetworkRow {
        private final SimpleStringProperty identifier = new SimpleStringProperty();
        private final SimpleLongProperty count = new SimpleLongProperty();
        private final SimpleStringProperty payload = new SimpleStringProperty();
        private final SimpleLongProperty failures = new SimpleLongProperty();
        private final SimpleStringProperty players = new SimpleStringProperty();

        private NetworkRow(String identifier, long count, String payload, long failures, String players) {
            this.identifier.set(identifier);
            this.count.set(count);
            this.payload.set(payload);
            this.failures.set(failures);
            this.players.set(players);
        }

        static NetworkRow from(NetworkProbe.PacketStatSnapshot stat, String direction) {
            String payload = humanBytes(stat.totalPayloadBytes());
            String players = stat.topPlayers().entrySet().stream()
                    .map(entry -> entry.getKey().substring(0, Math.min(6, entry.getKey().length())) + ":" + entry.getValue())
                    .limit(5)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("-");
            return new NetworkRow(direction + " " + stat.identifier(), stat.totalCount(), payload, stat.failureCount(), players);
        }

        public String getIdentifier() {
            return identifier.get();
        }

        public long getCount() {
            return count.get();
        }

        public String getPayload() {
            return payload.get();
        }

        public long getFailures() {
            return failures.get();
        }

        public String getPlayers() {
            return players.get();
        }

        private static String humanBytes(long bytes) {
            if (bytes < 1024) {
                return bytes + " B";
            }
            double value = bytes;
            String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
            int unitIdx = 0;
            while (value >= 1024 && unitIdx < units.length - 1) {
                value /= 1024;
                unitIdx++;
            }
            return String.format(Locale.US, "%.2f %s", value, units[unitIdx]);
        }
    }
}
