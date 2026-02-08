package com.moud.server.profiler.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.moud.server.network.diagnostics.NetworkProbe;
import com.moud.server.profiler.model.PacketLog;
import javafx.application.Platform;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class NetworkInspectorView extends BorderPane {
    private final TableView<PacketLog> table;
    private TextArea jsonViewer = new TextArea();
    private final Label statusLabel;

    private final ObservableList<PacketLog> masterData = FXCollections.observableArrayList();
    private final FilteredList<PacketLog> filteredData;

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private boolean isSniffing = false;
    private final ToggleButton btnSniff;
    private final ToggleButton btnFreeze;
    private final TextField filterField;
    private final CheckBox chkHideSpam;

    public NetworkInspectorView() {

        ToolBar toolbar = new ToolBar();
        toolbar.setStyle("-fx-padding: 5px; -fx-background-color: #333;");

        btnSniff = new ToggleButton("⚫ REC");
        btnSniff.setStyle("-fx-base: #2d2d2d; -fx-font-weight: bold;");
        btnSniff.setOnAction(e -> {
            isSniffing = btnSniff.isSelected();
            if(isSniffing) {
                btnSniff.setText("🔴 REC");
                btnSniff.setStyle("-fx-base: #6a9955; -fx-text-fill: white;");
            } else {
                btnSniff.setText("⚫ REC");
                btnSniff.setStyle("-fx-base: #2d2d2d;");
            }
        });

        btnFreeze = new ToggleButton("❄ Freeze");
        btnFreeze.setStyle("-fx-base: #2d2d2d;");
        btnFreeze.selectedProperty().addListener((obs, old, isFrozen) -> {
            if (isFrozen) btnFreeze.setStyle("-fx-base: #4fc1ff; -fx-text-fill: black;");
            else btnFreeze.setStyle("-fx-base: #2d2d2d;");
        });

        Button btnClear = new Button("🗑 Clear");
        btnClear.setStyle("-fx-base: #2d2d2d;");
        btnClear.setOnAction(e -> {
            masterData.clear();
            jsonViewer.clear();
        });

        filterField = new TextField();
        filterField.setPromptText("Filter packets...");
        filterField.setPrefWidth(200);
        filterField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: #ccc; -fx-border-color: #3e3e42;");

        chkHideSpam = new CheckBox("Hide Spam");
        chkHideSpam.setSelected(true);

        chkHideSpam.setTooltip(new Tooltip("Hides Movement, Rotation, KeepAlive, and Chunk packets"));
        chkHideSpam.setStyle("-fx-text-fill: #aaa;");

        toolbar.getItems().addAll(
                btnSniff,
                btnFreeze,
                btnClear,
                new Separator(),
                filterField,
                chkHideSpam
        );
        setTop(toolbar);

        table = new TableView<>();
        table.setPlaceholder(new Label("Waiting for packets... (Click REC)"));
        table.setStyle("-fx-border-width: 0;");

        filteredData = new FilteredList<>(masterData, p -> true);

        filterField.textProperty().addListener((observable, oldValue, newValue) -> updateFilter());
        chkHideSpam.selectedProperty().addListener((observable, oldValue, newValue) -> updateFilter());

        SortedList<PacketLog> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sortedData);

        TableColumn<PacketLog, String> colTime = new TableColumn<>("Time");
        colTime.setCellValueFactory(d -> new SimpleStringProperty(timeFmt.format(Instant.ofEpochMilli(d.getValue().timestamp()))));
        colTime.setPrefWidth(90);
        colTime.setStyle("-fx-font-family: 'Consolas'; -fx-text-fill: #888;");

        TableColumn<PacketLog, String> colDir = new TableColumn<>("Dir");
        colDir.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().direction()));
        colDir.setPrefWidth(45);
        colDir.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); }
                else {
                    setText(item);
                    if ("OUT".equals(item)) setStyle("-fx-text-fill: #4fc1ff; -fx-font-weight: bold; -fx-alignment: center;");
                    else setStyle("-fx-text-fill: #6a9955; -fx-font-weight: bold; -fx-alignment: center;");
                }
            }
        });

        TableColumn<PacketLog, String> colName = new TableColumn<>("Packet Type");
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().packetName()));
        colName.setPrefWidth(300);
        colName.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); }
                else {
                    setText(item);

                    if (item.contains("Script") || item.contains("Event")) {
                        setStyle("-fx-text-fill: #ce9178;");

                    } else if (item.contains("Move") || item.contains("Look")) {
                        setStyle("-fx-text-fill: #606060;");

                    } else {
                        setStyle("-fx-text-fill: #cccccc;");
                    }
                }
            }
        });

        TableColumn<PacketLog, Number> colSize = new TableColumn<>("Size");
        colSize.setCellValueFactory(d -> new SimpleLongProperty(d.getValue().sizeBytes()));
        colSize.setPrefWidth(70);
        colSize.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if(empty || item == null) setText(null);
                else {
                    long bytes = item.longValue();
                    setText(bytes + " B");
                    if (bytes > 1024) setStyle("-fx-text-fill: #f48771;");

                    else setStyle("-fx-text-fill: #888;");
                }
            }
        });

        table.getColumns().addAll(colTime, colDir, colName, colSize);

        jsonViewer = new TextArea();
        jsonViewer.setEditable(false);
        jsonViewer.setFont(Font.font("Consolas", 12));
        jsonViewer.setStyle("-fx-control-inner-background: #1e1e1e; -fx-text-fill: #ce9178; -fx-highlight-fill: #264f78;");
        jsonViewer.setPromptText("Select a packet to inspect payload...");

        VBox detailBox = new VBox();
        HBox detailHeader = new HBox();
        detailHeader.setStyle("-fx-background-color: #252526; -fx-padding: 5; -fx-border-color: #3e3e42; -fx-border-width: 0 0 1 0;");
        Label lblPayload = new Label("PAYLOAD INSPECTOR");
        lblPayload.setStyle("-fx-font-weight: bold; -fx-text-fill: #aaa; -fx-font-size: 10px;");
        detailHeader.getChildren().add(lblPayload);

        detailBox.getChildren().addAll(detailHeader, jsonViewer);
        VBox.setVgrow(jsonViewer, Priority.ALWAYS);

        SplitPane split = new SplitPane(table, detailBox);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.65);
        setCenter(split);

        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-padding: 2 5; -fx-text-fill: #666; -fx-font-size: 10px;");
        setBottom(statusLabel);

        table.getSelectionModel().selectedItemProperty().addListener((obs, old, pkt) -> {
            if (pkt != null) renderPayload(pkt.packetObject());
        });
    }

    private void updateFilter() {
        String filterText = filterField.getText().toLowerCase(Locale.ROOT);
        boolean hideSpam = chkHideSpam.isSelected();

        filteredData.setPredicate(packet -> {

            if (hideSpam && isSpam(packet.packetName())) {
                return false;
            }

            if (filterText.isEmpty()) {
                return true;
            }

            return packet.packetName().toLowerCase(Locale.ROOT).contains(filterText) ||
                    packet.direction().toLowerCase(Locale.ROOT).contains(filterText);
        });

        statusLabel.setText("Showing " + filteredData.size() + " / " + masterData.size() + " packets");
    }

    private boolean isSpam(String name) {

        return name.contains("Position")
                || name.contains("Rotation")
                || name.contains("Look")
                || name.contains("Flying")
                || name.contains("KeepAlive")
                || name.contains("Chunk")
                || name.contains("Ack")
                || name.contains("Sound");
    }

    private void renderPayload(Object packetObj) {
        if (packetObj == null) { jsonViewer.setText("null"); return; }
        if (packetObj instanceof String s) { jsonViewer.setText(s); return; }

        new Thread(() -> {
            try {
                String json = mapper.writeValueAsString(packetObj);
                Platform.runLater(() -> jsonViewer.setText(json));
            } catch (Exception e) {
                Platform.runLater(() -> jsonViewer.setText("Error serializing: " + e.getMessage()));
            }
        }).start();
    }

    public void update() {
        if (!isSniffing) return;

        List<PacketLog> recents = NetworkProbe.getInstance().getRecentPackets();

        if (btnFreeze.isSelected()) return;

        Platform.runLater(() -> {

            masterData.setAll(recents);

            if (!masterData.isEmpty()) {

            }

            statusLabel.setText("Buffer: " + masterData.size() + " packets | Showing: " + filteredData.size());
        });
    }
}