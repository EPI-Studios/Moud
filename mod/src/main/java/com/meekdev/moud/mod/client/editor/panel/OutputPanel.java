package com.meekdev.moud.mod.client.editor.panel;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.editor.files.CodeEditor;
import com.meekdev.moud.mod.client.editor.kit.EmptyStates;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.place.Output;
import com.meekdev.moud.mod.place.PlaceToml;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImString;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OutputPanel implements Panel {

    public static final String ID = "output";

    private static final int MAX_LINES = 2000;
    private static final int SEARCH_CAPACITY = 128;
    private static final float LEVEL_MARKER_RADIUS = 4.0f;
    private static final float SEARCH_WIDTH = 180.0f;
    private static final Pattern SCRIPT_LOCATION = Pattern.compile("([\\w./\\\\-]+\\.(?:luau|lua|rv|java))(?::(\\d+))?");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private enum Filter { ALL, INFO, WARN, ERROR }

    private record Location(Path file, int line) {}

    private final Deque<Output.Line> lines = new ArrayDeque<>();
    private final ImString searchInput = new ImString(SEARCH_CAPACITY);
    private Filter filter = Filter.ALL;
    private boolean stickToBottom = true;
    private long seen;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Output";
    }

    @Override
    public void render() {
        drain();
        renderHeader();
        ImGui.separator();
        renderLines();
    }

    private void drain() {
        for (Output.Line line : Output.since(seen)) {
            lines.add(line);
            seen = line.sequence();
            while (lines.size() > MAX_LINES) lines.pollFirst();
        }
    }

    private void renderHeader() {
        for (Filter candidate : Filter.values()) {
            renderFilterButton(candidate);
            ImGui.sameLine();
        }
        ImGui.setNextItemWidth(EditorScale.of(SEARCH_WIDTH));
        ImGui.inputTextWithHint("##output-search", "Filter text", searchInput);
        ImGui.sameLine();
        if (ImGui.smallButton("Clear##output-clear")) {
            lines.clear();
            stickToBottom = true;
        }
    }

    private void renderFilterButton(Filter candidate) {
        boolean active = filter == candidate;
        if (active) ImGui.pushStyleColor(ImGuiCol.Button, EditorStyle.COLOR_WIDGET_ACTIVE);
        String label = switch (candidate) {
            case ALL -> "All";
            case INFO -> "Info";
            case WARN -> "Warnings";
            case ERROR -> "Errors";
        };
        if (ImGui.smallButton(label + "##output-filter-" + candidate.name())) filter = candidate;
        if (active) ImGui.popStyleColor();
    }

    private void renderLines() {
        ImGui.beginChild("##output-lines", 0.0f, 0.0f, false);
        if (lines.isEmpty()) {
            EmptyStates.centered("Output", List.of("Prints, script errors and editor messages show here."));
        }
        String query = searchInput.get().trim().toLowerCase(Locale.ROOT);
        int index = 0;
        for (Output.Line line : lines) {
            index++;
            if (matchesFilter(line.level()) && (query.isEmpty() || line.message().toLowerCase(Locale.ROOT).contains(query))) {
                ImGui.pushID(index);
                renderLine(line);
                ImGui.popID();
            }
        }
        applyStickToBottom();
        ImGui.endChild();
    }

    private void applyStickToBottom() {
        if (stickToBottom && ImGui.getScrollY() < ImGui.getScrollMaxY()) ImGui.setScrollHereY(1.0f);
        if (ImGui.getIO().getMouseWheel() != 0.0f && ImGui.isWindowHovered()) {
            stickToBottom = ImGui.getScrollY() >= ImGui.getScrollMaxY() - 1.0f;
        }
    }

    private void renderLine(Output.Line line) {
        drawLevelMarker(line.level());
        ImGui.pushStyleColor(ImGuiCol.Text, EditorStyle.COLOR_TEXT_FAINT);
        ImGui.textUnformatted(CLOCK.format(Instant.ofEpochMilli(line.time())) + "  " + line.side());
        ImGui.popStyleColor();
        ImGui.sameLine();
        Optional<Location> link = locationIn(line.message());
        if (link.isPresent()) {
            ImGui.pushStyleColor(ImGuiCol.Text, EditorStyle.COLOR_ACCENT);
            if (ImGui.selectable(line.message())) open(link.get());
            ImGui.popStyleColor();
            if (ImGui.isItemHovered()) ImGui.setTooltip("Open " + link.get().file() + ":" + link.get().line());
        } else {
            ImGui.pushStyleColor(ImGuiCol.Text, colorFor(line.level()));
            ImGui.textUnformatted(line.message());
            ImGui.popStyleColor();
        }
    }

    private void drawLevelMarker(Output.Level level) {
        float radius = EditorScale.of(LEVEL_MARKER_RADIUS);
        float centerX = ImGui.getCursorScreenPosX() + radius;
        float centerY = ImGui.getCursorScreenPosY() + ImGui.getTextLineHeight() * 0.5f;
        ImGui.getWindowDrawList().addCircleFilled(centerX, centerY, radius, colorFor(level));
        ImGui.dummy(radius * 2.0f + 2.0f, 1.0f);
        ImGui.sameLine();
    }

    private static Optional<Location> locationIn(String message) {
        Matcher matcher = SCRIPT_LOCATION.matcher(message);
        if (!matcher.find()) return Optional.empty();
        int lineNumber = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
        Path candidate = PlaceToml.root().resolve(matcher.group(1));
        return Files.isRegularFile(candidate) ? Optional.of(new Location(candidate, lineNumber)) : Optional.empty();
    }

    private static void open(Location location) {
        CodeEditor.open(location.file(), location.line());
    }

    private boolean matchesFilter(Output.Level level) {
        return switch (filter) {
            case ALL -> true;
            case INFO -> level == Output.Level.INFO || level == Output.Level.SYSTEM;
            case WARN -> level == Output.Level.WARN;
            case ERROR -> level == Output.Level.ERROR;
        };
    }

    private static int colorFor(Output.Level level) {
        return switch (level) {
            case ERROR -> EditorStyle.COLOR_DANGER;
            case WARN -> EditorStyle.COLOR_WARNING;
            case SYSTEM -> EditorStyle.COLOR_SYSTEM;
            case INFO -> EditorStyle.COLOR_TEXT_MUTED;
        };
    }
}
