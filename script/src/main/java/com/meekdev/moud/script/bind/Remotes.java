package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Remote;
import com.meekdev.moud.core.instance.Schema;
import com.meekdev.moud.core.instance.UnreliableRemote;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.script.api.PostRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;

// the place's side of a channel
//
// the two directions are separate methods rather than one send with a flag, and each is refused on
// the side it does not belong to. a place that calls fireServer on the server has made a mistake, and
// being told so beats a message that goes nowhere
public final class Remotes {

    // per lua state, not global
    //
    // a solo game runs a server vm and a client vm in one process, so "which side am I" cannot be a
    // static: the server would answer whatever the client installed last
    private record Side(PostRef post, boolean client) {}

    // keyed by the vm's main thread, and dropped when the vm closes
    //
    // the main thread rather than whatever state a call arrived on, because they are not the same one:
    // a lua function called from java is handed its own state, and a coroutine has another again. so
    // the side has to be looked up against the one thing every one of them shares
    //
    // and dropped explicitly, because a LuaState's equals and hashCode are its native pointer's. a
    // closed state and a fresh one allocated at the same address are therefore the *same key* -- so
    // leaving the entry in handed a new vm the side of the one before it, which on a server is a
    // server that thinks it is a client. holding the key weakly never helped with that: the entry goes
    // when the key object is collected, which has nothing to do with when the state closed
    private static final Map<LuaState, Side> SIDES = new HashMap<>();

    private Remotes() {}

    public static void install(LuaState state, PostRef carrier, boolean onClient) {
        SIDES.put(state.mainThread(), new Side(carrier, onClient));
    }

    // a closed state is not a side any more, and leaving it in is how the next one gets the wrong one
    public static void forget(LuaState state) {
        SIDES.remove(state.mainThread());
    }

    // whether this state is a client's, and who it belongs to. asked by the write guard rather than
    // by a channel, which is why they are not private to the firing verbs
    public static boolean onClient(LuaState state) {
        Side known = SIDES.get(state.mainThread());
        return known != null && known.client();
    }

    public static String me(LuaState state) {
        Side known = SIDES.get(state.mainThread());
        return known == null ? "" : known.post().me();
    }

    private static Side side(LuaState state) {
        Side known = SIDES.get(state.mainThread());
        if (known == null) throw state.error("nothing is carrying messages on this side");
        return known;
    }

    public static int fireServer(LuaState state, Remote remote) {
        Side side = side(state);
        if (!side.client()) {
            throw state.error("fireServer is the client's, and this is the server."
                    + " the server says fireClient or fireAllClients");
        }
        side.post().toServer(remote.id(), declared(state, remote, 2), reliable(remote));
        return 0;
    }

    public static int fireClient(LuaState state, Remote remote) {
        Side side = side(state);
        if (side.client()) throw state.error("fireClient is the server's, and this is a client");
        Instance who = (Instance) state.toUserDataTagged(2, Proxies.TAG);
        if (!(who instanceof Character body)) {
            throw state.error("fireClient wants the body of whoever it is for, as the first argument");
        }
        side.post().toClient(body.owner, remote.id(), declared(state, remote, 3), reliable(remote));
        return 0;
    }

    public static int fireAllClients(LuaState state, Remote remote) {
        Side side = side(state);
        if (side.client()) throw state.error("fireAllClients is the server's, and this is a client");
        side.post().toAllClients(remote.id(), declared(state, remote, 2), reliable(remote));
        return 0;
    }

    // read the stack, then hold it to what the channel says it takes
    //
    // here as well as on the server, because the two checks are for different people: this one tells
    // the place which argument is wrong while it is still looking at the line that sent it, and the
    // one on receive is the one a tampered client cannot skip
    private static List<Object> declared(LuaState state, Remote remote, int first) {
        List<Object> args = read(state, first);
        try {
            Schema.check(remote, args);
        } catch (IllegalArgumentException wrong) {
            throw state.error("%s", wrong.getMessage());
        }
        return args;
    }

