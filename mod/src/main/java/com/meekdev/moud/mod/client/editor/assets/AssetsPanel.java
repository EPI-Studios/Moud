package com.meekdev.moud.mod.client.editor.assets;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.mod.adapter.render.ModelSnapshots;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.document.ScriptTemplate;
import com.meekdev.moud.mod.client.editor.files.CodeEditor;
import com.meekdev.moud.mod.client.editor.files.FileManagerReveal;
import com.meekdev.moud.mod.client.editor.kit.Breadcrumb;
import com.meekdev.moud.mod.client.editor.kit.Dialogs;
import com.meekdev.moud.mod.client.editor.kit.Disabled;
import com.meekdev.moud.mod.client.editor.kit.EmptyStates;
import com.meekdev.moud.mod.client.editor.kit.SearchField;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.panel.Panel;
import com.meekdev.moud.mod.client.editor.style.EditorIcon;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import com.meekdev.moud.mod.place.ImportSettings;
import com.meekdev.moud.mod.place.Output;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiDir;
import imgui.flag.ImGuiFocusedFlags;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiPopupFlags;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import org.jspecify.annotations.Nullable;

public final class AssetsPanel implements Panel {

    public static final String ID = "assets";
    public static final String PAYLOAD = "moud-asset";

    private static final float FOLDER_TREE_WIDTH = 200.0f;
    private static final float SEARCH_FIELD_WIDTH = 180.0f;
    private static final float SMALL_COMBO_WIDTH = 110.0f;
    private static final float CELL_SIZE = 72.0f;
    private static final float CELL_PADDING = 10.0f;
    private static final float LABEL_HEIGHT = 20.0f;
    private static final float LIST_ROW_HEIGHT = 22.0f;
    private static final float LIST_THUMBNAIL_SIZE = 16.0f;
    private static final float SIZE_COLUMN_WIDTH = 90.0f;
    private static final float DIALOG_WIDTH = 380.0f;
    private static final int MAXIMUM_KEPT_EXTENSION = 12;
    private static final long RESCAN_MILLIS = 1000;
    private static final String ELLIPSIS = "...";
    private static final String NAME_DIALOG = "##asset-name";
    private static final String DELETE_DIALOG = "##asset-delete";
    private static final String REFERENCES_DIALOG = "##asset-references";
    private static final String RELOCATE_DIALOG = "##asset-relocate";
    private static final String SETTINGS_DIALOG = "##asset-import-settings";
    private static final int LISTED_REFERENCES = 12;

    private final SceneDocument document;
    private final IconWidgets icons;
    private final IntSupplier insertParent;
    private final Thumbnails thumbnails = new Thumbnails();
    private final ImString search = new ImString(128);
    private final ImString nameInput = new ImString(256);
    private final Set<Path> selection = new LinkedHashSet<>();
    private @Nullable Path root;
    private Path current = Path.of(".");
    private List<AssetEntry> entries = List.of();
    private long scannedAt;
    private boolean grid = true;
    private @Nullable AssetKind typeFilter;
    private @Nullable Path revealTarget;
    private boolean openNameDialog;
    private boolean openDeleteDialog;
    private String nameTitle = "";
    private Consumer<String> nameAction = name -> {};
    private List<Path> pendingDelete = List.of();
    private final MinecraftBrowser minecraft;
    private List<AssetReferences.Reference> references = List.of();
    private String referencesOf = "";
    private boolean openReferences;
    private @Nullable Relocation relocation;
    private boolean openRelocation;
    private @Nullable Path settingsFile;
    private boolean openSettings;
    private final float[] settingsScale = {1};
    private final float[] settingsVolume = {1};
    private final ImBoolean settingsNearest = new ImBoolean();
    private final ImBoolean settingsStream = new ImBoolean();

    private interface Move {
        Path run() throws IOException;
    }

    private record Relocation(Path source, String from, String to, List<AssetReferences.Reference> references, Move move) {}

    public AssetsPanel(SceneDocument document, IconWidgets icons, IntSupplier insertParent) {
        this.document = document;
        this.icons = icons;
        this.insertParent = insertParent;
        this.minecraft = new MinecraftBrowser(thumbnails, icons, id -> document.placeAsset(id, insertParent.getAsInt(), null), () -> current);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Assets";
    }

    @Override
    public void render() {
        Path place = AssetFiles.root();
        if (!place.equals(root)) {
            root = place;
            navigateTo(place);
        }
        if (!Files.isDirectory(current)) navigateTo(place);
        if (System.currentTimeMillis() - scannedAt > RESCAN_MILLIS) refresh();
        thumbnails.beginFrame();
        renderHeader();
        ImGui.separator();
        renderFolderTreeColumn();
        ImGui.sameLine();
        renderEntryList();
        handleDeleteShortcut();
        renderNameDialog();
        renderDeleteDialog();
        renderReferencesDialog();
        renderRelocationDialog();
        renderSettingsDialog();
    }

