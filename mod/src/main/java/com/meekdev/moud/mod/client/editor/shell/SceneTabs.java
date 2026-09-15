package com.meekdev.moud.mod.client.editor.shell;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.place.Toml;
import com.meekdev.moud.core.place.TomlEdit;
import com.meekdev.moud.core.scene.Json;
import com.meekdev.moud.core.scene.Scene;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.ClientPlace;
import com.meekdev.moud.mod.client.EditMode;
import com.meekdev.moud.mod.client.editor.assets.AssetFiles;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.document.SceneLink;
import com.meekdev.moud.mod.client.editor.kit.Dialogs;
import com.meekdev.moud.mod.client.editor.kit.SearchField;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.place.Output;
import com.meekdev.moud.mod.place.SceneBackups;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiTabBarFlags;
import imgui.flag.ImGuiTabItemFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

final class SceneTabs {

    private static final String SWITCH_PROMPT = "##scene-switch";
    private static final String NAME_DIALOG = "##scene-name";
    private static final String OPEN_DIALOG = "##scene-open";
    private static final String BACKUPS_DIALOG = "##scene-backups";
    private static final String RECOVER_DIALOG = "##scene-recover";
    private static final float DIALOG_WIDTH = 440.0f;
    private static final float LIST_HEIGHT = 260.0f;
    private static final int SEARCH_DEPTH = 8;
    private static final long PENDING_MILLIS = 5000;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM, HH:mm:ss", Locale.ROOT);

    private final SceneDocument document;
    private final List<String> tabs = new ArrayList<>();
    private final Set<String> recoveryChecked = new HashSet<>();
    private final ImString nameInput = new ImString(128);
    private final ImString openSearch = new ImString(128);
    private @Nullable Path tabsRoot;
    private String current = "";
    private boolean selectCurrent;
    private @Nullable String pending;
    private long pendingAt;
    private @Nullable String afterSave;
    private @Nullable String closing;
    private SceneBackups.@Nullable Backup recovery;
    private boolean openSwitch;
    private boolean openName;
    private boolean openOpen;
    private boolean openBackups;
    private boolean openRecover;
    private String nameTitle = "";
    private String nameHint = "";
    private Consumer<String> nameAction = name -> {};
    private List<String> allScenes = List.of();

    SceneTabs(SceneDocument document) {
        this.document = document;
    }

    void frame() {
        Path root = ClientPlace.root();
        if (root == null) return;
        if (!root.equals(tabsRoot)) {
            tabsRoot = root;
            readTabs(root);
            recoveryChecked.clear();
        }
        String file = SceneLink.file();
        if (!file.isEmpty() && !file.equals(current)) {
            current = file;
            selectCurrent = true;
            pending = null;
            if (!tabs.contains(file)) tabs.add(file);
            if (closing != null && !closing.equals(file)) tabs.remove(closing);
            closing = null;
            writeTabs(root);
        }
        if (pending != null && !openSwitch && System.currentTimeMillis() - pendingAt > PENDING_MILLIS && !ImGui.isPopupOpen(SWITCH_PROMPT)) {
            pending = null;
            closing = null;
            selectCurrent = true;
        }
        if (afterSave != null && !document.dirty()) {
            String target = afterSave;
            afterSave = null;
            SceneLink.open(target);
        }
        if (!current.isEmpty() && EditMode.editing() && recoveryChecked.add(current)) {
            recovery = SceneBackups.unsavedWork(root, current);
            if (recovery != null) openRecover = true;
        }
    }

    void renderTabs() {
        if (tabs.isEmpty()) return;
        int bar = ImGuiTabBarFlags.Reorderable | ImGuiTabBarFlags.FittingPolicyScroll;
        if (!ImGui.beginTabBar("##scene-tabs", bar)) return;
        String start = startScene();
        for (String scene : List.copyOf(tabs)) {
            ImBoolean open = new ImBoolean(true);
            int flags = ImGuiTabItemFlags.None;
            if (scene.equals(current) && document.dirty()) flags |= ImGuiTabItemFlags.UnsavedDocument;
            if (selectCurrent && scene.equals(current)) flags |= ImGuiTabItemFlags.SetSelected;
            boolean selected = tabs.size() > 1 ? ImGui.beginTabItem(name(scene) + "###" + scene, open, flags) : ImGui.beginTabItem(name(scene) + "###" + scene, flags);
            if (ImGui.isItemHovered()) ImGui.setTooltip(scene + (scene.equals(start) ? "\nStart scene: players arrive here" : ""));
            if (ImGui.beginPopupContextItem("##tab-menu-" + scene)) {
                if (ImGui.menuItem("Open", null, false, !scene.equals(current))) switchTo(scene);
                if (ImGui.menuItem("Set as start scene", null, false, !scene.equals(start))) setStart(scene);
                if (ImGui.menuItem("Close", null, false, tabs.size() > 1)) close(scene);
                ImGui.endPopup();
            }
            if (selected) {
                if (!selectCurrent && !scene.equals(current) && pending == null && afterSave == null) switchTo(scene);
                ImGui.endTabItem();
            }
            if (!open.get()) close(scene);
        }
        selectCurrent = false;
        if (ImGui.tabItemButton("+##scene-add", ImGuiTabItemFlags.Trailing | ImGuiTabItemFlags.NoTooltip)) ImGui.openPopup("##scene-add-menu");
        if (ImGui.isItemHovered()) ImGui.setTooltip("New or open a scene");
        if (ImGui.beginPopup("##scene-add-menu")) {
            if (ImGui.menuItem("New scene...")) askNew();
            if (ImGui.menuItem("Open scene...")) askOpen();
            ImGui.endPopup();
        }
        ImGui.endTabBar();
    }

