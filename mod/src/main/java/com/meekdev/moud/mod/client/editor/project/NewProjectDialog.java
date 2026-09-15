package com.meekdev.moud.mod.client.editor.project;

import com.meekdev.moud.mod.client.editor.files.FileBrowser;
import com.meekdev.moud.mod.client.editor.notify.Notifier;
import com.meekdev.moud.mod.client.editor.style.EditorFonts;
import com.meekdev.moud.mod.client.editor.style.EditorMotion;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
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
    private static final float WIDTH = 560.0f;
    private static final float PADDING = 24.0f;
    private static final float TITLE_SIZE = 20.0f;
    private static final float TEMPLATE_HEIGHT = 96.0f;
    private static final float BUTTON_WIDTH = 120.0f;
    private static final float BUTTON_HEIGHT = 34.0f;
    private static final float CORNER = 2.0f;
    private static final int NAME_CAPACITY = 128;
    private static final int PATH_CAPACITY = 512;

    private record TemplateCard(ProjectStore.Template template, String title, String description) {}

    private static final TemplateCard[] TEMPLATES = {
            new TemplateCard(ProjectStore.Template.BASEPLATE, "Baseplate", "A 128 m floor to stand on, ready to build"),
            new TemplateCard(ProjectStore.Template.EMPTY, "Empty", "Nothing in the scene, just the scripts")};

    private final FileBrowser browser;
    private final ProjectStore store;
    private final Notifier notifier;
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
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, EditorScale.of(CORNER));
        ImGui.pushStyleColor(ImGuiCol.PopupBg, EditorStyle.COLOR_ELEVATED_BACKGROUND);
        boolean open = ImGui.beginPopupModal(POPUP_ID, ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.AlwaysAutoResize);
        ImGui.popStyleColor();
        ImGui.popStyleVar(2);
        if (!open) return;
        renderContents();
        browser.render();
        ImGui.endPopup();
    }

    private void renderContents() {
        float width = ImGui.getContentRegionAvailX();
        font(EditorFonts.title(), TITLE_SIZE, () -> ImGui.textUnformatted("New project"));
        muted("Pick a name and a starting point. You can change everything later.");
        ImGui.dummy(0.0f, EditorScale.of(16.0f));
        caption("NAME");
        ImGui.setNextItemWidth(width);
        if (focusName) {
            ImGui.setKeyboardFocusHere();
            focusName = false;
        }
        ImGui.inputTextWithHint("##new-project-name", "My place", nameInput);
        ImGui.dummy(0.0f, EditorScale.of(10.0f));
        caption("LOCATION");
        float browseWidth = EditorScale.of(80.0f);
        ImGui.setNextItemWidth(width - browseWidth - EditorStyle.itemSpacingX());
        ImGui.inputText("##new-project-parent", parentInput);
        ImGui.sameLine();
        if (ImGui.button("Browse##new-project-browse", browseWidth, 0.0f)) browseParent();
        ImGui.dummy(0.0f, EditorScale.of(14.0f));
        caption("START FROM");
        renderTemplates(width);
        ImGui.dummy(0.0f, EditorScale.of(14.0f));
        Optional<String> error = validationError();
        if (error.isPresent()) {
            ImGui.pushStyleColor(ImGuiCol.Text, EditorStyle.COLOR_WARNING);
            ImGui.textUnformatted(error.get());
            ImGui.popStyleColor();
        } else {
            muted("Creates " + previewPath());
        }
        ImGui.dummy(0.0f, EditorScale.of(14.0f));
        float buttons = EditorScale.of(BUTTON_WIDTH) * 2.0f + EditorStyle.itemSpacingX();
        ImGui.setCursorPosX(ImGui.getCursorPosX() + width - buttons);
        if (action("new-project-cancel", "Cancel", false, true)) ImGui.closeCurrentPopup();
        ImGui.sameLine();
        if (action("new-project-create", "Create", true, error.isEmpty()) && error.isEmpty()) attemptCreate();
    }

    private void renderTemplates(float width) {
        float gap = EditorScale.of(12.0f);
        float cardWidth = (width - gap) * 0.5f;
        float height = EditorScale.of(TEMPLATE_HEIGHT);
        for (int index = 0; index < TEMPLATES.length; index++) {
            TemplateCard card = TEMPLATES[index];
            if (index > 0) ImGui.sameLine(0.0f, gap);
            float x = ImGui.getCursorScreenPosX();
            float y = ImGui.getCursorScreenPosY();
            String id = "new-project-template-" + index;
            if (ImGui.invisibleButton("##" + id, cardWidth, height)) template = card.template();
            boolean chosen = template == card.template();
            float emphasis = (ImGui.isItemHovered() || chosen) ? 1.0f : 0.0f;
            if (ImGui.isItemHovered()) ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
            ImDrawList draw = ImGui.getWindowDrawList();
            float rounding = EditorScale.of(CORNER);
            draw.addRectFilled(x, y, x + cardWidth, y + height, EditorMotion.blend(EditorStyle.COLOR_SUNKEN_BACKGROUND, EditorStyle.COLOR_WIDGET_BACKGROUND, emphasis * 0.6f), rounding);
            draw.addRect(x, y, x + cardWidth, y + height, chosen ? EditorStyle.COLOR_ACCENT : EditorStyle.withAlpha(EditorStyle.COLOR_TEXT, 0.08f), rounding, 0, chosen ? 1.5f : 1.0f);
            drawPreview(draw, card.template(), x + EditorScale.of(14.0f), y + EditorScale.of(14.0f), height - EditorScale.of(28.0f), chosen);
            float textX = x + height;
            float textY = y + EditorScale.of(22.0f);
            draw.addText(textX, textY, EditorStyle.COLOR_TEXT_FOCUS, card.title());
            float right = x + cardWidth - EditorScale.of(10.0f);
            draw.addText(ImGui.getFont(), (int) ImGui.getFontSize(), textX, textY + ImGui.getTextLineHeight() + EditorScale.of(6.0f),
                    EditorStyle.COLOR_TEXT_MUTED, card.description(), right - textX, textX, y, right, y + height);
        }
    }

    private static void drawPreview(ImDrawList draw, ProjectStore.Template template, float x, float y, float size, boolean chosen) {
        draw.addRectFilled(x, y, x + size, y + size, EditorStyle.COLOR_WINDOW_BACKGROUND, EditorScale.of(CORNER));
        float cx = x + size * 0.5f;
        float cy = y + size * 0.58f;
        float w = size * 0.4f;
        float h = size * 0.2f;
        int color = chosen ? EditorStyle.COLOR_TEXT_FOCUS : EditorStyle.COLOR_TEXT_MUTED;
        if (template == ProjectStore.Template.BASEPLATE) {
            draw.addQuadFilled(cx, cy - h, cx + w, cy, cx, cy + h, cx - w, cy, EditorStyle.withAlpha(color, 0.55f));
            draw.addQuad(cx, cy - h, cx + w, cy, cx, cy + h, cx - w, cy, color, 1.0f);
        } else {
            float dash = EditorScale.of(4.0f);
            float[][] corners = {{cx, cy - h}, {cx + w, cy}, {cx, cy + h}, {cx - w, cy}};
            for (int n = 0; n < 4; n++) {
                float[] a = corners[n];
                float[] b = corners[(n + 1) % 4];
                float length = (float) Math.hypot(b[0] - a[0], b[1] - a[1]);
                for (float t = 0.0f; t < length; t += dash * 2.0f) {
                    float t0 = t / length;
                    float t1 = Math.min(1.0f, (t + dash) / length);
                    draw.addLine(a[0] + (b[0] - a[0]) * t0, a[1] + (b[1] - a[1]) * t0, a[0] + (b[0] - a[0]) * t1, a[1] + (b[1] - a[1]) * t1,
                            EditorStyle.withAlpha(color, 0.7f));
                }
            }
        }
    }

    private static boolean action(String id, String label, boolean primary, boolean enabled) {
        float width = EditorScale.of(BUTTON_WIDTH);
        float height = EditorScale.of(BUTTON_HEIGHT);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton("##" + id, width, height);
        float emphasis = (enabled && ImGui.isItemHovered()) ? 1.0f : 0.0f;
        ImDrawList draw = ImGui.getWindowDrawList();
        int fill = primary
                ? (enabled ? EditorStyle.lighten(EditorStyle.COLOR_ACCENT, emphasis * 0.12f) : EditorStyle.withAlpha(EditorStyle.COLOR_ACCENT, 0.35f))
                : EditorMotion.blend(EditorStyle.COLOR_WIDGET_BACKGROUND, EditorStyle.COLOR_WIDGET_HOVER, emphasis);
        draw.addRectFilled(x, y, x + width, y + height, fill, EditorScale.of(CORNER));
        int text = primary ? EditorStyle.COLOR_TEXT_ON_ACCENT : EditorStyle.COLOR_TEXT;
        draw.addText(x + (width - ImGui.calcTextSizeX(label)) * 0.5f, y + (height - ImGui.getTextLineHeight()) * 0.5f, text, label);
        if (enabled && ImGui.isItemHovered()) ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        return clicked && enabled;
    }

    private static void caption(String text) {
        font(EditorFonts.body(), EditorFonts.SMALL, () -> {
            ImGui.pushStyleColor(ImGuiCol.Text, EditorStyle.COLOR_TEXT_FAINT);
            ImGui.textUnformatted(text);
            ImGui.popStyleColor();
        });
    }

    private static void muted(String text) {
        ImGui.pushStyleColor(ImGuiCol.Text, EditorStyle.COLOR_TEXT_MUTED);
        ImGui.textUnformatted(text);
        ImGui.popStyleColor();
    }

    private static void font(ImFont font, float size, Runnable body) {
        if (font == null) {
            body.run();
            return;
        }
        ImGui.pushFont(font, EditorScale.of(size));
        body.run();
        ImGui.popFont();
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