    public void importExternal(List<Path> sources, Consumer<List<String>> placed) {
        List<Path> folders = sources.stream().filter(Files::isDirectory).toList();
        if (!folders.isEmpty()) Output.add(Output.Level.WARN, "editor", "Folders can not be imported, drop the files inside: " + folders);
        try {
            List<Path> copied = AssetFiles.importInto(current, sources);
            if (copied.isEmpty()) return;
            for (Path file : copied) {
                ImportSettings guessed = ImportSettings.guess(file);
                if (!guessed.equals(ImportSettings.DEFAULT)) guessed.save(file);
            }
            selection.clear();
            selection.addAll(copied);
            refresh();
            Output.add(Output.Level.INFO, "editor", "Imported " + copied.size() + " file(s) into " + label(current));
            List<String> paths = new ArrayList<>();
            for (Path file : copied) {
                String res = AssetFiles.res(file);
                if (res != null) paths.add(res);
            }
            placed.accept(paths);
        } catch (IOException e) {
            Output.add(Output.Level.ERROR, "editor", "Import failed: " + e.getMessage());
        }
    }

    private void refresh() {
        scannedAt = System.currentTimeMillis();
        String text = search.get().strip();
        entries = text.isEmpty() ? AssetScanner.list(current) : AssetScanner.search(current, text);
        selection.removeIf(path -> !Files.exists(path));
    }

    private void navigateTo(Path directory) {
        minecraft.leave();
        current = directory;
        revealTarget = directory;
        selection.clear();
        refresh();
    }

    private String label(Path directory) {
        if (root == null || directory.equals(root)) return root == null ? "place" : root.getFileName().toString();
        return directory.getFileName().toString();
    }

    private void renderHeader() {
        if (icons.iconButton("assets-new-folder", EditorIcon.FOLDER, EditorStyle.iconSizeSmall())) askNewFolder();
        tooltip("New folder");
        ImGui.sameLine();
        if (icons.iconButton("assets-new", EditorIcon.ADD, EditorStyle.iconSizeSmall())) ImGui.openPopup("##assets-new-menu");
        tooltip("New script");
        renderNewMenu("##assets-new-menu");
        ImGui.sameLine();
        if (icons.iconButton("assets-refresh", EditorIcon.LOAD, EditorStyle.iconSizeSmall())) refresh();
        tooltip("Refresh");
        ImGui.sameLine();
        boolean atRoot = current.equals(root);
        Disabled.push(atRoot);
        boolean up = ImGui.arrowButton("##assets-up", ImGuiDir.Up);
        Disabled.pop(atRoot);
        if (up && !atRoot) navigateTo(current.getParent());
        ImGui.sameLine();
        if (minecraft.active()) Texts.muted(minecraft.location());
        else renderBreadcrumb();
        ImGui.sameLine();
        float offset = ImGui.getContentRegionMaxX() - EditorScale.of(SEARCH_FIELD_WIDTH);
        if (offset > ImGui.getCursorPosX()) ImGui.setCursorPosX(offset);
        if (SearchField.render("##assets-search", "Search", search, EditorScale.of(SEARCH_FIELD_WIDTH))) refresh();
    }

    private void renderBreadcrumb() {
        if (root == null) return;
        List<Path> segments = new ArrayList<>();
        for (Path at = current; at != null && at.startsWith(root); at = at.getParent()) {
            segments.addFirst(at);
            if (at.equals(root)) break;
        }
        List<String> labels = segments.stream().map(this::label).toList();
        Breadcrumb.render("assets", labels).ifPresent(index -> navigateTo(segments.get(index)));
    }

    private void renderNewMenu(String id) {
        if (!ImGui.beginPopup(id)) return;
        if (ImGui.menuItem("Folder")) askNewFolder();
        ImGui.separator();
        for (ScriptTemplate template : ScriptTemplate.SERVER) {
            if (ImGui.menuItem("Script: " + template.label())) createScript(template);
        }
        for (ScriptTemplate template : ScriptTemplate.CLIENT) {
            if (ImGui.menuItem("Local script: " + template.label())) createScript(template);
        }
        ImGui.endPopup();
    }

    private void renderFolderTreeColumn() {
        ImGui.beginChild("##asset-folders", EditorScale.of(FOLDER_TREE_WIDTH), 0, true);
        if (root != null) renderFolderNode(root, true);
        minecraft.renderTree();
        ImGui.endChild();
        revealTarget = null;
    }

