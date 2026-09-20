package com.meekdev.moud.mod.client.editor.project;

import com.meekdev.moud.mod.client.editor.files.FileBrowser;
import com.meekdev.moud.mod.client.editor.notify.Notifier;
import com.meekdev.moud.mod.client.editor.style.EditorFonts;
import com.meekdev.moud.mod.client.editor.style.EditorMotion;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

public final class NewProjectDialog {

    private static final String POPUP_ID = "##new-project";
    private static final String INVALID_NAME_CHARS = "/\\:*?\"<>|";
    private static final float WIDTH = 600.0f;
    private static final float PADDING = 28.0f;
    private static final float ROW_HEIGHT = 96.0f;
    private static final float ROW_MARK = 38.0f;
    private static final float BUTTON_WIDTH = 124.0f;
    private static final float CORNER = 6.0f;
    private static final float CORNER_MARK = 10.0f;
    private static final float CORNER_BOX = 12.0f;
    private static final int NAME_CAPACITY = 128;
    private static final int PATH_CAPACITY = 512;

    private record TemplateCard(ProjectStore.Template template, HubIcon mark, String title, String description) {}

    private static final TemplateCard[] TEMPLATES = {
            new TemplateCard(ProjectStore.Template.BASEPLATE, HubIcon.CUBE, "Baseplate", "A 128 m floor to stand on, ready to build"),
            new TemplateCard(ProjectStore.Template.MENU, HubIcon.LAYOUT, "Main menu", "A menu over a flying camera, Play spawns you, Escape pauses"),
            new TemplateCard(ProjectStore.Template.EMPTY, HubIcon.RECTANGLE_DASHED, "Empty", "Nothing in the scene, just the scripts")};

    private final FileBrowser browser;
    private final ProjectStore store;
    private final Notifier notifier;
    private final IconWidgets icons;
    private final Consumer<Project> onCreated;
    private final ImString nameInput = new ImString(NAME_CAPACITY);
    private final ImString parentInput = new ImString(ProjectStore.defaultProjectsFolder().toString(), PATH_CAPACITY);
    private ProjectStore.Template template = ProjectStore.Template.BASEPLATE;
    private boolean openRequested;
    private boolean focusName;

    public NewProjectDialog(ProjectStore store, Notifier notifier, IconWidgets icons, Consumer<Project> onCreated) {
        this.browser = new FileBrowser(icons);
        this.store = store;
        this.notifier = notifier;
        this.icons = icons;
        this.onCreated = onCreated;
    }

    public void open() {
        nameInput.set("");
        template = ProjectStore.Template.BASEPLATE;
        openRequested = true;
        focusName = true;
    }