    void renderDialogs() {
        renderSwitchPrompt();
        renderNameDialog();
        renderOpenDialog();
        renderBackupsDialog();
        renderRecoverDialog();
    }

    boolean ready() {
        return EditMode.editing() && EditMode.allowed() && ClientPlace.root() != null && !current.isEmpty();
    }

    void switchTo(String scene) {
        if (scene.equals(current)) return;
        if (!tabs.contains(scene)) tabs.add(scene);
        pendingAt = System.currentTimeMillis();
        if (document.dirty()) {
            pending = scene;
            openSwitch = true;
            selectCurrent = true;
            return;
        }
        pending = scene;
        SceneLink.open(scene);
    }

    void askNew() {
        openNameDialog("New scene", "Level 2", "Saved in scenes/ as a .scene file", name -> {
            Path root = ClientPlace.root();
            if (root == null) return;
            try {
                Path folder = root.resolve("scenes");
                Files.createDirectories(folder);
                Path file = AssetFiles.unique(folder, clean(name) + ".scene");
                Files.writeString(file, Scene.save(List.of()));
                String res = AssetFiles.res(file);
                if (res != null) switchTo(res);
            } catch (IOException | RuntimeException e) {
                Output.add(Output.Level.ERROR, "editor", "Could not create the scene: " + e.getMessage());
            }
        });
    }

    void askOpen() {
        allScenes = listScenes();
        openSearch.set("");
        openOpen = true;
    }

    void askSaveAs() {
        openNameDialog("Save scene as", name(current) + " copy", "A copy is written and opened, the original stays as it was saved", name -> {
            Path root = ClientPlace.root();
            if (root == null) return;
            Path currentFile = root.resolve(Res.parse(current));
            Path folder = currentFile.getParent() == null ? root.resolve("scenes") : currentFile.getParent();
            Path target = folder.resolve(clean(name) + ".scene");
            if (Files.exists(target)) {
                Output.add(Output.Level.WARN, "editor", target.getFileName() + " already exists, pick another name");
                return;
            }
            String res = AssetFiles.res(target);
            if (res == null) return;
            tabs.add(res);
            SceneLink.saveAs(res);
        });
    }

    void askBackups() {
        openBackups = true;
    }

    void setStart(String scene) {
        Path root = ClientPlace.root();
        if (root == null) return;
        Path toml = root.resolve("place.toml");
        try {
            String text = Files.isRegularFile(toml) ? Files.readString(toml) : "";
            Files.writeString(toml, TomlEdit.set(text, "entry", "scene", scene));
            Output.add(Output.Level.INFO, "editor", scene + " is now the start scene, players arrive there when the place starts");
        } catch (IOException e) {
            Output.add(Output.Level.ERROR, "editor", "Could not edit place.toml: " + e.getMessage());
        }
    }

    String current() {
        return current;
    }

    private void close(String scene) {
        if (tabs.size() <= 1) return;
        if (!scene.equals(current)) {
            tabs.remove(scene);
            if (tabsRoot != null) writeTabs(tabsRoot);
            return;
        }
        int index = tabs.indexOf(scene);
        String neighbour = tabs.get(index > 0 ? index - 1 : index + 1);
        closing = scene;
        switchTo(neighbour);
    }

