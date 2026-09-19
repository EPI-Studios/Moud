package com.meekdev.moud.mod.client.editor.plugin;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.addon.Addons;
import com.meekdev.moud.mod.client.EditMode;
import com.meekdev.moud.mod.client.editor.command.Shortcut;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.files.FileManagerReveal;
import com.meekdev.moud.mod.client.editor.kit.Notices;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.kit.ToggleStyle;
import com.meekdev.moud.mod.client.editor.kit.Toolbars;
import com.meekdev.moud.mod.client.editor.style.EditorIcon;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import com.meekdev.moud.mod.client.editor.viewport.ViewportPanel;
import com.meekdev.moud.mod.place.Game;
import com.meekdev.moud.mod.place.Languages;
import com.meekdev.moud.mod.place.Output;
import com.meekdev.moud.mod.place.PlaceFileRef;
import com.meekdev.moud.mod.place.PlaceToml;
import com.meekdev.moud.script.api.ModuleSource;
import com.meekdev.moud.script.api.PluginRef;
import com.meekdev.moud.script.engine.PlaceModules;
import com.meekdev.moud.script.engine.ScriptLanguage;
import com.meekdev.moud.script.err.ScriptError;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.plugin.Plugins;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiHoveredFlags;
import imgui.type.ImBoolean;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public final class EditorPlugins {

    public static final Path USER_FOLDER = Path.of(System.getProperty("user.home"), ".moud", "plugins");

    private static final Path SETTINGS = Path.of(System.getProperty("user.home"), ".moud", "plugin-settings.json");
    private static final String FOLDER = "plugins";
    private static final String EXTENSION = ".luau";
    private static final String SIDE = "plugins";
    private static final long SCAN_NANOS = 500_000_000L;

    private record Source(Path file, String chunk, FileTime modified, long size) {}

    private final SceneDocument document;
    private final IconWidgets icons;
    private final ViewportPanel viewport;
    private final PluginSettings settings = new PluginSettings(SETTINGS);
    private final Map<String, Source> sources = new LinkedHashMap<>();
    private final Map<String, PanelWidgets> widgets = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private @Nullable Host host;
    private @Nullable Instance hostWorld;
    private @Nullable Plugins plugins;
    private @Nullable PluginEdits edits;
    private @Nullable List<Integer> selected;
    private long scannedAt;
    private boolean restart;

    public EditorPlugins(SceneDocument document, IconWidgets icons, ViewportPanel viewport) {
        this.document = document;
        this.icons = icons;
        this.viewport = viewport;
    }

    public void tick() {
        if (host != null && (!EditMode.editing() || document.world() != hostWorld)) stop();
    }

    public void frame() {
        Instance world = document.world();
        if (!EditMode.editing() || world == null || Game.standalone()) {
            stop();
            return;
        }
        if (restart || host != null && hostWorld != world) stop();
        restart = false;
        if (host == null) start(world);
        Host running = host;
        if (running == null) return;
        scan();
        settle(() -> running.renderStep(ImGui.getIO().getDeltaTime()));
        List<Integer> now = document.selection().all();
        if (selected != null && !selected.equals(now)) {
            Plugins loaded = plugins;
            if (loaded != null) settle(loaded::selectionChanged);
        }
        selected = now;
    }

    public boolean capturing() {
        return plugins != null && plugins.capturing();
    }

    public void viewportClicked(PluginRef.@Nullable Mouse mouse) {
        Plugins loaded = plugins;
        if (loaded != null && mouse != null) settle(() -> loaded.viewportClicked(mouse));
    }

    public void renderToolbar() {
        Plugins loaded = plugins;
        if (loaded == null) return;
        for (Plugins.Toolbar toolbar : List.copyOf(loaded.toolbars())) {
            if (toolbar.buttons().isEmpty()) continue;
            Toolbars.groupSeparator();
            int index = 0;
            for (Plugins.Button button : List.copyOf(toolbar.buttons())) {
                if (index > 0) ImGui.sameLine();
                index++;
                String id = "plugin-" + toolbar.plugin().name() + "-" + toolbar.name() + "-" + index;
                EditorIcon icon = icon(button);
                ImGui.beginDisabled(!button.enabled());
                boolean clicked;
                if (icon != null) {
                    clicked = icons.toggleButton(id, icon, EditorStyle.iconSizeToolbar(), button.active());
                } else {
                    ToggleStyle.push(button.active());
                    clicked = Toolbars.textButton(button.text() + "##" + id);
                    ToggleStyle.pop(button.active());
                }
                ImGui.endDisabled();
                if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
                    ImGui.setTooltip(button.tooltip().isEmpty() ? button.text() : button.tooltip());
                }
                if (clicked) settle(button::click);
            }
        }
    }

    public void renderMenu() {
        if (!ImGui.beginMenu("Plugins")) return;
        Plugins loaded = plugins;
        if (loaded != null) {
            List<Plugins.Command> commands = List.copyOf(loaded.commands());
            for (Plugins.Command command : commands) {
                Shortcut shortcut = shortcut(command);
                String label = command.name() + "###plugin-command-" + command.plugin().name() + "-" + command.name();
                if (ImGui.menuItem(label, shortcut == null ? "" : shortcut.label())) settle(() -> loaded.run(command));
            }
            List<Plugins.Panel> panels = List.copyOf(loaded.panels());
            if (!commands.isEmpty() && !panels.isEmpty()) ImGui.separator();
            for (Plugins.Panel panel : panels) {
                if (ImGui.menuItem(panel.title() + "###plugin-panel-menu-" + panel.key(), "", panel.visible())) panel.visible(!panel.visible());
            }
            if (loaded.names().isEmpty()) Texts.muted("No plugins yet, add .luau files to plugins/");
            ImGui.separator();
        }
        if (ImGui.menuItem("Reload Plugins")) restart = true;
        if (ImGui.menuItem("Open Plugins Folder")) reveal(PlaceToml.root().resolve(FOLDER));
        if (ImGui.menuItem("Open User Plugins Folder")) reveal(USER_FOLDER);
        ImGui.endMenu();
    }

    public void renderPanels(ToIntFunction<String> docks) {
        Plugins loaded = plugins;
        if (loaded == null) return;
        for (Plugins.Panel panel : List.copyOf(loaded.panels())) {
            if (!panel.visible()) continue;
            int node = docks.applyAsInt(panel.dock());
            if (node != 0) ImGui.setNextWindowDockID(node, ImGuiCond.FirstUseEver);
            ImGui.setNextWindowSize(EditorScale.of((float) panel.width()), EditorScale.of((float) panel.height()), ImGuiCond.FirstUseEver);
            ImBoolean open = new ImBoolean(true);
            boolean shown = ImGui.begin(panel.title() + "###plugin-panel-" + panel.key(), open);
            try {
                if (shown && panel.failure() != null) Notices.danger(panel.failure());
                else if (shown) {
                    PanelWidgets screen = widgets.computeIfAbsent(panel.key(), key -> new PanelWidgets());
                    ImGui.pushID(panel.key());
                    try {
                        settle(() -> loaded.draw(panel, screen));
                    } finally {
                        ImGui.popID();
                    }
                }
            } finally {
                ImGui.end();
            }
            if (!open.get()) panel.visible(false);
        }
    }

    public void handleShortcuts() {
        Plugins loaded = plugins;
        if (loaded == null || ImGui.getIO().getWantTextInput()) return;
        for (Plugins.Command command : List.copyOf(loaded.commands())) {
            Shortcut shortcut = shortcut(command);
            if (shortcut != null && shortcut.pressed()) settle(() -> loaded.run(command));
        }
    }

    private void start(Instance world) {
        ScriptLanguage luau = Languages.all().stream().filter(language -> language.extensions().contains("luau")).findFirst().orElse(null);
        if (luau == null) return;
        Path root = PlaceToml.root();
        Plugins[] made = new Plugins[1];
        PluginEdits changes = new PluginEdits(document, () -> made[0] == null ? "" : made[0].running());
        Plugins fresh = new Plugins(new PluginDesk(document, changes, settings, viewport::mouse));
        made[0] = fresh;
        Host started = new Host(world, Addons.classes(), true)
                .modules(modules(root))
                .files(new PlaceFileRef(root))
                .plugins(fresh)
                .edits(changes)
                .onError(error -> report(fresh, error))
                .onPrint(line -> Output.add(Output.Level.INFO, SIDE, line));
        try {
            started.start(luau);
        } catch (RuntimeException e) {
            MoudMod.LOG.error("plugins could not start", e);
            Output.add(Output.Level.ERROR, SIDE, "plugins could not start: " + e.getMessage());
            started.close();
            return;
        }
        host = started;
        hostWorld = world;
        plugins = fresh;
        edits = changes;
        selected = null;
        scannedAt = 0;
    }

    private void stop() {
        if (host == null) return;
        PluginEdits changes = edits;
        if (changes != null) {
            try {
                changes.flush();
            } catch (RuntimeException e) {
                MoudMod.LOG.warn("plugin changes could not be kept", e);
            }
        }
        try {
            host.close();
        } catch (RuntimeException e) {
            MoudMod.LOG.warn("plugins did not close cleanly", e);
        }
        host = null;
        hostWorld = null;
        plugins = null;
        edits = null;
        selected = null;
        sources.clear();
        widgets.clear();
    }

    private void settle(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            MoudMod.LOG.error("a plugin broke the editor frame", e);
            Output.add(Output.Level.ERROR, SIDE, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        PluginEdits changes = edits;
        if (changes == null) return;
        try {
            changes.flush();
        } catch (RuntimeException e) {
            MoudMod.LOG.error("plugin changes could not be kept", e);
            Output.add(Output.Level.ERROR, SIDE, "plugin changes could not be kept: " + e.getMessage());
        }
    }

    private void report(Plugins from, ScriptError error) {
        String who = from.running();
        MoudMod.LOG.warn("[plugins] {}", error.getMessage());
        Output.add(Output.Level.ERROR, SIDE, (who.isEmpty() ? "" : who + ": ") + error.getMessage());
    }

    private void scan() {
        long now = System.nanoTime();
        if (scannedAt != 0 && now - scannedAt < SCAN_NANOS) return;
        scannedAt = now;
        Plugins loaded = plugins;
        if (loaded == null) return;
        Map<String, Source> found = new LinkedHashMap<>();
        find(PlaceToml.root().resolve(FOLDER), FOLDER + "/", found);
        find(USER_FOLDER, "~/.moud/plugins/", found);
        for (String name : List.copyOf(sources.keySet())) {
            if (found.containsKey(name)) continue;
            sources.remove(name);
            settle(() -> loaded.unload(name));
            Output.add(Output.Level.SYSTEM, SIDE, "unloaded " + name);
        }
        for (Map.Entry<String, Source> entry : found.entrySet()) {
            Source before = sources.get(entry.getKey());
            Source source = entry.getValue();
            if (before != null && before.equals(source)) continue;
            sources.put(entry.getKey(), source);
            String code;
            try {
                code = Files.readString(source.file());
            } catch (IOException e) {
                Output.add(Output.Level.ERROR, SIDE, "could not read " + source.chunk() + ": " + e.getMessage());
                continue;
            }
            settle(() -> loaded.load(entry.getKey(), source.chunk(), code));
            Output.add(Output.Level.SYSTEM, SIDE, (before == null ? "loaded " : "reloaded ") + entry.getKey());
        }
    }

    private void find(Path folder, String prefix, Map<String, Source> into) {
        if (!Files.isDirectory(folder)) return;
        List<Path> files = new ArrayList<>();
        try (Stream<Path> listed = Files.list(folder)) {
            listed.filter(file -> file.getFileName().toString().endsWith(EXTENSION) && !file.getFileName().toString().endsWith(".d" + EXTENSION))
                    .filter(Files::isRegularFile).sorted().forEach(files::add);
        } catch (IOException | UncheckedIOException e) {
            MoudMod.LOG.warn("could not list {}", folder, e);
            return;
        }
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            String name = fileName.substring(0, fileName.length() - EXTENSION.length());
            if (into.containsKey(name)) {
                if (warned.add("twice " + name)) Output.add(Output.Level.WARN, SIDE, name + " is both a project and a user plugin, the project one runs");
                continue;
            }
            try {
                into.put(name, new Source(file, prefix + fileName, Files.getLastModifiedTime(file), Files.size(file)));
            } catch (IOException e) {
                MoudMod.LOG.warn("could not look at {}", file, e);
            }
        }
    }

    private static ModuleSource modules(Path root) {
        ModuleSource place = new PlaceModules(root, true);
        return path -> {
            if (!path.startsWith(FOLDER + "/")) return place.read(path);
            Path file = root.resolve(path);
            if (!Files.isRegularFile(file)) return null;
            try {
                return Files.readString(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    private @Nullable Shortcut shortcut(Plugins.Command command) {
        if (command.shortcut().isEmpty()) return null;
        Shortcut shortcut = Shortcut.parse(command.shortcut());
        if (shortcut == null && warned.add("shortcut " + command.shortcut())) {
            Output.add(Output.Level.WARN, SIDE, command.plugin().name() + ": '" + command.shortcut()
                    + "' is not a shortcut, use something like Ctrl+Shift+G or F5");
        }
        return shortcut;
    }

    private @Nullable EditorIcon icon(Plugins.Button button) {
        if (button.icon().isEmpty()) return null;
        EditorIcon icon = EditorIcon.named(button.icon());
        if (icon == null && warned.add("icon " + button.icon())) {
            Output.add(Output.Level.WARN, SIDE, "there is no editor icon called '" + button.icon() + "', the button shows its text");
        }
        return icon;
    }

    private static void reveal(Path folder) {
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            Output.add(Output.Level.ERROR, SIDE, "could not make " + folder + ": " + e.getMessage());
            return;
        }
        FileManagerReveal.reveal(folder).ifPresent(why -> Output.add(Output.Level.WARN, SIDE, "could not open " + folder + ": " + why));
    }
}
