package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.text.RichText;
import com.meekdev.moud.script.api.ChatRef;
import com.meekdev.moud.script.err.ScriptError;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.ToIntFunction;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;

public final class Chat {

    public static final List<String> SIGNALS = List.of("messageReceived", "sending", "edited", "deleted",
            "linkClicked", "bodyClicked", "messageClicked", "opened", "closed", "typing");

    private final Map<String, Signals.Handlers> signals = new HashMap<>();
    private final LuaState state;
    private final Consumer<ScriptError> onError;

    public Chat(LuaState state, Consumer<ScriptError> onError) {
        this.state = state;
        this.onError = onError;
        for (String name : SIGNALS) signals.put(name, new Signals.Handlers());
    }

    public void install(ChatRef chat) {
        state.getGlobal("game");
        state.newTable();
        function("send", s -> {
            Map<String, Object> message;
            if (s.toUserDataTagged(2, Proxies.TAG) instanceof Instance channel) {
                message = options(s, 4);
                message.put("channel", channel);
                message.put("text", s.checkString(3));
            } else {
                message = options(s, 3);
                message.put("text", s.checkString(2));
            }
            s.pushNumber(run(s, () -> chat.send(message)));
            return 1;
        });
        function("system", s -> {
            Map<String, Object> message = options(s, 3);
            message.put("text", s.checkString(2));
            message.remove("from");
            message.put("system", true);
            s.pushNumber(run(s, () -> chat.send(message)));
            return 1;
        });
        function("edit", s -> {
            long id = (long) s.checkNumber(2);
            Map<String, Object> changes = s.type(3) == LuaType.STRING ? new HashMap<>(Map.of("text", s.toString(3))) : options(s, 3);
            run(s, () -> {
                chat.edit(id, changes);
                return 0L;
            });
            return 0;
        });
        function("delete", s -> {
            long id = (long) s.checkNumber(2);
            run(s, () -> {
                chat.delete(id);
                return 0L;
            });
            return 0;
        });
        function("addPlayer", s -> {
            Instance channel = instance(s, 2, "a channel");
            Instance body = instance(s, 3, "a body");
            run(s, () -> {
                chat.addPlayer(channel, body);
                return 0L;
            });
            return 0;
        });
        function("removePlayer", s -> {
            Instance channel = instance(s, 2, "a channel");
            Instance body = instance(s, 3, "a body");
            run(s, () -> {
                chat.removePlayer(channel, body);
                return 0L;
            });
            return 0;
        });
        function("open", s -> {
            String prefill = s.isNoneOrNil(2) ? "" : s.checkString(2);
            run(s, () -> {
                chat.open(prefill);
                return 0L;
            });
            return 0;
        });
        function("close", s -> {
            run(s, () -> {
                chat.close();
                return 0L;
            });
            return 0;
        });
        function("isOpen", s -> {
            boolean[] open = new boolean[1];
            run(s, () -> {
                open[0] = chat.isOpen();
                return 0L;
            });
            s.pushBoolean(open[0]);
            return 1;
        });
        function("clear", s -> {
            run(s, () -> {
                chat.clear();
                return 0L;
            });
            return 0;
        });
        function("setTarget", s -> {
            Instance channel = s.isNoneOrNil(2) ? null : instance(s, 2, "a channel");
            run(s, () -> {
                chat.setTarget(channel);
                return 0L;
            });
            return 0;
        });
        function("getTarget", s -> {
            Instance[] target = new Instance[1];
            run(s, () -> {
                target[0] = chat.target();
                return 0L;
            });
            Plain.push(s, target[0]);
            return 1;
        });
        function("messages", s -> {
            Object[] out = new Object[1];
            run(s, () -> {
                out[0] = chat.messages();
                return 0L;
            });
            Plain.push(s, out[0]);
            return 1;
        });
        function("bubble", s -> {
            Instance target = instance(s, 2, "a body or part");
            String text = s.checkString(3);
            Map<String, Object> look = options(s, 4);
            run(s, () -> {
                chat.bubble(target, text, look);
                return 0L;
            });
            return 0;
        });
        state.pushFunction(LuaFunc.wrap(s -> {
            s.pushString(RichText.escape(s.checkString(1)));
            return 1;
        }, "chat.escape"));
        state.rawSetField(-2, "escape");
        state.pushFunction(LuaFunc.wrap(s -> {
            s.pushString(RichText.plain(s.checkString(1)));
            return 1;
        }, "chat.plain"));
        state.rawSetField(-2, "plain");
        state.pushFunction(LuaFunc.wrap(s -> {
            Instance body = instance(s, 1, "a body");
            String label = s.isNoneOrNil(2) ? body.name() : s.checkString(2);
            s.pushString("<body id=" + body.id() + ">" + RichText.escape(label) + "</body>");
            return 1;
        }, "chat.bodyLink"));
        state.rawSetField(-2, "bodyLink");
        state.pushFunction(LuaFunc.wrap(s -> {
            String id = s.checkString(1);
            int count = s.isNoneOrNil(2) ? 1 : (int) s.checkNumber(2);
            s.pushString("<item id=\"" + id.replace("\"", "") + "\" count=" + count + "/>");
            return 1;
        }, "chat.itemLink"));
        state.rawSetField(-2, "itemLink");
        for (String name : SIGNALS) {
            Signals.push(state, signals.get(name));
            state.rawSetField(-2, name);
        }
        state.rawSetField(-2, "chat");
        state.pop(1);
    }

    public Object[] hook(String name, Object... args) {
        int top = state.top();
        try {
            state.getGlobal("game");
            if (state.type(-1) != LuaType.TABLE || state.rawGetField(-1, "chat") != LuaType.TABLE) return null;
            if (state.rawGetField(-1, name) != LuaType.FUNCTION) return null;
            int base = state.top() - 1;
            for (Object arg : args) Plain.push(state, arg);
            try {
                state.call(args.length, -1);
            } catch (RuntimeException broken) {
                onError.accept(new ScriptError("chat." + name, broken.getMessage(), broken));
                return null;
            }
            int count = state.top() - base;
            Object[] out = new Object[count];
            for (int n = 0; n < count; n++) out[n] = Plain.read(state, base + 1 + n);
            return out;
        } finally {
            state.top(top);
        }
    }

    public void fire(String name, Object... args) {
        Signals.Handlers handlers = signals.get(name);
        if (handlers == null) return;
        Signals.fire(state, handlers, onError, s -> {
            for (Object arg : args) Plain.push(s, arg);
            return args.length;
        });
    }

    private void function(String name, ToIntFunction<LuaState> body) {
        state.pushFunction(LuaFunc.wrap(body::applyAsInt, "chat:" + name));
        state.rawSetField(-2, name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> options(LuaState s, int at) {
        if (s.isNoneOrNil(at) || s.type(at) != LuaType.TABLE) return new HashMap<>();
        Object read = Plain.read(s, at);
        return read instanceof Map<?, ?> map ? new HashMap<>((Map<String, Object>) map) : new HashMap<>();
    }

    private static Instance instance(LuaState s, int at, String what) {
        if (!(s.toUserDataTagged(at, Proxies.TAG) instanceof Instance instance)) throw s.error("wants %s", what);
        return instance;
    }

    private static long run(LuaState s, LongSupplier body) {
        try {
            return body.getAsLong();
        } catch (UnsupportedOperationException | IllegalArgumentException | IllegalStateException wrong) {
            throw s.error("%s", wrong.getMessage());
        }
    }
}
