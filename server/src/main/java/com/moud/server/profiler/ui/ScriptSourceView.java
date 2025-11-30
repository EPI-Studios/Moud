package com.moud.server.profiler.ui;

import com.moud.server.project.ProjectLoader;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScriptSourceView extends ScrollPane {
    private final VBox container;
    private final Map<String, String> fileCache = new HashMap<>();

    private static final Pattern JS_SYNTAX = Pattern.compile(
            "(?<KEYWORD>\\b(function|var|let|const|if|else|return|new|this|import|class)\\b)" +
                    "|(?<STRING>\"[^\"]*\"|'[^']*'|`[^`]*`)" +
                    "|(?<COMMENT>//.*|/\\*[\\s\\S]*?\\*/)" +
                    "|(?<NUMBER>\\b\\d+(\\.\\d+)?\\b)" +
                    "|(?<NORMAL>\\w+|\\S)"
    );

    public ScriptSourceView() {
        this.container = new VBox();
        this.container.setStyle("-fx-background-color: #1e1e1e;");
        setContent(container);
        setFitToWidth(true);
        setFitToHeight(true);
        setStyle("-fx-background: #1e1e1e; -fx-border-color: #3e3e42;");
    }

    public void displaySource(String scriptName, int targetLine) {
        container.getChildren().clear();
        if (scriptName == null || scriptName.equals("<unknown>")) {
            showError("Internal script (no source)");
            return;
        }

        new Thread(() -> {
            String content = loadFileContent(scriptName);
            Platform.runLater(() -> {
                if (content == null) showError("File not found: " + scriptName);
                else renderCode(content, targetLine);
            });
        }).start();
    }

    private String loadFileContent(String scriptName) {
        if (fileCache.containsKey(scriptName)) return fileCache.get(scriptName);
        try {
            Path root = ProjectLoader.findProjectRoot();
            Path file = root.resolve(scriptName);
            if (!Files.exists(file)) file = root.resolve("src").resolve(scriptName);
            if (Files.exists(file)) {
                String text = Files.readString(file);
                fileCache.put(scriptName, text);
                return text;
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    private void renderCode(String content, int targetLine) {
        container.getChildren().clear();
        String[] lines = content.split("\n");
        int startLine = Math.max(0, targetLine - 10);
        int endLine = Math.min(lines.length, targetLine + 10);

        for (int i = startLine; i < endLine; i++) {
            int lineNum = i + 1;
            TextFlow lineFlow = highlightSyntax(lines[i]);
            Label numLabel = new Label(String.format("%4d  ", lineNum));
            numLabel.setTextFill(Color.GRAY);
            numLabel.setFont(Font.font("Consolas", 12));
            numLabel.setMinWidth(40);

            HBox row = new HBox(numLabel, lineFlow);
            row.setPadding(new Insets(0, 5, 0, 5));
            if (lineNum == targetLine) {
                row.setStyle("-fx-background-color: #37373d;");
                numLabel.setTextFill(Color.web("#c586c0"));
            }
            container.getChildren().add(row);
        }
    }

    private TextFlow highlightSyntax(String text) {
        TextFlow flow = new TextFlow();
        Matcher matcher = JS_SYNTAX.matcher(text);
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                addText(flow, text.substring(lastEnd, matcher.start()), Color.web("#d4d4d4"), false);
            }
            Color color = Color.web("#d4d4d4");
            boolean bold = false;
            if (matcher.group("KEYWORD") != null) { color = Color.web("#569cd6"); bold = true; }
            else if (matcher.group("STRING") != null) color = Color.web("#ce9178");
            else if (matcher.group("COMMENT") != null) color = Color.web("#6a9955");
            else if (matcher.group("NUMBER") != null) color = Color.web("#b5cea8");

            addText(flow, matcher.group(), color, bold);
            lastEnd = matcher.end();
        }
        if (lastEnd < text.length()) addText(flow, text.substring(lastEnd), Color.web("#d4d4d4"), false);
        return flow;
    }

    private void addText(TextFlow flow, String content, Color color, boolean bold) {
        Text t = new Text(content);
        t.setFill(color);
        t.setFont(Font.font("Consolas", bold ? FontWeight.BOLD : FontWeight.NORMAL, 12));
        flow.getChildren().add(t);
    }

    private void showError(String msg) {
        Label l = new Label(msg);
        l.setTextFill(Color.RED);
        container.getChildren().add(l);
    }
}