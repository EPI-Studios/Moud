package com.meekdev.moud.mod.client.editor.shell;

import com.meekdev.moud.core.place.PlaceConfig;
import com.meekdev.moud.core.place.TomlEdit;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.ClientPlace;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.files.CodeEditor;
import com.meekdev.moud.mod.client.editor.files.FileManagerReveal;
import com.meekdev.moud.mod.client.editor.kit.Dialogs;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.place.GameExport;
import com.meekdev.moud.mod.place.Languages;
import com.meekdev.moud.mod.place.Output;
import com.meekdev.moud.mod.place.PlaceExport;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import imgui.type.ImString;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final float WIDTH = 600.0f;
    private static final float ISSUES_HEIGHT = 190.0f;

    private enum Stage { REVIEW, RUNNING, DONE, FAILED }

    private final SceneDocument document;
    private final ImString destination = new ImString(1024);
    private final AtomicBoolean cancel = new AtomicBoolean();
    private final AtomicReference<GameExport.Progress> progress = new AtomicReference<>();
    private final AtomicReference<String> outcome = new AtomicReference<>("");
    private volatile Stage stage = Stage.REVIEW;
    private volatile Path written;
    private List<PlaceExport.Issue> issues = List.of();
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
            destination.set((Files.isDirectory(desktop) ? desktop : home).resolve(config.id() + "-" + config.version() + ".jar").toString());
        }
        open = true;
    }

    void render() {
        if (open) {
            ImGui.openPopup(DIALOG);
            open = false;
        }
        if (!Dialogs.begin(DIALOG, WIDTH)) return;
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
        Dialogs.title("Export " + config.name());
        Texts.wrapped("Builds one jar that is the whole game: the Moud engine without the editor, its companion mods and this place. A player drops it in the mods folder of Minecraft " + SharedConstants.getCurrentVersion().name() + " with Fabric Loader and Fabric API, and the game starts straight into the place.");
        Dialogs.gap();
        ImGui.alignTextToFramePadding();
        ImGui.textUnformatted("Version " + config.version());
        ImGui.sameLine();
        if (ImGui.smallButton("Bump to " + bumped(config.version())) && root != null) bump(root);
        if (document.dirty()) {
            ImGui.sameLine();
            Texts.colored(EditorStyle.COLOR_WARNING, "  The open scene has unsaved changes, they are not exported");
        }
        Dialogs.gap();
        long errors = issues.stream().filter(i -> i.severity() == PlaceExport.Severity.ERROR).count();
        long warnings = issues.stream().filter(i -> i.severity() == PlaceExport.Severity.WARNING).count();
        if (errors == 0 && warnings == 0) Texts.colored(EditorStyle.COLOR_SUCCESS, "Checks passed");
        else Texts.colored(errors > 0 ? EditorStyle.COLOR_DANGER : EditorStyle.COLOR_WARNING, errors + " problem(s), " + warnings + " warning(s)");
        ImGui.sameLine();
        if (ImGui.smallButton("Check again") && root != null) review(root);
        ImGui.beginChild("##export-issues", 0, EditorScale.of(ISSUES_HEIGHT), true);
        if (issues.isEmpty()) Texts.muted("Every referenced file exists and the entry points are in place.");
        for (int n = 0; n < issues.size(); n++) {
            PlaceExport.Issue issue = issues.get(n);
            ImGui.pushID(n);
            int colour = switch (issue.severity()) {
                case ERROR -> EditorStyle.COLOR_DANGER;
                case WARNING -> EditorStyle.COLOR_WARNING;
                case INFO -> EditorStyle.COLOR_TEXT_MUTED;
            };
            Texts.colored(colour, switch (issue.severity()) {
                case ERROR -> "Problem";
                case WARNING -> "Warning";
                case INFO -> "Note";
            });
            ImGui.sameLine(EditorScale.of(70));
            ImGui.pushTextWrapPos(ImGui.getContentRegionMaxX() - (issue.file() != null ? EditorScale.of(50) : 0));
            ImGui.textUnformatted(issue.message());
            ImGui.popTextWrapPos();
            if (issue.file() != null) {
                ImGui.sameLine(ImGui.getContentRegionMaxX() - EditorScale.of(44));
                if (ImGui.smallButton("Open")) CodeEditor.open(issue.file(), Math.max(1, issue.line()));
            }
            ImGui.popID();
        }
        ImGui.endChild();
        Dialogs.gap();
        Texts.muted("Save the game as");
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - Dialogs.buttonWidth() - ImGui.getStyle().getItemSpacingX());
        ImGui.inputText("##export-destination", destination);
        ImGui.sameLine();
        if (ImGui.button("Browse...", Dialogs.buttonWidth(), 0)) browse();
        String target = destination.get().strip();
        boolean valid = target.toLowerCase(Locale.ROOT).endsWith(".jar") && root != null && !Path.of(target).toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize());
        if (!target.isEmpty() && !valid) Texts.colored(EditorStyle.COLOR_WARNING, "Pick a .jar file outside the place folder");
        else if (valid && Files.exists(Path.of(target))) Texts.colored(EditorStyle.COLOR_WARNING, "That file exists and will be replaced");
        Dialogs.gap();
        Dialogs.alignFooter(2);
        if (Dialogs.button("Cancel##export-cancel") || ImGui.isKeyPressed(ImGuiKey.Escape)) ImGui.closeCurrentPopup();
        ImGui.sameLine();
        if (Dialogs.primaryButton((errors > 0 ? "Export anyway" : "Export") + "##export-go", valid)) start(root, Path.of(target));
    }

    private void renderRunning() {
        GameExport.Progress now = progress.get();
        Dialogs.title("Building the game");
        float fraction = now == null || now.total() == 0 ? 0 : (float) now.done() / now.total();
        ImGui.progressBar(fraction, -1, 0, now == null ? "starting" : now.step());
        Dialogs.gap();
        Dialogs.alignFooter(2);
        if (Dialogs.button("Hide##export-hide")) ImGui.closeCurrentPopup();
        ImGui.sameLine();
        if (Dialogs.button("Stop##export-stop")) cancel.set(true);
    }

    private void renderDone() {
        Dialogs.title("The game is built");
        Texts.wrapped(outcome.get());
        Texts.muted("Players also need Fabric Loader and Fabric API. Launching it unpacks the place into moud-games/" + config.id() + " next to their saves.");
        Dialogs.gap();
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
        Texts.colored(EditorStyle.COLOR_DANGER, outcome.get());
        Dialogs.gap();
        Dialogs.alignFooter(1);
        if (Dialogs.button("Back##export-back")) stage = Stage.REVIEW;
    }

    private void review(Path root) {
        issues = PlaceExport.check(root, Languages.extensions());
        try {
            Path toml = root.resolve("place.toml");
            config = Files.isRegularFile(toml) ? PlaceConfig.parse(Files.readString(toml)) : PlaceConfig.DEFAULT;
        } catch (IOException | IllegalArgumentException e) {
            config = PlaceConfig.DEFAULT;
        }
    }

    private void bump(Path root) {
        Path toml = root.resolve("place.toml");
        try {
            String text = Files.isRegularFile(toml) ? Files.readString(toml) : "";
            String next = bumped(config.version());
            Files.writeString(toml, TomlEdit.set(text, "", "version", next));
            String current = destination.get();
            destination.set(current.replace(config.id() + "-" + config.version() + ".jar", config.id() + "-" + next + ".jar"));
            review(root);
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
                outcome.set(String.format(Locale.ROOT, "%s, %.1f MB, holding %s.", result.jar(), result.bytes() / 1_048_576.0, String.join(", ", result.included())));
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
