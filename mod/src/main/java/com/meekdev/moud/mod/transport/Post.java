package com.meekdev.moud.mod.transport;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Remote;
import com.meekdev.moud.core.instance.Schema;
import com.meekdev.moud.net.transport.Transport;
import com.meekdev.moud.net.transport.Wire;
import com.meekdev.moud.mod.adapter.chat.ServerChat;
import com.meekdev.moud.script.api.PostRef;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// the one carrier both sides hold, and the two drains that turn a delivery into a signal
//
// it is the game's own connection, in a solo game as much as on somebody else's server. the seam
// §6.1 names is still the seam -- nothing above this finds out what is underneath it -- but there is
// one carrier under it rather than two, because a second path that only runs in single player is a
// path where a bug waits until the first time two people play together
public final class Post {

    private static final Logger LOGGER = LoggerFactory.getLogger("moud/post");

    private static final Wired CARRIER = new Wired();

    // a place's side of it, per side, because the guard on which verb is allowed is per side. and
    // because who "me" is differs: the server is nobody in particular and owns what it did not hand
    // out, a client is one player and may write what that player owns
    // who this client is, handed in by the client side rather than read here: this class is loaded on
    // a dedicated server too, and a field naming the local player would drag the client's world in
    private static Supplier<String> who = () -> "";

    public static final PostRef SERVER = ref(() -> "");
    public static final PostRef CLIENT = ref(() -> who.get());

    private Post() {}

    public static Wired wired() {
        return CARRIER;
    }

    // the codecs, then the two ends of them. declared once for both directions because both sides
    // have to be able to read what the other writes
    public static void install() {
        Packets.declare();
        CARRIER.listen();
        ServerChat.listen();
    }

    // asked rather than stored, because a respawn hands out a new player and a stored id would be
    // the one from before
    public static void installOnClient(Supplier<String> me) {
        who = me;
        CARRIER.listenAsClient();
    }

    // what clients sent, on the server's tick
    public static void drainToServer(InstanceTree tree) {
        if (tree == null) return;
        CARRIER.drainServer((player, id, args) -> deliver(tree, id, player, args, true));
    }

    // what the server sent, on the client's tick
    public static void drainToClient(InstanceTree tree) {
        if (tree == null) return;
        CARRIER.drainClient((id, args) -> deliver(tree, id, "", args, false));
    }

    private static void deliver(InstanceTree tree, int id, String from, List<Object> args,
                                boolean toServer) {
        Instance instance = tree.byId(id);
        // a channel that has been destroyed since the delivery left is not an error: it is the
        // ordinary race between one side closing it and the other hearing about it
        if (!(instance instanceof Remote remote)) return;
        List<Object> delivered = Wire.unpack(args, tree);
        // the check that matters, because this side did not write the client. a delivery of the
        // wrong shape is dropped and said once -- it is not the tick's fault and not a handler's
        try {
            Schema.check(remote, delivered);
        } catch (IllegalArgumentException wrong) {
            LOGGER.warn("dropped a delivery on {}: {}", remote.name(), wrong.getMessage());
            return;
        }
        Remote.Sent sent = new Remote.Sent(from, delivered);
        // a handler that throws must not take the rest of the batch with it, nor the tick
        try {
            if (toServer) {
                remote.onServer.fire(sent);
            } else {
                remote.onClient.fire(sent);
            }
        } catch (RuntimeException failed) {
            LOGGER.warn("a handler on {} threw: {}", remote.name(), failed.toString());
        }
    }

    private static PostRef ref(Supplier<String> who) {
        return new PostRef() {
            @Override
            public String me() {
                return who.get();
            }

            @Override
            public void toServer(int remote, List<Object> args, boolean reliable) {
                CARRIER.toServer(remote, Wire.pack(args), reliable);
            }

            @Override
            public void toClient(String player, int remote, List<Object> args, boolean reliable) {
                CARRIER.toClient(player, remote, Wire.pack(args), reliable);
            }

            @Override
            public void toAllClients(int remote, List<Object> args, boolean reliable) {
                CARRIER.toAllClients(remote, Wire.pack(args), reliable);
            }
        };
    }
}