    private static boolean reliable(Remote remote) {
        return !(remote instanceof UnreliableRemote);
    }

    // what arrived, handed to a handler
    //
    // on the server the sender comes first and is the *body* of whoever sent it, which is how a
    // player is addressed everywhere else here -- `game.players:me()` answers with a body too. it is
    // supplied rather than passed, so it is the one argument a client cannot lie about
    public static int pushSent(LuaState state, Remote.Sent sent, InstanceTree tree) {
        int pushed = 0;
        if (!sent.from().isEmpty()) {
            Character body = bodyOf(tree, sent.from());
            if (body == null) {
                state.pushNil();
            } else {
                Proxies.push(state, body);
            }
            pushed++;
        }
        for (Object arg : sent.args()) {
            write(state, arg);
            pushed++;
        }
        return pushed;
    }

    private static Character bodyOf(InstanceTree tree, String owner) {
        if (tree == null) return null;
        for (Instance instance : tree.ofClass(Classes.CHARACTER)) {
            if (instance instanceof Character body && owner.equals(body.owner)) return body;
        }
        return null;
    }

    // the stack from `first` onward, as plain objects. the wire decides what may cross; this only
    // decides what a lua value is
    private static List<Object> read(LuaState state, int first) {
        int top = state.top();
        List<Object> args = new ArrayList<>(Math.max(0, top - first + 1));
        for (int at = first; at <= top; at++) args.add(one(state, at, 0));
        return args;
    }

    private static Object one(LuaState state, int at, int depth) {
        if (depth > 16) throw state.error("a table nested that deep is not a message");
        LuaType type = state.type(at);
        return switch (type) {
            case NIL, NONE -> null;
            case BOOLEAN -> state.toBoolean(at);
            case NUMBER -> state.toNumber(at);
            case STRING -> state.toString(at);
            case TABLE -> table(state, at, depth);
            case USERDATA -> userdata(state, at);
            default -> throw state.error("a %s cannot be sent", type.name().toLowerCase());
        };
    }

    private static Object userdata(LuaState state, int at) {
        Object value = Values.value(state, at);
        if (value != null) return value;
        Object instance = state.toUserDataTagged(at, Proxies.TAG);
        if (instance != null) return instance;
        throw state.error("that is not something that can be sent");
    }

    // a list or a table keyed by text, never both. roblox allows the mix and then warns you off it;
    // refusing it is the same advice with teeth
    private static Object table(LuaState state, int at, int depth) {
        int length = state.len(at);
        if (length > 0) {
            List<Object> list = new ArrayList<>(length);
            for (int n = 1; n <= length; n++) {
                state.rawGetI(at, n);
                list.add(one(state, state.top(), depth + 1));
                state.pop(1);
            }
            return list;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        state.pushNil();
        while (state.next(at < 0 ? at - 1 : at)) {
            if (state.type(-2) != LuaType.STRING) {
                throw state.error("a table that is sent is a list or is keyed by text");
            }
            map.put(state.toString(-2), one(state, state.top(), depth + 1));
            state.pop(1);
        }
        return map;
    }

    private static void write(LuaState state, Object value) {
        switch (value) {
            case null -> state.pushNil();
            case Boolean b -> state.pushBoolean(b);
            case Double d -> state.pushNumber(d);
            case String s -> state.pushString(s);
            case Vec3 v -> Values.push(state, v);
            case Quat q -> Values.push(state, q);
            case CFrame c -> Values.push(state, c);
            case Color c -> Values.push(state, c);
            case Instance i -> Proxies.push(state, i);
            case List<?> list -> {
                state.createTable(list.size(), 0);
                for (int n = 0; n < list.size(); n++) {
                    write(state, list.get(n));
                    state.rawSetI(-2, n + 1);
                }
            }
            case Map<?, ?> map -> {
                state.createTable(0, map.size());
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    write(state, entry.getValue());
                    state.rawSetField(-2, String.valueOf(entry.getKey()));
                }
            }
            default -> state.pushNil();
        }
    }
}
