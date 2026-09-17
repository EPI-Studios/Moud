package com.meekdev.moud.script.host;

import com.meekdev.moud.script.api.GameRef;
import com.meekdev.moud.script.api.SettingsRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

final class GameLibrary {

    private GameLibrary() {}

    static void install(Host host, Members game) {
        host.api().declare(HostSignal.decl("PauseSignal", "(reason: string) -> ()"));
        Map<String, Object> priorities = new LinkedHashMap<>();
        priorities.put("first", 0.0);
        priorities.put("input", 100.0);
        priorities.put("camera", RenderSteps.CAMERA);
        priorities.put("character", 300.0);
        priorities.put("last", 2000.0);
        game.value("isServer", "boolean", !host.client())
                .value("isClient", "boolean", host.client())
                .field("isStudio", "boolean", host::studio)
                .field("renderPriority", "{ first: number, input: number, camera: number, character: number, last: number }", () -> new LinkedHashMap<>(priorities))
                .method("bindToRenderStep", "(name: string, priority: number, handler: (delta: number) -> ()) -> ()", a -> {
                    if (!host.client()) throw new HostError("bindToRenderStep is client-only");
                    host.renderBindings().bind(a.string(1), a.number(2), a.callable(3));
                    return null;
                })
                .method("unbindFromRenderStep", "(name: string) -> ()", a -> {
                    if (!host.client()) throw new HostError("unbindFromRenderStep is client-only");
                    host.renderBindings().unbind(a.string(1));
                    return null;
                });
        game.method("bindToClose", "(handler: () -> ()) -> ()", a -> {
            Callable closing = a.callable(1).retain();
            host.onClose(() -> host.call(closing, "game:bindToClose"));
            return null;
        });
        GameRef ref = host.game();
        if (ref != null) {
            game.value("pauseRequested", "PauseSignal", host.pauseSignal())
                    .field("paused", "boolean", ref::paused, value -> ref.paused(bool("game.paused", value)))
                    .field("exported", "boolean", ref::exported)
                    .method("quit", "() -> ()", a -> {
                        ref.quit();
                        return null;
                    })
                    .method("openSettings", "() -> ()", a -> {
                        ref.openSettings();
                        return null;
                    });
        }
        SettingsRef settings = host.settings();
        if (settings == null) return;
        Members members = new Members("Settings")
                .field("fov", "number", settings::fov, value -> settings.fov(number("settings.fov", value, 30, 110)))
                .field("fullscreen", "boolean", settings::fullscreen, value -> settings.fullscreen(bool("settings.fullscreen", value)))
                .field("vsync", "boolean", settings::vsync, value -> settings.vsync(bool("settings.vsync", value)))
                .field("maxFps", "number", () -> (double) settings.maxFps(), value -> settings.maxFps((int) number("settings.maxFps", value, 10, 260)))
                .field("guiScale", "number", () -> (double) settings.guiScale(), value -> settings.guiScale((int) number("settings.guiScale", value, 0, 16)))
                .field("renderDistance", "number", () -> (double) settings.renderDistance(), value -> settings.renderDistance((int) number("settings.renderDistance", value, 2, 32)))
                .field("sensitivity", "number", settings::sensitivity, value -> settings.sensitivity(number("settings.sensitivity", value, 0, 1)))
                .method("volume", "(category: string) -> number", a -> settings.volume(category(settings, a.string(1))))
                .method("setVolume", "(category: string, value: number) -> ()", a -> {
                    settings.volume(category(settings, a.string(1)), Math.clamp(a.number(2), 0, 1));
                    return null;
                })
                .method("volumes", "() -> { string }", a -> new ArrayList<Object>(settings.volumes()))
                .method("actions", "() -> { string }", a -> new ArrayList<Object>(settings.actions()))
                .method("keyOf", "(action: string) -> string", a -> settings.keyOf(action(settings, a.string(1))))
                .method("bind", "(action: string, key: string) -> ()", a -> {
                    settings.bind(action(settings, a.string(1)), a.string(2));
                    return null;
                })
                .method("save", "() -> ()", a -> {
                    settings.save();
                    return null;
                });
        host.global("settings", "Settings", members);
        host.declare(members);
    }

    private static String category(SettingsRef settings, String name) {
        if (!settings.volumes().contains(name)) throw new HostError("'%s' is not a volume, expected one of %s", name, String.join(", ", settings.volumes()));
        return name;
    }

    private static String action(SettingsRef settings, String name) {
        if (!settings.actions().contains(name)) throw new HostError("'%s' is not an action, expected one of %s", name, String.join(", ", settings.actions()));
        return name;
    }

    private static boolean bool(String where, Object value) {
        if (!(value instanceof Boolean on)) throw new HostError("%s expects true or false", where);
        return on;
    }

    private static double number(String where, Object value, double min, double max) {
        if (!(value instanceof Number n)) throw new HostError("%s expects a number", where);
        double v = n.doubleValue();
        if (v < min || v > max) throw new HostError("%s goes from %s to %s, not %s", where, trim(min), trim(max), trim(v));
        return v;
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
