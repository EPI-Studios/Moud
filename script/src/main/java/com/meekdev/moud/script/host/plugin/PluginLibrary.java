package com.meekdev.moud.script.host.plugin;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.script.api.PluginRef;
import com.meekdev.moud.script.host.Callable;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostSignal;
import com.meekdev.moud.script.host.Members;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PluginLibrary {

    private static final List<String> DOCKS = List.of("float", "left", "right", "bottom");
    private static final double PANEL_WIDTH = 320;
    private static final double PANEL_HEIGHT = 240;

    private PluginLibrary() {}

    static Members install(Host host, Plugins plugins) {
        PluginRef editor = plugins.editor();
        host.api().alias("PluginMouse", "{ origin: Vector3, direction: Vector3, hit: Vector3?, normal: Vector3?, target: Instance?, over: boolean }");
        host.api().alias("PanelOptions", "{ width: number?, height: number?, dock: (\"float\" | \"left\" | \"right\" | \"bottom\")? }");
        host.api().declare(HostSignal.decl("PluginMouseSignal", "(mouse: PluginMouse) -> ()"));
        host.declare(toolbar(plugins, new Plugins.Toolbar(null, "")));
        host.declare(button(new Plugins.Button(null, "", "", "")));
        host.declare(panel(plugins, new Plugins.Panel(null, "", "", PANEL_WIDTH, PANEL_HEIGHT, "float")));
        Members ui = ui(host, plugins);
        host.declare(ui);

        Members selection = new Members("PluginSelection")
                .method("get", "() -> { Instance }", a -> {
                    plugins.current();
                    return new ArrayList<Object>(editor.selection());
                })
                .method("set", "(instances: { Instance }) -> ()", a -> {
                    plugins.current();
                    List<Instance> chosen = new ArrayList<>();
                    for (Object value : a.list(1)) {
                        if (!(value instanceof Instance instance)) throw new HostError("selection:set expects a list of instances");
                        if (!instance.isAlive()) throw new HostError("cannot select %s, it has been destroyed", instance.name());
                        chosen.add(instance);
                    }
                    editor.select(chosen);
                    return null;
                })
                .value("changed", "AnySignal", plugins.selectionSignal());
        host.declare(selection);

        Members plugin = new Members("Plugin")
                .field("name", "string", () -> plugins.current().name())
                .value("selection", "PluginSelection", selection)
                .value("viewportClicked", "PluginMouseSignal", plugins.viewportSignal())
                .field("preview", "Instance", () -> plugins.preview(plugins.current()))
                .method("toolbar", "(name: string) -> PluginToolbar", a -> toolbar(plugins, plugins.toolbar(plugins.current(), a.string(1))))
                .method("command", "(name: string, handler: () -> ()) -> ()", a -> {
                    Plugins.Plugin owner = plugins.current();
                    String name = a.string(1);
                    if (name.isBlank()) throw new HostError("a command needs a name");
                    if (a.get(2) instanceof Callable handler) {
                        plugins.command(owner, name, "", handler);
                        return null;
                    }
                    String shortcut = a.has(2) ? a.string(2) : "";
                    plugins.command(owner, name, shortcut, a.callable(3));
                    return null;
                })
                .declareMethod("command", "(name: string, shortcut: string?, handler: () -> ()) -> ()")
                .method("panel", "(title: string, options: PanelOptions?) -> PluginPanel", a -> {
                    Plugins.Plugin owner = plugins.current();
                    String title = a.string(1);
                    if (title.isBlank()) throw new HostError("a panel needs a title");
                    Map<String, Object> options = a.map(2, Map.of());
                    String dock = options.get("dock") == null ? "float" : text(options.get("dock"), "dock");
                    if (!DOCKS.contains(dock)) throw new HostError("dock is one of %s, not '%s'", String.join(", ", DOCKS), dock);
                    double width = size(options.get("width"), "width", PANEL_WIDTH);
                    double height = size(options.get("height"), "height", PANEL_HEIGHT);
                    return panel(plugins, plugins.panel(owner, title, width, height, dock));
                })
                .method("recording", "(name: string, changes: () -> ()) -> ()", a -> {
                    plugins.current();
                    String name = a.string(1);
                    Callable changes = a.callable(2);
                    editor.record(name, changes::call);
                    return null;
                })
                .method("setting", "(key: string) -> any", a -> editor.setting(plugins.current().settingsKey(), a.string(1)))
                .method("setSetting", "(key: string, value: any) -> ()", a -> {
                    Plugins.Plugin owner = plugins.current();
                    Object value = a.get(2);
                    storable(value);
                    editor.setting(owner.settingsKey(), a.string(1), value);
                    return null;
                })
                .method("mouse", "() -> PluginMouse", a -> {
                    plugins.current();
                    return mouse(editor.mouse());
                })
                .method("activate", "(on: boolean) -> ()", a -> {
                    plugins.activate(plugins.current(), a.truthy(1));
                    return null;
                })
                .field("active", "boolean", () -> plugins.current().active());
        host.undeclaredGlobal("plugin", plugin);
        host.declare(plugin);
        return ui;
    }

    static Map<String, Object> mouse(PluginRef.Mouse mouse) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (mouse == null) {
            out.put("origin", Vector3.ZERO);
            out.put("direction", Vector3.ZERO);
            out.put("over", false);
            return out;
        }
        out.put("origin", mouse.origin());
        out.put("direction", mouse.direction());
        if (mouse.hit() != null) out.put("hit", mouse.hit());
        if (mouse.normal() != null) out.put("normal", mouse.normal());
        if (mouse.target() != null && mouse.target().isAlive()) out.put("target", mouse.target());
        out.put("over", mouse.over());
        return out;
    }

    private static Members toolbar(Plugins plugins, Plugins.Toolbar toolbar) {
        return new Members("PluginToolbar")
                .value("name", "string", toolbar.name())
                .method("button", "(text: string, tooltip: string?, icon: string?) -> PluginButton", a -> {
                    String text = a.string(1);
                    if (text.isBlank()) throw new HostError("a button needs some text");
                    return button(plugins.button(toolbar, text, a.string(2, ""), a.string(3, "")));
                });
    }

    private static Members button(Plugins.Button button) {
        return new Members("PluginButton")
                .field("text", "string", button::text, value -> button.text = text(value, "text"))
                .field("tooltip", "string", button::tooltip, value -> button.tooltip = text(value, "tooltip"))
                .field("icon", "string", button::icon, value -> button.icon = text(value, "icon"))
                .field("active", "boolean", button::active, value -> button.active = bool(value, "active"))
                .field("enabled", "boolean", button::enabled, value -> button.enabled = bool(value, "enabled"))
                .value("click", "AnySignal", button.clicked());
    }

    private static Members panel(Plugins plugins, Plugins.Panel panel) {
        return new Members("PluginPanel")
                .field("title", "string", panel::title, value -> {
                    String title = text(value, "title");
                    if (title.isBlank()) throw new HostError("a panel needs a title");
                    panel.title = title;
                })
                .field("visible", "boolean", panel::visible, value -> panel.visible = bool(value, "visible"))
                .method("onDraw", "(draw: (ui: PanelUi) -> ()) -> ()", a -> {
                    plugins.onDraw(panel, a.has(1) ? a.callable(1) : null);
                    return null;
                });
    }

    private static Members ui(Host host, Plugins plugins) {
        return new Members("PanelUi")
                .method("text", "(text: string) -> ()", a -> {
                    plugins.drawing().text(host.text(a.get(1)));
                    return null;
                })
                .method("muted", "(text: string) -> ()", a -> {
                    plugins.drawing().muted(host.text(a.get(1)));
                    return null;
                })
                .method("heading", "(text: string) -> ()", a -> {
                    plugins.drawing().heading(host.text(a.get(1)));
                    return null;
                })
                .method("button", "(label: string) -> boolean", a -> plugins.drawing().button(a.string(1)))
                .method("input", "(label: string, value: string) -> string", a -> plugins.drawing().input(a.string(1), a.string(2, "")))
                .method("number", "(label: string, value: number, step: number?) -> number",
                        a -> plugins.drawing().number(a.string(1), a.number(2), a.number(3, 0.1)))
                .method("slider", "(label: string, value: number, min: number, max: number) -> number", a -> {
                    double minimum = a.number(3);
                    double maximum = a.number(4);
                    if (maximum <= minimum) throw new HostError("a slider needs its max above its min");
                    return plugins.drawing().slider(a.string(1), a.number(2), minimum, maximum);
                })
                .method("checkbox", "(label: string, value: boolean) -> boolean", a -> plugins.drawing().checkbox(a.string(1), a.truthy(2)))
                .method("color", "(label: string, value: Color) -> Color", a -> plugins.drawing().color(a.string(1), a.color(2)))
                .method("choice", "(label: string, value: string, options: { string }) -> string", a -> {
                    List<String> options = new ArrayList<>();
                    for (Object option : a.list(3)) options.add(host.text(option));
                    if (options.isEmpty()) throw new HostError("a choice needs at least one option");
                    return plugins.drawing().choice(a.string(1), a.string(2), options);
                })
                .method("separator", "() -> ()", a -> {
                    plugins.drawing().separator();
                    return null;
                })
                .method("sameLine", "() -> ()", a -> {
                    plugins.drawing().sameLine();
                    return null;
                });
    }

    private static void storable(Object value) {
        switch (value) {
            case null -> { }
            case String s -> { }
            case Number n -> { }
            case Boolean b -> { }
            case List<?> list -> {
                for (Object item : list) storable(item);
            }
            case Map<?, ?> map -> {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String)) throw new HostError("settings keep tables with string keys");
                    storable(entry.getValue());
                }
            }
            default -> throw new HostError("settings keep strings, numbers, booleans and tables of them, not a %s", Host.typeOf(value));
        }
    }

    private static String text(Object value, String what) {
        if (value instanceof String s) return s;
        throw new HostError("%s expects a string", what);
    }

    private static boolean bool(Object value, String what) {
        if (value instanceof Boolean b) return b;
        throw new HostError("%s expects true or false", what);
    }

    private static double size(Object value, String what, double fallback) {
        if (value == null) return fallback;
        if (!(value instanceof Number n)) throw new HostError("%s expects a number", what);
        if (n.doubleValue() < 1) throw new HostError("%s must be at least 1", what);
        return n.doubleValue();
    }
}
