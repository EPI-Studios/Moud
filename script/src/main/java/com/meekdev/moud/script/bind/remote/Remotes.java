package com.meekdev.moud.script.bind.remote;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.remote.Remote;
import com.meekdev.moud.core.remote.Schema;
import com.meekdev.moud.core.remote.UnreliableRemote;
import com.meekdev.moud.script.api.PostRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import net.hollowcube.luau.LuaState;
import com.meekdev.moud.script.bind.Plain;
import com.meekdev.moud.script.bind.Proxies;

public final class Remotes {

    private record Side(PostRef post, boolean client) {}

    private static final Map<LuaState, Side> SIDES = new HashMap<>();

    private Remotes() {}

    public static void install(LuaState state, PostRef carrier, boolean onClient) {
        SIDES.put(state.mainThread(), new Side(carrier, onClient));
    }

    public static void forget(LuaState state) {
        SIDES.remove(state.mainThread());
    }

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
        if (known == null) throw state.error("no transport bound");
        return known;
    }

    public static int fireServer(LuaState state, Remote remote) {
        Side side = side(state);
        if (!side.client()) {
            throw state.error("fireServer is client-only");
        }
        if (!remote.isAlive()) {
            throw state.error("remote '%s' was destroyed, look it up again", remote.name());
        }
        side.post().toServer(remote.id(), declared(state, remote, 2), reliable(remote));
        return 0;
    }

    public static int fireClient(LuaState state, Remote remote) {
        Side side = side(state);
        if (side.client()) throw state.error("fireClient is server-only");
        Instance who = (Instance) state.toUserDataTagged(2, Proxies.TAG);
        if (!(who instanceof Character body)) {
            throw state.error("fireClient expects a body as the first argument");
        }
        side.post().toClient(body.owner, remote.id(), declared(state, remote, 3), reliable(remote));
        return 0;
    }

    public static int fireAllClients(LuaState state, Remote remote) {
        Side side = side(state);
        if (side.client()) throw state.error("fireAllClients is server-only");
        side.post().toAllClients(remote.id(), declared(state, remote, 2), reliable(remote));
        return 0;
    }

    private static List<Object> declared(LuaState state, Remote remote, int first) {
        List<Object> args = read(state, first);
        try {
            Schema.check(remote, args);
        } catch (IllegalArgumentException e) {
            throw state.error("%s", e.getMessage());
        }
        return args;
    }

    private static boolean reliable(Remote remote) {
        return !(remote instanceof UnreliableRemote);
    }

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
            Plain.push(state, arg);
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

    private static List<Object> read(LuaState state, int first) {
        int top = state.top();
        List<Object> args = new ArrayList<>(Math.max(0, top - first + 1));
        for (int at = first; at <= top; at++) args.add(Plain.read(state, at));
        return args;
    }

}
