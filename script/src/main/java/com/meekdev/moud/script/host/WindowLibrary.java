package com.meekdev.moud.script.host;

import com.meekdev.moud.script.api.WindowRef;
import java.util.ArrayList;

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
        Members members = new Members("Window")
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
        host.global("window", "Window", members);
        host.declare(members);
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
