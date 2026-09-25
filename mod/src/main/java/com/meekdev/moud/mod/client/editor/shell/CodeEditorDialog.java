package com.meekdev.moud.mod.client.editor.shell;

import com.meekdev.moud.mod.client.editor.files.CodeEditor;
import com.meekdev.moud.mod.client.editor.kit.Dialogs;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.place.PlaceToml;
import imgui.ImGui;
import imgui.flag.ImGuiFocusedFlags;
import imgui.flag.ImGuiKey;
import imgui.type.ImString;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

final class CodeEditorDialog {

    private static final String DIALOG = "##code-editor";
    private static final float WIDTH = 560.0f;
    private static final float LABEL_WIDTH = 110.0f;
    private static final String HINT = "myeditor --goto \"{file}:{line}\"";

    private final ImString command = new ImString(512);
    private CodeEditor.Choice choice = CodeEditor.Choice.AUTO;
    private CodeEditor.Launch launch = new CodeEditor.Launch(List.of(), null);
    private String shownFor = "";
    private String problem = "";
    private boolean open;

    void open() {
        choice = CodeEditor.choice();
        command.set(CodeEditor.custom());
        shownFor = "";
        problem = "";
        open = true;
    }

    void render() {
        if (open) {
            ImGui.openPopup(DIALOG);
            open = false;
        }
        if (!Dialogs.begin(DIALOG, WIDTH)) return;
        Dialogs.title("Code editor");
        Texts.muted("Where scripts open. Kept for this computer in " + CodeEditor.PREFERENCES);
        Dialogs.gap();
        field("Editor", this::renderChoice);
        if (choice == CodeEditor.Choice.CUSTOM) {
            field("Command", () -> {
                ImGui.setNextItemWidth(-1);
                ImGui.inputTextWithHint("##command", HINT, command);
            });
            ImGui.setCursorPosX(EditorScale.of(LABEL_WIDTH));
            Texts.muted("{file}, {line} and {folder} are filled in. Quotes keep spaces together.");
        }
        Dialogs.gap();
        refresh();
        if (launch.problem() != null) Texts.colored(EditorStyle.COLOR_WARNING, launch.problem());
        else Texts.muted("Runs " + CodeEditor.join(launch.command()));
        if (!problem.isEmpty()) Texts.colored(EditorStyle.COLOR_DANGER, problem);
        Dialogs.gap();
        Dialogs.alignFooter(3);
        if (Dialogs.button("Test##code-editor-test")) CodeEditor.open(choice, command.get(), sample(), 1);
        if (ImGui.isItemHovered()) ImGui.setTooltip("Open place.toml with this editor");
        ImGui.sameLine();
        boolean escaped = ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows) && ImGui.isKeyPressed(ImGuiKey.Escape);
        if (Dialogs.button("Cancel##code-editor-cancel") || escaped) ImGui.closeCurrentPopup();
        ImGui.sameLine();
        if (Dialogs.primaryButton("Save##code-editor-save", true)) save();
        Dialogs.end();
    }

    private void renderChoice() {
        ImGui.setNextItemWidth(-1);
        if (!ImGui.beginCombo("##choice", choice.label())) return;
        for (CodeEditor.Choice one : CodeEditor.Choice.values()) {
            if (ImGui.selectable(one.label(), one == choice)) choice = one;
        }
        ImGui.endCombo();
    }

    private void refresh() {
        String key = choice + "\n" + command.get();
        if (key.equals(shownFor)) return;
        shownFor = key;
        launch = CodeEditor.launch(choice, command.get(), sample(), 1);
    }

    private void save() {
        try {
            CodeEditor.set(choice, command.get());
            ImGui.closeCurrentPopup();
        } catch (IOException e) {
            problem = "Not saved: " + e.getMessage();
        }
    }

    private static Path sample() {
        return PlaceToml.root().resolve("place.toml");
    }

    private static void field(String label, Runnable editor) {
        ImGui.alignTextToFramePadding();
        ImGui.textUnformatted(label);
        ImGui.sameLine(EditorScale.of(LABEL_WIDTH));
        ImGui.pushID(label);
        editor.run();
        ImGui.popID();
    }
}
