package com.meekdev.moud.mod.client.editor.shell;

import com.meekdev.amnetic.client.ui.AmneticEditor;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.editor.EditMode;
import com.meekdev.moud.mod.client.editor.Editor;
import com.meekdev.moud.mod.client.editor.EditorScreen;
import com.meekdev.moud.mod.client.editor.command.Commands;
import com.meekdev.moud.mod.client.editor.command.EditorCommand;
import com.meekdev.moud.mod.client.editor.command.Shortcut;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.document.SceneLink;
import com.meekdev.moud.mod.client.editor.kit.Dialogs;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.kit.Toolbars;
import com.meekdev.moud.mod.client.editor.panel.ExplorerPanel;
import com.meekdev.moud.mod.client.editor.panel.OutputPanel;
import com.meekdev.moud.mod.client.editor.panel.Panels;
import com.meekdev.moud.mod.client.editor.panel.PropertiesPanel;
import com.meekdev.moud.mod.client.editor.style.EditorFonts;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorScaling;
import com.meekdev.moud.mod.client.editor.style.EditorIcon;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.client.editor.style.IconAtlas;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import com.meekdev.moud.mod.client.editor.viewport.ViewportPanel;
import imgui.ImFont;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.extension.imguizmo.ImGuizmo;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiDockNodeFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import java.util.Locale;
import net.minecraft.client.Minecraft;

public final class EditorShell {

    private static final float STATUS_BAR_HEIGHT = 26.0f;
    private static final String CLOSE_PROMPT = "##close-project";
    private static final float CLOSE_PROMPT_WIDTH = 420.0f;
    private static final float BRAND_MARGIN = 6.0f;
    private static final float LOGO_ASPECT = 234.0f / 238.0f;
    private static final float STATUS_GAP = 24.0f;
    private static final int PLAY_BUTTON_COUNT = 4;
    private static final int HOST_WINDOW_FLAGS = ImGuiWindowFlags.NoDocking | ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoBringToFrontOnFocus | ImGuiWindowFlags.NoNavFocus
            | ImGuiWindowFlags.NoBackground;

    private final SceneDocument document = new SceneDocument();
    private final IconWidgets icons = new IconWidgets(new IconAtlas());
    private final DockLayout dockLayout = new DockLayout();
    private final ViewportPanel viewport = new ViewportPanel(document, icons);
    private final ExplorerPanel explorer = new ExplorerPanel(document, icons, viewport::frameSelection);
    private final Panels panels = new Panels()
            .add(viewport)
            .add(explorer)
            .add(new PropertiesPanel(document, icons))
            .add(new OutputPanel());
    private final Commands commands = new Commands();

    private boolean closePrompt;
    private boolean closeAfterSave;

    private void closeProject() {
        if (document.dirty()) closePrompt = true;
        else Editor.requestCloseProject();
    }

    private void renderClosePrompt() {
        if (closeAfterSave && !document.dirty()) {
            closeAfterSave = false;
            Editor.requestCloseProject();
        }
        if (closePrompt) {
            ImGui.openPopup(CLOSE_PROMPT);
            closePrompt = false;
        }
        if (!Dialogs.begin(CLOSE_PROMPT, CLOSE_PROMPT_WIDTH)) return;
        Dialogs.title("Save the scene before closing?");
        Texts.muted(SceneLink.file() + " has changes that are not saved.");
        Dialogs.gap();
        Dialogs.alignFooter(3);
        if (Dialogs.button("Cancel##close-cancel")) ImGui.closeCurrentPopup();
        ImGui.sameLine();
        if (Dialogs.button("Don't save##close-discard")) {
            ImGui.closeCurrentPopup();
            Editor.requestCloseProject();
        }
        ImGui.sameLine();
        if (Dialogs.primaryButton("Save##close-save", true)) {
            ImGui.closeCurrentPopup();
            closeAfterSave = true;
            document.save();
        }
        Dialogs.end();
    }

    public EditorShell() {
        addCommands();
        document.spawnPoint(viewport::spawnPoint);
    }