    private void renderFolderNode(Path directory, boolean isRoot) {
        List<Path> children = AssetScanner.folders(directory);
        int flags = ImGuiTreeNodeFlags.OpenOnArrow | ImGuiTreeNodeFlags.SpanAvailWidth;
        if (directory.equals(current) && !minecraft.active()) flags |= ImGuiTreeNodeFlags.Selected;
        if (isRoot) flags |= ImGuiTreeNodeFlags.DefaultOpen;
        if (children.isEmpty()) flags |= ImGuiTreeNodeFlags.Leaf;
        if (revealTarget != null && revealTarget.startsWith(directory) && !revealTarget.equals(directory)) ImGui.setNextItemOpen(true, ImGuiCond.Always);
        boolean open = ImGui.treeNodeEx(directory.toString(), flags, iconSpacing() + label(directory));
        paintFolderIcon();
        if (ImGui.isItemClicked() && !ImGui.isItemToggledOpen()) navigateTo(directory);
        acceptMoveInto(directory);
        if (!open) return;
        for (Path child : children) renderFolderNode(child, false);
        ImGui.treePop();
    }

    private void paintFolderIcon() {
        float size = EditorStyle.iconSizeSmall();
        float left = ImGui.getItemRectMinX() + ImGui.getTextLineHeight() + EditorStyle.framePaddingX() * 2.0f;
        float top = ImGui.getItemRectMinY() + (ImGui.getItemRectMaxY() - ImGui.getItemRectMinY() - size) * 0.5f;
        ImGui.getWindowDrawList().addImage(icons.atlasTextureId(EditorIcon.FOLDER), left, top, left + size, top + size);
    }

    private static String iconSpacing() {
        float spaceWidth = Math.max(1.0f, ImGui.calcTextSizeX(" "));
        float needed = EditorStyle.iconSizeSmall() + EditorStyle.innerSpacing();
        return " ".repeat((int) Math.ceil(needed / spaceWidth));
    }

    private void acceptMoveInto(Path directory) {
        if (!ImGui.beginDragDropTarget()) return;
        String dropped = ImGui.acceptDragDropPayload(PAYLOAD, String.class);
        if (dropped != null && dropped.startsWith(Res.SCHEME)) {
            Path source = AssetFiles.root().resolve(dropped.substring(Res.SCHEME.length()));
            relocate(source, directory.resolve(source.getFileName().toString()), () -> AssetFiles.move(source, directory));
        }
        ImGui.endDragDropTarget();
    }

    private void renderEntryList() {
        ImGui.beginChild("##asset-entries", 0, 0, true);
        if (minecraft.active()) {
            if (icons.toggleButton("assets-mc-grid", EditorIcon.GRID, EditorStyle.iconSizeSmall(), grid)) grid = !grid;
            tooltip(grid ? "Show as a list" : "Show as a grid");
            ImGui.sameLine();
            minecraft.renderEntries(search.get(), grid);
            ImGui.endChild();
            return;
        }
        if (icons.toggleButton("assets-grid", EditorIcon.GRID, EditorStyle.iconSizeSmall(), grid)) grid = !grid;
        tooltip(grid ? "Show as a list" : "Show as a grid");
        ImGui.sameLine();
        ImGui.setNextItemWidth(EditorScale.of(SMALL_COMBO_WIDTH));
        if (ImGui.beginCombo("##assets-type", "Type: " + (typeFilter == null ? "All" : typeFilter.label()))) {
            if (ImGui.selectable("All", typeFilter == null)) typeFilter = null;
            for (AssetKind kind : AssetKind.values()) {
                if (kind != AssetKind.FOLDER && ImGui.selectable(kind.label(), typeFilter == kind)) typeFilter = kind;
            }
            ImGui.endCombo();
        }
        List<AssetEntry> visible = entries.stream().filter(entry -> typeFilter == null || entry.folder() || entry.kind() == typeFilter).toList();
        ImGui.sameLine();
        Texts.muted(visible.size() + " items");
        ImGui.separator();
        if (visible.isEmpty()) {
            EmptyStates.centered(search.get().isBlank() ? "This folder is empty" : "Nothing matches", List.of("Drop files from your computer onto the window to import them"));
        } else if (grid) {
            renderGrid(visible);
        } else {
            for (AssetEntry entry : visible) renderListRow(entry);
        }
        ImGui.dummy(0, EditorScale.of(6.0f));
        if (ImGui.beginPopupContextWindow("##assets-background", ImGuiPopupFlags.MouseButtonRight | ImGuiPopupFlags.NoOpenOverItems)) {
            if (ImGui.menuItem("New folder")) askNewFolder();
            if (ImGui.beginMenu("New script")) {
                for (ScriptTemplate template : ScriptTemplate.SERVER) {
                    if (ImGui.menuItem(template.label())) createScript(template);
                }
                ImGui.endMenu();
            }
            if (ImGui.menuItem("Reveal in file manager")) reveal(current);
            ImGui.endPopup();
        }
        ImGui.endChild();
    }

