package com.meekdev.moud.mod.client.editor.shell;

import com.meekdev.moud.core.place.PlaceConfig;
import com.meekdev.moud.core.place.TomlEdit;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.ClientPlace;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.files.CodeEditor;
import com.meekdev.moud.mod.client.editor.files.FileManagerReveal;
import com.meekdev.moud.mod.client.editor.kit.Chips;
import com.meekdev.moud.mod.client.editor.kit.Dialogs;
import com.meekdev.moud.mod.client.editor.kit.Notices;
import com.meekdev.moud.mod.client.editor.kit.Sections;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.place.GameExport;
import com.meekdev.moud.mod.place.Languages;
import com.meekdev.moud.mod.place.Output;
import com.meekdev.moud.mod.place.PlaceExport;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiKey;
import imgui.type.ImString;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.SharedConstants;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

final class ExportDialog {

    private static final String DIALOG = "##export";
    private static final float WIDTH = 620.0f;
    private static final float HEIGHT = 560.0f;
    private static final float CARD_HEIGHT = 74.0f;
    private static final float CHECKS_HEIGHT = 168.0f;
    private static final float ROW_PADDING = 6.0f;
    private static final float DOT = 4.0f;

    private enum Stage { REVIEW, RUNNING, DONE, FAILED }

    private final SceneDocument document;
    private final ImString destination = new ImString(1024);
    private final AtomicBoolean cancel = new AtomicBoolean();
    private final AtomicReference<GameExport.Progress> progress = new AtomicReference<>();
    private final AtomicReference<String> outcome = new AtomicReference<>("");
    private final AtomicReference<List<String>> included = new AtomicReference<>(List.of());
    private volatile Stage stage = Stage.REVIEW;
    private volatile Path written;
    private volatile long writtenBytes;
    private List<PlaceExport.Issue> issues = List.of();
    private GameExport.Plan plan = new GameExport.Plan(List.of(), 0, 0);
    private PlaceConfig config = PlaceConfig.DEFAULT;
    private boolean open;

    ExportDialog(SceneDocument document) {
        this.document = document;
    }

    void open() {
        Path root = ClientPlace.root();
        if (root == null) return;
        if (stage != Stage.RUNNING) {
            stage = Stage.REVIEW;
            review(root);
            Path home = Path.of(System.getProperty("user.home"));
            Path desktop = home.resolve("Desktop");
            destination.set((Files.isDirectory(desktop) ? desktop : home).resolve(fileName()).toString());
        }
        open = true;
    }

    void render() {
        if (open) {
            ImGui.openPopup(DIALOG);
            open = false;
        }
        if (!Dialogs.begin(DIALOG, WIDTH, HEIGHT)) return;
        switch (stage) {
            case REVIEW -> renderReview();
            case RUNNING -> renderRunning();
            case DONE -> renderDone();
            case FAILED -> renderFailed();
        }
        Dialogs.end();
    }