    private void addCommands() {
        commands.add(new EditorCommand("save", "File", "Save Scene", Shortcut.ctrl(ImGuiKey.S, "S"), EditMode::allowed, document::save));
        commands.add(new EditorCommand("close-project", "File", "Close Project", null, () -> true, this::closeProject));
        commands.add(new EditorCommand("undo", "Edit", "Undo", Shortcut.ctrl(ImGuiKey.Z, "Z"), this::canUndo, this::undo));
        commands.add(new EditorCommand("redo", "Edit", "Redo", Shortcut.ctrl(ImGuiKey.Y, "Y"), this::canRedo, this::redo));
        commands.add(new EditorCommand("redo-shift", "Edit", "Redo", Shortcut.ctrlShift(ImGuiKey.Z, "Z"), this::canRedo, this::redo).hidden());
        commands.add(new EditorCommand("copy", "Edit", "Copy", Shortcut.ctrl(ImGuiKey.C, "C"),
                this::hasSelection, () -> ExplorerPanel.copy(document)));
        commands.add(new EditorCommand("paste", "Edit", "Paste", Shortcut.ctrl(ImGuiKey.V, "V"),
                EditMode::allowed, () -> document.pasteText(ImGui.getClipboardText())));
        commands.add(new EditorCommand("duplicate", "Edit", "Duplicate", Shortcut.ctrl(ImGuiKey.D, "D"),
                this::hasSelection, document::duplicateSelected));
        commands.add(new EditorCommand("rename", "Edit", "Rename", Shortcut.key(ImGuiKey.F2, "F2"),
                this::hasPrimary, () -> explorer.beginRename(document.primary().id())));
        commands.add(new EditorCommand("delete", "Edit", "Delete", Shortcut.key(ImGuiKey.Delete, "Del"),
                this::hasSelection, document::deleteSelected));
        commands.add(new EditorCommand("frame", "Edit", "Frame Selection", null, this::hasSelection, viewport::frameSelection));
        commands.add(new EditorCommand("play", "Place", "Play", Shortcut.key(ImGuiKey.F5, "F5"), EditMode::allowed, () -> EditMode.request(false)));
        commands.add(new EditorCommand("reset-layout", "Window", "Reset Layout", null, () -> true, dockLayout::requestDefault));
        commands.add(new EditorCommand("renderer-tools", "Window", "Renderer Tools", null, () -> true, AmneticEditor::toggle));
    }

    private boolean canUndo() {
        return document.history().canUndo();
    }

    private void undo() {
        document.history().undo();
    }

    private boolean canRedo() {
        return document.history().canRedo();
    }

    private void redo() {
        document.history().redo();
    }

    private boolean hasSelection() {
        return document.selection().count() > 0;
    }

    private boolean hasPrimary() {
        return document.primary() != null;
    }

    public void render() {
        if (!EditMode.editing() || !(Minecraft.getInstance().screen instanceof EditorScreen)) return;
        ImGui.getIO().addConfigFlags(ImGuiConfigFlags.DockingEnable);
        ImGui.getIO().setConfigWindowsMoveFromTitleBarOnly(true);
        float fontScale = EditorScaling.begin();
        EditorStyle.apply();
        ImFont body = EditorFonts.body();
        if (body != null) ImGui.pushFont(body, EditorScale.of(EditorFonts.BODY));
        try {
            ImGuizmo.beginFrame();
            renderMainMenuBar();
            renderHostWindow();
            panels.render();
            if (!viewport.flying()) commands.handleShortcuts();
            renderClosePrompt();
            document.frame(ImGui.isAnyItemActive() || ImGui.isMouseDown(ImGuiMouseButton.Left));
        } catch (RuntimeException e) {
            MoudMod.LOG.error("editor frame failed", e);
        } finally {
            if (body != null) ImGui.popFont();
            EditorScaling.end(fontScale);
        }
    }


    private void renderMainMenuBar() {
        if (!ImGui.beginMainMenuBar()) return;
        renderBrandMark();
        commands.renderMenus();
        Toolbars.pushFlatButtons();
        renderPlayControls();
        Toolbars.popFlatButtons();
        ImGui.endMainMenuBar();
    }

    private void renderBrandMark() {
        float size = ImGui.getFontSize();
        float lineY = ImGui.getCursorPosY();
        ImGui.dummy(EditorScale.of(BRAND_MARGIN), 0.0f);
        ImGui.sameLine();
        ImGui.setCursorPosY(lineY + (ImGui.getFrameHeight() - size) * 0.5f);
        ImGui.image(icons.logoTextureId(), size * LOGO_ASPECT, size);
        ImGui.sameLine();
        ImGui.setCursorPosY(lineY);
        ImGui.dummy(EditorScale.of(BRAND_MARGIN), 0.0f);
        ImGui.sameLine();
    }