    public void render() {
        if (openRequested) {
            ImGui.openPopup(POPUP_ID);
            openRequested = false;
        }
        ImGuiViewport viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getCenterX(), viewport.getCenterY(), ImGuiCond.Appearing, 0.5f, 0.5f);
        ImGui.setNextWindowSize(EditorScale.of(WIDTH), 0.0f, ImGuiCond.Always);
        float padding = EditorScale.of(PADDING);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, padding, padding);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, EditorScale.of(CORNER_BOX));
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, HubStyle.hairline());
        ImGui.pushStyleColor(ImGuiCol.PopupBg, HubStyle.SURFACE);
        ImGui.pushStyleColor(ImGuiCol.Border, HubStyle.LINE_STRONG);
        boolean open = ImGui.beginPopupModal(POPUP_ID, ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.AlwaysAutoResize);
        ImGui.popStyleColor(2);
        ImGui.popStyleVar(3);
        if (!open) return;
        renderContents();
        HubStyle.asEditor(browser::render);
        ImGui.endPopup();
    }

    private void renderContents() {
        float width = ImGui.getContentRegionAvailX();
        HubStyle.write(HubStyle.HEADING, true, HubStyle.TEXT_BRIGHT, "New project");
        ImGui.dummy(0.0f, EditorScale.of(8.0f));
        HubStyle.write(HubStyle.SMALL, false, HubStyle.TEXT_MUTED, "Pick a name and a starting point. You can change everything later.");
        ImGui.dummy(0.0f, EditorScale.of(22.0f));
        HubStyle.label("Name");
        ImGui.setNextItemWidth(width);
        if (focusName) {
            ImGui.setKeyboardFocusHere();
            focusName = false;
        }
        field(() -> ImGui.inputTextWithHint("##new-project-name", "My place", nameInput));
        ImGui.dummy(0.0f, EditorScale.of(14.0f));
        HubStyle.label("Location");
        float browseWidth = EditorScale.of(96.0f);
        float gap = EditorScale.of(8.0f);
        ImGui.setNextItemWidth(width - browseWidth - gap);
        field(() -> ImGui.inputText("##new-project-parent", parentInput));
        ImGui.sameLine(0.0f, gap);
        if (HubStyle.action("new-project-browse", "Browse", browseWidth, false, true)) browseParent();
        ImGui.dummy(0.0f, EditorScale.of(18.0f));
        HubStyle.label("Start from");
        renderTemplates(width);
        ImGui.dummy(0.0f, EditorScale.of(16.0f));
        Optional<String> error = validationError();
        HubStyle.write(HubStyle.SMALL, false, error.isPresent() ? HubStyle.TEXT_MAIN : HubStyle.TEXT_LIGHT,
                error.orElseGet(() -> "Creates " + previewPath()));
        ImGui.dummy(0.0f, EditorScale.of(18.0f));
        float buttons = EditorScale.of(BUTTON_WIDTH) * 2.0f + EditorScale.of(8.0f);
        ImGui.setCursorPosX(ImGui.getCursorPosX() + width - buttons);
        if (HubStyle.action("new-project-cancel", "Cancel", EditorScale.of(BUTTON_WIDTH), false, true)) ImGui.closeCurrentPopup();
        ImGui.sameLine(0.0f, EditorScale.of(8.0f));
        if (HubStyle.action("new-project-create", "Create", EditorScale.of(BUTTON_WIDTH), true, error.isEmpty())) attemptCreate();
    }

    private void renderTemplates(float width) {
        float gap = EditorScale.of(10.0f);
        float cardWidth = (width - gap) * 0.5f;
        float height = EditorScale.of(ROW_HEIGHT);
        for (int index = 0; index < TEMPLATES.length; index++) {
            TemplateCard card = TEMPLATES[index];
            if (index % 2 != 0) ImGui.sameLine(0.0f, gap);
            String id = "new-project-template-" + index;
            float x = ImGui.getCursorScreenPosX();
            float y = ImGui.getCursorScreenPosY();
            if (ImGui.invisibleButton("##" + id, cardWidth, height)) template = card.template();
            boolean chosen = template == card.template();
            float emphasis = EditorMotion.towards(id, ImGui.isItemHovered());
            ImDrawList draw = ImGui.getWindowDrawList();
            float rounding = EditorScale.of(CORNER);
            draw.addRectFilled(x, y, x + cardWidth, y + height,
                    EditorMotion.blend(HubStyle.BACKGROUND, HubStyle.SURFACE_HOVER, chosen ? 1.0f : emphasis * 0.6f), rounding);
            draw.addRect(x, y, x + cardWidth, y + height, chosen ? HubStyle.ACCENT : HubStyle.LINE_STRONG, rounding, 0,
                    chosen ? EditorScale.ofAtLeastOne(1.5f) : HubStyle.hairline());
            float mark = EditorScale.of(ROW_MARK);
            float markX = x + EditorScale.of(14.0f);
            float markY = y + EditorScale.of(14.0f);
            draw.addRectFilled(markX, markY, markX + mark, markY + mark,
                    chosen ? HubStyle.SURFACE_ACTIVE : HubStyle.SURFACE, EditorScale.of(CORNER_MARK));
            float glyph = mark * 0.52f;
            draw.addImage(icons.imageId(card.mark().resourcePath()), markX + (mark - glyph) * 0.5f, markY + (mark - glyph) * 0.5f,
                    markX + (mark + glyph) * 0.5f, markY + (mark + glyph) * 0.5f, 0.0f, 0.0f, 1.0f, 1.0f,
                    chosen ? HubStyle.TEXT_MAIN : HubStyle.TEXT_MUTED);
            float textX = markX + mark + EditorScale.of(14.0f);
            float right = x + cardWidth - EditorScale.of(12.0f);
            HubStyle.paint(draw, HubStyle.TILE_TITLE, true, textX, markY + EditorScale.of(1.0f),
                    chosen ? HubStyle.TEXT_BRIGHT : HubStyle.TEXT_MAIN, card.title());
            ImFont body = EditorFonts.page(HubStyle.SMALL, false);
            if (body != null) {
                draw.addText(body, EditorScale.ofInteger(EditorFonts.pageSize(HubStyle.SMALL, false)), textX, markY + EditorScale.of(HubStyle.TILE_TITLE + 8.0f),
                        HubStyle.TEXT_MUTED, card.description(), right - textX, textX, y, right, y + height);
            }
            if (ImGui.isItemHovered()) ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }
    }


    private static void field(Runnable body) {
        ImGui.pushStyleColor(ImGuiCol.FrameBg, HubStyle.BACKGROUND);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, HubStyle.BACKGROUND);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, HubStyle.BACKGROUND);
        ImGui.pushStyleColor(ImGuiCol.Border, HubStyle.LINE_STRONG);
        ImGui.pushStyleColor(ImGuiCol.Text, HubStyle.TEXT_MAIN);
        ImFont font = EditorFonts.page(HubStyle.BODY, false);
        if (font != null) ImGui.pushFont(font, EditorScale.of(HubStyle.BODY));
        body.run();
        if (font != null) ImGui.popFont();
        ImGui.popStyleColor(5);
    }





    private void browseParent() {
        Path start = currentParent().filter(Files::isDirectory).orElse(Path.of(System.getProperty("user.home")));
        browser.chooseFolder("Choose where the project goes", start, path -> parentInput.set(path.toString()));
    }

    private void attemptCreate() {
        try {
            Path parent = currentParent().orElseThrow(() -> new IOException("Invalid parent folder"));
            Files.createDirectories(parent);
            String name = nameInput.get().trim();
            Project project = store.createProject(name, parent.resolve(name), template);
            store.recordOpened(project);
            ImGui.closeCurrentPopup();
            onCreated.accept(project);
        } catch (IOException e) {
            notifier.show("Creation failed: " + e.getMessage());
        }
    }

    private String previewPath() {
        String name = nameInput.get().trim();
        return currentParent().filter(parent -> !name.isEmpty()).map(parent -> parent.resolve(name).toString()).orElse("-");
    }

    private Optional<Path> currentParent() {
        String raw = parentInput.get().trim();
        if (raw.isEmpty()) return Optional.empty();
        try {
            return Optional.of(Path.of(raw));
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    private Optional<String> validationError() {
        String name = nameInput.get().trim();
        if (name.isEmpty()) return Optional.of("Give the project a name.");
        if (name.chars().anyMatch(c -> INVALID_NAME_CHARS.indexOf(c) >= 0)) return Optional.of("A name can not contain / \\ : * ? \" < > |");
        Optional<Path> parent = currentParent();
        if (parent.isEmpty()) return Optional.of("Choose where the project goes.");
        if (!Files.isDirectory(parent.get()) && !parent.get().equals(ProjectStore.defaultProjectsFolder())) return Optional.of("That folder does not exist.");
        if (Files.exists(parent.get().resolve(name))) return Optional.of("A folder with this name is already there.");
        return Optional.empty();
    }
}