    private void renderReview() {
        Path root = ClientPlace.root();
        Dialogs.title("Export game");
        Texts.muted("One jar that is the whole game, without the editor.");
        Dialogs.gap();
        renderCard(root);
        Dialogs.gap();
        renderChecks(root);
        Dialogs.gap();
        Sections.caption("INSIDE THE JAR");
        List<String> parts = new ArrayList<>(plan.mods());
        parts.add(plan.files() + " place files, " + megabytes(plan.bytes()));
        chips(parts);
        Dialogs.gap();
        Sections.caption("SAVE AS");
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - Dialogs.buttonWidth() - ImGui.getStyle().getItemSpacingX());
        ImGui.inputText("##export-destination", destination);
        ImGui.sameLine();
        if (ImGui.button("Browse...", Dialogs.buttonWidth(), 0)) browse();
        String target = destination.get().strip();
        boolean valid = target.toLowerCase(Locale.ROOT).endsWith(".jar") && root != null && !Path.of(target).toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize());
        if (!target.isEmpty() && !valid) Texts.colored(EditorStyle.COLOR_WARNING, "Pick a .jar file outside the place folder");
        else if (valid && Files.exists(Path.of(target))) Texts.muted("That file is replaced");
        else Texts.muted(" ");
        long errors = issues.stream().filter(i -> i.severity() == PlaceExport.Severity.ERROR).count();
        footer();
        Texts.muted("Players need Minecraft " + SharedConstants.getCurrentVersion().name() + ", Fabric Loader and Fabric API.");
        Dialogs.alignFooter(2);
        if (Dialogs.button("Cancel##export-cancel") || ImGui.isKeyPressed(ImGuiKey.Escape)) ImGui.closeCurrentPopup();
        ImGui.sameLine();
        if (Dialogs.primaryButton((errors > 0 ? "Export anyway" : "Export") + "##export-go", valid)) start(root, Path.of(target));
    }

    private void renderCard(Path root) {
        ImDrawList draw = ImGui.getWindowDrawList();
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        float width = ImGui.getContentRegionAvailX();
        float height = EditorScale.of(CARD_HEIGHT);
        draw.addRectFilled(left, top, left + width, top + height, EditorStyle.COLOR_SUNKEN_BACKGROUND, EditorStyle.frameRounding() * 2);
        float pad = EditorScale.of(14);
        ImGui.setCursorScreenPos(left + pad, top + pad);
        ImGui.beginGroup();
        ImGui.setWindowFontScale(1.35f);
        ImGui.textUnformatted(config.name());
        ImGui.setWindowFontScale(1f);
        Texts.muted(config.id() + "  ·  version " + config.version() + (document.dirty() ? "  ·  the open scene has unsaved changes, they are left out" : ""));
        ImGui.endGroup();
        float button = Dialogs.buttonWidth() * 1.3f;
        ImGui.setCursorScreenPos(left + width - pad - button, top + (height - ImGui.getFrameHeight()) / 2);
        if (ImGui.button("Bump to " + bumped(config.version()) + "##export-bump", button, 0) && root != null) bump(root);
        if (ImGui.isItemHovered()) ImGui.setTooltip("Raise the last number of the version in place.toml");
        ImGui.setCursorScreenPos(left, top + height);
        ImGui.dummy(width, 0);
    }

    private void renderChecks(Path root) {
        long errors = issues.stream().filter(i -> i.severity() == PlaceExport.Severity.ERROR).count();
        long warnings = issues.stream().filter(i -> i.severity() == PlaceExport.Severity.WARNING).count();
        Sections.caption("CHECKS");
        ImGui.sameLine();
        if (errors > 0) Chips.drawInline(errors + (errors == 1 ? " problem" : " problems"), EditorStyle.COLOR_DANGER);
        else if (warnings > 0) Chips.drawInline(warnings + (warnings == 1 ? " warning" : " warnings"), EditorStyle.COLOR_WARNING);
        else Chips.drawInline("Ready", EditorStyle.COLOR_SUCCESS);
        ImGui.sameLine(ImGui.getContentRegionMaxX() - ImGui.calcTextSizeX("Check again") - EditorStyle.framePaddingX() * 2);
        if (ImGui.smallButton("Check again") && root != null) review(root);
        if (issues.stream().noneMatch(i -> i.severity() != PlaceExport.Severity.INFO)) {
            Notices.success("The entry scripts and the start scene exist, and every res:// path points at a file.");
        }
        ImGui.pushStyleColor(ImGuiCol.ChildBg, EditorStyle.COLOR_SUNKEN_BACKGROUND);
        ImGui.beginChild("##export-issues", 0, EditorScale.of(CHECKS_HEIGHT), false);
        ImDrawList draw = ImGui.getWindowDrawList();
        float pad = EditorScale.of(ROW_PADDING);
        for (int n = 0; n < issues.size(); n++) {
            PlaceExport.Issue issue = issues.get(n);
            ImGui.pushID(n);
            int colour = switch (issue.severity()) {
                case ERROR -> EditorStyle.COLOR_DANGER;
                case WARNING -> EditorStyle.COLOR_WARNING;
                case INFO -> EditorStyle.COLOR_TEXT_FAINT;
            };
            float rowTop = ImGui.getCursorScreenPosY() + pad;
            float x = ImGui.getCursorScreenPosX() + pad;
            draw.addCircleFilled(x + EditorScale.of(DOT), rowTop + ImGui.getTextLineHeight() / 2, EditorScale.of(DOT), colour);
            ImGui.setCursorScreenPos(x + EditorScale.of(DOT * 4), rowTop);
            boolean openable = issue.file() != null;
            float openWidth = openable ? ImGui.calcTextSizeX("Open") + EditorStyle.framePaddingX() * 2 + pad : 0;
            ImGui.pushTextWrapPos(ImGui.getContentRegionMaxX() - openWidth - pad);
            ImGui.textUnformatted(issue.message());
            ImGui.popTextWrapPos();
            if (openable) {
                ImGui.sameLine(ImGui.getContentRegionMaxX() - openWidth);
                ImGui.setCursorScreenPos(ImGui.getCursorScreenPosX(), rowTop);
                if (ImGui.smallButton("Open")) CodeEditor.open(issue.file(), Math.max(1, issue.line()));
            }
            ImGui.dummy(0, pad);
            ImGui.popID();
        }
        ImGui.endChild();
        ImGui.popStyleColor();
    }

    private void renderRunning() {
        GameExport.Progress now = progress.get();
        Dialogs.title("Building " + config.name());
        Texts.muted("This takes a few seconds. The editor keeps working meanwhile.");
        Dialogs.gap();
        float fraction = now == null || now.total() == 0 ? 0.02f : Math.max(0.02f, (float) now.done() / now.total());
        ImGui.progressBar(fraction, -1, EditorScale.of(22), "");
        Texts.plain(now == null ? "Starting" : now.step() + "...");
        footer();
        Dialogs.alignFooter(2);
        if (Dialogs.button("Hide##export-hide")) ImGui.closeCurrentPopup();
        ImGui.sameLine();
        if (Dialogs.button("Stop##export-stop")) cancel.set(true);
    }

    private void renderDone() {
        Dialogs.title(config.name() + " " + config.version() + " is ready");
        Notices.success("Built " + megabytes(writtenBytes) + ". Put it in the mods folder next to Fabric API and launch Minecraft.");
        Dialogs.gap();
        Sections.caption("FILE");
        Texts.plain(written == null ? "" : written.toString());
        Dialogs.gap();
        Sections.caption("INSIDE");
        chips(included.get());
        Dialogs.gap();
        Texts.muted("On first launch the place unpacks into moud-games/" + config.id() + " and the game starts straight in it.");
        footer();
        Dialogs.alignFooter(3);
        if (Dialogs.button("Copy path##export-copy") && written != null) ImGui.setClipboardText(written.toString());
        ImGui.sameLine();
        if (Dialogs.button("Show file##export-show") && written != null) {
            FileManagerReveal.reveal(written).ifPresent(why -> Output.add(Output.Level.WARN, "editor", "Could not show the file: " + why));
        }
        ImGui.sameLine();
        if (Dialogs.primaryButton("Done##export-done", true) || ImGui.isKeyPressed(ImGuiKey.Escape)) {
            stage = Stage.REVIEW;
            ImGui.closeCurrentPopup();
        }
    }

    private void renderFailed() {
        Dialogs.title("The export stopped");
        Notices.danger(outcome.get());
        Texts.muted("Nothing was written.");
        footer();
        Dialogs.alignFooter(1);
        if (Dialogs.button("Back##export-back")) stage = Stage.REVIEW;
    }

    private static void chips(List<String> labels) {
        float right = ImGui.getCursorScreenPosX() + ImGui.getContentRegionAvailX();
        boolean first = true;
        for (String label : labels) {
            float width = ImGui.calcTextSizeX(label) + EditorScale.of(16);
            if (!first) {
                ImGui.sameLine();
                if (ImGui.getCursorScreenPosX() + width > right) ImGui.newLine();
            }
            Chips.draw(label, EditorStyle.COLOR_TEXT);
            first = false;
        }
    }

    private static void footer() {
        float bottom = ImGui.getWindowHeight() - ImGui.getStyle().getWindowPaddingY() - ImGui.getFrameHeightWithSpacing() * 2.2f;
        if (ImGui.getCursorPosY() < bottom) ImGui.setCursorPosY(bottom);
    }

    private void review(Path root) {
        issues = PlaceExport.check(root, Languages.extensions());
        plan = GameExport.plan(root);
        try {
            Path toml = root.resolve("place.toml");
            config = Files.isRegularFile(toml) ? PlaceConfig.parse(Files.readString(toml)) : PlaceConfig.DEFAULT;
        } catch (IOException | IllegalArgumentException e) {
            config = PlaceConfig.DEFAULT;
        }
    }

    private String fileName() {
        return config.id() + "-" + config.version() + ".jar";
    }

    private void bump(Path root) {
        Path toml = root.resolve("place.toml");
        try {
            String before = fileName();
            String text = Files.isRegularFile(toml) ? Files.readString(toml) : "";
            Files.writeString(toml, TomlEdit.set(text, "", "version", bumped(config.version())));
            review(root);
            destination.set(destination.get().replace(before, fileName()));
        } catch (IOException e) {
            Output.add(Output.Level.WARN, "editor", "Could not change the version: " + e.getMessage());
        }
    }

    private void start(Path root, Path target) {
        cancel.set(false);
        progress.set(null);
        stage = Stage.RUNNING;
        Thread worker = new Thread(() -> {
            try {
                Path parent = target.toAbsolutePath().getParent();
                if (parent != null) Files.createDirectories(parent);
                GameExport.Result result = GameExport.build(root, config, target.toAbsolutePath(), progress::set, cancel);
                written = result.jar();
                writtenBytes = result.bytes();
                included.set(result.included());
                Output.add(Output.Level.INFO, "editor", "Exported " + config.name() + " " + config.version() + " to " + result.jar());
                stage = Stage.DONE;
            } catch (IOException | RuntimeException e) {
                MoudMod.LOG.warn("export failed", e);
                outcome.set(String.valueOf(e.getMessage()));
                stage = Stage.FAILED;
            }
        }, "moud export");
        worker.setDaemon(true);
        worker.start();
    }

    private void browse() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.jar"));
            filters.flip();
            String picked = TinyFileDialogs.tinyfd_saveFileDialog("Export the game", destination.get(), filters, "Game jar");
            if (picked != null) destination.set(picked.toLowerCase(Locale.ROOT).endsWith(".jar") ? picked : picked + ".jar");
        }
    }

    private static String megabytes(long bytes) {
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0);
    }

    private static String bumped(String version) {
        String[] parts = version.strip().split("[.-]");
        if (parts.length < 3) return "0.1.0";
        try {
            return parts[0] + "." + parts[1] + "." + (Integer.parseInt(parts[2]) + 1);
        } catch (NumberFormatException e) {
            return version;
        }
    }

    boolean running() {
        return stage == Stage.RUNNING;
    }
}
