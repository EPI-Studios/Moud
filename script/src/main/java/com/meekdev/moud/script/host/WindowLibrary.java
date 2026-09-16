package com.meekdev.moud.script.host;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.ui.AppWindow;
import com.meekdev.moud.script.api.WindowRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class WindowLibrary {

    private WindowLibrary() {}

    static void install(Host host) {
        WindowRef window = host.window();
        if (window == null) return;
        host.api().declare(HostSignal.decl("WindowSizeSignal", "(width: number, height: number) -> ()"));
        host.api().declare(HostSignal.decl("WindowFocusSignal", "(focused: boolean) -> ()"));
        HostSignal resized = new HostSignal(host, "WindowSizeSignal", "window.resized");
        HostSignal moved = new HostSignal(host, "WindowSizeSignal", "window.moved");
        HostSignal focusChanged = new HostSignal(host, "WindowFocusSignal", "window.focusChanged");
        host.windowClosing(new HostSignal(host, "AnySignal", "window.closing"));
        int[] last = {window.width(), window.height(), window.x(), window.y(), window.focused() ? 1 : 0};
        host.onRenderStep(dt -> {
            int width = window.width();
            int height = window.height();
            if (width != last[0] || height != last[1]) {
                last[0] = width;
                last[1] = height;
                resized.fire((double) width, (double) height);
            }
            int x = window.x();
            int y = window.y();
            if (x != last[2] || y != last[3]) {
                last[2] = x;
                last[3] = y;
                moved.fire((double) x, (double) y);
            }
            int focused = window.focused() ? 1 : 0;
            if (focused != last[4]) {
                last[4] = focused;
                focusChanged.fire(focused == 1);
            }
        });
        Members members = new Members("MainWindow")
                .field("title", "string", window::title, value -> window.title(text("window.title", value)))
                .field("icon", "string", window::icon, value -> window.icon(text("window.icon", value)))
                .field("fullscreen", "boolean", window::fullscreen, value -> window.fullscreen(bool("window.fullscreen", value)))
                .field("width", "number", () -> (double) window.width())
                .field("height", "number", () -> (double) window.height())
                .field("x", "number", () -> (double) window.x(), value -> window.moveTo((int) Math.round(number("window.x", value)), window.y()))
                .field("y", "number", () -> (double) window.y(), value -> window.moveTo(window.x(), (int) Math.round(number("window.y", value))))
                .field("canMove", "boolean", window::canMove)
                .field("minWidth", "number", () -> (double) window.minWidth(), value -> window.minSize(size("window.minWidth", value), window.minHeight()))
                .field("minHeight", "number", () -> (double) window.minHeight(), value -> window.minSize(window.minWidth(), size("window.minHeight", value)))
                .field("resizable", "boolean", window::resizable, value -> window.resizable(bool("window.resizable", value)))
                .field("focused", "boolean", window::focused)
                .field("minimized", "boolean", window::minimized)
                .field("opacity", "number", window::opacity, value -> window.opacity(Math.clamp(number("window.opacity", value), 0, 1)))
                .field("cursor", "string", window::cursor, value -> window.cursor(text("window.cursor", value)))
                .field("cursorVisible", "boolean", window::cursorVisible, value -> window.cursorVisible(bool("window.cursorVisible", value)))
                .field("fps", "number", () -> (double) window.fps())
                .field("displayWidth", "number", () -> (double) window.displayWidth())
                .field("displayHeight", "number", () -> (double) window.displayHeight())
                .value("resized", "WindowSizeSignal", resized)
                .value("moved", "WindowSizeSignal", moved)
                .value("focusChanged", "WindowFocusSignal", focusChanged)
                .value("closing", "AnySignal", host.windowClosingSignal())
                .method("resize", "(width: number, height: number) -> ()", a -> {
                    window.resize(size("width", a.number(1)), size("height", a.number(2)));
                    return null;
                })
                .method("moveTo", "(x: number, y: number) -> ()", a -> {
                    window.moveTo((int) Math.round(a.number(1)), (int) Math.round(a.number(2)));
                    return null;
                })
                .method("center", "() -> ()", a -> {
                    window.center();
                    return null;
                })
                .method("monitors", "() -> { { [string]: any } }", a -> new ArrayList<Object>(window.monitors()))
                .method("flash", "() -> ()", a -> {
                    window.flash();
                    return null;
                })
                .method("setClipboard", "(text: string) -> ()", a -> {
                    window.clipboard(a.string(1));
                    return null;
                })
                .method("preventClose", "() -> ()", a -> {
                    window.preventClose();
                    return null;
                });
        members.field("visible", "boolean", window::visible, value -> window.visible(bool("window.visible", value)))
                .function("open", "(properties: { [string]: any }?) -> Window", a -> open(host, a.map(0, Map.of()), Map.of()))
                .function("overlay", "(properties: { [string]: any }?) -> Window", a -> {
                    Map<String, Object> preset = new LinkedHashMap<>();
                    preset.put("title", "Overlay");
                    preset.put("decorated", false);
                    preset.put("transparent", true);
                    preset.put("alwaysOnTop", true);
                    preset.put("clickThrough", true);
                    preset.put("resizable", false);
                    Map<String, Object> monitor = primary(window);
                    if (monitor != null) {
                        preset.put("x", monitor.get("x"));
                        preset.put("y", monitor.get("y"));
                        preset.put("width", monitor.get("width"));
                        preset.put("height", monitor.get("height"));
                    }
                    return open(host, a.map(0, Map.of()), preset);
                });
        Members windows = host.instances().of(Classes.WINDOW);
        windows.method("close", "() -> ()", a -> {
            Instances.destroy(a.self());
            return null;
        });
        windows.method("preventClose", "() -> ()", a -> {
            a.self(AppWindow.class).keepOpen();
            return null;
        });
        windows.method("focus", "() -> ()", a -> {
            a.self(AppWindow.class).focus();
            return null;
        });
        windows.method("moveTo", "(x: number, y: number) -> ()", a -> {
            AppWindow self = a.self(AppWindow.class);
            host.instances().set(self, "x", a.number(1));
            host.instances().set(self, "y", a.number(2));
            return null;
        });
        windows.method("resize", "(width: number, height: number) -> ()", a -> {
            AppWindow self = a.self(AppWindow.class);
            host.instances().set(self, "width", a.number(1));
            host.instances().set(self, "height", a.number(2));
            return null;
        });
        windows.method("center", "() -> ()", a -> {
            AppWindow self = a.self(AppWindow.class);
            Map<String, Object> monitor = primary(window);
            if (monitor == null) return null;
            double x = ((Number) monitor.get("workX")).doubleValue() + (((Number) monitor.get("workWidth")).doubleValue() - self.width) / 2;
            double y = ((Number) monitor.get("workY")).doubleValue() + (((Number) monitor.get("workHeight")).doubleValue() - self.height) / 2;
            host.instances().set(self, "x", Math.round(x));
            host.instances().set(self, "y", Math.round(y));
            return null;
        });
        host.global("window", "MainWindow", members);
        host.declare(members);
    }

    private static Object open(Host host, Map<String, Object> properties, Map<String, Object> preset) {
        AppWindow made = Instances.createLocal(Classes.WINDOW, host.world(), "Window");
        try {
            for (Map.Entry<String, Object> entry : preset.entrySet()) host.instances().set(made, entry.getKey(), entry.getValue());
            for (Map.Entry<String, Object> entry : properties.entrySet()) host.instances().set(made, entry.getKey(), entry.getValue());
        } catch (RuntimeException e) {
            Instances.destroy(made);
            throw e;
        }
        host.ownership().onRelease(host.ownership().current(), () -> {
            if (made.isAlive()) Instances.destroy(made);
        });
        return made;
    }

    private static Map<String, Object> primary(WindowRef window) {
        List<Map<String, Object>> monitors = window.monitors();
        if (monitors == null || monitors.isEmpty()) return null;
        for (Map<String, Object> monitor : monitors) {
            if (Boolean.TRUE.equals(monitor.get("primary"))) return monitor;
        }
        return monitors.getFirst();
    }

    private static String text(String where, Object value) {
        if (!(value instanceof String s)) throw new HostError("%s expects a string", where);
        return s;
    }

    private static boolean bool(String where, Object value) {
        if (!(value instanceof Boolean b)) throw new HostError("%s expects true or false", where);
        return b;
    }

    private static double number(String where, Object value) {
        if (!(value instanceof Number n)) throw new HostError("%s expects a number", where);
        return n.doubleValue();
    }

    private static int size(String where, Object value) {
        int size = (int) Math.round(number(where, value));
        if (size < 1) throw new HostError("%s must be at least 1", where);
        return size;
    }
}