    private void renderGrid(List<AssetEntry> visible) {
        float cell = EditorScale.of(CELL_SIZE);
        float cellWidth = cell + EditorScale.of(CELL_PADDING);
        int columns = Math.max(1, (int) (ImGui.getContentRegionAvailX() / cellWidth));
        for (int index = 0; index < visible.size(); index++) {
            if (index % columns != 0) ImGui.sameLine();
            renderGridCell(visible.get(index), cell);
        }
    }

    private void renderGridCell(AssetEntry entry, float cell) {
        ImGui.pushID(entry.path().toString());
        ImGui.beginGroup();
        float startX = ImGui.getCursorPosX();
        float startY = ImGui.getCursorPosY();
        float label = EditorScale.of(LABEL_HEIGHT);
        if (ImGui.selectable("##cell", selection.contains(entry.path()), ImGuiSelectableFlags.AllowDoubleClick | ImGuiSelectableFlags.AllowOverlap, cell, cell + label)) {
            applyClick(entry);
            if (ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) activate(entry);
        }
        decorate(entry);
        ImGui.setCursorPos(startX, startY);
        drawThumbnail(entry, cell);
        if (entry.kind() == AssetKind.SOUND) {
            ImGui.setCursorPos(startX + EditorScale.of(2), startY + EditorScale.of(2));
            renderListenButton(entry);
        }
        ImGui.setCursorPos(startX, startY + cell);
        String text = elide(entry.name(), cell);
        float indent = (cell - ImGui.calcTextSize(text).x) * 0.5f;
        if (indent > 0.0f) ImGui.setCursorPosX(ImGui.getCursorPosX() + indent);
        ImGui.textUnformatted(text);
        ImGui.setCursorPos(startX, startY + cell + label);
        ImGui.endGroup();
        ImGui.popID();
    }

    private void renderListRow(AssetEntry entry) {
        ImGui.pushID(entry.path().toString());
        float rowHeight = EditorScale.of(LIST_ROW_HEIGHT);
        float thumbnail = EditorScale.of(LIST_THUMBNAIL_SIZE);
        float startY = ImGui.getCursorPosY();
        if (ImGui.selectable("##row", selection.contains(entry.path()), ImGuiSelectableFlags.AllowDoubleClick, 0.0f, rowHeight)) {
            applyClick(entry);
            if (ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) activate(entry);
        }
        decorate(entry);
        ImGui.setCursorPosY(startY + (rowHeight - thumbnail) * 0.5f);
        ImGui.setCursorPosX(ImGui.getCursorPosX() + EditorScale.of(CELL_PADDING) * 0.5f);
        drawThumbnail(entry, thumbnail);
        ImGui.sameLine();
        ImGui.setCursorPosY(startY + (rowHeight - ImGui.getTextLineHeight()) * 0.5f);
        ImGui.textUnformatted(entry.name());
        if (entry.kind() == AssetKind.SOUND) {
            ImGui.sameLine();
            ImGui.setCursorPosY(startY + (rowHeight - EditorStyle.iconSizeSmall()) * 0.5f - EditorStyle.framePaddingY());
            renderListenButton(entry);
        }
        if (!entry.folder()) {
            ImGui.sameLine();
            ImGui.setCursorPosX(ImGui.getContentRegionMaxX() - EditorScale.of(SIZE_COLUMN_WIDTH));
            ImGui.setCursorPosY(startY + (rowHeight - ImGui.getTextLineHeight()) * 0.5f);
            ImGui.pushStyleColor(ImGuiCol.Text, EditorStyle.COLOR_TEXT_MUTED);
            ImGui.textUnformatted(entry.formattedSize());
            ImGui.popStyleColor();
        }
        ImGui.setCursorPosY(startY + rowHeight);
        ImGui.popID();
    }

    private void drawThumbnail(AssetEntry entry, float size) {
        OptionalLong texture = switch (entry.kind()) {
            case TEXTURE -> thumbnails.get(entry);
            case MODEL -> modelThumbnail(entry);
            default -> OptionalLong.empty();
        };
        if (texture.isPresent()) {
            ImGui.image(texture.getAsLong(), size, size);
            return;
        }
        float iconX = ImGui.getCursorScreenPosX();
        float iconY = ImGui.getCursorScreenPosY();
        icons.draw(entry.kind().icon(), size);
        if (entry.kind() == AssetKind.OTHER || entry.kind() == AssetKind.FONT) ExtensionBadge.draw(entry.name(), iconX, iconY, size);
    }