    private void renderPlayControls() {
        Toolbars.groupSeparator();
        float leftEdge = ImGui.getCursorPosX();
        float centered = (ImGui.getWindowWidth() - playGroupWidth()) * 0.5f;
        ImGui.sameLine(Math.max(centered, leftEdge));
        ImGui.beginDisabled(!EditMode.allowed());
        if (icons.iconButton("toolbar-play", EditorIcon.PLAY, EditorStyle.iconSizeToolbar())) EditMode.request(false);
        ImGui.endDisabled();
        tooltip("Play (F5)");
        ImGui.sameLine();
        ImGui.beginDisabled(true);
        icons.iconButton("toolbar-pause", EditorIcon.PAUSE, EditorStyle.iconSizeToolbar());
        ImGui.sameLine();
        icons.iconButton("toolbar-step", EditorIcon.REDO, EditorStyle.iconSizeToolbar());
        ImGui.sameLine();
        icons.iconButton("toolbar-stop", EditorIcon.STOP, EditorStyle.iconSizeToolbar());
        ImGui.endDisabled();
    }

    private static float playGroupWidth() {
        return iconButtonWidth() * PLAY_BUTTON_COUNT + ImGui.getStyle().getItemSpacingX() * (PLAY_BUTTON_COUNT - 1);
    }

    private static float iconButtonWidth() {
        return EditorStyle.iconSizeToolbar() + ImGui.getStyle().getFramePaddingX() * 2.0f;
    }

    private static void tooltip(String text) {
        if (ImGui.isItemHovered()) ImGui.setTooltip(text);
    }

    private void renderHostWindow() {
        ImGuiViewport viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getWorkPosX(), viewport.getWorkPosY(), ImGuiCond.Always);
        ImGui.setNextWindowSize(viewport.getWorkSizeX(), viewport.getWorkSizeY(), ImGuiCond.Always);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0.0f, 0.0f);
        ImGui.begin("##editor-host", HOST_WINDOW_FLAGS);
        ImGui.popStyleVar(2);
        dockLayout.buildIfRequested(viewport);
        ImGui.dockSpace(dockLayout.dockspaceId(), 0.0f, -EditorScale.of(STATUS_BAR_HEIGHT), ImGuiDockNodeFlags.PassthruCentralNode);
        renderStatusBar();
        ImGui.end();
    }

    private void renderStatusBar() {
        ImGui.pushStyleColor(ImGuiCol.ChildBg, EditorStyle.COLOR_WINDOW_BACKGROUND);
        ImGui.beginChild("##status-bar", 0.0f, EditorScale.of(STATUS_BAR_HEIGHT), false);
        ImGui.setCursorPosX(EditorStyle.windowPadding());
        ImGui.setCursorPosY(ImGui.getCursorPosY() + EditorStyle.framePaddingY() - 1.0f);
        Texts.muted("Editing, scripts are off");
        ImGui.sameLine(0.0f, EditorScale.of(STATUS_GAP));
        if (document.dirty()) Texts.colored(EditorStyle.COLOR_WARNING, "Unsaved  " + SceneLink.file());
        else Texts.muted(SceneLink.file());
        String message = SceneLink.message();
        if (!message.isEmpty()) {
            ImGui.sameLine(0.0f, EditorScale.of(STATUS_GAP));
            Texts.colored(message.startsWith("saved") ? EditorStyle.COLOR_SUCCESS : EditorStyle.COLOR_DANGER, message);
        }
        ImGui.sameLine(0.0f, EditorScale.of(STATUS_GAP));
        Instance world = document.world();
        Texts.muted((world == null ? 0 : count(world) - 1) + " instances");
        String fps = String.format(Locale.ROOT, "%.0f FPS", ImGui.getIO().getFramerate());
        ImGui.sameLine(ImGui.getWindowWidth() - ImGui.calcTextSizeX(fps) - EditorStyle.windowPadding());
        Texts.colored(EditorStyle.COLOR_ACCENT, fps);
        ImGui.endChild();
        ImGui.popStyleColor();
    }

    private static int count(Instance instance) {
        int total = instance.id() < 0 ? 0 : 1;
        for (Instance child : instance.children()) total += count(child);
        return total;
    }
}
