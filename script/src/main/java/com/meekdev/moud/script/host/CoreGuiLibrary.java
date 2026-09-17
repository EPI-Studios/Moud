package com.meekdev.moud.script.host;

import com.meekdev.moud.script.api.CoreGuiRef;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class CoreGuiLibrary {

    static final List<String> PARTS =
            List.of("health", "hunger", "hotbar", "chat", "playerList", "crosshair", "experience");

    private static final String ALL = "all";
    private static final double DEFAULT_SECONDS = 5;

    private CoreGuiLibrary() {}

    static void install(Host host) {
        CoreGuiRef ref = host.coreGui();
        if (ref == null) return;
        host.api().alias("Notification", "{ title: string, text: string?, icon: string?, duration: number?, "
                + "button1: string?, button2: string?, callback: ((button: string) -> ())? }");
        Members members = new Members("CoreGui")
                .function("setCoreGuiEnabled", "(name: string, on: boolean) -> ()", a -> {
                    String name = name(a.string(0));
                    boolean on = a.truthy(1);
                    if (name.equals(ALL)) for (String part : PARTS) ref.enabled(part, on);
                    else ref.enabled(name, on);
                    return null;
                })
                .function("getCoreGuiEnabled", "(name: string) -> boolean", a -> {
                    String name = name(a.string(0));
                    if (!name.equals(ALL)) return ref.enabled(name);
                    for (String part : PARTS) {
                        if (!ref.enabled(part)) return false;
                    }
                    return true;
                })
                .function("sendNotification", "(notification: Notification) -> ()", a -> {
                    ref.notify(notification(host, a.map(0)));
                    return null;
                });
        host.global("ui", "CoreGui", members);
        host.declare(members);
    }

    private static String name(String raw) {
        if (raw.equals(ALL) || PARTS.contains(raw)) return raw;
        throw new HostError("'%s' is not part of the core interface, expected one of %s or all",
                raw, String.join(", ", PARTS));
    }

    private static CoreGuiRef.Notification notification(Host host, Map<String, Object> notice) {
        String title = text(notice, "title");
        if (title.isEmpty()) throw new HostError("a notification needs a title");
        double seconds = notice.get("duration") instanceof Number n ? n.doubleValue() : DEFAULT_SECONDS;
        if (seconds <= 0) throw new HostError("a notification lasts longer than no time at all");
        Callable handler = notice.get("callback") instanceof Callable fn ? fn.retain() : null;
        Consumer<String> answered = button -> {
            if (handler == null) return;
            if (button != null) host.call(handler, "ui.sendNotification", button);
            handler.release();
        };
        return new CoreGuiRef.Notification(title, text(notice, "text"), text(notice, "icon"), seconds,
                text(notice, "button1"), text(notice, "button2"), answered);
    }

    private static String text(Map<String, Object> notice, String key) {
        Object value = notice.get(key);
        if (value == null) return "";
        if (value instanceof String s) return s;
        throw new HostError("%s expects a string", key);
    }
}
