package com.moud.server.profiler.ui;

import com.moud.server.profiler.model.ProfilerFrame;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.function.Function;

public class TimelineTrack extends HBox {
    private final Canvas canvas;
    private final Label valueLabel;
    private final Color trackColor;
    private final Function<ProfilerFrame, Double> valueExtractor;
    private final Function<Double, String> formatter;

    private double maxValue = 1.0;
    private boolean autoScale = true;

    public TimelineTrack(String title, Color color, Function<ProfilerFrame, Double> valueExtractor, Function<Double, String> formatter) {
        this.trackColor = color;
        this.valueExtractor = valueExtractor;
        this.formatter = formatter;

        getStyleClass().add("timeline-track");

        setMinHeight(60);
        setPrefHeight(60);
        setMaxHeight(60);

        VBox header = new VBox(2);
        header.getStyleClass().add("track-header");
        header.setMinWidth(120);
        header.setPrefWidth(120);
        header.setMaxWidth(120);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("track-label");

        valueLabel = new Label("--");
        valueLabel.getStyleClass().add("track-value");
        valueLabel.setTextFill(color);

        header.getChildren().addAll(titleLabel, valueLabel);

        Pane canvasContainer = new Pane();
        canvas = new Canvas();
        canvas.widthProperty().bind(canvasContainer.widthProperty());
        canvas.heightProperty().bind(canvasContainer.heightProperty());
        canvasContainer.getChildren().add(canvas);

        HBox.setHgrow(canvasContainer, Priority.ALWAYS);
        getChildren().addAll(header, canvasContainer);

        canvas.widthProperty().addListener(o -> requestLayout());
        canvas.heightProperty().addListener(o -> requestLayout());
    }

    public void setMaxValue(double max) {
        this.maxValue = max;
        this.autoScale = false;
    }

    public void render(List<ProfilerFrame> frames, int hoverIndex) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        if (w <= 0 || h <= 0) return;

        gc.clearRect(0, 0, w, h);

        gc.setStroke(Color.web("#2d2d2d"));
        gc.setLineWidth(1);
        gc.strokeLine(0, h * 0.25, w, h * 0.25);
        gc.strokeLine(0, h * 0.5, w, h * 0.5);
        gc.strokeLine(0, h * 0.75, w, h * 0.75);

        if (frames.isEmpty()) return;

        double currentMax = this.autoScale ? 1.0 : this.maxValue;
        if (this.autoScale) {
            for (ProfilerFrame frame : frames) {
                currentMax = Math.max(currentMax, valueExtractor.apply(frame));
            }
            currentMax = Math.max(currentMax, 0.0001);
            currentMax *= 1.1;
        }

        gc.setStroke(trackColor);
        gc.setFill(new Color(trackColor.getRed(), trackColor.getGreen(), trackColor.getBlue(), 0.15));
        gc.setLineWidth(1.5);

        double stepX = w / Math.max(1, frames.size() - 1);

        gc.beginPath();
        gc.moveTo(0, h);

        for (int i = 0; i < frames.size(); i++) {
            double val = valueExtractor.apply(frames.get(i));
            double x = i * stepX;
            double y = h - ((val / currentMax) * (h * 0.9));
            gc.lineTo(x, y);
        }

        gc.lineTo((frames.size() - 1) * stepX, h);
        gc.closePath();
        gc.fill();

        gc.beginPath();
        for (int i = 0; i < frames.size(); i++) {
            double val = valueExtractor.apply(frames.get(i));
            double x = i * stepX;
            double y = h - ((val / currentMax) * (h * 0.9));
            if (i == 0) gc.moveTo(x, y);
            else gc.lineTo(x, y);
        }
        gc.stroke();

        if (hoverIndex >= 0 && hoverIndex < frames.size()) {
            double x = hoverIndex * stepX;
            gc.setStroke(Color.web("#4fc1ff"));
            gc.setLineWidth(1);
            gc.strokeLine(x, 0, x, h);
            Double val = valueExtractor.apply(frames.get(hoverIndex));
            valueLabel.setText(formatter.apply(val));
        } else {
            Double val = valueExtractor.apply(frames.get(frames.size() - 1));
            valueLabel.setText(formatter.apply(val));
        }
    }
}