    private OptionalLong modelThumbnail(AssetEntry entry) {
        String res = AssetFiles.res(entry.path());
        if (res == null) return OptionalLong.empty();
        String name = Integer.toHexString((res + "@" + entry.modified()).hashCode()) + ".png";
        Path png = AssetFiles.root().resolve(".moud").resolve("thumbnails").resolve(name);
        if (!Files.isRegularFile(png)) {
            ModelSnapshots.request(res, png);
            return OptionalLong.empty();
        }
        return thumbnails.get(png, png.toString());
    }

    private void applyClick(AssetEntry entry) {
        if (ImGui.getIO().getKeyCtrl()) {
            if (!selection.remove(entry.path())) selection.add(entry.path());
            return;
        }
        selection.clear();
        selection.add(entry.path());
    }

    private void activate(AssetEntry entry) {
        switch (entry.kind()) {
            case FOLDER -> navigateTo(entry.path());
            case SCRIPT, SHADER, SCENE -> CodeEditor.open(entry.path(), 1);
            case MODEL, SOUND -> place(entry);
            default -> reveal(entry.path());
        }
    }

    private void decorate(AssetEntry entry) {
        String res = AssetFiles.res(entry.path());
        if (res != null && ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload(PAYLOAD, res, ImGuiCond.Once);
            icons.drawInline(entry.kind().icon(), EditorStyle.iconSizeSmall());
            ImGui.textUnformatted(entry.name());
            ImGui.endDragDropSource();
        }
        if (entry.folder()) acceptMoveInto(entry.path());
        if (!ImGui.beginPopupContextItem("asset-context")) return;
        if (!selection.contains(entry.path())) {
            selection.clear();
            selection.add(entry.path());
        }
        if (entry.folder() && ImGui.menuItem("Open")) navigateTo(entry.path());
        if (entry.kind() == AssetKind.SCRIPT || entry.kind() == AssetKind.SHADER || entry.kind() == AssetKind.SCENE) {
            if (ImGui.menuItem("Open in Zed")) CodeEditor.open(entry.path(), 1);
        }
        if (placeable(entry) && ImGui.menuItem("Place in scene")) place(entry);
        if (entry.kind() == AssetKind.SOUND && res != null && ImGui.menuItem(SoundPreview.playing(res) ? "Stop" : "Listen")) SoundPreview.toggle(res);
        if (res != null && ImGui.menuItem("Find references")) showReferences(entry.path(), res);
        if (hasImportSettings(entry) && ImGui.menuItem("Import settings...")) askSettings(entry.path());
        ImGui.separator();
        if (res != null && ImGui.menuItem("Copy path")) ImGui.setClipboardText(res);
        if (ImGui.menuItem("Copy system path")) ImGui.setClipboardText(entry.path().toAbsolutePath().toString());
        if (ImGui.menuItem("Reveal in file manager")) reveal(entry.path());
        ImGui.separator();
        if (ImGui.menuItem("Rename", "F2")) askRename(entry.path());
        if (ImGui.menuItem("Duplicate")) duplicate(entry.path());
        if (ImGui.menuItem("Delete", "Del")) askDelete(List.copyOf(selection));
        ImGui.endPopup();
    }

    private static boolean placeable(AssetEntry entry) {
        return entry.kind() == AssetKind.MODEL || entry.kind() == AssetKind.SOUND || entry.kind() == AssetKind.SCRIPT || entry.kind() == AssetKind.SCENE;
    }

    private void place(AssetEntry entry) {
        String res = AssetFiles.res(entry.path());
        if (res != null) document.placeAsset(res, insertParent.getAsInt(), null);
    }

    private void createScript(ScriptTemplate template) {
        Path folder = current;
        try {
            Path file = AssetFiles.unique(folder, template.name() + ".luau");
            Files.writeString(file, template.code());
            refresh();
            selection.clear();
            selection.add(file);
            CodeEditor.open(file, 1);
        } catch (IOException e) {
            Output.add(Output.Level.ERROR, "editor", "Could not create the script: " + e.getMessage());
        }
    }

    private void askNewFolder() {
        openName("New folder", "New Folder", name -> {
            try {
                Path folder = AssetFiles.unique(current, name.strip());
                Files.createDirectories(folder);
                refresh();
            } catch (IOException | RuntimeException e) {
                Output.add(Output.Level.ERROR, "editor", "Could not create the folder: " + e.getMessage());
            }
        });
    }

