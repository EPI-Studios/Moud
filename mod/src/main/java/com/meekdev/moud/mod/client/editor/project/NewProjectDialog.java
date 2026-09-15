package com.meekdev.moud.mod.client.editor.project;

import com.meekdev.moud.mod.client.editor.kit.Text;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.notify.Notifier;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import com.meekdev.moud.mod.client.editor.files.FileBrowser;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

public final class NewProjectDialog {

    private static final String POPUP_TITLE = "New Project";
    private static final String INVALID_NAME_CHARS = "/\\:*?\"<>|";
    private static final float DIALOG_WIDTH = 520.0f;
    private static final int NAME_CAPACITY = 128;
    private static final int PATH_CAPACITY = 512;

    private final FileBrowser browser;
    private final ProjectStore store;
    private final Notifier notifier;
    private final Consumer<Project> onCreated;
    private final ImString nameInput = new ImString(NAME_CAPACITY);
    private final ImString parentInput = new ImString(ProjectStore.defaultProjectsFolder().toString(), PATH_CAPACITY);
    private boolean openRequested;

    public NewProjectDialog(ProjectStore store, Notifier notifier, IconWidgets icons,
                            Consumer<Project> onCreated) {
        this.browser = new FileBrowser(icons);
        this.store = store;
        this.notifier = notifier;
        this.onCreated = onCreated;
    }

    public void open() {
        nameInput.set("");
        openRequested = true;
    }

    public void render() {
        if (openRequested) {
            ImGui.openPopup(Text.label("New Project", "new-project-dialog"));
            openRequested = false;
        }
        ImGui.setNextWindowSize(EditorScale.of(DIALOG_WIDTH), 0.0f, ImGuiCond.Appearing);
        if (!ImGui.beginPopupModal(Text.label("New Project", "new-project-dialog"),
                ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }
        renderFields();
        renderValidationAndButtons();
        browser.render();
        ImGui.endPopup();
    }

    private void renderFields() {
        ImGui.inputText(Text.label("Project name",
                "new-project-name"), nameInput);
        ImGui.inputText(Text.label("Parent folder",
                "new-project-parent"), parentInput);
        ImGui.sameLine();
        if (ImGui.button(Text.label("Browse",
                "new-project-browse"))) {
            browseParent();
        }
        Texts.muted(Text.of("Final path: {0}", previewPath()));
    }

    private void browseParent() {
        Path start = currentParent().filter(Files::isDirectory)
                .orElse(Path.of(System.getProperty("user.home")));
        browser.chooseFolder("Choose a parent folder",
                start, path -> parentInput.set(path.toString()));
    }

    private void renderValidationAndButtons() {
        Optional<String> error = validationError();
        error.ifPresent(message -> Texts.colored(EditorStyle.COLOR_DANGER, message));
        ImGui.separator();
        ImGui.beginDisabled(error.isPresent());
        if (ImGui.button(Text.label("Create",
                "new-project-create"))) {
            attemptCreate();
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        if (ImGui.button(Text.label("Cancel",
                "new-project-cancel"))) {
            ImGui.closeCurrentPopup();
        }
    }

    private void attemptCreate() {
        try {
            Path parent = currentParent().orElseThrow(() -> new IOException("Invalid parent folder"));
            Files.createDirectories(parent);
            Project project = store.createProject(nameInput.get().trim(), parent.resolve(nameInput.get().trim()));
            store.recordOpened(project);
            ImGui.closeCurrentPopup();
            onCreated.accept(project);
        } catch (IOException error) {
            notifier.show(Text.of("Creation failed: {0}",
                    error.getMessage()));
        }
    }

    private String previewPath() {
        String name = nameInput.get().trim();
        return currentParent().filter(parent -> !name.isEmpty())
                .map(parent -> parent.resolve(name).toString())
                .orElse("-");
    }

    private Optional<Path> currentParent() {
        String raw = parentInput.get().trim();
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(raw));
        } catch (InvalidPathException error) {
            return Optional.empty();
        }
    }

    private Optional<String> validationError() {
        String name = nameInput.get().trim();
        if (name.isEmpty()) {
            return Optional.of("A project name is required.");
        }
        if (name.chars().anyMatch(character -> INVALID_NAME_CHARS.indexOf(character) >= 0)) {
            return Optional.of("Forbidden characters: / \\ : * ? \" < > |");
        }
        return parentValidationError(name);
    }

    private Optional<String> parentValidationError(String name) {
        Optional<Path> parent = currentParent();
        if (parent.isEmpty()) {
            return Optional.of("A parent folder is required.");
        }
        if (!Files.isDirectory(parent.get()) && !parent.get().equals(ProjectStore.defaultProjectsFolder())) {
            return Optional.of("The parent folder must exist.");
        }
        if (Files.exists(parent.get().resolve(name))) {
            return Optional.of("A folder with this name already exists.");
        }
        return Optional.empty();
    }
}
