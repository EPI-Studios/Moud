package com.meekdev.moud.mod.client.editor.project;

import com.meekdev.moud.mod.client.editor.kit.Text;
import com.meekdev.moud.mod.client.editor.files.FileManagerReveal;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorIcon;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import com.meekdev.moud.mod.client.editor.notify.Notifier;
import com.meekdev.moud.mod.client.editor.style.EditorMotion;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.client.editor.files.FileBrowser;
import com.meekdev.moud.mod.client.editor.kit.FuzzyScore;
import com.meekdev.moud.mod.client.editor.kit.IconButtons;
import com.meekdev.moud.mod.client.editor.kit.SearchField;
import com.meekdev.moud.mod.client.editor.kit.SegmentedControl;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public final class ProjectSelectorView {

    private static final DateTimeFormatter ABSOLUTE_DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final int HOST_WINDOW_FLAGS = ImGuiWindowFlags.NoDecoration
            | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoDocking
            | ImGuiWindowFlags.NoBringToFrontOnFocus
            | ImGuiWindowFlags.NoSavedSettings;
    private static final float CONTENT_MAX_WIDTH = 940.0f;
    private static final float SIDE_MARGIN = 48.0f;
    private static final float TOP_MARGIN = 44.0f;
    private static final float HEADER_GAP = 28.0f;
    private static final float SEARCH_GAP = 20.0f;
    private static final float FOOTER_GAP = 16.0f;
    private static final float ACTION_BUTTON_WIDTH = 176.0f;
    private static final float ACTION_BUTTON_HEIGHT = 38.0f;
    private static final float ROW_HEIGHT = 66.0f;
    private static final float ROW_GAP = 8.0f;
    private static final float ROW_PADDING = 18.0f;
    private static final float ROW_TEXT_GAP = 6.0f;
    private static final float LOGO_SIZE = 40.0f;
    private static final float LOGO_GAP = 14.0f;
    private static final float PROJECT_ICON_SIZE = 34.0f;
    private static final float PIN_COLUMN_WIDTH = 28.0f;
    private static final float PIN_RADIUS = 4.5f;
    private static final float SELECTION_BAR_WIDTH = 3.0f;
    private static final float HOVER_ALPHA = 0.55f;
    private static final int SEARCH_CAPACITY = 128;
    private static final int FILTER_ALL = 0;
    private static final int FILTER_PINNED = 1;
    private static final int FILTER_BROKEN = 2;

    private final FileBrowser browser;
    private final ProjectStore store;
    private final Notifier notifier;
    private final IconWidgets icons;
    private final Consumer<Project> onProjectOpened;
    private final NewProjectDialog newProjectDialog;
    private final ProjectIcons projectIcons = new ProjectIcons();
    private final ImString search = new ImString("", SEARCH_CAPACITY);
    private List<RecentEntry> recents = List.of();
    private String selectedPath = "";
    private int filter = FILTER_ALL;
    private boolean confirmClearRequested;
    private boolean focusSearchRequested;
    private boolean searchActive;

    public ProjectSelectorView(ProjectStore store, Notifier notifier, IconWidgets icons,
                               Consumer<Project> onProjectOpened) {
        this.store = store;
        this.notifier = notifier;
        this.icons = icons;
        this.onProjectOpened = onProjectOpened;
        this.browser = new FileBrowser(icons);
        this.newProjectDialog = new NewProjectDialog(store, notifier, icons, onProjectOpened);
        reloadRecents();
    }

    private void reloadRecents() {
        Set<String> pinned = store.loadPinnedPaths();
        List<RecentEntry> entries = new ArrayList<>();
        for (Project project : store.loadRecents()) {
            entries.add(new RecentEntry(project, Files.isRegularFile(project.markerFile()),
                    pinned.contains(project.rootDirectory().toAbsolutePath().toString())));
        }
        entries.sort(Comparator.comparing(RecentEntry::pinned).reversed()
                .thenComparing(Comparator.comparingLong(RecentEntry::lastOpenedMillis).reversed()));
        recents = List.copyOf(entries);
    }

    public void dispose() {
        projectIcons.dispose();
    }

    public void render(float deltaSeconds) {
        ImGuiViewport viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getWorkPosX(), viewport.getWorkPosY(), ImGuiCond.Always);
        ImGui.setNextWindowSize(viewport.getWorkSizeX(), viewport.getWorkSizeY(), ImGuiCond.Always);
        if (ImGui.begin(Text.label("Moud Projects", "project-selector"),
                HOST_WINDOW_FLAGS)) {
            renderContent();
        }
        newProjectDialog.render();
        renderClearConfirm();
        browser.render();
        ImGui.end();
    }

    private void renderContent() {
        ImGui.dummy(0.0f, EditorScale.of(TOP_MARGIN));
        float available = ImGui.getContentRegionAvailX();
        float contentWidth = Math.min(EditorScale.of(CONTENT_MAX_WIDTH),
                available - EditorScale.of(SIDE_MARGIN) * 2.0f);
        ImGui.setCursorPosX(ImGui.getCursorPosX() + (available - contentWidth) * 0.5f);
        ImGui.beginChild("##content", contentWidth, 0.0f, false, ImGuiWindowFlags.NoScrollbar);
        renderColumn(ImGui.getContentRegionAvailX());
        ImGui.endChild();
    }

    private void renderColumn(float contentWidth) {
        renderHeader(contentWidth);
        ImGui.dummy(0.0f, EditorScale.of(HEADER_GAP));
        renderSearchRow(contentWidth);
        ImGui.dummy(0.0f, EditorScale.of(SEARCH_GAP));
        List<RecentEntry> visible = visibleEntries();
        renderList(contentWidth, visible);
        renderFooter();
        handleShortcuts(visible);
    }

    private void renderHeader(float contentWidth) {
        float titleTop = ImGui.getCursorPosY();
        renderTitle();
        float buttonWidth = EditorScale.of(ACTION_BUTTON_WIDTH);
        ImGui.setCursorPosY(titleTop);
        ImGui.sameLine(contentWidth - buttonWidth * 2.0f - EditorStyle.itemSpacingX());
        if (IconButtons.withLabel(icons, "new-project", EditorIcon.ADD,
                "New Project", buttonWidth,
                EditorScale.of(ACTION_BUTTON_HEIGHT))) {
            newProjectDialog.open();
        }
        ImGui.sameLine();
        if (IconButtons.withLabel(icons, "open-folder", EditorIcon.FOLDER,
                "Open Folder", buttonWidth,
                EditorScale.of(ACTION_BUTTON_HEIGHT))) {
            pickProjectFolder();
        }
    }

    private void renderTitle() {
        float logoSize = EditorScale.of(LOGO_SIZE);
        ImGui.image(icons.atlasTextureId(EditorIcon.PACKED_SCENE), logoSize, logoSize);
        ImGui.sameLine(0.0f, EditorScale.of(LOGO_GAP));
        ImGui.beginGroup();
        EditorStyle.titleFont().ifPresent(font -> ImGui.pushFont(font, EditorStyle.titleFontPixelHeight()));
        ImGui.textUnformatted("Moud Projects");
        EditorStyle.titleFont().ifPresent(font -> ImGui.popFont());
        EditorStyle.smallFont().ifPresent(font -> ImGui.pushFont(font, EditorStyle.smallFontPixelHeight()));
        Texts.muted("RECENT PROJECTS");
        EditorStyle.smallFont().ifPresent(font -> ImGui.popFont());
        ImGui.endGroup();
    }

    private void renderSearchRow(float contentWidth) {
        List<String> filters = List.of(
                "All",
                "Pinned",
                "Unreadable");
        float filterWidth = SegmentedControl.width(filters);
        if (focusSearchRequested) {
            ImGui.setKeyboardFocusHere();
            focusSearchRequested = false;
        }
        float rowTop = ImGui.getCursorPosY();
        SearchField.render("##project-search",
                "Search projects", search,
                contentWidth - filterWidth - EditorStyle.itemSpacingX() * 2.0f);
        searchActive = ImGui.isItemActive();
        ImGui.sameLine(contentWidth - filterWidth);
        ImGui.setCursorPosY(rowTop + (ImGui.getFrameHeight() - SegmentedControl.height()) * 0.5f);
        filter = SegmentedControl.render("##project-filter", filters, filter);
    }

    private List<RecentEntry> visibleEntries() {
        String query = search.get().trim();
        List<RecentEntry> matching = new ArrayList<>();
        for (RecentEntry entry : recents) {
            if (matchesFilter(entry) && (query.isEmpty() || scoreOf(entry, query) > FuzzyScore.NO_MATCH)) {
                matching.add(entry);
            }
        }
        if (!query.isEmpty()) {
            matching.sort(Comparator.comparingInt((RecentEntry entry) -> scoreOf(entry, query)).reversed());
        }
        return matching;
    }

    private boolean matchesFilter(RecentEntry entry) {
        return switch (filter) {
            case FILTER_PINNED -> entry.pinned();
            case FILTER_BROKEN -> !entry.readable();
            default -> true;
        };
    }

    private static int scoreOf(RecentEntry entry, String query) {
        int byName = FuzzyScore.of(entry.project().name(), query);
        int byPath = FuzzyScore.of(entry.project().rootDirectory().toString(), query);
        return Math.max(byName, byPath);
    }

    private void renderList(float contentWidth, List<RecentEntry> visible) {
        float footerHeight = ImGui.getTextLineHeightWithSpacing() + ImGui.getFrameHeightWithSpacing()
                + EditorScale.of(FOOTER_GAP + TOP_MARGIN);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0.0f, EditorScale.of(ROW_GAP));
        ImGui.beginChild("##project-list", contentWidth, -footerHeight, false);
        if (recents.isEmpty()) {
            renderEmptyRecents();
        } else if (visible.isEmpty()) {
            Texts.muted("No project matches this search");
        } else {
            for (RecentEntry entry : visible) {
                renderRow(entry);
            }
        }
        ImGui.endChild();
        ImGui.popStyleVar();
    }

    private void renderEmptyRecents() {
        ImGui.spacing();
        Texts.muted("No recent projects");
        Texts.muted("Create your first project via \"New Project\".");
    }

    private void renderRow(RecentEntry entry) {
        String id = entry.absolutePath();
        ImGui.pushID(id);
        boolean clicked = ImGui.button("##row", ImGui.getContentRegionAvailX(), EditorScale.of(ROW_HEIGHT));
        boolean hovered = ImGui.isItemHovered();
        paintRow(entry, EditorMotion.towards(id, hovered || id.equals(selectedPath)));
        boolean pinClicked = hovered && clicked && withinPinColumn();
        renderRowMenu(entry);
        ImGui.popID();
        if (pinClicked) {
            togglePinned(entry);
        } else if (clicked && entry.readable()) {
            openExistingProject(entry.project());
        }
    }

    private static boolean withinPinColumn() {
        return ImGui.getMousePosX() < ImGui.getItemRectMinX() + EditorScale.of(PIN_COLUMN_WIDTH + ROW_PADDING);
    }

    private void paintRow(RecentEntry entry, float emphasis) {
        float minX = ImGui.getItemRectMinX();
        float minY = ImGui.getItemRectMinY();
        float maxX = ImGui.getItemRectMaxX();
        float maxY = ImGui.getItemRectMaxY();
        var drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(minX, minY, maxX, maxY,
                EditorStyle.withAlpha(EditorStyle.COLOR_ACCENT, emphasis * HOVER_ALPHA * 0.12f),
                EditorStyle.frameRounding());
        if (entry.absolutePath().equals(selectedPath)) {
            drawList.addRectFilled(minX, minY, minX + EditorScale.of(SELECTION_BAR_WIDTH), maxY,
                    EditorStyle.COLOR_ACCENT);
        }
        paintPin(entry, minX, (minY + maxY) * 0.5f, emphasis);
        paintRowText(entry, minX, minY, maxX);
    }

    private void paintPin(RecentEntry entry, float minX, float centerY, float emphasis) {
        float centerX = minX + EditorScale.of(ROW_PADDING + PIN_COLUMN_WIDTH * 0.5f);
        float radius = EditorScale.of(PIN_RADIUS);
        var drawList = ImGui.getWindowDrawList();
        if (entry.pinned()) {
            drawList.addCircleFilled(centerX, centerY, radius, EditorStyle.COLOR_HIGHLIGHT);
            return;
        }
        if (emphasis > 0.0f) {
            drawList.addCircle(centerX, centerY, radius,
                    EditorStyle.withAlpha(EditorStyle.COLOR_TEXT_FAINT, emphasis), 0, 1.5f);
        }
    }

    private void paintRowText(RecentEntry entry, float minX, float minY, float maxX) {
        float padding = EditorScale.of(ROW_PADDING);
        float iconSize = EditorScale.of(PROJECT_ICON_SIZE);
        float iconX = minX + padding + EditorScale.of(PIN_COLUMN_WIDTH);
        float centerY = minY + EditorScale.of(ROW_HEIGHT) * 0.5f;
        var drawList = ImGui.getWindowDrawList();
        projectIcons.of(entry.project().rootDirectory()).ifPresent(texture ->
                drawList.addImage(texture, iconX, centerY - iconSize * 0.5f,
                        iconX + iconSize, centerY + iconSize * 0.5f));
        float textX = iconX + iconSize + padding;
        float lineHeight = ImGui.getTextLineHeight();
        float gap = EditorScale.of(ROW_TEXT_GAP);
        float nameY = centerY - lineHeight - gap * 0.5f;
        drawList.addText(textX, nameY,
                entry.readable() ? EditorStyle.COLOR_TEXT : EditorStyle.COLOR_TEXT_FAINT,
                entry.project().name());
        drawList.addText(textX, nameY + lineHeight + gap,
                entry.readable() ? EditorStyle.COLOR_TEXT_MUTED : EditorStyle.COLOR_WARNING,
                entry.readable()
                        ? entry.project().rootDirectory().toString()
                        : "Folder not found");
        String date = relativeDateText(entry.lastOpenedMillis());
        drawList.addText(maxX - padding - ImGui.calcTextSizeX(date), nameY,
                EditorStyle.COLOR_TEXT_MUTED, date);
    }

    private void renderRowMenu(RecentEntry entry) {
        if (!ImGui.beginPopupContextItem("##row-menu")) {
            return;
        }
        if (ImGui.menuItem("Open") && entry.readable()) {
            openExistingProject(entry.project());
        }
        if (ImGui.menuItem(entry.pinned() ? "Unpin" : "Pin")) {
            togglePinned(entry);
        }
        if (ImGui.menuItem("Show the folder")) {
            FileManagerReveal.reveal(entry.project().rootDirectory()).ifPresent(notifier::show);
        }
        if (ImGui.menuItem("Remove from the list")) {
            removeEntry(entry);
        }
        ImGui.endPopup();
    }

    private void renderFooter() {
        ImGui.dummy(0.0f, EditorScale.of(FOOTER_GAP));
        EditorStyle.smallFont().ifPresent(font -> ImGui.pushFont(font, EditorStyle.smallFontPixelHeight()));
        Texts.muted("Up and down to move, Enter to open, Delete to remove, Ctrl+F to search");
        EditorStyle.smallFont().ifPresent(font -> ImGui.popFont());
        if (!recents.isEmpty() && ImGui.smallButton(Text.label("Clear list", "project-selector-clear-list"))) {
            confirmClearRequested = true;
        }
    }

    private void handleShortcuts(List<RecentEntry> visible) {
        if (ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.F)) {
            focusSearchRequested = true;
        }
        if (visible.isEmpty()) {
            return;
        }
        int index = selectedIndexIn(visible);
        if (ImGui.isKeyPressed(ImGuiKey.DownArrow)) {
            selectedPath = visible.get(Math.min(index + 1, visible.size() - 1)).absolutePath();
        }
        if (ImGui.isKeyPressed(ImGuiKey.UpArrow)) {
            selectedPath = visible.get(Math.max(index - 1, 0)).absolutePath();
        }
        RecentEntry selected = visible.get(selectedIndexIn(visible));
        if (ImGui.isKeyPressed(ImGuiKey.Enter) && selected.readable()) {
            openExistingProject(selected.project());
        }
        if (!searchActive && ImGui.isKeyPressed(ImGuiKey.Delete)) {
            removeEntry(selected);
        }
    }

    private int selectedIndexIn(List<RecentEntry> visible) {
        for (int index = 0; index < visible.size(); index++) {
            if (visible.get(index).absolutePath().equals(selectedPath)) {
                return index;
            }
        }
        return 0;
    }

    private void togglePinned(RecentEntry entry) {
        try {
            store.setPinned(entry.project().rootDirectory(), !entry.pinned());
            reloadRecents();
        } catch (IOException error) {
            notifier.show(Text.of("Could not update the list: {0}",
                    error.getMessage()));
        }
    }

    private void removeEntry(RecentEntry entry) {
        try {
            store.removeRecent(entry.project().rootDirectory());
            projectIcons.forget(entry.project().rootDirectory());
            reloadRecents();
            notifier.show(Text.of("{0} removed from the list. Nothing was deleted on disk.",
                    entry.project().name()));
        } catch (IOException error) {
            notifier.show(Text.of("Could not update the list: {0}",
                    error.getMessage()));
        }
    }

    private void renderClearConfirm() {
        if (confirmClearRequested) {
            ImGui.openPopup(Text.label("Clear recent projects?",
                    "project-selector-clear-recent"));
            confirmClearRequested = false;
        }
        if (!ImGui.beginPopupModal(Text.label("Clear recent projects?",
                "project-selector-clear-recent"), ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }
        ImGui.textUnformatted("This removes entries from the list but does not delete any files on disk.");
        ImGui.separator();
        if (ImGui.button(Text.label("Clear",
                "project-selector-clear-confirm"))) {
            clearRecents();
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (ImGui.button(Text.label("Cancel",
                "project-selector-clear-cancel"))) {
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private void pickProjectFolder() {
        browser.chooseFolder("Open a project folder",
                Path.of(System.getProperty("user.home")), this::openFolder);
    }

    private void openFolder(Path folder) {
        Optional<Project> project = store.readProjectFromDisk(folder, System.currentTimeMillis());
        if (project.isEmpty()) {
            notifier.show("This folder does not contain a Moud project.");
            return;
        }
        openExistingProject(project.get());
    }

    private void openExistingProject(Project project) {
        try {
            store.recordOpened(project);
        } catch (IOException error) {
            notifier.show(Text.of("Could not record the project: {0}",
                    error.getMessage()));
            return;
        }
        onProjectOpened.accept(project);
    }

    private void clearRecents() {
        try {
            store.clearRecents();
            projectIcons.dispose();
            reloadRecents();
            notifier.show("Recent projects list cleared.");
        } catch (IOException error) {
            notifier.show(Text.of("Could not clear the list: {0}",
                    error.getMessage()));
        }
    }

    private static String relativeDateText(long millis) {
        if (millis <= 0L) {
            return "";
        }
        LocalDate date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
        long daysBetween = ChronoUnit.DAYS.between(date, LocalDate.now());
        if (daysBetween == 0L) {
            return "Today";
        }
        if (daysBetween == 1L) {
            return "Yesterday";
        }
        return daysBetween < 7L
                ? Text.of("{0} days ago", daysBetween)
                : date.format(ABSOLUTE_DATE_FORMAT);
    }

    private record RecentEntry(Project project, boolean readable, boolean pinned) {

        private String absolutePath() {
            return project.rootDirectory().toAbsolutePath().toString();
        }

        private long lastOpenedMillis() {
            return project.lastOpenedMillis();
        }
    }
}