    private void askRename(Path path) {
        openName("Rename " + path.getFileName(), path.getFileName().toString(), name -> {
            String original = path.getFileName().toString();
            String clean = name.strip();
            int dot = original.indexOf('.', 1);
            if (!Files.isDirectory(path) && dot > 0 && clean.indexOf('.', 1) < 0) clean += original.substring(dot);
            relocate(path, path.resolveSibling(clean), () -> AssetFiles.rename(path, name));
        });
    }

    private void renderListenButton(AssetEntry entry) {
        String res = AssetFiles.res(entry.path());
        if (res == null) return;
        boolean playing = SoundPreview.playing(res);
        if (icons.iconButton("listen", playing ? EditorIcon.STOP : EditorIcon.PLAY, EditorStyle.iconSizeSmall())) SoundPreview.toggle(res);
        tooltip(playing ? "Stop" : "Listen");
    }

    private static boolean hasImportSettings(AssetEntry entry) {
        return entry.kind() == AssetKind.MODEL || entry.kind() == AssetKind.SOUND || entry.kind() == AssetKind.TEXTURE;
    }

    private void showReferences(Path path, String res) {
        references = AssetReferences.find(document, res, Files.isDirectory(path));
        referencesOf = path.getFileName().toString();
        openReferences = true;
    }

    private void relocate(Path source, Path target, Move move) {
        String from = AssetFiles.res(source);
        String to = AssetFiles.res(target);
        if (from == null || to == null || from.equals(to)) {
            finishMove(move, source, List.of(), from, to);
            return;
        }
        List<AssetReferences.Reference> found = AssetReferences.find(document, from, Files.isDirectory(source));
        if (found.isEmpty()) {
            finishMove(move, source, found, from, to);
            return;
        }
        relocation = new Relocation(source, from, to, found, move);
        openRelocation = true;
    }

    private void finishMove(Move move, Path source, List<AssetReferences.Reference> update, @Nullable String from, @Nullable String to) {
        try {
            Path moved = move.run();
            selection.remove(source);
            selection.add(moved);
            if (!update.isEmpty() && from != null && to != null) {
                int changed = AssetReferences.apply(document, update, from, to);
                Output.add(Output.Level.INFO, "editor", "Updated " + changed + " reference(s) from " + from + " to " + to);
            }
            refresh();
        } catch (IOException e) {
            Output.add(Output.Level.WARN, "editor", "Could not move " + source.getFileName() + ": " + e.getMessage());
        }
    }

    private void renderReferencesDialog() {
        if (openReferences) {
            ImGui.openPopup(REFERENCES_DIALOG);
            openReferences = false;
        }
        if (!Dialogs.begin(REFERENCES_DIALOG, EditorScale.of(DIALOG_WIDTH * 1.6f))) return;
        Dialogs.title(references.isEmpty() ? "Nothing uses " + referencesOf : references.size() + " place(s) use " + referencesOf);
        if (references.isEmpty()) Texts.muted("No instance property or script mentions it. It is safe to rename, move or delete.");
        else Texts.muted("Click one to select the instance or open the script at that line.");
        Dialogs.gap();
        ImGui.beginChild("##references-list", 0, Math.min(references.size(), LISTED_REFERENCES) * ImGui.getFrameHeightWithSpacing() + EditorScale.of(4), false);
        for (int n = 0; n < references.size(); n++) {
            AssetReferences.Reference reference = references.get(n);
            ImGui.pushID(n);
            if (ImGui.selectable(reference.label())) {
                ImGui.closeCurrentPopup();
                openReference(reference);
            }
            ImGui.sameLine();
            ImGui.pushStyleColor(ImGuiCol.Text, EditorStyle.COLOR_TEXT_MUTED);
            ImGui.textUnformatted(reference.value());
            ImGui.popStyleColor();
            ImGui.popID();
        }
        ImGui.endChild();
        Dialogs.gap();
        Dialogs.alignFooter(1);
        if (Dialogs.button("Close##references-close") || ImGui.isKeyPressed(ImGuiKey.Escape)) ImGui.closeCurrentPopup();
        Dialogs.end();
    }

    private void openReference(AssetReferences.Reference reference) {
        if (reference.script() != null) {
            CodeEditor.open(reference.script(), reference.line());
            return;
        }
        if (document.find(reference.instance()) != null) document.selection().set(List.of(reference.instance()));
    }

