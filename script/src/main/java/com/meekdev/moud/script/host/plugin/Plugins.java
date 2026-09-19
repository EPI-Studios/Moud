package com.meekdev.moud.script.host.plugin;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.script.api.PluginRef;
import com.meekdev.moud.script.host.Callable;
import com.meekdev.moud.script.host.Fiber;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostSignal;
import com.meekdev.moud.script.host.Members;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Plugins {

    public static final class Plugin {

        private final String name;
        private final String chunk;
        private final Set<String> requires = new HashSet<>();
        private boolean active;
        private Instance preview;

        Plugin(String name, String chunk) {
            this.name = name;
            this.chunk = chunk;
        }

        public String name() {
            return name;
        }

        public String chunk() {
            return chunk;
        }

        public boolean active() {
            return active;
        }

        public String settingsKey() {
            return chunk.startsWith(USER) ? "~/" + name : name;
        }
    }

    public static final class Toolbar {

        private final Plugin plugin;
        private final String name;
        private final List<Button> buttons = new ArrayList<>();

        Toolbar(Plugin plugin, String name) {
            this.plugin = plugin;
            this.name = name;
        }

        public Plugin plugin() {
            return plugin;
        }

        public String name() {
            return name;
        }

        public List<Button> buttons() {
            return buttons;
        }
    }

    public static final class Button {

        private final HostSignal click;
        String text;
        String tooltip;
        String icon;
        boolean active;
        boolean enabled = true;

        Button(HostSignal click, String text, String tooltip, String icon) {
            this.click = click;
            this.text = text;
            this.tooltip = tooltip;
            this.icon = icon;
        }

        public String text() {
            return text;
        }

        public String tooltip() {
            return tooltip;
        }

        public String icon() {
            return icon;
        }

        public boolean active() {
            return active;
        }

        public boolean enabled() {
            return enabled;
        }

        public void click() {
            if (enabled) click.fire();
        }

        HostSignal clicked() {
            return click;
        }
    }

    public static final class Command {

        private final Plugin plugin;
        private final String name;
        private final String shortcut;
        private final Callable handler;

        Command(Plugin plugin, String name, String shortcut, Callable handler) {
            this.plugin = plugin;
            this.name = name;
            this.shortcut = shortcut;
            this.handler = handler;
        }

        public Plugin plugin() {
            return plugin;
        }

        public String name() {
            return name;
        }

        public String shortcut() {
            return shortcut;
        }
    }

    public static final class Panel {

        private final Plugin plugin;
        private final String key;
        String title;
        final double width;
        final double height;
        final String dock;
        boolean visible = true;
        Callable draw;
        String failure;

        Panel(Plugin plugin, String key, String title, double width, double height, String dock) {
            this.plugin = plugin;
            this.key = key;
            this.title = title;
            this.width = width;
            this.height = height;
            this.dock = dock;
        }

        public Plugin plugin() {
            return plugin;
        }

        public String key() {
            return key;
        }

        public String title() {
            return title;
        }

        public double width() {
            return width;
        }

        public double height() {
            return height;
        }

        public String dock() {
            return dock;
        }

        public boolean visible() {
            return visible;
        }

        public void visible(boolean on) {
            visible = on;
        }

        public String failure() {
            return failure;
        }
    }

    private static final String USER = "~/";

    private final PluginRef editor;
    private final Map<String, Boolean> shown;
    private final Map<String, Set<String>> needs = new HashMap<>();
    private final Map<String, Plugin> loaded = new LinkedHashMap<>();
    private final List<Toolbar> toolbars = new ArrayList<>();
    private final List<Command> commands = new ArrayList<>();
    private final List<Panel> panels = new ArrayList<>();
    private Host host;
    private HostSignal selectionChanged;
    private HostSignal viewportClicked;
    private PluginRef.Ui drawing;
    private Members ui;

    public Plugins(PluginRef editor) {
        this(editor, new HashMap<>());
    }

    public Plugins(PluginRef editor, Map<String, Boolean> shown) {
        this.editor = editor;
        this.shown = shown;
    }

    PluginRef editor() {
        return editor;
    }

    public void install(Host host) {
        this.host = host;
        selectionChanged = new HostSignal(host, "AnySignal", "plugin.selection.changed");
        viewportClicked = new HostSignal(host, "PluginMouseSignal", "plugin.viewportClicked");
        ui = PluginLibrary.install(host, this);
        host.onClose(() -> {
            for (Plugin plugin : List.copyOf(loaded.values())) unload(plugin.name);
            selectionChanged.clear();
            viewportClicked.clear();
        });
    }

    HostSignal selectionSignal() {
        return selectionChanged;
    }

    HostSignal viewportSignal() {
        return viewportClicked;
    }

    public void load(String name, String chunk, String source) {
        unload(name);
        Plugin plugin = new Plugin(name, chunk);
        loaded.put(name, plugin);
        host.ownership().onRelease(plugin, () -> forget(plugin));
        Object before = host.ownership().enter(plugin);
        try {
            Fiber fiber = host.engine().script(chunk, source, null);
            if (fiber != null) host.scheduler().start(fiber, plugin);
        } catch (RuntimeException e) {
            host.error(chunk, e);
        } finally {
            host.ownership().leave(before);
        }
    }

    public void unload(String name) {
        Plugin plugin = loaded.remove(name);
        if (plugin != null) host.ownership().release(plugin);
    }

    public Set<String> names() {
        return loaded.keySet();
    }

    public List<Toolbar> toolbars() {
        return toolbars;
    }

    public List<Command> commands() {
        return commands;
    }

    public List<Panel> panels() {
        return panels;
    }

    public boolean capturing() {
        for (Plugin plugin : loaded.values()) {
            if (plugin.active) return true;
        }
        return false;
    }

    public String running() {
        return host != null && host.ownership().current() instanceof Plugin plugin ? plugin.name : "";
    }

    public String runningChunk() {
        return host != null && host.ownership().current() instanceof Plugin plugin ? plugin.chunk : "";
    }

    public void show(Panel panel, boolean on) {
        panel.visible = on;
        shown.put(panel.key, on);
    }

    public void required(String from, String path) {
        if (from != null) needs.computeIfAbsent(from, key -> new HashSet<>()).add(path);
        if (host != null && host.ownership().current() instanceof Plugin plugin) plugin.requires.add(path);
    }

    public Set<String> modules() {
        Set<String> all = new HashSet<>(needs.keySet());
        for (Set<String> required : needs.values()) all.addAll(required);
        for (Plugin plugin : loaded.values()) all.addAll(plugin.requires);
        return all;
    }

    public List<String> changed(Collection<String> paths) {
        Set<String> stale = new HashSet<>(paths);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Map.Entry<String, Set<String>> entry : needs.entrySet()) {
                if (!stale.contains(entry.getKey()) && !Collections.disjoint(entry.getValue(), stale)) grew |= stale.add(entry.getKey());
            }
        }
        for (String path : stale) {
            needs.remove(path);
            host.forgetModule(path);
        }
        List<String> reload = new ArrayList<>();
        for (Plugin plugin : loaded.values()) {
            if (!Collections.disjoint(plugin.requires, stale)) reload.add(plugin.name);
        }
        return reload;
    }

    public void run(Command command) {
        call(command.plugin, command.handler, command.plugin.chunk + " " + command.name);
    }

    public void draw(Panel panel, PluginRef.Ui screen) {
        if (panel.draw == null || panel.failure != null) return;
        PluginRef.Ui before = drawing;
        drawing = screen;
        try {
            if (call(panel.plugin, panel.draw, panel.plugin.chunk + " " + panel.title, ui) == null) {
                panel.failure = "This panel stopped after an error, see the output. Save the plugin to try again.";
            }
        } finally {
            drawing = before;
        }
    }

    public void selectionChanged() {
        selectionChanged.fire();
    }

    public void viewportClicked(PluginRef.Mouse mouse) {
        viewportClicked.fire(PluginLibrary.mouse(mouse));
    }

    PluginRef.Ui drawing() {
        if (drawing == null) throw new HostError("the panel ui can only be used while its panel draws");
        return drawing;
    }

    Plugin current() {
        if (host.ownership().current() instanceof Plugin plugin && loaded.get(plugin.name) == plugin) return plugin;
        throw new HostError("plugin can only be used by a plugin script");
    }

    Toolbar toolbar(Plugin plugin, String name) {
        for (Toolbar toolbar : toolbars) {
            if (toolbar.plugin == plugin && toolbar.name.equals(name)) return toolbar;
        }
        Toolbar made = new Toolbar(plugin, name);
        toolbars.add(made);
        return made;
    }

    Button button(Toolbar toolbar, String text, String tooltip, String icon) {
        HostSignal click = new HostSignal(host, "AnySignal", toolbar.plugin.chunk + " " + text);
        Button made = new Button(click, text, tooltip, icon);
        toolbar.buttons.add(made);
        return made;
    }

    void command(Plugin plugin, String name, String shortcut, Callable handler) {
        commands.removeIf(command -> {
            if (command.plugin != plugin || !command.name.equals(name)) return false;
            command.handler.release();
            return true;
        });
        commands.add(new Command(plugin, name, shortcut, handler.retain()));
    }

    Panel panel(Plugin plugin, String title, double width, double height, String dock) {
        String key = plugin.name + "/" + title;
        for (int n = 2; taken(key); n++) key = plugin.name + "/" + title + "/" + n;
        Panel made = new Panel(plugin, key, title, width, height, dock);
        made.visible = shown.getOrDefault(key, true);
        panels.add(made);
        return made;
    }

    private boolean taken(String key) {
        for (Panel panel : panels) {
            if (panel.key.equals(key)) return true;
        }
        return false;
    }

    void onDraw(Panel panel, Callable fn) {
        if (panel.draw != null) panel.draw.release();
        panel.draw = fn == null ? null : fn.retain();
        panel.failure = null;
    }

    void activate(Plugin plugin, boolean on) {
        plugin.active = on;
    }

    Instance preview(Plugin plugin) {
        if (plugin.preview == null || !plugin.preview.isAlive()) {
            plugin.preview = Instances.createLocal(Classes.FOLDER, host.world(), plugin.name + " preview");
        }
        return plugin.preview;
    }

    private Object[] call(Plugin plugin, Callable fn, String where, Object... args) {
        Object before = host.ownership().enter(plugin);
        try {
            return host.call(fn, where, args);
        } finally {
            host.ownership().leave(before);
        }
    }

    private void forget(Plugin plugin) {
        plugin.active = false;
        for (Toolbar toolbar : toolbars) {
            if (toolbar.plugin == plugin) for (Button button : toolbar.buttons) button.clicked().clear();
        }
        toolbars.removeIf(toolbar -> toolbar.plugin == plugin);
        commands.removeIf(command -> {
            if (command.plugin != plugin) return false;
            command.handler.release();
            return true;
        });
        panels.removeIf(panel -> {
            if (panel.plugin != plugin) return false;
            if (panel.draw != null) panel.draw.release();
            panel.draw = null;
            return true;
        });
        if (plugin.preview != null && plugin.preview.isAlive()) Instances.destroy(plugin.preview);
        plugin.preview = null;
        if (loaded.get(plugin.name) == plugin) loaded.remove(plugin.name);
    }
}
