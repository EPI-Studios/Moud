package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.text.Markup;
import com.meekdev.moud.script.api.ChatRef;
import com.meekdev.moud.script.err.ScriptError;
import java.util.function.Consumer;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;

// game.chat: say, escape, the messaged signal, and the format hook a place sets to style or drop what
// players type
public final class Chat {

    private Chat() {}

    public static void install(LuaState state, ChatRef chat, Signals.Handlers messaged) {
        state.getGlobal("game");
        state.newTable();
        state.pushFunction(LuaFunc.wrap(s -> {
            String text = s.checkString(2);
            String to = "";
            if (!s.isNoneOrNil(3)) {
                if (!(s.toUserDataTagged(3, Proxies.TAG) instanceof Character body) || body.owner.isEmpty()) {
                    throw s.error("chat:say sends to a body a player is wearing, or to everyone with no second argument");
                }
                to = body.owner;
            }
            chat.say(text, to);
            return 0;
        }, "chat:say"));
        state.rawSetField(-2, "say");
        state.pushFunction(LuaFunc.wrap(s -> {
            s.pushString(Markup.escape(s.checkString(1)));
            return 1;
        }, "chat.escape"));
        state.rawSetField(-2, "escape");
        Signals.push(state, messaged);
        state.rawSetField(-2, "messaged");
        state.rawSetField(-2, "chat");
        state.pop(1);
    }

    // what a player typed, turned into the line everyone sees. the place's format function decides when
    // it has one: a string is the line, nil drops the message. without one it is <name> text, with the
    // text escaped so a player cannot style their own message
    public static String format(LuaState state, Instance body, String name, String text, Signals.Handlers messaged,
                                Consumer<ScriptError> onError) {
        int top = state.top();
        String fallback = "<" + Markup.escape(name) + "> " + Markup.escape(text);
        state.getGlobal("game");
        state.rawGetField(-1, "chat");
        LuaType hook = state.type(-1) == LuaType.TABLE ? state.rawGetField(-1, "format") : LuaType.NIL;
        String line = fallback;
        if (hook == LuaType.FUNCTION) {
            if (body == null) {
                state.pushNil();
            } else {
                Proxies.push(state, body);
            }
            state.pushString(text);
            try {
                state.call(2, 1);
                line = state.isString(-1) ? state.toString(-1) : null;
            } catch (RuntimeException broken) {
                onError.accept(new ScriptError("chat.format", broken.getMessage(), broken));
                line = fallback;
            }
        }
        state.top(top);
        if (line != null) {
            Signals.fire(state, messaged, onError, s -> {
                if (body == null) {
                    s.pushNil();
                } else {
                    Proxies.push(s, body);
                }
                s.pushString(text);
                return 2;
            });
        }
        return line;
    }
}