    private void renderSwitchPrompt() {
        if (openSwitch) {
            ImGui.openPopup(SWITCH_PROMPT);
            openSwitch = false;
        }
        if (!Dialogs.begin(SWITCH_PROMPT, DIALOG_WIDTH)) return;
        String target = pending == null ? "" : pending;
        Dialogs.title("Save " + name(current) + " first?");
        Texts.wrapped(current + " has changes that are not saved. Opening " + name(target) + " replaces what is in the viewport.");
        Dialogs.gap();
        Dialogs.alignFooter(3);
        if (Dialogs.button("Cancel##switch-cancel") || ImGui.isKeyPressed(ImGuiKey.Escape)) {
            pending = null;
            closing = null;
            selectCurrent = true;
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (Dialogs.button("Don't save##switch-discard")) {
            ImGui.closeCurrentPopup();
            if (!target.isEmpty()) SceneLink.open(target);
        }
        ImGui.sameLine();
        if (Dialogs.primaryButton("Save##switch-save", true)) {
            ImGui.closeCurrentPopup();
            afterSave = target.isEmpty() ? null : target;
            document.save();
        }
        Dialogs.end();
    }

    private void openNameDialog(String title, String initial, String hint, Consumer<String> action) {
        nameTitle = title;
        nameHint = hint;
        nameInput.set(initial);
        nameAction = action;
        openName = true;
    }

    private void renderNameDialog() {
        if (openName) {
            ImGui.openPopup(NAME_DIALOG);
            openName = false;
        }
        if (!Dialogs.begin(NAME_DIALOG, DIALOG_WIDTH)) return;
        Dialogs.title(nameTitle);
        Texts.muted(nameHint);
        if (ImGui.isWindowAppearing()) ImGui.setKeyboardFocusHere();
        ImGui.setNextItemWidth(-1);
        boolean submitted = ImGui.inputText("##scene-name-input", nameInput, ImGuiInputTextFlags.EnterReturnsTrue);
        boolean valid = !clean(nameInput.get()).isEmpty();
        Dialogs.gap();
        Dialogs.alignFooter(2);
        if (Dialogs.button("Cancel##scene-name-cancel") || ImGui.isKeyPressed(ImGuiKey.Escape)) ImGui.closeCurrentPopup();
        ImGui.sameLine();
        if ((Dialogs.primaryButton("OK##scene-name-ok", valid) || submitted) && valid) {
            ImGui.closeCurrentPopup();
            nameAction.accept(nameInput.get());
        }
        Dialogs.end();
    }

    private void renderOpenDialog() {
        if (openOpen) {
            ImGui.openPopup(OPEN_DIALOG);
            openOpen = false;
        }
        if (!Dialogs.begin(OPEN_DIALOG, DIALOG_WIDTH)) return;
        Dialogs.title("Open a scene");
        SearchField.render("##scene-open-search", "Search scenes", openSearch, ImGui.getContentRegionAvailX());
        Dialogs.gap();
        String needle = openSearch.get().strip().toLowerCase(Locale.ROOT);
        String start = startScene();
        ImGui.beginChild("##scene-open-list", 0, EditorScale.of(LIST_HEIGHT), true);
        int shown = 0;
        for (String scene : allScenes) {
            if (!needle.isEmpty() && !scene.toLowerCase(Locale.ROOT).contains(needle)) continue;
            shown++;
            if (ImGui.selectable(name(scene) + "##" + scene, scene.equals(current))) {
                ImGui.closeCurrentPopup();
                switchTo(scene);
            }
            ImGui.sameLine();
            ImGui.pushStyleColor(ImGuiCol.Text, EditorStyle.COLOR_TEXT_MUTED);
            ImGui.textUnformatted(scene + (scene.equals(start) ? "  · start" : ""));
            ImGui.popStyleColor();
        }
        if (shown == 0) Texts.muted(allScenes.isEmpty() ? "This place has no .scene files yet" : "Nothing matches");
        ImGui.endChild();
        Dialogs.gap();
        Dialogs.alignFooter(2);
        if (Dialogs.button("New...##scene-open-new")) {
            ImGui.closeCurrentPopup();
            askNew();
        }
        ImGui.sameLine();
        if (Dialogs.button("Close##scene-open-close") || ImGui.isKeyPressed(ImGuiKey.Escape)) ImGui.closeCurrentPopup();
        Dialogs.end();
    }

    private void renderBackupsDialog() {
        if (openBackups) {
            ImGui.openPopup(BACKUPS_DIALOG);
            openBackups = false;
        }
        if (!Dialogs.begin(BACKUPS_DIALOG, DIALOG_WIDTH * 1.3f)) return;
        Path root = ClientPlace.root();
        Dialogs.title("Backups of " + name(current));
        Texts.wrapped("While you edit, the scene is copied every minute it changed, and the saved file is copied before each save. The last " + SceneBackups.KEPT + " are kept in .moud/backups.");
        Dialogs.gap();
        List<SceneBackups.Backup> backups = root == null ? List.of() : SceneBackups.list(root, current);
        ImGui.beginChild("##scene-backups-list", 0, EditorScale.of(LIST_HEIGHT), true);
        if (backups.isEmpty()) Texts.muted("No backups yet");
        for (SceneBackups.Backup backup : backups) {
            ImGui.pushID(backup.file().toString());
            ImGui.alignTextToFramePadding();
            ImGui.textUnformatted(when(backup.time()));
            ImGui.sameLine();
            Texts.muted(backup.kind() == SceneBackups.Kind.AUTO ? "autosave" : "saved copy");
            ImGui.sameLine(ImGui.getContentRegionMaxX() - Dialogs.buttonWidth());
            if (ImGui.button("Restore", Dialogs.buttonWidth(), 0) && root != null) {
                ImGui.closeCurrentPopup();
                SceneLink.restore(root.relativize(backup.file()).toString().replace('\\', '/'));
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip("Replaces what is open with this copy. It stays unsaved until you save.");
            ImGui.popID();
        }
        ImGui.endChild();
        Dialogs.gap();
        Dialogs.alignFooter(1);
        if (Dialogs.button("Close##scene-backups-close") || ImGui.isKeyPressed(ImGuiKey.Escape)) ImGui.closeCurrentPopup();
        Dialogs.end();
    }

    private void renderRecoverDialog() {
        if (openRecover) {
            ImGui.openPopup(RECOVER_DIALOG);
            openRecover = false;
        }
        if (!Dialogs.begin(RECOVER_DIALOG, DIALOG_WIDTH)) return;
        SceneBackups.Backup found = recovery;
        Path root = ClientPlace.root();
        Dialogs.title("Recover unsaved work?");
        Texts.wrapped(name(current) + " has an autosave from " + (found == null ? "earlier" : when(found.time())) + " that is newer than the saved file. The editor may have closed before it was saved.");
        Dialogs.gap();
        Dialogs.alignFooter(2);
        if (Dialogs.button("Keep saved##recover-skip") || ImGui.isKeyPressed(ImGuiKey.Escape)) {
            recovery = null;
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (Dialogs.primaryButton("Recover##recover-ok", found != null && root != null)) {
            ImGui.closeCurrentPopup();
            if (found != null && root != null) SceneLink.restore(root.relativize(found.file()).toString().replace('\\', '/'));
            recovery = null;
        }
        Dialogs.end();
    }

    private List<String> listScenes() {
        Path root = ClientPlace.root();
        if (root == null) return List.of();
        try (Stream<Path> walk = Files.walk(root, SEARCH_DEPTH)) {
            return walk.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".scene"))
                    .filter(file -> !hidden(root, file))
                    .map(AssetFiles::res)
                    .filter(res -> res != null)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static boolean hidden(Path root, Path file) {
        for (Path part : root.relativize(file)) {
            if (part.toString().startsWith(".")) return true;
        }
        return false;
    }

    private static String startScene() {
        Path root = ClientPlace.root();
        if (root == null) return "";
        try {
            Path toml = root.resolve("place.toml");
            if (!Files.isRegularFile(toml)) return "";
            Object entry = Toml.parse(Files.readString(toml)).get("entry");
            return entry instanceof Map<?, ?> map && map.get("scene") instanceof String scene ? scene : "";
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    private void readTabs(Path root) {
        tabs.clear();
        Path file = root.resolve(".moud").resolve("editor.json");
        try {
            if (Files.isRegularFile(file) && Json.parse(Files.readString(file)) instanceof Map<?, ?> map && map.get("scenes") instanceof List<?> scenes) {
                for (Object scene : scenes) {
                    if (scene instanceof String res && Files.isRegularFile(root.resolve(Res.parse(res))) && !tabs.contains(res)) tabs.add(res);
                }
            }
        } catch (IOException | RuntimeException e) {
            MoudMod.LOG.warn("could not read the open scene tabs from {}", file, e);
        }
    }

    private void writeTabs(Path root) {
        Path file = root.resolve(".moud").resolve("editor.json");
        try {
            Files.createDirectories(file.getParent());
            Map<String, Object> state = new LinkedHashMap<>();
            if (Files.isRegularFile(file) && Json.parse(Files.readString(file)) instanceof Map<?, ?> existing) {
                existing.forEach((key, value) -> state.put(String.valueOf(key), value));
            }
            state.put("scenes", List.copyOf(tabs));
            Files.writeString(file, Json.write(state));
        } catch (IOException | RuntimeException e) {
            MoudMod.LOG.warn("could not remember the open scene tabs in {}", file, e);
        }
    }

    private static String name(String scene) {
        String file = scene.substring(scene.lastIndexOf('/') + 1);
        return file.endsWith(".scene") ? file.substring(0, file.length() - ".scene".length()) : file;
    }

    private static String clean(String name) {
        return name.strip().replaceAll("[\\\\/:*?\"<>|]", "").replaceAll("\\.scene$", "");
    }

    private static String when(long millis) {
        return WHEN.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }
}
