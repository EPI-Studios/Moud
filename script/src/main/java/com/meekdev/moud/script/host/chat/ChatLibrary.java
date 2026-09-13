package com.meekdev.moud.script.host.chat;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.text.RichText;
import com.meekdev.moud.script.api.ChatRef;
import com.meekdev.moud.script.host.Args;
import com.meekdev.moud.script.host.Builtin;
import com.meekdev.moud.script.host.Callable;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostSignal;
import com.meekdev.moud.script.host.Members;
import com.meekdev.moud.script.host.player.Players;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class ChatLibrary {

    public static final List<String> SIGNALS = List.of("messageReceived", "sending", "edited", "deleted",
            "linkClicked", "bodyClicked", "messageClicked", "opened", "closed", "typing");

    private static final List<String> HOOKS = List.of("onIncoming", "shouldSend", "onBubble");

    private final Host host;
    private final Map<String, HostSignal> signals = new LinkedHashMap<>();
    private final Map<String, Callable> hooks = new HashMap<>();

    private ChatLibrary(Host host) {
        this.host = host;
    }

    public static ChatLibrary install(Host host, Members game) {
        ChatLibrary library = new ChatLibrary(host);
        ChatRef chat = host.chat();
        host.api().alias("ChatMessage", "{ id: number, text: string, prefix: string, metadata: string, channel: Instance?, "
                + "source: Instance?, body: Instance?, position: Vector3?, timestamp: number, status: string, [string]: any }");
        host.api().alias("ChatCheck", "(message: ChatMessage, source: Instance) -> boolean");

        Members members = new Members("Chat")
                .method("send", "(channelOrText: Instance | string, textOrOptions: (string | { [string]: any })?, options: { [string]: any }?) -> number", a -> {
                    Map<String, Object> message;
                    if (a.get(1) instanceof Instance channel) {
                        message = options(a, 3);
                        message.put("channel", channel);
                        message.put("text", a.string(2));
                    } else {
                        message = options(a, 2);
                        message.put("text", a.string(1));
                    }
                    return (double) guard(() -> chat.send(message));
                })
                .method("system", "(text: string, options: { [string]: any }?) -> number", a -> {
                    Map<String, Object> message = options(a, 2);
                    message.put("text", a.string(1));
                    message.remove("from");
                    message.put("system", true);
                    return (double) guard(() -> chat.send(message));
                })
                .method("edit", "(id: number, changes: string | { [string]: any }) -> ()", a -> {
                    long id = (long) a.number(1);
                    Map<String, Object> changes = a.get(2) instanceof String text ? new HashMap<>(Map.of("text", text)) : options(a, 2);
                    return perform(() -> chat.edit(id, changes));
                })
                .method("delete", "(id: number) -> ()", a -> perform(() -> chat.delete((long) a.number(1))))
                .method("addPlayer", "(channel: Instance, body: Instance) -> ()", a -> perform(() -> chat.addPlayer(a.instance(1), a.instance(2))))
                .method("removePlayer", "(channel: Instance, body: Instance) -> ()", a -> perform(() -> chat.removePlayer(a.instance(1), a.instance(2))))
                .method("open", "(prefill: string?) -> ()", a -> perform(() -> chat.open(a.string(1, ""))))
                .method("close", "() -> ()", a -> perform(chat::close))
                .method("isOpen", "() -> boolean", a -> guard(chat::isOpen))
                .method("clear", "() -> ()", a -> perform(chat::clear))
                .method("setTarget", "(channel: Instance?) -> ()", a -> perform(() -> chat.setTarget(a.instance(1, null))))
                .method("getTarget", "() -> Instance?", a -> guard(chat::target))
                .method("messages", "() -> { ChatMessage }", a -> guard(chat::messages))
                .method("bubble", "(target: Instance, text: string, look: { [string]: any }?) -> ()",
                        a -> perform(() -> chat.bubble(a.instance(1), a.string(2), options(a, 3))))
                .function("escape", "(text: string) -> string", a -> RichText.escape(a.string(0)))
                .function("plain", "(text: string) -> string", a -> RichText.plain(a.string(0)))
                .function("bodyLink", "(body: Instance, label: string?) -> string", a -> {
                    Instance body = a.instance(0);
                    return "<body id=" + body.id() + ">" + RichText.escape(a.string(1, body.name())) + "</body>";
                })
                .function("itemLink", "(id: string, count: number?) -> string",
                        a -> "<item id=\"" + a.string(0).replace("\"", "") + "\" count=" + a.integer(1, 1) + "/>")
                .function("within", "(range: number) -> ChatCheck", a -> library.within(a.number(0)))
                .function("all", "(...ChatCheck) -> ChatCheck", a -> library.combine(a, true))
                .function("any", "(...ChatCheck) -> ChatCheck", a -> library.combine(a, false))
                .function("distance", "(message: ChatMessage, source: Instance) -> number?", a -> {
                    Vector3 from = position(a.get(0));
                    Instance body = body(a.get(1));
                    return from == null || body == null ? null : Players.position(body).sub(from).length();
                })
                .function("audible", "(range: number) -> ChatCheck", a -> library.audible(a.number(0)))
                .function("sameTag", "(tag: string) -> ChatCheck", a -> {
                    String tag = a.string(0);
                    return (Callable) args -> {
                        Instance body = body(args.length > 1 ? args[1] : null);
                        return new Object[] {body != null && body.hasTag(tag)};
                    };
                });
        for (String hook : HOOKS) {
            members.field(hook, hookType(hook), () -> library.hooks.get(hook), value -> library.assign(hook, value));
        }
        for (String name : SIGNALS) {
            HostSignal signal = new HostSignal(host, name.equals("messageReceived") || name.equals("sending")
                    || name.equals("edited") || name.equals("messageClicked") ? "ChatMessageSignal" : "AnySignal", "chat." + name);
            library.signals.put(name, signal);
            members.value(name, signal.typeName(), signal);
        }
        host.api().declare(HostSignal.decl("ChatMessageSignal", "(message: ChatMessage) -> ()"));
        host.onClose(() -> library.hooks.values().forEach(Callable::release));
        host.declare(members);
        game.value("chat", "Chat", members);
        return library;
    }

    private static String hookType(String hook) {
        return switch (hook) {
            case "onIncoming" -> "((message: ChatMessage) -> { [string]: any }?)?";
            case "shouldSend" -> "((message: ChatMessage) -> boolean?)?";
            default -> "((message: ChatMessage) -> (boolean | { [string]: any })?)?";
        };
    }

    private void assign(String hook, Object value) {
        Callable old = hooks.remove(hook);
        if (old != null) old.release();
        if (value == null) return;
        if (!(value instanceof Callable fn)) throw new HostError("chat.%s expects a function or nil", hook);
        hooks.put(hook, fn.retain());
    }

    public Object[] hook(String name, Object... args) {
        Callable fn = hooks.get(name);
        return fn == null ? null : host.call(fn, "chat." + name, args);
    }

    public void fire(String name, Object... args) {
        HostSignal signal = signals.get(name);
        if (signal != null) signal.fire(args);
    }

    private Callable within(double range) {
        double limit = range * range;
        return args -> {
            Vector3 from = position(args.length > 0 ? args[0] : null);
            Instance body = body(args.length > 1 ? args[1] : null);
            if (from == null || body == null) return new Object[] {true};
            return new Object[] {Players.position(body).sub(from).lengthSq() <= limit};
        };
    }

    private Callable audible(double range) {
        Callable near = within(range);
        return args -> {
            if (Boolean.FALSE.equals(near.call(args)[0])) return new Object[] {false};
            Instance from = args.length > 0 && args[0] instanceof Map<?, ?> message && message.get("body") instanceof Instance b ? b : null;
            Instance to = body(args.length > 1 ? args[1] : null);
            if (from == null || to == null) return new Object[] {true};
            Object seen = host.invoke((Builtin) host.index(from, "canSee"), new Object[] {from, to});
            return new Object[] {!Boolean.FALSE.equals(seen)};
        };
    }

    private Callable combine(Args a, boolean all) {
        Object[] raw = a.from(0);
        Callable[] checks = new Callable[raw.length];
        for (int n = 0; n < raw.length; n++) {
            if (!(raw[n] instanceof Callable check)) throw a.error("argument %d expects a function", n + 1);
            checks[n] = check.retain();
        }
        return args -> {
            for (Callable check : checks) {
                Object[] out = check.call(args);
                boolean passed = out == null || out.length == 0 || !Boolean.FALSE.equals(out[0]);
                if (all && !passed) return new Object[] {false};
                if (!all && passed) return new Object[] {true};
            }
            return new Object[] {all};
        };
    }

    private static Vector3 position(Object message) {
        return message instanceof Map<?, ?> map && map.get("position") instanceof Vector3 at ? at : null;
    }

    private static Instance body(Object source) {
        if (source instanceof Instance instance) {
            Object body = instance.def().property("body") == null ? null : instance.def().property("body").getObj(instance);
            return body instanceof Instance b && b.isAlive() ? b : null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> options(Args a, int at) {
        return a.get(at) instanceof Map<?, ?> map ? new HashMap<>((Map<String, Object>) map) : new HashMap<>();
    }

    private static <T> T guard(Supplier<T> body) {
        try {
            return body.get();
        } catch (UnsupportedOperationException | IllegalArgumentException | IllegalStateException e) {
            throw new HostError(e.getMessage());
        }
    }

    private static Object perform(Runnable body) {
        return guard(() -> {
            body.run();
            return null;
        });
    }
}