    private void renderRelocationDialog() {
        if (openRelocation) {
            ImGui.openPopup(RELOCATE_DIALOG);
            openRelocation = false;
        }
        if (!Dialogs.begin(RELOCATE_DIALOG, EditorScale.of(DIALOG_WIDTH * 1.6f))) return;
        Relocation pending = relocation;
        if (pending == null) {
            ImGui.closeCurrentPopup();
            Dialogs.end();
            return;
        }
        Dialogs.title(pending.references().size() + " place(s) point at " + pending.source().getFileName());
        Texts.wrapped("Moving it to " + pending.to() + " breaks them unless they follow. Updating rewrites the properties as one undoable step and edits the scripts on disk.");
        Dialogs.gap();
        ImGui.beginChild("##relocate-list", 0, Math.min(pending.references().size(), LISTED_REFERENCES) * ImGui.getTextLineHeightWithSpacing() + EditorScale.of(4), false);
        for (AssetReferences.Reference reference : pending.references()) {
            ImGui.textUnformatted(reference.label());
            ImGui.sameLine();
            Texts.muted(reference.value());
        }
        ImGui.endChild();
        Dialogs.gap();
        Dialogs.alignFooter(3);
        if (Dialogs.button("Cancel##relocate-cancel") || ImGui.isKeyPressed(ImGuiKey.Escape)) {
            relocation = null;
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (Dialogs.button("Move only##relocate-only")) {
            relocation = null;
            ImGui.closeCurrentPopup();
            finishMove(pending.move(), pending.source(), List.of(), pending.from(), pending.to());
        }
        ImGui.sameLine();
        if (Dialogs.primaryButton("Update " + pending.references().size() + "##relocate-update", true)) {
            relocation = null;
            ImGui.closeCurrentPopup();
            finishMove(pending.move(), pending.source(), pending.references(), pending.from(), pending.to());
        }
        Dialogs.end();
    }

    private void askSettings(Path file) {
        ImportSettings settings = Files.isRegularFile(ImportSettings.sidecar(file)) ? ImportSettings.of(file) : ImportSettings.guess(file);
        settingsFile = file;
        settingsScale[0] = (float) settings.scale();
        settingsVolume[0] = (float) settings.volume();
        settingsNearest.set(settings.nearest());
        settingsStream.set(settings.stream());
        openSettings = true;
    }

    private void renderSettingsDialog() {
        if (openSettings) {
            ImGui.openPopup(SETTINGS_DIALOG);
            openSettings = false;
        }
        if (!Dialogs.begin(SETTINGS_DIALOG, EditorScale.of(DIALOG_WIDTH))) return;
        Path file = settingsFile;
        if (file == null) {
            ImGui.closeCurrentPopup();
            Dialogs.end();
            return;
        }
        AssetKind kind = AssetKind.of(file.getFileName().toString());
        Dialogs.title("Import settings");
        Texts.muted(file.getFileName() + " · saved next to it as " + file.getFileName() + ImportSettings.SUFFIX);
        Dialogs.gap();
        switch (kind) {
            case MODEL -> {
                ImGui.setNextItemWidth(-1);
                ImGui.dragFloat("##scale", settingsScale, 0.01f, 0.01f, 100f, "Scale %.2f");
                Texts.muted("Placed meshes start at their own size times this.");
            }
            case TEXTURE -> {
                ImGui.checkbox("Pixel art (nearest filtering)", settingsNearest);
                Texts.muted("Keeps small images crisp instead of blurring them when drawn larger.");
            }
            case SOUND -> {
                ImGui.setNextItemWidth(-1);
                ImGui.sliderFloat("##volume", settingsVolume, 0f, 2f, "Volume %.2f");
                ImGui.checkbox("Stream from disk", settingsStream);
                Texts.muted("Streaming suits long music; short effects load faster whole. Applies to newly placed Sounds.");
            }
            default -> Texts.muted("This kind of file has no import settings.");
        }
        Dialogs.gap();
        Dialogs.alignFooter(3);
        if (Dialogs.button("Cancel##settings-cancel") || ImGui.isKeyPressed(ImGuiKey.Escape)) ImGui.closeCurrentPopup();
        ImGui.sameLine();
        if (Dialogs.button("Guess##settings-guess")) askSettingsGuess(file);
        ImGui.sameLine();
        if (Dialogs.primaryButton("Save##settings-save", true)) {
            ImGui.closeCurrentPopup();
            try {
                new ImportSettings(settingsScale[0], settingsNearest.get(), settingsStream.get(), settingsVolume[0]).save(file);
                Output.add(Output.Level.INFO, "editor", "Saved import settings for " + file.getFileName() + ", they apply when the file is next loaded or placed");
            } catch (IOException e) {
                Output.add(Output.Level.WARN, "editor", "Could not save import settings: " + e.getMessage());
            }
        }
        Dialogs.end();
    }

    private void askSettingsGuess(Path file) {
        ImportSettings guessed = ImportSettings.guess(file);
        settingsScale[0] = (float) guessed.scale();
        settingsVolume[0] = (float) guessed.volume();
        settingsNearest.set(guessed.nearest());
        settingsStream.set(guessed.stream());
    }

    private void duplicate(Path path) {
        try {
            Path copy = AssetFiles.duplicate(path);
            selection.clear();
            selection.add(copy);
            refresh();
        } catch (IOException e) {
            Output.add(Output.Level.WARN, "editor", "Duplicate failed: " + e.getMessage());
        }
    }

    private void reveal(Path path) {
        Optional<String> failure = FileManagerReveal.reveal(path);
        failure.ifPresent(why -> Output.add(Output.Level.WARN, "editor", "Could not reveal the file: " + why));
    }

    private void openName(String title, String initial, Consumer<String> action) {
        nameTitle = title;
        nameInput.set(initial);
        nameAction = action;
        openNameDialog = true;
    }

    private void renderNameDialog() {
        if (openNameDialog) {
            ImGui.openPopup(NAME_DIALOG);
            openNameDialog = false;
        }
        if (!Dialogs.begin(NAME_DIALOG, EditorScale.of(DIALOG_WIDTH))) return;
        Dialogs.title(nameTitle);
        if (ImGui.isWindowAppearing()) ImGui.setKeyboardFocusHere();
        ImGui.setNextItemWidth(-1.0f);
        boolean submitted = ImGui.inputText("##asset-name-input", nameInput, ImGuiInputTextFlags.EnterReturnsTrue);
        Dialogs.gap();
        Dialogs.alignFooter(2);
        if (Dialogs.button("Cancel##asset-name-cancel") || ImGui.isKeyPressed(ImGuiKey.Escape)) ImGui.closeCurrentPopup();
        ImGui.sameLine();
        boolean valid = !nameInput.get().isBlank();
        if ((Dialogs.primaryButton("OK##asset-name-ok", valid) || submitted) && valid) {
            ImGui.closeCurrentPopup();
            nameAction.accept(nameInput.get());
        }
        Dialogs.end();
    }

    private void handleDeleteShortcut() {
        if (!ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows) || ImGui.getIO().getWantTextInput()) return;
        if (ImGui.isKeyPressed(ImGuiKey.Delete, false) && !selection.isEmpty()) askDelete(List.copyOf(selection));
        if (ImGui.isKeyPressed(ImGuiKey.F2, false) && selection.size() == 1) askRename(selection.iterator().next());
    }

