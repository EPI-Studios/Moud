package com.meekdev.moud.mod.client.editor.project;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meekdev.moud.mod.client.editor.files.FileBrowser;
import com.meekdev.moud.mod.client.editor.files.FileManagerReveal;
import com.meekdev.moud.mod.client.editor.kit.FuzzyScore;
import com.meekdev.moud.mod.client.editor.kit.SearchField;
import com.meekdev.moud.mod.client.editor.kit.SegmentedControl;
import com.meekdev.moud.mod.client.editor.notify.Notifier;
import com.meekdev.moud.mod.client.editor.style.EditorData;
import com.meekdev.moud.mod.client.editor.style.EditorFonts;
import com.meekdev.moud.mod.client.editor.style.EditorIcon;
import com.meekdev.moud.mod.client.editor.style.EditorMotion;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImDrawFlags;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public final class ProjectHubView {

    private static final int HOST_FLAGS = ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoDocking
            | ImGuiWindowFlags.NoBringToFrontOnFocus | ImGuiWindowFlags.NoSavedSettings;
    private static final DateTimeFormatter ABSOLUTE_DATE = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final float SIDEBAR_WIDTH = 248.0f;
    private static final float SIDEBAR_PADDING = 20.0f;
    private static final float MAIN_PADDING_X = 36.0f;
    private static final float MAIN_PADDING_Y = 28.0f;
    private static final float LOGO_SIZE = 30.0f;
    private static final float PRIMARY_HEIGHT = 38.0f;
    private static final float NAV_ROW_HEIGHT = 32.0f;
    private static final float HERO_HEIGHT = 156.0f;
    private static final float HERO_COVER_WIDTH = 260.0f;
    private static final float CARD_MIN_WIDTH = 230.0f;
    private static final float CARD_GAP = 18.0f;
    private static final float CARD_COVER_RATIO = 0.56f;
    private static final float CARD_TEXT_HEIGHT = 58.0f;
    private static final float CARD_ROUNDING = 8.0f;
    private static final float COVER_INITIALS_SIZE = 34.0f;
    private static final float HERO_TITLE_SIZE = 24.0f;
    private static final float PAGE_TITLE_SIZE = 22.0f;
    private static final float SECTION_GAP = 28.0f;
    private static final int SEARCH_CAPACITY = 128;
    private static final int NAV_ALL = 0;
    private static final int NAV_PINNED = 1;
    private static final int SORT_RECENT = 0;
    private static final int SORT_NAME = 1;
    private static final String COVERS_TABLE = "/assets/moud/editor/project-covers.json";
    private static final float COVER_STRIPE_SPACING = 18.0f;
    private static final float COVER_STRIPE_SLOPE = 0.577f;
    private static final float CUBE_HALF_WIDTH = 0.866f;
    private static final List<Cover> COVERS = loadCovers();

    private record Cover(int light, int dark) {}

    private record Entry(Project project, boolean pinned) {

        String path() {
            return project.rootDirectory().toAbsolutePath().toString();
        }
    }

    private final ProjectStore store;
    private final Notifier notifier;
    private final IconWidgets icons;
    private final Consumer<Project> onOpened;
    private final FileBrowser browser;
    private final NewProjectDialog newProject;
    private final ProjectIcons projectIcons = new ProjectIcons();
    private final ImString search = new ImString("", SEARCH_CAPACITY);
    private List<Entry> entries = List.of();
    private int nav = NAV_ALL;
    private int sort = SORT_RECENT;
    private String selected = "";
    private boolean focusSearch;

    public ProjectHubView(ProjectStore store, Notifier notifier, IconWidgets icons, Consumer<Project> onOpened) {
        this.store = store;
        this.notifier = notifier;
        this.icons = icons;
        this.onOpened = onOpened;
        this.browser = new FileBrowser(icons);
        this.newProject = new NewProjectDialog(store, notifier, icons, onOpened);
        reload();
    }

    public void dispose() {
        projectIcons.dispose();
    }

    private void reload() {
        Set<String> pinned = store.loadPinnedPaths();
        List<Entry> loaded = new ArrayList<>();
        for (Project project : store.loadRecents()) {
            loaded.add(new Entry(project, pinned.contains(project.rootDirectory().toAbsolutePath().toString())));
        }
        entries = List.copyOf(loaded);
    }

    public void render() {
        ImGuiViewport viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getWorkPosX(), viewport.getWorkPosY(), ImGuiCond.Always);
        ImGui.setNextWindowSize(viewport.getWorkSizeX(), viewport.getWorkSizeY(), ImGuiCond.Always);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0.0f, 0.0f);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, EditorStyle.COLOR_PANEL_BACKGROUND);
        boolean open = ImGui.begin("Moud###project-hub", HOST_FLAGS);
        ImGui.popStyleColor();
        ImGui.popStyleVar();
        if (open) {
            renderSidebar();
            ImGui.sameLine(0.0f, 0.0f);
            renderMain();
        }
        newProject.render();
        browser.render();
        ImGui.end();
    }

    private void renderSidebar() {
        float width = EditorScale.of(SIDEBAR_WIDTH);
        float padding = EditorScale.of(SIDEBAR_PADDING);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, EditorStyle.COLOR_WINDOW_BACKGROUND);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, padding, padding);
        ImGui.beginChild("##hub-sidebar", width, 0.0f, ImGuiChildFlags.AlwaysUseWindowPadding, ImGuiWindowFlags.NoScrollbar);
        ImGui.popStyleVar();
        renderBrand();
        ImGui.dummy(0.0f, EditorScale.of(SECTION_GAP));
        float inner = ImGui.getContentRegionAvailX();
        if (primaryButton("hub-new", "New project", inner)) newProject.open();
        ImGui.dummy(0.0f, EditorScale.of(6.0f));
        if (secondaryButton("hub-open", "Open folder", inner)) pickFolder();
        ImGui.dummy(0.0f, EditorScale.of(SECTION_GAP));
        sidebarCaption("LIBRARY");
        navRow("hub-nav-all", EditorIcon.PACKED_SCENE, "All projects", entries.size(), NAV_ALL, inner);
        navRow("hub-nav-pinned", EditorIcon.LOCK, "Pinned", (int) entries.stream().filter(Entry::pinned).count(), NAV_PINNED, inner);
        renderSidebarFooter(inner);
        ImGui.endChild();
        ImGui.popStyleColor();
        ImDrawList draw = ImGui.getWindowDrawList();
        float x = ImGui.getItemRectMaxX();
        draw.addLine(x, ImGui.getItemRectMinY(), x, ImGui.getItemRectMaxY(), EditorStyle.COLOR_OUTLINE);
    }

    private void renderBrand() {
        float size = EditorScale.of(LOGO_SIZE);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        ImDrawList draw = ImGui.getWindowDrawList();
        drawLogo(draw, x, y, size);
        ImGui.dummy(size, size);
        ImGui.sameLine(0.0f, EditorScale.of(12.0f));
        ImGui.beginGroup();
        withFont(EditorFonts.title(), EditorFonts.TITLE, () -> ImGui.textUnformatted("Moud"));
        withFont(EditorFonts.body(), EditorFonts.SMALL, () -> text(EditorStyle.COLOR_TEXT_MUTED, "Studio"));
        ImGui.endGroup();
    }

    private static void drawLogo(ImDrawList draw, float x, float y, float size) {
        float half = size * 0.5f;
        float cx = x + half;
        float cy = y + half;
        float r = size * 0.46f;
        int top = EditorStyle.COLOR_HIGHLIGHT;
        int left = EditorStyle.darken(EditorStyle.COLOR_HIGHLIGHT, 0.28f);
        int right = EditorStyle.darken(EditorStyle.COLOR_HIGHLIGHT, 0.5f);
        float dx = r * CUBE_HALF_WIDTH;
        float dy = r * 0.5f;
        draw.addQuadFilled(cx, cy - r, cx + dx, cy - dy, cx, cy, cx - dx, cy - dy, top);
        draw.addQuadFilled(cx - dx, cy - dy, cx, cy, cx, cy + r, cx - dx, cy + dy, left);
        draw.addQuadFilled(cx, cy, cx + dx, cy - dy, cx + dx, cy + dy, cx, cy + r, right);
    }

    private static void sidebarCaption(String caption) {
        withFont(EditorFonts.body(), EditorFonts.SMALL, () -> text(EditorStyle.COLOR_TEXT_FAINT, caption));
        ImGui.dummy(0.0f, EditorScale.of(4.0f));
    }

    private void navRow(String id, EditorIcon icon, String label, int count, int value, float width) {
        float height = EditorScale.of(NAV_ROW_HEIGHT);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        if (ImGui.invisibleButton("##" + id, width, height)) nav = value;
        boolean active = nav == value;
        float hover = EditorMotion.towards(id, ImGui.isItemHovered() || active);
        ImDrawList draw = ImGui.getWindowDrawList();
        int fill = active ? EditorStyle.withAlpha(EditorStyle.COLOR_ACCENT, 0.12f) : EditorStyle.withAlpha(EditorStyle.COLOR_WIDGET_HOVER, hover * 0.5f);
        draw.addRectFilled(x, y, x + width, y + height, fill, EditorStyle.frameRounding());
        float iconSize = EditorStyle.iconSizeSmall();
        float iconX = x + EditorScale.of(10.0f);
        float midY = y + height * 0.5f;
        draw.addImage(icons.textureId(icon), iconX, midY - iconSize * 0.5f, iconX + iconSize, midY + iconSize * 0.5f);
        float lineY = midY - ImGui.getTextLineHeight() * 0.5f;
        draw.addText(iconX + iconSize + EditorScale.of(10.0f), lineY, active ? EditorStyle.COLOR_TEXT_FOCUS : EditorStyle.COLOR_TEXT, label);
        String number = Integer.toString(count);
        draw.addText(x + width - EditorScale.of(10.0f) - ImGui.calcTextSizeX(number), lineY, EditorStyle.COLOR_TEXT_FAINT, number);
        if (ImGui.isItemHovered()) ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
    }

    private void renderSidebarFooter(float width) {
        float footer = ImGui.getTextLineHeightWithSpacing() * 2.0f + ImGui.getFrameHeight();
        float remaining = ImGui.getContentRegionAvailY() - footer;
        if (remaining > 0.0f) ImGui.dummy(0.0f, remaining);
        sidebarCaption("PROJECTS FOLDER");
        String folder = ProjectStore.defaultProjectsFolder().toString();
        withFont(EditorFonts.body(), EditorFonts.SMALL, () -> {
            text(EditorStyle.COLOR_TEXT_MUTED, ellipsize(folder, width));
            if (ImGui.isItemHovered()) ImGui.setTooltip(folder);
        });
    }

    private void renderMain() {
        float padX = EditorScale.of(MAIN_PADDING_X);
        float padY = EditorScale.of(MAIN_PADDING_Y);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, padX, padY);
        ImGui.beginChild("##hub-main", 0.0f, 0.0f, ImGuiChildFlags.AlwaysUseWindowPadding, 0);
        ImGui.popStyleVar();
        float width = ImGui.getContentRegionAvailX();
        List<Entry> visible = visible();
        renderTopBar(width, visible.size());
        ImGui.dummy(0.0f, EditorScale.of(SECTION_GAP));
        if (entries.isEmpty()) {
            renderWelcome(width);
        } else {
            boolean hero = nav == NAV_ALL && search.get().isBlank() && sort == SORT_RECENT;
            if (hero) {
                Entry latest = entries.stream().max(Comparator.comparingLong((Entry entry) -> entry.project().lastOpenedMillis())).orElseThrow();
                renderHero(latest, width);
                visible = new ArrayList<>(visible);
                visible.remove(latest);
                if (!visible.isEmpty()) {
                    ImGui.dummy(0.0f, EditorScale.of(SECTION_GAP));
                    sectionTitle("Recent");
                    renderGrid(visible, width);
                }
            } else if (visible.isEmpty()) {
                text(EditorStyle.COLOR_TEXT_MUTED, nav == NAV_PINNED && search.get().isBlank()
                        ? "Nothing pinned yet. Right click a project to pin it." : "No project matches \"" + search.get().trim() + "\".");
            } else {
                renderGrid(visible, width);
            }
        }
        handleShortcuts(visible());
        ImGui.endChild();
    }

    private void renderTopBar(float width, int count) {
        float top = ImGui.getCursorPosY();
        withFont(EditorFonts.title(), PAGE_TITLE_SIZE, () -> ImGui.textUnformatted(nav == NAV_PINNED ? "Pinned" : "Projects"));
        ImGui.sameLine(0.0f, EditorScale.of(10.0f));
        ImGui.setCursorPosY(top + EditorScale.of(PAGE_TITLE_SIZE) - ImGui.getTextLineHeight());
        text(EditorStyle.COLOR_TEXT_FAINT, count + (count == 1 ? " project" : " projects"));
        List<String> sorts = List.of("Recent", "Name");
        float sortWidth = SegmentedControl.width(sorts);
        float searchWidth = Math.min(EditorScale.of(280.0f), width * 0.35f);
        float right = width - sortWidth - searchWidth - EditorStyle.itemSpacingX() * 2.0f;
        ImGui.sameLine(Math.max(ImGui.getCursorPosX(), right));
        ImGui.setCursorPosY(top);
        if (focusSearch) {
            ImGui.setKeyboardFocusHere();
            focusSearch = false;
        }
        SearchField.render("##hub-search", "Search projects", search, searchWidth);
        ImGui.sameLine();
        ImGui.setCursorPosY(top + (ImGui.getFrameHeight() - SegmentedControl.height()) * 0.5f);
        sort = SegmentedControl.render("##hub-sort", sorts, sort);
    }

    private static void sectionTitle(String title) {
        withFont(EditorFonts.body(), EditorFonts.SMALL, () -> text(EditorStyle.COLOR_TEXT_FAINT, title.toUpperCase(Locale.ROOT)));
        ImGui.dummy(0.0f, EditorScale.of(8.0f));
    }

    private void renderHero(Entry entry, float width) {
        float height = EditorScale.of(HERO_HEIGHT);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton("##hub-hero", width, height);
        boolean hovered = ImGui.isItemHovered();
        float emphasis = EditorMotion.towards("hub-hero", hovered);
        renderMenu(entry, "##hub-hero-menu");
        ImDrawList draw = ImGui.getWindowDrawList();
        float rounding = EditorScale.of(CARD_ROUNDING);
        draw.addRectFilled(x, y, x + width, y + height, EditorStyle.COLOR_ELEVATED_BACKGROUND, rounding);
        float coverWidth = Math.min(EditorScale.of(HERO_COVER_WIDTH), width * 0.4f);
        drawCover(draw, entry, x, y, x + coverWidth, y + height, rounding, ImDrawFlags.RoundCornersLeft, emphasis);
        draw.addRect(x, y, x + width, y + height, EditorStyle.withAlpha(EditorStyle.COLOR_HIGHLIGHT, 0.25f + emphasis * 0.5f), rounding, 0, 1.0f);
        float textX = x + coverWidth + EditorScale.of(28.0f);
        float textY = y + EditorScale.of(24.0f);
        ImFont small = EditorFonts.body();
        ImFont title = EditorFonts.title();
        if (small != null) draw.addText(small, (int) EditorFonts.SMALL, textX, textY, EditorStyle.COLOR_HIGHLIGHT, "CONTINUE WHERE YOU LEFT OFF");
        if (title != null) draw.addText(title, (int) EditorScale.of(HERO_TITLE_SIZE), textX, textY + EditorScale.of(20.0f), EditorStyle.COLOR_TEXT_FOCUS, entry.project().name());
        float detailY = textY + EditorScale.of(20.0f + HERO_TITLE_SIZE + 8.0f);
        String detail = "Opened " + relativeDate(entry.project().lastOpenedMillis()).toLowerCase(Locale.ROOT) + "   ·   " + entry.path();
        draw.pushClipRect(textX, y, x + width - EditorScale.of(20.0f), y + height, true);
        draw.addText(textX, detailY, EditorStyle.COLOR_TEXT_MUTED, detail);
        draw.popClipRect();
        String action = "Open in editor";
        float padX = EditorScale.of(16.0f);
        float buttonWidth = ImGui.calcTextSizeX(action) + padX * 2.0f;
        float buttonHeight = EditorScale.of(32.0f);
        float buttonY = y + height - EditorScale.of(24.0f) - buttonHeight;
        int buttonFill = EditorMotion.blend(EditorStyle.COLOR_WIDGET_BACKGROUND, EditorStyle.COLOR_HIGHLIGHT, emphasis);
        draw.addRectFilled(textX, buttonY, textX + buttonWidth, buttonY + buttonHeight, buttonFill, EditorStyle.frameRounding());
        int buttonText = EditorMotion.blend(EditorStyle.COLOR_TEXT, EditorStyle.COLOR_TEXT_ON_ACCENT, emphasis);
        draw.addText(textX + padX, buttonY + (buttonHeight - ImGui.getTextLineHeight()) * 0.5f, buttonText, action);
        if (hovered) ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        if (clicked) open(entry.project());
    }

    private void renderGrid(List<Entry> visible, float width) {
        float gap = EditorScale.of(CARD_GAP);
        int columns = Math.max(1, (int) ((width + gap) / (EditorScale.of(CARD_MIN_WIDTH) + gap)));
        float cardWidth = (width - gap * (columns - 1)) / columns;
        float coverHeight = cardWidth * CARD_COVER_RATIO;
        float cardHeight = coverHeight + EditorScale.of(CARD_TEXT_HEIGHT);
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        for (int index = 0; index < visible.size(); index++) {
            float x = left + (index % columns) * (cardWidth + gap);
            float y = top + (index / columns) * (cardHeight + gap);
            ImGui.setCursorScreenPos(x, y);
            renderCard(visible.get(index), cardWidth, coverHeight, cardHeight);
        }
        int rows = (visible.size() + columns - 1) / columns;
        ImGui.setCursorScreenPos(left, top + rows * (cardHeight + gap));
        ImGui.dummy(width, 0.0f);
    }

    private void renderCard(Entry entry, float width, float coverHeight, float height) {
        String id = "hub-card-" + entry.path();
        ImGui.pushID(entry.path());
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton("##card", width, height);
        boolean hovered = ImGui.isItemHovered();
        boolean isSelected = entry.path().equals(selected);
        float emphasis = EditorMotion.towards(id, hovered || isSelected);
        renderMenu(entry, "##card-menu");
        ImDrawList draw = ImGui.getWindowDrawList();
        float rounding = EditorScale.of(CARD_ROUNDING);
        float lift = emphasis * EditorScale.of(2.0f);
        float cy = y - lift;
        draw.addRectFilled(x, cy, x + width, cy + height, EditorStyle.COLOR_ELEVATED_BACKGROUND, rounding);
        drawCover(draw, entry, x, cy, x + width, cy + coverHeight, rounding, ImDrawFlags.RoundCornersTop, emphasis);
        int border = isSelected ? EditorStyle.COLOR_HIGHLIGHT : EditorStyle.withAlpha(EditorStyle.COLOR_TEXT, 0.06f + emphasis * 0.18f);
        draw.addRect(x, cy, x + width, cy + height, border, rounding, 0, isSelected ? 1.5f : 1.0f);
        if (entry.pinned()) {
            float r = EditorScale.of(4.5f);
            draw.addCircleFilled(x + width - EditorScale.of(14.0f), cy + EditorScale.of(14.0f), r, EditorStyle.COLOR_HIGHLIGHT);
        }
        float textX = x + EditorScale.of(14.0f);
        float nameY = cy + coverHeight + EditorScale.of(12.0f);
        draw.pushClipRect(textX, cy, x + width - EditorScale.of(12.0f), cy + height, true);
        draw.addText(textX, nameY, EditorStyle.COLOR_TEXT_FOCUS, entry.project().name());
        draw.addText(textX, nameY + ImGui.getTextLineHeight() + EditorScale.of(4.0f), EditorStyle.COLOR_TEXT_FAINT, relativeDate(entry.project().lastOpenedMillis()));
        draw.popClipRect();
        if (hovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
            ImGui.setTooltip(entry.path());
        }
        ImGui.popID();
        if (clicked) {
            selected = entry.path();
            open(entry.project());
        }
    }

    private void drawCover(ImDrawList draw, Entry entry, float x0, float y0, float x1, float y1, float rounding, int corners, float emphasis) {
        Optional<Long> icon = projectIcons.of(entry.project().rootDirectory());
        if (icon.isPresent()) {
            draw.addImageRounded(icon.get(), x0, y0, x1, y1, 0.0f, 0.0f, 1.0f, 1.0f, 0xFFFFFFFF, rounding, corners);
            return;
        }
        Cover cover = COVERS.get(Math.floorMod(entry.project().name().hashCode(), COVERS.size()));
        int light = EditorStyle.lighten(cover.light(), emphasis * 0.08f);
        int dark = cover.dark();
        draw.addRectFilled(x0, y0, x1, y1, dark, rounding, corners);
        draw.pushClipRect(x0, y0, x1, y1, true);
        float w = x1 - x0;
        float h = y1 - y0;
        draw.addRectFilledMultiColor(x0 + rounding * 0.3f, y0, x1, y1 - rounding * 0.3f, light, light, dark, dark);
        int line = EditorStyle.withAlpha(0xFFFFFFFF, 0.05f);
        float step = EditorScale.of(COVER_STRIPE_SPACING);
        for (float offset = -h; offset < w + h; offset += step) {
            draw.addLine(x0 + offset, y1, x0 + offset + h * COVER_STRIPE_SLOPE, y0, line);
            draw.addLine(x0 + offset, y0, x0 + offset + h * COVER_STRIPE_SLOPE, y1, line);
        }
        float cube = Math.min(w, h) * 0.34f;
        drawGhostCube(draw, x0 + w * 0.78f, y0 + h * 0.62f, cube, emphasis);
        draw.popClipRect();
        ImFont title = EditorFonts.title();
        String initials = initials(entry.project().name());
        float size = EditorScale.of(COVER_INITIALS_SIZE);
        if (title != null) {
            draw.addText(title, (int) size, x0 + EditorScale.of(16.0f), y1 - size - EditorScale.of(12.0f), EditorStyle.withAlpha(0xFFFFFFFF, 0.85f), initials);
        }
    }

    private static void drawGhostCube(ImDrawList draw, float cx, float cy, float r, float emphasis) {
        float dx = r * CUBE_HALF_WIDTH;
        float dy = r * 0.5f;
        int alpha = (int) (255 * (0.10f + emphasis * 0.08f));
        int top = (alpha << 24) | 0xFFFFFF;
        int side = ((int) (alpha * 0.6f) << 24) | 0xFFFFFF;
        int shade = ((int) (alpha * 0.3f) << 24) | 0xFFFFFF;
        draw.addQuadFilled(cx, cy - r, cx + dx, cy - dy, cx, cy, cx - dx, cy - dy, top);
        draw.addQuadFilled(cx - dx, cy - dy, cx, cy, cx, cy + r, cx - dx, cy + dy, side);
        draw.addQuadFilled(cx, cy, cx + dx, cy - dy, cx + dx, cy + dy, cx, cy + r, shade);
    }

    private void renderWelcome(float width) {
        float height = EditorScale.of(300.0f);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        ImDrawList draw = ImGui.getWindowDrawList();
        float rounding = EditorScale.of(CARD_ROUNDING);
        draw.addRectFilled(x, y, x + width, y + height, EditorStyle.COLOR_ELEVATED_BACKGROUND, rounding);
        draw.addRect(x, y, x + width, y + height, EditorStyle.withAlpha(EditorStyle.COLOR_TEXT, 0.06f), rounding);
        float cube = EditorScale.of(46.0f);
        drawLogo(draw, x + width * 0.5f - cube * 0.5f, y + EditorScale.of(48.0f), cube);
        ImFont title = EditorFonts.title();
        String heading = "Build your first place";
        float headingSize = EditorScale.of(HERO_TITLE_SIZE);
        if (title != null) {
            float headingWidth = title.calcTextSizeAX(headingSize, Float.MAX_VALUE, 0.0f, heading);
            draw.addText(title, (int) headingSize, x + (width - headingWidth) * 0.5f, y + EditorScale.of(118.0f), EditorStyle.COLOR_TEXT_FOCUS, heading);
        }
        String line = "A project is a folder with a place.toml, its scripts and its scenes. Start from a baseplate or an empty world.";
        draw.addText(x + (width - ImGui.calcTextSizeX(line)) * 0.5f, y + EditorScale.of(158.0f), EditorStyle.COLOR_TEXT_MUTED, line);
        float buttonWidth = EditorScale.of(180.0f);
        ImGui.setCursorScreenPos(x + (width - buttonWidth * 2.0f - EditorStyle.itemSpacingX() * 2.0f) * 0.5f, y + EditorScale.of(200.0f));
        if (primaryButton("hub-welcome-new", "New project", buttonWidth)) newProject.open();
        ImGui.sameLine(0.0f, EditorStyle.itemSpacingX() * 2.0f);
        if (secondaryButton("hub-welcome-open", "Open folder", buttonWidth)) pickFolder();
        ImGui.setCursorScreenPos(x, y + height);
        ImGui.dummy(width, 0.0f);
    }

    private void renderMenu(Entry entry, String id) {
        if (!ImGui.beginPopupContextItem(id)) return;
        if (ImGui.menuItem("Open")) open(entry.project());
        if (ImGui.menuItem(entry.pinned() ? "Unpin" : "Pin")) togglePinned(entry);
        if (ImGui.menuItem("Show the folder")) FileManagerReveal.reveal(entry.project().rootDirectory()).ifPresent(notifier::show);
        ImGui.separator();
        if (ImGui.menuItem("Remove from the list")) remove(entry);
        ImGui.endPopup();
    }

    private List<Entry> visible() {
        String query = search.get().trim();
        List<Entry> out = new ArrayList<>();
        for (Entry entry : entries) {
            if (nav == NAV_PINNED && !entry.pinned()) continue;
            if (!query.isEmpty() && score(entry, query) == FuzzyScore.NO_MATCH) continue;
            out.add(entry);
        }
        if (!query.isEmpty()) out.sort(Comparator.comparingInt((Entry entry) -> score(entry, query)).reversed());
        else if (sort == SORT_NAME) out.sort(Comparator.comparing((Entry entry) -> entry.project().name().toLowerCase(Locale.ROOT)));
        else out.sort(Comparator.comparing(Entry::pinned).reversed().thenComparing(Comparator.comparingLong((Entry entry) -> entry.project().lastOpenedMillis()).reversed()));
        return out;
    }

    private static int score(Entry entry, String query) {
        return Math.max(FuzzyScore.of(entry.project().name(), query), FuzzyScore.of(entry.path(), query));
    }

    private void handleShortcuts(List<Entry> visible) {
        if (ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.F)) focusSearch = true;
        if (ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.N)) newProject.open();
        if (visible.isEmpty() || ImGui.getIO().getWantTextInput()) return;
        int index = 0;
        for (int n = 0; n < visible.size(); n++) if (visible.get(n).path().equals(selected)) index = n;
        if (ImGui.isKeyPressed(ImGuiKey.RightArrow) || ImGui.isKeyPressed(ImGuiKey.DownArrow)) selected = visible.get(Math.min(index + 1, visible.size() - 1)).path();
        if (ImGui.isKeyPressed(ImGuiKey.LeftArrow) || ImGui.isKeyPressed(ImGuiKey.UpArrow)) selected = visible.get(Math.max(index - 1, 0)).path();
        if (ImGui.isKeyPressed(ImGuiKey.Enter) && !selected.isEmpty()) open(visible.get(index).project());
    }

    private void togglePinned(Entry entry) {
        try {
            store.setPinned(entry.project().rootDirectory(), !entry.pinned());
            reload();
        } catch (IOException e) {
            notifier.show("Could not update the list: " + e.getMessage());
        }
    }

    private void remove(Entry entry) {
        try {
            store.removeRecent(entry.project().rootDirectory());
            projectIcons.forget(entry.project().rootDirectory());
            reload();
            notifier.show(entry.project().name() + " removed from the list. Nothing was deleted on disk.");
        } catch (IOException e) {
            notifier.show("Could not update the list: " + e.getMessage());
        }
    }

    private void pickFolder() {
        browser.chooseFolder("Open a project folder", ProjectStore.defaultProjectsFolder(), folder -> {
            Optional<Project> project = store.readProjectFromDisk(folder, System.currentTimeMillis());
            if (project.isEmpty()) {
                notifier.show("This folder does not contain a Moud project.");
                return;
            }
            open(project.get());
        });
    }

    private void open(Project project) {
        try {
            store.recordOpened(project);
        } catch (IOException e) {
            notifier.show("Could not record the project: " + e.getMessage());
            return;
        }
        onOpened.accept(project);
    }

    private static boolean primaryButton(String id, String label, float width) {
        return button(id, label, width, true);
    }

    private static boolean secondaryButton(String id, String label, float width) {
        return button(id, label, width, false);
    }

    private static boolean button(String id, String label, float width, boolean primary) {
        float height = EditorScale.of(PRIMARY_HEIGHT);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton("##" + id, width, height);
        float emphasis = EditorMotion.towards(id, ImGui.isItemHovered());
        boolean held = ImGui.isItemActive();
        ImDrawList draw = ImGui.getWindowDrawList();
        int fill = primary
                ? EditorStyle.lighten(EditorStyle.COLOR_HIGHLIGHT, emphasis * 0.08f - (held ? 0.08f : 0.0f))
                : EditorMotion.blend(EditorStyle.COLOR_WIDGET_BACKGROUND, EditorStyle.COLOR_WIDGET_HOVER, emphasis);
        draw.addRectFilled(x, y, x + width, y + height, fill, EditorStyle.frameRounding());
        String shown = (primary ? "+  " : "") + label;
        draw.addText(x + (width - ImGui.calcTextSizeX(shown)) * 0.5f, y + (height - ImGui.getTextLineHeight()) * 0.5f,
                primary ? EditorStyle.COLOR_TEXT_ON_ACCENT : EditorStyle.COLOR_TEXT, shown);
        if (ImGui.isItemHovered()) ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        return clicked;
    }

    private static void text(int color, String value) {
        ImGui.pushStyleColor(ImGuiCol.Text, color);
        ImGui.textUnformatted(value);
        ImGui.popStyleColor();
    }

    private static void withFont(ImFont font, float size, Runnable body) {
        if (font == null) {
            body.run();
            return;
        }
        ImGui.pushFont(font, EditorScale.of(size));
        body.run();
        ImGui.popFont();
    }

    private static List<Cover> loadCovers() {
        List<Cover> covers = new ArrayList<>();
        for (JsonElement element : EditorData.read(COVERS_TABLE).getAsJsonArray()) {
            JsonObject cover = element.getAsJsonObject();
            covers.add(new Cover(color(cover, "light"), color(cover, "dark")));
        }
        return List.copyOf(covers);
    }

    private static int color(JsonObject cover, String key) {
        return opaque(Integer.parseInt(cover.get(key).getAsString().substring(1), 16));
    }

    private static int opaque(int rgb) {
        return EditorStyle.rgb((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    private static String initials(String name) {
        StringBuilder out = new StringBuilder();
        for (String word : name.trim().split("[\\s_-]+")) {
            if (!word.isEmpty() && out.length() < 2) out.append(Character.toUpperCase(word.charAt(0)));
        }
        return out.isEmpty() ? "?" : out.toString();
    }

    private static String ellipsize(String value, float width) {
        if (ImGui.calcTextSizeX(value) <= width) return value;
        String text = value;
        while (text.length() > 4 && ImGui.calcTextSizeX("…" + text) > width) text = text.substring(1);
        return "…" + text;
    }

    private static String relativeDate(long millis) {
        if (millis <= 0L) return "Never opened";
        LocalDate date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
        long days = ChronoUnit.DAYS.between(date, LocalDate.now());
        if (days == 0L) {
            long minutes = ChronoUnit.MINUTES.between(Instant.ofEpochMilli(millis), Instant.now());
            if (minutes < 1) return "Just now";
            if (minutes < 60) return minutes + " min ago";
            return "Today at " + LocalTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        if (days == 1L) return "Yesterday";
        if (days < 7L) return days + " days ago";
        return date.format(ABSOLUTE_DATE);
    }
}
