package com.meekdev.moud.mod.client.editor.project;

import com.meekdev.moud.mod.client.editor.files.FileBrowser;
import com.meekdev.moud.mod.client.editor.files.FileManagerReveal;
import com.meekdev.moud.mod.client.editor.kit.FuzzyScore;
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
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import java.io.IOException;
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
    private static final float CONTENT_MAX_WIDTH = 1180.0f;
    private static final float LOGO_SIZE = 30.0f;
    private static final float BUTTON_HEIGHT = 38.0f;
    private static final float NAV_ROW_HEIGHT = 32.0f;
    private static final float HERO_HEIGHT = 146.0f;
    private static final float HERO_ART_WIDTH = 260.0f;
    private static final float CARD_MIN_WIDTH = 230.0f;
    private static final float CARD_GAP = 18.0f;
    private static final float CARD_ART_RATIO = 0.56f;
    private static final float CARD_TEXT_HEIGHT = 54.0f;
    private static final float CARD_MARK = 48.0f;
    private static final float ART_GRID_STEP = 18.0f;
    private static final float FIELD_HEIGHT = 34.0f;
    private static final float FIELD_WIDTH = 280.0f;
    private static final float SORT_HEIGHT = 30.0f;
    private static final float SECTION_GAP = 30.0f;
    private static final float TITLE_SIZE = HubStyle.TITLE;
    private static final float HERO_TITLE_SIZE = HubStyle.HEADING;
    private static final float CORNER = 6.0f;
    private static final float CORNER_ART = 8.0f;
    private static final int SEARCH_CAPACITY = 128;
    private static final int NAV_ALL = 0;
    private static final int NAV_PINNED = 1;
    private static final int SORT_RECENT = 0;
    private static final int SORT_NAME = 1;

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
        ImGui.pushStyleColor(ImGuiCol.WindowBg, HubStyle.BACKGROUND);
        boolean open = ImGui.begin("Moud###project-hub", HOST_FLAGS);
        ImGui.popStyleColor();
        ImGui.popStyleVar();
        if (open) {
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0.0f, 0.0f);
            renderSidebar();
            ImGui.sameLine(0.0f, 0.0f);
            renderMain();
            ImGui.popStyleVar();
        }
        newProject.render();
        HubStyle.asEditor(browser::render);
        ImGui.end();
    }

    private void renderSidebar() {
        float width = EditorScale.of(SIDEBAR_WIDTH);
        float padding = EditorScale.of(SIDEBAR_PADDING);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, HubStyle.TOP_BAR);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, padding, padding);
        ImGui.beginChild("##hub-sidebar", width, 0.0f, ImGuiChildFlags.AlwaysUseWindowPadding, ImGuiWindowFlags.NoScrollbar);
        ImGui.popStyleVar();
        ImGui.popStyleColor();
        float inner = ImGui.getContentRegionAvailX();
        renderBrand();
        ImGui.dummy(0.0f, EditorScale.of(SECTION_GAP));
        if (button("hub-new", "New project", HubIcon.PLUS, inner, true)) newProject.open();
        ImGui.dummy(0.0f, EditorScale.of(8.0f));
        if (button("hub-open", "Open a folder", HubIcon.FOLDER_OPEN, inner, false)) pickFolder();
        ImGui.dummy(0.0f, EditorScale.of(SECTION_GAP));
        caption("LIBRARY");
        navRow("hub-nav-all", HubIcon.CUBE, "All projects", entries.size(), NAV_ALL, inner);
        navRow("hub-nav-pinned", HubIcon.PUSH_PIN, "Pinned", (int) entries.stream().filter(Entry::pinned).count(), NAV_PINNED, inner);
        renderSidebarFooter(inner);
        float left = ImGui.getWindowPosX();
        float top = ImGui.getWindowPosY();
        float height = ImGui.getWindowHeight();
        ImGui.endChild();
        ImGui.getWindowDrawList().addLine(left + width - HubStyle.hairline(), top, left + width - HubStyle.hairline(), top + height,
                HubStyle.LINE, HubStyle.hairline());
    }

    private void renderBrand() {
        float size = EditorScale.of(LOGO_SIZE);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        ImDrawList draw = ImGui.getWindowDrawList();
        float logoWidth = size * 234.0f / 238.0f;
        draw.addImage(icons.logoTextureId(), x + (size - logoWidth) * 0.5f, y, x + (size + logoWidth) * 0.5f, y + size);
        float textX = x + size + EditorScale.of(12.0f);
        paint(draw, HubStyle.BRAND, true, textX, y - EditorScale.of(1.0f), HubStyle.TEXT_BRIGHT, "Moud");
        paint(draw, HubStyle.SMALL, false, textX, y + EditorScale.of(HubStyle.BRAND), HubStyle.TEXT_MUTED, "Studio");
        ImGui.dummy(size, size);
    }

    private void navRow(String id, HubIcon mark, String label, int count, int value, float width) {
        float height = EditorScale.of(NAV_ROW_HEIGHT);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        if (ImGui.invisibleButton("##" + id, width, height)) nav = value;
        boolean active = nav == value;
        float emphasis = EditorMotion.towards(id, ImGui.isItemHovered());
        ImDrawList draw = ImGui.getWindowDrawList();
        float rounding = EditorScale.of(CORNER);
        if (active) draw.addRectFilled(x, y, x + width, y + height, HubStyle.SURFACE_HOVER, rounding);
        else if (emphasis > 0.0f) draw.addRectFilled(x, y, x + width, y + height, EditorStyle.withAlpha(HubStyle.SURFACE, emphasis), rounding);
        if (active) draw.addRectFilled(x, y + EditorScale.of(7.0f), x + EditorScale.of(2.0f), y + height - EditorScale.of(7.0f), HubStyle.ACCENT);
        float glyph = EditorScale.of(HubStyle.ITEM);
        float glyphX = x + EditorScale.of(12.0f);
        int ink = active ? HubStyle.TEXT_BRIGHT : EditorMotion.blend(HubStyle.TEXT_MUTED, HubStyle.TEXT_MAIN, emphasis);
        icon(draw, mark, glyphX, y + (height - glyph) * 0.5f, glyph, ink);
        paint(draw, HubStyle.ITEM, active, glyphX + glyph + EditorScale.of(10.0f), middle(y, height, HubStyle.ITEM), ink, label);
        String tally = Integer.toString(count);
        paint(draw, HubStyle.SMALL, false, x + width - EditorScale.of(12.0f) - widthOf(HubStyle.SMALL, false, tally),
                middle(y, height, HubStyle.SMALL), HubStyle.TEXT_LIGHT, tally);
        if (ImGui.isItemHovered()) ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
    }

    private void renderSidebarFooter(float width) {
        float footer = EditorScale.of(HubStyle.CAPTION + HubStyle.SMALL + 28.0f);
        float remaining = ImGui.getContentRegionAvailY() - footer;
        if (remaining > 0.0f) ImGui.dummy(0.0f, remaining);
        caption("PROJECTS FOLDER");
        String folder = ProjectStore.defaultProjectsFolder().toString();
        write(HubStyle.SMALL, false, HubStyle.TEXT_LIGHT, ellipsize(folder, width));
        if (ImGui.isItemHovered()) ImGui.setTooltip(folder);
    }

    private void renderMain() {
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, EditorScale.of(MAIN_PADDING_X), EditorScale.of(MAIN_PADDING_Y));
        ImGui.beginChild("##hub-main", 0.0f, 0.0f, ImGuiChildFlags.AlwaysUseWindowPadding, 0);
        ImGui.popStyleVar();
        float available = ImGui.getContentRegionAvailX();
        float width = Math.min(available, EditorScale.of(CONTENT_MAX_WIDTH));
        float indent = (available - width) * 0.5f;
        if (indent > 0.0f) ImGui.indent(indent);
        List<Entry> visible = visible();
        renderHeader(width, visible.size());
        ImGui.dummy(0.0f, EditorScale.of(SECTION_GAP));
        if (entries.isEmpty()) {
            renderWelcome(width);
        } else if (visible.isEmpty()) {
            write(HubStyle.BODY, false, HubStyle.TEXT_MUTED, nav == NAV_PINNED && search.get().isBlank()
                    ? "Nothing pinned yet. Right click a project to pin it."
                    : "No project matches \"" + search.get().trim() + "\".");
        } else if (nav == NAV_ALL && sort == SORT_RECENT && search.get().isBlank()) {
            Entry latest = visible.getFirst();
            caption("CONTINUE WHERE YOU LEFT OFF");
            renderHero(latest, width);
            List<Entry> rest = visible.subList(1, visible.size());
            if (!rest.isEmpty()) {
                ImGui.dummy(0.0f, EditorScale.of(SECTION_GAP));
                caption("RECENT");
                renderGrid(rest, width);
            }
        } else {
            renderGrid(visible, width);
        }
        handleShortcuts(visible);
        if (indent > 0.0f) ImGui.unindent(indent);
        ImGui.endChild();
    }

    private void renderHeader(float width, int count) {
        float height = EditorScale.of(FIELD_HEIGHT);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        ImDrawList draw = ImGui.getWindowDrawList();
        String title = nav == NAV_PINNED ? "Pinned" : "Projects";
        paint(draw, TITLE_SIZE, true, x, y + (height - EditorScale.of(TITLE_SIZE)) * 0.5f, HubStyle.TEXT_BRIGHT, title);
        String tally = count + (count == 1 ? " project" : " projects");
        paint(draw, HubStyle.BODY, false, x + widthOf(TITLE_SIZE, true, title) + EditorScale.of(12.0f),
                middle(y, height, HubStyle.BODY), HubStyle.TEXT_LIGHT, tally);
        float sortWidth = sortsWidth();
        float fieldWidth = Math.min(EditorScale.of(FIELD_WIDTH), width * 0.34f);
        renderSorts(x + width - sortWidth, y + (height - EditorScale.of(SORT_HEIGHT)) * 0.5f);
        renderSearchField(x + width - sortWidth - EditorScale.of(10.0f) - fieldWidth, y, fieldWidth);
        ImGui.setCursorScreenPos(x, y + height);
        ImGui.dummy(width, 0.0f);
    }

    private void renderSearchField(float x, float y, float width) {
        float height = EditorScale.of(FIELD_HEIGHT);
        ImDrawList draw = ImGui.getWindowDrawList();
        float rounding = EditorScale.of(CORNER_ART);
        draw.addRectFilled(x, y, x + width, y + height, HubStyle.SURFACE, rounding);
        float glyph = EditorStyle.iconSizeMedium();
        icon(draw, HubIcon.MAGNIFYING_GLASS, x + EditorScale.of(11.0f), y + (height - glyph) * 0.5f, glyph, HubStyle.TEXT_MUTED);
        float fieldX = x + EditorScale.of(11.0f) + glyph + EditorScale.of(9.0f);
        ImGui.setCursorScreenPos(fieldX, y + (height - EditorScale.of(HubStyle.BODY)) * 0.5f - EditorScale.of(2.0f));
        ImGui.setNextItemWidth(x + width - EditorScale.of(10.0f) - fieldX);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 0.0f, EditorScale.of(2.0f));
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 0.0f);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, EditorStyle.rgba(0, 0, 0, 0));
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, EditorStyle.rgba(0, 0, 0, 0));
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, EditorStyle.rgba(0, 0, 0, 0));
        ImGui.pushStyleColor(ImGuiCol.Text, HubStyle.TEXT_MAIN);
        ImGui.pushStyleColor(ImGuiCol.TextDisabled, HubStyle.TEXT_MUTED);
        ImFont font = EditorFonts.page(HubStyle.BODY, false);
        if (font != null) ImGui.pushFont(font, EditorScale.of(HubStyle.BODY));
        if (focusSearch) {
            ImGui.setKeyboardFocusHere();
            focusSearch = false;
        }
        ImGui.inputTextWithHint("##hub-search", "Search projects", search);
        boolean active = ImGui.isItemActive();
        if (font != null) ImGui.popFont();
        ImGui.popStyleColor(5);
        ImGui.popStyleVar(2);
        draw.addRect(x, y, x + width, y + height, active ? HubStyle.ACCENT : HubStyle.LINE_STRONG, rounding, 0, HubStyle.hairline());
    }

    private float sortsWidth() {
        return widthOf(HubStyle.ITEM, true, "Recent") + widthOf(HubStyle.ITEM, true, "Name") + EditorScale.of(48.0f);
    }

    private void renderSorts(float x, float y) {
        float cursor = renderSort(x, y, "Recent", SORT_RECENT);
        renderSort(cursor, y, "Name", SORT_NAME);
    }

    private float renderSort(float x, float y, String label, int value) {
        float height = EditorScale.of(SORT_HEIGHT);
        float width = widthOf(HubStyle.ITEM, true, label) + EditorScale.of(22.0f);
        ImGui.setCursorScreenPos(x, y);
        if (ImGui.invisibleButton("##hub-sort-" + value, width, height)) sort = value;
        boolean active = sort == value;
        float emphasis = EditorMotion.towards("hub-sort-" + value, ImGui.isItemHovered());
        ImDrawList draw = ImGui.getWindowDrawList();
        float rounding = EditorScale.of(CORNER);
        if (active) draw.addRectFilled(x, y, x + width, y + height, HubStyle.SURFACE_HOVER, rounding);
        else if (emphasis > 0.0f) draw.addRectFilled(x, y, x + width, y + height, EditorStyle.withAlpha(HubStyle.SURFACE_HOVER, emphasis), rounding);
        paint(draw, HubStyle.ITEM, active, x + EditorScale.of(11.0f), middle(y, height, HubStyle.ITEM),
                active ? HubStyle.TEXT_MAIN : EditorMotion.blend(HubStyle.TEXT_MUTED, HubStyle.TEXT_MAIN, emphasis), label);
        if (ImGui.isItemHovered()) ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        return x + width + EditorScale.of(4.0f);
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
        float artWidth = Math.min(EditorScale.of(HERO_ART_WIDTH), width * 0.38f);
        renderArt(draw, entry, x, y, artWidth, height, emphasis);
        float textX = x + artWidth + EditorScale.of(26.0f);
        paint(draw, HERO_TITLE_SIZE, true, textX, y + EditorScale.of(4.0f), HubStyle.TEXT_BRIGHT, entry.project().name());
        draw.pushClipRect(textX, y, x + width, y + height, true);
        paint(draw, HubStyle.SMALL, false, textX, y + EditorScale.of(4.0f + HERO_TITLE_SIZE + 10.0f), HubStyle.TEXT_MUTED,
                relativeDate(entry.project().lastOpenedMillis()) + "   ·   " + entry.path());
        draw.popClipRect();
        String action = "Open in editor";
        float actionWidth = widthOf(HubStyle.ITEM, true, action) + EditorScale.of(32.0f);
        float actionHeight = EditorScale.of(BUTTON_HEIGHT);
        float actionY = y + height - actionHeight;
        float rounding = EditorScale.of(CORNER);
        int fill = EditorMotion.blend(HubStyle.SURFACE_HOVER, HubStyle.TEXT_MAIN, emphasis);
        draw.addRectFilled(textX, actionY, textX + actionWidth, actionY + actionHeight, fill, rounding);
        if (emphasis < 1.0f) draw.addRect(textX, actionY, textX + actionWidth, actionY + actionHeight,
                EditorStyle.withAlpha(HubStyle.LINE_STRONG, 1.0f - emphasis), rounding, 0, HubStyle.hairline());
        paint(draw, HubStyle.ITEM, true, textX + EditorScale.of(16.0f), middle(actionY, actionHeight, HubStyle.ITEM),
                EditorMotion.blend(HubStyle.TEXT_MAIN, HubStyle.TOP_BAR, emphasis), action);
        if (hovered) ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        if (clicked) open(entry.project());
    }

    private void renderGrid(List<Entry> visible, float width) {
        float gap = EditorScale.of(CARD_GAP);
        int columns = Math.max(1, (int) ((width + gap) / (EditorScale.of(CARD_MIN_WIDTH) + gap)));
        float cardWidth = (width - gap * (columns - 1)) / columns;
        float artHeight = cardWidth * CARD_ART_RATIO;
        float cardHeight = artHeight + EditorScale.of(CARD_TEXT_HEIGHT);
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        for (int index = 0; index < visible.size(); index++) {
            float x = left + (index % columns) * (cardWidth + gap);
            float y = top + (index / columns) * (cardHeight + gap);
            ImGui.setCursorScreenPos(x, y);
            renderCard(visible.get(index), cardWidth, artHeight, cardHeight);
        }
        int rows = (visible.size() + columns - 1) / columns;
        ImGui.setCursorScreenPos(left, top + rows * (cardHeight + gap));
        ImGui.dummy(width, 0.0f);
    }

    private void renderCard(Entry entry, float width, float artHeight, float height) {
        ImGui.pushID(entry.path());
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton("##card", width, height);
        boolean hovered = ImGui.isItemHovered();
        float emphasis = EditorMotion.towards("hub-card-" + entry.path(), hovered || entry.path().equals(selected));
        renderMenu(entry, "##card-menu");
        ImDrawList draw = ImGui.getWindowDrawList();
        renderArt(draw, entry, x, y, width, artHeight, emphasis);
        float textY = y + artHeight + EditorScale.of(12.0f);
        float titleX = x;
        draw.pushClipRect(x, y, x + width, y + height, true);
        if (entry.pinned()) {
            float glyph = EditorScale.of(HubStyle.SMALL);
            icon(draw, HubIcon.PUSH_PIN, x, textY + EditorScale.of(2.0f), glyph, HubStyle.TEXT_MUTED);
            titleX += glyph + EditorScale.of(6.0f);
        }
        paint(draw, HubStyle.TILE_TITLE, true, titleX, textY, HubStyle.TEXT_BRIGHT, entry.project().name());
        paint(draw, HubStyle.SMALL, false, x, textY + EditorScale.of(HubStyle.TILE_TITLE + 7.0f), HubStyle.TEXT_MUTED,
                relativeDate(entry.project().lastOpenedMillis()));
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

    private void renderArt(ImDrawList draw, Entry entry, float x, float y, float width, float height, float emphasis) {
        float rounding = EditorScale.of(CORNER_ART);
        Optional<ProjectIcons.Image> cover = projectIcons.of(entry.project().rootDirectory());
        if (cover.isPresent()) {
            float box = width / height;
            float sideways = Math.max(0.0f, (1.0f - box / cover.get().aspect()) * 0.5f);
            float crop = Math.max(0.0f, (1.0f - cover.get().aspect() / box) * 0.5f);
            draw.addImageRounded(cover.get().textureId(), x, y, x + width, y + height,
                    sideways, crop, 1.0f - sideways, 1.0f - crop, 0xFFFFFFFF, rounding);
        } else {
            draw.addRectFilled(x, y, x + width, y + height, HubStyle.ART, rounding);
            renderArtGrid(draw, x + rounding, y + rounding, width - rounding * 2.0f, height - rounding * 2.0f, emphasis);
            float mark = Math.min(EditorScale.of(CARD_MARK), height * 0.5f) * (1.0f + emphasis * 0.08f);
            icon(draw, HubIcon.CUBE, x + (width - mark) * 0.5f, y + (height - mark) * 0.5f, mark, HubStyle.ART_MARK);
        }
        draw.addRect(x, y, x + width, y + height, EditorMotion.blend(HubStyle.LINE_STRONG, HubStyle.TEXT_LIGHT, emphasis),
                rounding, 0, HubStyle.hairline());
    }

    private static void renderArtGrid(ImDrawList draw, float x, float y, float width, float height, float emphasis) {
        float step = EditorScale.of(ART_GRID_STEP);
        int line = EditorStyle.withAlpha(HubStyle.TEXT_BRIGHT, 0.035f + emphasis * 0.025f);
        float thickness = HubStyle.hairline();
        draw.pushClipRect(x, y, x + width, y + height, true);
        for (float column = x + step; column < x + width; column += step) draw.addLine(column, y, column, y + height, line, thickness);
        for (float row = y + step; row < y + height; row += step) draw.addLine(x, row, x + width, row, line, thickness);
        draw.popClipRect();
    }

    private void renderWelcome(float width) {
        float height = EditorScale.of(320.0f);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        ImDrawList draw = ImGui.getWindowDrawList();
        float rounding = EditorScale.of(CORNER_ART);
        draw.addRectFilled(x, y, x + width, y + height, HubStyle.SURFACE, rounding);
        HubStyle.glow(draw, x + width * 0.5f, y + height * 0.42f, width * 0.35f, height * 0.5f, HubStyle.SURFACE_HOVER, 0.9f);
        draw.addRect(x, y, x + width, y + height, HubStyle.LINE_STRONG, rounding, 0, HubStyle.hairline());
        float mark = EditorScale.of(52.0f);
        draw.addImage(icons.logoTextureId(), x + (width - mark) * 0.5f, y + EditorScale.of(54.0f),
                x + (width + mark) * 0.5f, y + EditorScale.of(54.0f) + mark);
        String heading = "Build your first place";
        paint(draw, HERO_TITLE_SIZE, true, x + (width - widthOf(HERO_TITLE_SIZE, true, heading)) * 0.5f,
                y + EditorScale.of(130.0f), HubStyle.TEXT_BRIGHT, heading);
        String line = "A project is a folder with a place.toml, its scripts and its scenes. Start from a baseplate or an empty world.";
        paint(draw, HubStyle.BODY, false, x + (width - widthOf(HubStyle.BODY, false, line)) * 0.5f,
                y + EditorScale.of(168.0f), HubStyle.TEXT_MUTED, line);
        float buttonWidth = EditorScale.of(180.0f);
        float gap = EditorScale.of(10.0f);
        ImGui.setCursorScreenPos(x + (width - buttonWidth * 2.0f - gap) * 0.5f, y + EditorScale.of(212.0f));
        if (button("hub-welcome-new", "New project", HubIcon.PLUS, buttonWidth, true)) newProject.open();
        ImGui.sameLine(0.0f, gap);
        if (button("hub-welcome-open", "Open a folder", HubIcon.FOLDER_OPEN, buttonWidth, false)) pickFolder();
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

    private boolean button(String id, String label, HubIcon mark, float width, boolean primary) {
        float height = EditorScale.of(BUTTON_HEIGHT);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton("##" + id, width, height);
        float emphasis = EditorMotion.towards(id, ImGui.isItemHovered());
        ImDrawList draw = ImGui.getWindowDrawList();
        float rounding = EditorScale.of(CORNER);
        int fill = primary
                ? EditorMotion.blend(HubStyle.TEXT_MAIN, HubStyle.TEXT_BRIGHT, emphasis)
                : EditorMotion.blend(HubStyle.SURFACE_HOVER, HubStyle.SURFACE_ACTIVE, emphasis);
        draw.addRectFilled(x, y, x + width, y + height, fill, rounding);
        if (!primary) draw.addRect(x, y, x + width, y + height, HubStyle.LINE_STRONG, rounding, 0, HubStyle.hairline());
        int ink = primary ? HubStyle.TOP_BAR : HubStyle.TEXT_MAIN;
        float glyph = EditorStyle.iconSizeSmall();
        float content = glyph + EditorScale.of(8.0f) + widthOf(HubStyle.ITEM, true, label);
        float glyphX = x + (width - content) * 0.5f;
        icon(draw, mark, glyphX, y + (height - glyph) * 0.5f, glyph, ink);
        paint(draw, HubStyle.ITEM, true, glyphX + glyph + EditorScale.of(8.0f), middle(y, height, HubStyle.ITEM), ink, label);
        if (ImGui.isItemHovered()) ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        return clicked;
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

    private void icon(ImDrawList draw, HubIcon mark, float x, float y, float size, int color) {
        draw.addImage(icons.imageId(mark.resourcePath()), x, y, x + size, y + size, 0.0f, 0.0f, 1.0f, 1.0f, color);
    }

    private static void caption(String text) {
        paintTracked(ImGui.getWindowDrawList(), HubStyle.CAPTION, ImGui.getCursorScreenPosX(), ImGui.getCursorScreenPosY(), HubStyle.TEXT_LIGHT, text);
        ImGui.dummy(0.0f, EditorScale.of(HubStyle.CAPTION + 10.0f));
    }

    private static void write(float size, boolean bold, int color, String value) {
        ImFont font = EditorFonts.page(size, bold);
        if (font != null) ImGui.pushFont(font, EditorScale.of(EditorFonts.pageSize(size, bold)));
        ImGui.pushStyleColor(ImGuiCol.Text, color);
        ImGui.textUnformatted(value);
        ImGui.popStyleColor();
        if (font != null) ImGui.popFont();
    }

    private static void paint(ImDrawList draw, float size, boolean bold, float x, float y, int color, String value) {
        ImFont font = EditorFonts.page(size, bold);
        if (font == null) draw.addText(x, y, color, value);
        else draw.addText(font, EditorScale.ofInteger(EditorFonts.pageSize(size, bold)), x, y, color, value);
    }

    private static void paintTracked(ImDrawList draw, float size, float x, float y, int color, String value) {
        float tracking = EditorScale.of(size) * 0.07f;
        float cursor = x;
        for (int index = 0; index < value.length(); index++) {
            String glyph = String.valueOf(value.charAt(index));
            paint(draw, size, true, cursor, y, color, glyph);
            cursor += widthOf(size, true, glyph) + tracking;
        }
    }

    private static float widthOf(float size, boolean bold, String value) {
        ImFont font = EditorFonts.page(size, bold);
        if (font == null) return ImGui.calcTextSizeX(value);
        return font.calcTextSizeAX(EditorScale.of(EditorFonts.pageSize(size, bold)), Float.MAX_VALUE, 0.0f, value);
    }

    private static float middle(float top, float height, float size) {
        return top + (height - EditorScale.of(size)) * 0.5f;
    }

    private static String ellipsize(String value, float width) {
        if (widthOf(HubStyle.SMALL, false, value) <= width) return value;
        String text = value;
        while (text.length() > 4 && widthOf(HubStyle.SMALL, false, "…" + text) > width) text = text.substring(1);
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