    private void askDelete(List<Path> paths) {
        pendingDelete = paths;
        openDeleteDialog = true;
    }

    private void renderDeleteDialog() {
        if (openDeleteDialog) {
            ImGui.openPopup(DELETE_DIALOG);
            openDeleteDialog = false;
        }
        if (!Dialogs.begin(DELETE_DIALOG, EditorScale.of(DIALOG_WIDTH))) return;
        Dialogs.title(pendingDelete.size() == 1 ? "Delete this file?" : "Delete " + pendingDelete.size() + " items?");
        Texts.muted(pendingDelete.size() == 1 ? pendingDelete.getFirst().getFileName() + " will be removed from disk." : "They will be removed from disk.");
        Dialogs.gap();
        Dialogs.alignFooter(2);
        if (Dialogs.button("Cancel##asset-delete-cancel")) ImGui.closeCurrentPopup();
        ImGui.sameLine();
        if (Dialogs.primaryButton("Delete##asset-delete-ok", true)) {
            ImGui.closeCurrentPopup();
            for (Path path : pendingDelete) {
                try {
                    AssetFiles.delete(path);
                } catch (IOException e) {
                    MoudMod.LOG.warn("could not delete {}", path, e);
                    Output.add(Output.Level.WARN, "editor", "Delete failed: " + e.getMessage());
                }
            }
            selection.clear();
            refresh();
        }
        Dialogs.end();
    }

    private static void tooltip(String text) {
        if (ImGui.isItemHovered()) ImGui.setTooltip(text);
    }

    private static String elide(String name, float width) {
        if (ImGui.calcTextSize(name).x <= width) return name;
        int extensionStart = name.lastIndexOf('.');
        if (extensionStart > 0 && name.length() - extensionStart <= MAXIMUM_KEPT_EXTENSION) {
            String stem = name.substring(0, extensionStart);
            String extension = name.substring(extensionStart);
            for (int length = stem.length() - 1; length >= 1; length--) {
                String candidate = stem.substring(0, length) + ELLIPSIS + extension;
                if (ImGui.calcTextSize(candidate).x <= width) return candidate;
            }
        }
        for (int length = name.length() - 1; length >= 1; length--) {
            String candidate = name.substring(0, length) + ELLIPSIS;
            if (ImGui.calcTextSize(candidate).x <= width) return candidate;
        }
        return ELLIPSIS;
    }
}
