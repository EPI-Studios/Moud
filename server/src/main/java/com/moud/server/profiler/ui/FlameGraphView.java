package com.moud.server.profiler.ui;

import com.moud.server.profiler.model.ScriptExecutionType;
import com.moud.server.profiler.model.ScriptSample;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.*;
import java.util.function.Consumer;

public class FlameGraphView extends Pane {
    private final Canvas canvas;
    private List<ScriptSample> currentSamples = Collections.emptyList();
    private ScriptSample hoveredSample = null;
    private Consumer<ScriptSample> onSampleSelected;

    private double zoomFactor = 1.0;
    private double scrollOffset = 0.0;
    private double lastMouseX = 0;

    public FlameGraphView() {
        this.canvas = new Canvas();
        getChildren().add(canvas);

        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());

        widthProperty().addListener(o -> draw());
        heightProperty().addListener(o -> draw());

        canvas.setOnMouseMoved(this::handleMouseMove);
        canvas.setOnMouseClicked(this::handleMouseClick);
        canvas.setOnMousePressed(e -> lastMouseX = e.getX());
        canvas.setOnMouseDragged(this::handleDrag);
        canvas.setOnScroll(this::handleScroll);

        setStyle("-fx-background-color: #252526; -fx-cursor: crosshair;");
    }

    public void setSamples(List<ScriptSample> samples) {
        this.currentSamples = samples;
        draw();
    }

    public void setOnSampleSelected(Consumer<ScriptSample> callback) {
        this.onSampleSelected = callback;
    }

    private void handleScroll(ScrollEvent e) {
        if (currentSamples.isEmpty()) return;

        double zoomIntensity = 0.25;
        double mouseRatio = e.getX() / getWidth();

        double oldZoom = zoomFactor;
        if (e.getDeltaY() > 0) zoomFactor *= (1.0 + zoomIntensity);
        else zoomFactor /= (1.0 + zoomIntensity);

        zoomFactor = Math.max(1.0, Math.min(zoomFactor, 1_000_000.0));

        double viewSizeOld = 1.0 / oldZoom;
        double viewSizeNew = 1.0 / zoomFactor;

        scrollOffset += (mouseRatio * viewSizeOld) - (mouseRatio * viewSizeNew);
        clampScroll();
        draw();
        e.consume();
    }

    private void handleDrag(MouseEvent e) {
        if (zoomFactor <= 1.0) return;
        double deltaX = e.getX() - lastMouseX;
        lastMouseX = e.getX();

        double viewSize = 1.0 / zoomFactor;
        double shift = (deltaX / getWidth()) * viewSize;
        scrollOffset -= shift;
        clampScroll();
        draw();
    }

    private void clampScroll() {
        double viewSize = 1.0 / zoomFactor;
        scrollOffset = Math.max(0, Math.min(scrollOffset, 1.0 - viewSize));
    }

    private void handleMouseMove(MouseEvent e) {
        if (currentSamples.isEmpty()) return;
        ScriptSample found = findSampleAt(e.getX(), e.getY());
        if (found != hoveredSample) {
            hoveredSample = found;
            draw();
        }
    }

    private void handleMouseClick(MouseEvent e) {
        if (onSampleSelected != null) {
            onSampleSelected.accept(hoveredSample);
        }
    }

    private void draw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();

        gc.setFill(Color.web("#1e1e1e"));
        gc.fillRect(0, 0, w, h);

        if (currentSamples.isEmpty()) {
            gc.setFill(Color.GRAY);
            gc.setFont(Font.font("Segoe UI", 12));
            gc.fillText("No script data captured", 10, 20);
            return;
        }

        long minTime = Long.MAX_VALUE;
        long maxTime = Long.MIN_VALUE;
        Map<Long, List<ScriptSample>> children = new HashMap<>();

        for (ScriptSample s : currentSamples) {
            children.computeIfAbsent(s.parentSpanId(), k -> new ArrayList<>()).add(s);
            minTime = Math.min(minTime, s.startEpochMillis());
            maxTime = Math.max(maxTime, s.startEpochMillis() + (long)s.durationMillis());
        }

        double totalDuration = Math.max(1, maxTime - minTime);
        double headerHeight = 20;

        renderRecursive(gc, children, -1L, 0, minTime, totalDuration, w, headerHeight, 24);

        drawTimeRuler(gc, w, headerHeight, totalDuration);

        if (zoomFactor > 1.1) {
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Consolas", 12));
            String scaleText = String.format("x%.1f", zoomFactor);
            gc.fillText(scaleText, w - 80, h - 10);
        }
    }

    private void drawTimeRuler(GraphicsContext gc, double w, double h, double totalDurationMs) {
        gc.setFill(Color.web("#2d2d2d"));
        gc.fillRect(0, 0, w, h);
        gc.setStroke(Color.GRAY);
        gc.setLineWidth(1);
        gc.strokeLine(0, h, w, h);

        double visibleDuration = totalDurationMs / zoomFactor;
        double viewStartMs = scrollOffset * totalDurationMs;

        double idealStep = visibleDuration / 10.0;
        double step = Math.pow(10, Math.floor(Math.log10(idealStep)));
        if (idealStep / step > 5) step *= 5;
        else if (idealStep / step > 2) step *= 2;

        gc.setFill(Color.LIGHTGRAY);
        gc.setFont(Font.font("Consolas", 9));

        double startTick = Math.floor(viewStartMs / step) * step;

        for (double t = startTick; t < viewStartMs + visibleDuration; t += step) {
            double ratio = (t - viewStartMs) / visibleDuration;
            double x = ratio * w;

            if (x >= 0 && x <= w) {
                gc.strokeLine(x, h - 5, x, h);
                gc.fillText(String.format("%.1fms", t), x + 2, h - 8);
            }
        }
    }

    private void renderRecursive(GraphicsContext gc, Map<Long, List<ScriptSample>> children, long parentId, int depth, long minTime, double totalDuration, double w, double startY, double rowHeight) {
        List<ScriptSample> layer = children.get(parentId);
        if (layer == null) return;

        double viewStart = scrollOffset;
        double viewEnd = scrollOffset + (1.0 / zoomFactor);

        for (ScriptSample sample : layer) {
            double sampleStartRatio = (double)(sample.startEpochMillis() - minTime) / totalDuration;
            double sampleDurationRatio = sample.durationMillis() / totalDuration;
            double sampleEndRatio = sampleStartRatio + sampleDurationRatio;

            if (sampleEndRatio < viewStart || sampleStartRatio > viewEnd) continue;

            double x = (sampleStartRatio - viewStart) * zoomFactor * w;
            double rawWidth = sampleDurationRatio * zoomFactor * w;
            double y = startY + (depth * rowHeight);

            double renderWidth = Math.max(rawWidth, 3.0);
            double renderX = Math.floor(x);

            Color baseColor = getColorForType(sample.type());
            if (!sample.success()) baseColor = Color.web("#f44336");

            if (sample == hoveredSample) {
                gc.setFill(Color.WHITE);
                gc.setStroke(baseColor.darker());
            } else {
                gc.setFill(baseColor);
                gc.setStroke(Color.web("#1e1e1e"));

            }

            gc.fillRect(renderX, y, renderWidth, rowHeight - 1);

            if (renderWidth > 5) {
                gc.strokeRect(renderX, y, renderWidth, rowHeight - 1);
            }

            if (renderWidth > 35) {
                gc.setFill(sample == hoveredSample ? Color.BLACK : Color.WHITE);
                gc.setFont(Font.font("Segoe UI", 10));

                String label = sample.label() != null && !sample.label().isBlank() ? sample.label() : sample.functionName();

                String text = label;
                if (renderWidth > 80) {
                    text += String.format(" (%.2fms)", sample.durationMillis());
                }

                if (text.length() * 6 < renderWidth - 4) {
                    gc.fillText(text, renderX + 4, y + 16);
                } else if (renderWidth > 40) {

                    String clipped = label.substring(0, Math.min(label.length(), (int)(renderWidth / 6))) + "..";
                    gc.fillText(clipped, renderX + 4, y + 16);
                }
            }

            renderRecursive(gc, children, sample.spanId(), depth + 1, minTime, totalDuration, w, startY, rowHeight);
        }
    }

    private ScriptSample findSampleAt(double mx, double my) {
        if (currentSamples.isEmpty()) return null;
        long minTime = Long.MAX_VALUE;
        long maxTime = Long.MIN_VALUE;
        Map<Long, List<ScriptSample>> children = new HashMap<>();
        for (ScriptSample s : currentSamples) {
            children.computeIfAbsent(s.parentSpanId(), k -> new ArrayList<>()).add(s);
            minTime = Math.min(minTime, s.startEpochMillis());
            maxTime = Math.max(maxTime, s.startEpochMillis() + (long)s.durationMillis());
        }
        double totalDuration = Math.max(1, maxTime - minTime);

        return findRecursiveHit(children, -1L, 0, minTime, totalDuration, getWidth(), mx, my - 20);
    }

    private ScriptSample findRecursiveHit(Map<Long, List<ScriptSample>> children, long parentId, int depth, long minTime, double totalDuration, double w, double mx, double my) {
        List<ScriptSample> layer = children.get(parentId);
        if (layer == null) return null;
        double rowHeight = 24;
        double y = depth * rowHeight;

        if (my >= y && my <= y + rowHeight) {
            for (ScriptSample sample : layer) {
                double start = (double)(sample.startEpochMillis() - minTime) / totalDuration;
                double dur = sample.durationMillis() / totalDuration;
                double x = (start - scrollOffset) * zoomFactor * w;
                double width = Math.max(dur * zoomFactor * w, 3.0);

                if (mx >= x && mx <= x + width) return sample;
            }
        } else if (my > y + rowHeight) {
            for (ScriptSample sample : layer) {
                double start = (double)(sample.startEpochMillis() - minTime) / totalDuration;
                double dur = sample.durationMillis() / totalDuration;
                double x = (start - scrollOffset) * zoomFactor * w;
                double width = Math.max(dur * zoomFactor * w, 3.0);

                if (mx >= x && mx <= x + width) {
                    ScriptSample hit = findRecursiveHit(children, sample.spanId(), depth + 1, minTime, totalDuration, w, mx, my);
                    if (hit != null) return hit;
                }
            }
        }
        return null;
    }

    private Color getColorForType(ScriptExecutionType type) {
        return switch (type) {
            case EVENT -> Color.web("#007acc");

            case ASYNC_TASK -> Color.web("#800080");

            case TIMEOUT -> Color.web("#d16969");

            case INTERVAL -> Color.web("#ce9178");

            case RUNTIME_TICK -> Color.web("#6a9955");

            case COMMAND -> Color.web("#dcdcaa");

            default -> Color.web("#606060");
        };
    }
}